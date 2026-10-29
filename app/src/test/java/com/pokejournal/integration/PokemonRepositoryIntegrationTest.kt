package com.pokejournal.integration

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pokejournal.data.local.AppDatabase
import com.pokejournal.data.repository.OfflineFirstPokemonRepository
import com.pokejournal.data.repository.RefreshResult
import com.pokejournal.testing.FakePokeApi
import com.pokejournal.testing.FakeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PokemonRepositoryIntegrationTest {

    private lateinit var db: AppDatabase
    private var api = FakePokeApi()
    private val time = FakeTime()
    private lateinit var repository: OfflineFirstPokemonRepository
    private val ttl = 24 * 3_600_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = OfflineFirstPokemonRepository(api, db, time)
    }

    @After
    fun tearDown() {
        db.close()
    }


    private fun useLargeCatalog(size: Int) {
        api = FakePokeApi.withCatalog(size)
        repository = OfflineFirstPokemonRepository(api, db, time)
    }

    @Test
    fun `catalog refresh stores pokemon with types and ignores alternate forms`() = runTest {
        val result = repository.refreshCatalog(limit = 151, ttlMillis = ttl, force = false)

        assertEquals(RefreshResult.Updated, result)
        val catalog = repository.observeCatalog().first()
        assertEquals(listOf(1, 4, 7, 25), catalog.map { it.id })
        assertEquals(listOf("grass", "poison"), catalog.first().types)
        assertEquals(time.current, repository.observeLastCatalogSync().first())
    }

    @Test
    fun `fresh cache is not re-downloaded until ttl expires or force`() = runTest {
        repository.refreshCatalog(151, ttl, force = false)
        assertEquals(RefreshResult.UpToDate, repository.refreshCatalog(151, ttl, force = false))
        assertEquals(1, api.listCalls)

        assertEquals(RefreshResult.Updated, repository.refreshCatalog(151, ttl, force = true))
        assertEquals(2, api.listCalls)

        time.advance(ttl)
        assertEquals(RefreshResult.Updated, repository.refreshCatalog(151, ttl, force = false))
        assertEquals(3, api.listCalls)


        assertEquals(RefreshResult.Updated, repository.refreshCatalog(251, ttl, force = false))
    }

    @Test
    fun `network failure keeps previously cached data readable`() = runTest {
        repository.refreshCatalog(151, ttl, force = false)
        api.offline = true

        val result = repository.refreshCatalog(151, ttl, force = true)

        assertTrue(result is RefreshResult.Failed)
        assertEquals(4, repository.observeCatalog().first().size)
    }

    @Test
    fun `detail is persisted, re-read from Room and survives catalog refresh`() = runTest {
        repository.refreshCatalog(151, ttl, force = false)
        assertNull(repository.observeDetail(1).first()?.fetchedAt)

        repository.refreshDetail(1, ttl, force = false)
        val detail = repository.observeDetail(1).first()
        assertNotNull(detail?.fetchedAt)
        assertEquals("Seed Pokémon", detail?.genus)
        assertEquals("A strange seed was planted on its back.", detail?.flavorText)
        assertEquals(listOf("hp", "attack"), detail?.stats?.map { it.name })


        repository.refreshCatalog(151, ttl, force = true)
        assertNotNull(repository.observeDetail(1).first()?.fetchedAt)


        api.offline = true
        assertTrue(repository.refreshDetail(1, ttl, force = true) is RefreshResult.Failed)
        assertEquals("bulbasaur", repository.observeDetail(1).first()?.name)
    }

    @Test
    fun `prefetch skips fresh details, dedupes ids and counts failures`() = runTest {
        repository.refreshDetail(1, ttl, force = false)
        api.failingPokemonIds = setOf(7)
        val callsBefore = api.pokemonCalls

        val report = repository.prefetchDetails(listOf(1, 4, 4, 7), ttl)

        assertEquals(3, report.total)
        assertEquals(1, report.upToDate)
        assertEquals(1, report.updated)
        assertEquals(1, report.failed)
        assertEquals(callsBefore + 2, api.pokemonCalls)
        assertEquals(2, repository.observeOfflineDetailCount().first())
    }



    @Test
    fun `full 649 catalog is stored with types despite sqlite parameter limit`() = runTest {
        useLargeCatalog(649)

        assertEquals(RefreshResult.Updated, repository.refreshCatalog(649, ttl, force = false))

        val catalog = repository.observeCatalog().first()
        assertEquals((1..649).toList(), catalog.map { it.id })
        assertTrue(catalog.all { it.types.isNotEmpty() })
        assertTrue(catalog.none { it.id > 10_000 })
    }

    @Test
    fun `refresh after shrinking pokedex does not mark the larger range as fresh`() = runTest {
        useLargeCatalog(649)
        repository.refreshCatalog(649, ttl, force = false)
        time.advance(ttl / 2)


        assertEquals(RefreshResult.Updated, repository.refreshCatalog(151, ttl, force = true))
        assertEquals(2, api.listCalls)

        assertEquals(RefreshResult.Updated, repository.refreshCatalog(649, ttl, force = false))
        assertEquals(3, api.listCalls)

        assertEquals(649, repository.observeCatalog().first().size)
    }

    @Test
    fun `smaller pokedex is served from a fresh larger cache without network`() = runTest {
        useLargeCatalog(649)
        repository.refreshCatalog(649, ttl, force = false)

        assertEquals(RefreshResult.UpToDate, repository.refreshCatalog(151, ttl, force = false))
        assertEquals(1, api.listCalls)
    }



    @Test
    fun `species failure saves card as incomplete and it is refetched before ttl`() = runTest {
        api.failingSpeciesIds = setOf(1)

        val first = repository.refreshDetail(1, ttl, force = false)

        assertTrue(first is RefreshResult.Partial)
        val partial = repository.observeDetail(1).first()!!
        assertNotNull(partial.fetchedAt) // карточка всё равно доступна офлайн
        assertEquals(false, partial.speciesLoaded)
        assertNull(partial.genus)


        api.failingSpeciesIds = emptySet()
        assertEquals(RefreshResult.Updated, repository.refreshDetail(1, ttl, force = false))
        val full = repository.observeDetail(1).first()!!
        assertEquals(true, full.speciesLoaded)
        assertEquals("Seed Pokémon", full.genus)
        assertEquals(RefreshResult.UpToDate, repository.refreshDetail(1, ttl, force = false))
    }

    @Test
    fun `species failure on refresh keeps previously loaded description`() = runTest {
        repository.refreshDetail(1, ttl, force = false)
        api.failingSpeciesIds = setOf(1)

        assertTrue(repository.refreshDetail(1, ttl, force = true) is RefreshResult.Partial)

        assertEquals("Seed Pokémon", repository.observeDetail(1).first()?.genus)
    }

    @Test
    fun `missing species (404) is permanent and does not cause endless refetch`() = runTest {
        api.notFoundSpeciesIds = setOf(1)

        assertEquals(RefreshResult.Updated, repository.refreshDetail(1, ttl, force = false))
        assertEquals(RefreshResult.UpToDate, repository.refreshDetail(1, ttl, force = false))
        assertEquals(1, api.pokemonCalls)
    }


    @Test
    fun `prefetch runs in parallel but never exceeds the concurrency limit`() = runTest {
        useLargeCatalog(151)
        api.pokemonDelayMillis = 100
        val progress = mutableListOf<Int>()


        val report = withContext(Dispatchers.Default) {
            repository.prefetchDetails((1..40).toList(), ttl) { done, total ->
                assertEquals(40, total)
                progress += done
            }
        }

        assertEquals(40, report.updated)
        assertEquals(OfflineFirstPokemonRepository.PREFETCH_CONCURRENCY, api.maxConcurrentPokemonCalls)
        assertEquals((1..40).toList(), progress)
        assertEquals(40, repository.observeOfflineDetailCount().first())
    }

    @Test
    fun `prefetch of a large set reports partial failures and which of them are retryable`() = runTest {
        useLargeCatalog(649)
        api.failingPokemonIds = setOf(10, 20, 30)
        api.notFoundPokemonIds = setOf(40)
        api.failingSpeciesIds = setOf(50)

        val report = repository.prefetchDetails((1..200).toList(), ttl)

        assertEquals(200, report.total)
        assertEquals(195, report.updated)
        assertEquals(4, report.failed)
        assertEquals(1, report.partial)
        assertEquals(4, report.retryable)
        assertEquals(196, repository.observeOfflineDetailCount().first())
    }
}
