package com.pokejournal.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.pokejournal.data.repository.RefreshResult
import com.pokejournal.domain.model.CatchStatus
import com.pokejournal.testing.FakeCollectionRepository
import com.pokejournal.testing.FakeHistoryRepository
import com.pokejournal.testing.FakeJournalRepository
import com.pokejournal.testing.FakeNetworkMonitor
import com.pokejournal.testing.FakePokemonRepository
import com.pokejournal.testing.FakeSettingsRepository
import com.pokejournal.testing.MainDispatcherRule
import com.pokejournal.testing.TestData
import com.pokejournal.ui.navigation.Routes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class PokemonDetailViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val pokemon = FakePokemonRepository()
    private val journal = FakeJournalRepository()
    private val collections = FakeCollectionRepository()
    private val history = FakeHistoryRepository()
    private val settings = FakeSettingsRepository()
    private val network = FakeNetworkMonitor()

    private fun TestScope.createViewModel(id: Int = 25): PokemonDetailViewModel {
        val vm = PokemonDetailViewModel(
            SavedStateHandle(mapOf(Routes.ARG_ID to id)),
            pokemon, journal, collections, history, settings, network,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `reads id from SavedStateHandle and records history with profile limit`() = runTest {
        settings.updateUserSettings(1L) { it.copy(historyLimit = 20) }
        val vm = createViewModel(id = 25)
        advanceUntilIdle()

        assertEquals(25, vm.pokemonId)
        assertEquals(listOf(FakeHistoryRepository.Record(1L, 25, 20)), history.records)
        assertEquals(listOf(25), pokemon.detailCalls)
        val content = vm.uiState.value.content
        assertTrue(content is DetailContent.Success)
        assertEquals(25, (content as DetailContent.Success).pokemon.id)
    }

    @Test
    fun `error without cache then retry shows details`() = runTest {
        var attempt = 0
        pokemon.onRefreshDetail = { id, _ ->
            if (attempt++ == 0) {
                RefreshResult.Failed(IOException())
            } else {
                pokemon.details.value = mapOf(id to TestData.detail(id))
                RefreshResult.Updated
            }
        }
        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(DetailContent.Error("Нет подключения к сети"), vm.uiState.value.content)

        vm.onEvent(PokemonDetailEvent.Retry)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.content is DetailContent.Success)
    }

    @Test
    fun `offline with cached detail keeps Success and shows refresh error`() = runTest {
        pokemon.details.value = mapOf(25 to TestData.detail(25))
        pokemon.onRefreshDetail = { _, _ -> RefreshResult.Failed(IOException()) }
        network.online.value = false
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.content is DetailContent.Success)
        assertTrue(state.isOffline)
        assertEquals("Нет подключения к сети", state.refreshError)
    }

    @Test
    fun `personal data is separated by profile and follows profile switch`() = runTest {
        journal.setStatus(1L, 25, CatchStatus.CAUGHT)
        journal.saveNote(2L, 25, "Встретил в Viridian Forest")
        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(CatchStatus.CAUGHT, vm.uiState.value.userState.status)
        assertEquals("", vm.uiState.value.note)

        settings.setActiveProfile(2L)
        advanceUntilIdle()
        assertEquals(CatchStatus.NONE, vm.uiState.value.userState.status)
        assertEquals("Встретил в Viridian Forest", vm.uiState.value.note)
        // Открытие карточки попало в историю обоих профилей.
        assertEquals(listOf(1L, 2L), history.records.map { it.profileId })
    }

    @Test
    fun `create collection from detail adds pokemon and duplicate name is rejected`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onEvent(PokemonDetailEvent.CreateCollectionWithPokemon("Команда"))
        advanceUntilIdle()
        assertEquals("Добавлено в «Команда»", vm.messages.first())
        assertEquals(listOf(CollectionChoice(1L, "Команда", selected = true)), vm.uiState.value.collections)

        vm.onEvent(PokemonDetailEvent.CreateCollectionWithPokemon("команда"))
        advanceUntilIdle()
        assertEquals("Такое название уже есть", vm.messages.first())
        assertEquals(1, collections.collections.value.size)
    }
}
