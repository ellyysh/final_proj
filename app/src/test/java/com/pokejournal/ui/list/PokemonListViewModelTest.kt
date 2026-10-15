package com.pokejournal.ui.list

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.pokejournal.data.repository.RefreshResult
import com.pokejournal.testing.CatalogCall
import com.pokejournal.testing.FakeJournalRepository
import com.pokejournal.testing.FakeNetworkMonitor
import com.pokejournal.testing.FakePokemonRepository
import com.pokejournal.testing.FakePresetRepository
import com.pokejournal.testing.FakeSettingsRepository
import com.pokejournal.testing.MainDispatcherRule
import com.pokejournal.testing.TestData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class PokemonListViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val pokemon = FakePokemonRepository()
    private val journal = FakeJournalRepository()
    private val presets = FakePresetRepository()
    private val settings = FakeSettingsRepository()
    private val network = FakeNetworkMonitor()

    private fun createViewModel() = PokemonListViewModel(pokemon, journal, presets, settings, network)

    /** Собирает состояния, пока не выполнится условие; возвращает всю последовательность. */
    private suspend fun ReceiveTurbine<PokemonListUiState>.collectUntil(
        predicate: (PokemonListUiState) -> Boolean,
    ): List<PokemonListUiState> {
        val result = mutableListOf<PokemonListUiState>()
        do {
            val item = awaitItem()
            result += item
        } while (!predicate(item))
        return result
    }

    private fun List<PokemonListUiState>.contentKinds(): List<String> =
        map { it.content::class.simpleName!! }.fold(emptyList()) { acc, kind -> if (acc.lastOrNull() == kind) acc else acc + kind }

    private fun PokemonListUiState.ids(): List<Int> =
        (content as? ListContent.Success)?.items?.map { it.summary.id }.orEmpty()

    @Test
    fun `initial load emits Loading then Success without flicker`() = runTest {
        pokemon.onRefreshCatalog = {
            delay(1_000)
            pokemon.catalog.value = TestData.catalog
            RefreshResult.Updated
        }
        val vm = createViewModel()

        vm.uiState.test {
            val states = collectUntil { it.content is ListContent.Success && !it.isRefreshing }
            assertEquals(listOf("Loading", "Success"), states.contentKinds())
            assertEquals(TestData.catalog.map { it.id }, states.last().ids())
            cancelAndIgnoreRemainingEvents()
        }
        // Первая загрузка уважает TTL (force = false) и размер Pokédex из настроек профиля.
        assertEquals(listOf(CatalogCall(limit = 151, force = false)), pokemon.catalogCalls)
    }

    @Test
    fun `error without cache shows Error, retry leads to Loading then Success`() = runTest {
        var attempt = 0
        pokemon.onRefreshCatalog = {
            delay(100)
            if (attempt++ == 0) {
                RefreshResult.Failed(IOException("no network"))
            } else {
                pokemon.catalog.value = TestData.catalog
                RefreshResult.Updated
            }
        }
        val vm = createViewModel()

        vm.uiState.test {
            val beforeRetry = collectUntil { it.content is ListContent.Error }
            assertEquals(listOf("Loading", "Error"), beforeRetry.contentKinds())
            assertEquals("Нет подключения к сети", (beforeRetry.last().content as ListContent.Error).message)

            vm.onEvent(PokemonListEvent.Retry)
            val afterRetry = collectUntil { it.content is ListContent.Success && !it.isRefreshing }
            assertEquals(listOf("Loading", "Success"), afterRetry.contentKinds())
            cancelAndIgnoreRemainingEvents()
        }
        // retry() действительно инициирует новую попытку и делает её принудительной.
        assertEquals(listOf(false, true), pokemon.catalogCalls.map { it.force })
    }

    @Test
    fun `cached data stays visible when refresh fails offline`() = runTest {
        pokemon.catalog.value = TestData.catalog
        pokemon.onRefreshCatalog = { RefreshResult.Failed(IOException("offline")) }
        network.online.value = false
        val vm = createViewModel()

        vm.uiState.test {
            val state = collectUntil { it.refreshError != null }.last()
            assertTrue(state.content is ListContent.Success)
            assertTrue(state.isOffline)
            assertEquals("Нет подключения к сети", state.refreshError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search with no results gives Empty instead of Success with empty list`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            collectUntil { it.content is ListContent.Success }
            vm.onEvent(PokemonListEvent.QueryChanged("missingno"))
            val state = collectUntil { it.content is ListContent.Empty }.last()
            assertEquals(ListContent.Empty(EmptyReason.NO_MATCHES), state.content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fast typing is debounced and filter is applied exactly once`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            collectUntil { it.content is ListContent.Success && !it.isRefreshing }

            vm.onEvent(PokemonListEvent.QueryChanged("b"))
            advanceTimeBy(100)
            vm.onEvent(PokemonListEvent.QueryChanged("bu"))
            advanceTimeBy(100)
            vm.onEvent(PokemonListEvent.QueryChanged("bul"))

            val states = collectUntil { it.ids() == listOf(1) }
            // Пока пользователь печатает, текст поля обновляется сразу, а список — нет.
            val intermediate = states.dropLast(1)
            assertTrue(intermediate.all { it.ids() == TestData.catalog.map { p -> p.id } })
            assertEquals("bul", states.last().query)
            // Никаких промежуточных отфильтрованных списков для "b" и "bu".
            assertEquals(2, (states.map { it.ids() } + listOf(TestData.catalog.map { it.id })).distinct().size)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry cancels a stale in-flight refresh so its failure never reaches UI`() = runTest {
        var staleCancelled = false
        var call = 0
        pokemon.onRefreshCatalog = {
            if (call++ == 0) {
                try {
                    delay(5_000)
                    RefreshResult.Failed(IOException("stale"))
                } catch (e: CancellationException) {
                    staleCancelled = true
                    throw e
                }
            } else {
                pokemon.catalog.value = TestData.catalog
                RefreshResult.Updated
            }
        }
        val vm = createViewModel()

        vm.uiState.test {
            advanceTimeBy(100)
            vm.onEvent(PokemonListEvent.Retry)
            val states = collectUntil { it.content is ListContent.Success && !it.isRefreshing }
            advanceTimeBy(10_000)
            expectNoEvents()
            assertFalse(states.any { it.content is ListContent.Error || it.refreshError != null })
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(staleCancelled)
    }

    @Test
    fun `journal changes from Room update list reactively without refresh`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            collectUntil { it.content is ListContent.Success && !it.isRefreshing }
            val callsBefore = pokemon.catalogCalls.size

            journal.setHidden(1L, 1, hidden = true)
            journal.setPinned(1L, 151, pinned = true)

            val state = collectUntil { it.ids().firstOrNull() == 151 && 1 !in it.ids() }.last()
            assertEquals(listOf(151, 4, 7, 25), state.ids())
            assertEquals(callsBefore, pokemon.catalogCalls.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `extending pokedex in settings and network restore trigger refresh`() = runTest {
        val vm = createViewModel()
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        settings.updateUserSettings(1L) { it.copy(pokedexSize = 251) }
        advanceUntilIdle()
        network.online.value = false
        runCurrent()
        network.online.value = true
        advanceUntilIdle()

        assertEquals(
            listOf(CatalogCall(151, false), CatalogCall(251, false), CatalogCall(251, false)),
            pokemon.catalogCalls,
        )
        job.cancel()
    }

    @Test
    fun `saving preset emits one time message and preset appears in state`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            collectUntil { it.content is ListContent.Success }
            vm.onEvent(PokemonListEvent.QueryChanged("pika"))
            vm.onEvent(PokemonListEvent.SavePreset("Электро"))
            // One-time событие приходит отдельным потоком и не хранится в состоянии экрана.
            val message = vm.messages.first()
            assertEquals("Фильтр «Электро» сохранён", message)
            val state = collectUntil { it.presets.isNotEmpty() }.last()
            assertEquals("pika", state.presets.single().query)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
