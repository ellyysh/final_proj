package com.pokejournal.integration

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pokejournal.data.local.AppDatabase
import com.pokejournal.data.repository.OfflineFirstPokemonRepository
import com.pokejournal.data.repository.RefreshResult
import com.pokejournal.testing.FakePokeApi
import com.pokejournal.testing.FakeTime
import kotlinx.coroutines.flow.first
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

/** Интеграция data-слоя: Repository + Fake API + настоящая Room (in-memory). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PokemonRepositoryIntegrationTest {

    private lateinit var db: AppDatabase
    private val api = FakePokeApi()
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

        // Расширение Pokédex требует догрузки даже при свежем кэше.
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

        // Обновление каталога не должно затирать загруженные детали.
        repository.refreshCatalog(151, ttl, force = true)
        assertNotNull(repository.observeDetail(1).first()?.fetchedAt)

        // Офлайн карточка читается из Room повторно.
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
}
