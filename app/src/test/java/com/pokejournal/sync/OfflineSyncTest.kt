package com.pokejournal.sync

import com.pokejournal.data.repository.CreateResult
import com.pokejournal.data.repository.ImportantPokemonSource
import com.pokejournal.data.repository.RefreshResult
import com.pokejournal.testing.FakeCollectionRepository
import com.pokejournal.testing.FakePokemonRepository
import com.pokejournal.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class OfflineSyncTest {
    private val pokemon = FakePokemonRepository()
    private val collections = FakeCollectionRepository()
    private val settings = FakeSettingsRepository()
    private val important = object : ImportantPokemonSource {
        override suspend fun importantIds(): List<Int> = listOf(25, 1, 25)
    }
    private val sync = OfflineSync(pokemon, collections, settings, important)

    @Test
    fun `catalog failure stops sync before prefetch`() = runTest {
        pokemon.onRefreshCatalog = { RefreshResult.Failed(IOException()) }
        val outcome = sync.syncCatalogAndPrefetch()
        assertTrue(outcome is SyncOutcome.Failure)
        assertTrue(pokemon.prefetchRequests.isEmpty())
    }

    @Test
    fun `prefetch of important pokemon respects setting and active profile pokedex size`() = runTest {
        settings.updateUserSettings(1L) { it.copy(pokedexSize = 386) }
        val outcome = sync.syncCatalogAndPrefetch()
        assertTrue(outcome is SyncOutcome.Success)
        assertEquals(386, pokemon.catalogCalls.single().limit)
        assertEquals(listOf(listOf(25, 1, 25)), pokemon.prefetchRequests)

        settings.updateAppSettings { it.copy(offlinePrefetchEnabled = false) }
        val second = sync.syncCatalogAndPrefetch() as SyncOutcome.Success
        assertEquals(null, second.prefetch)
        assertEquals(1, pokemon.prefetchRequests.size)
    }

    @Test
    fun `download collection prefetches every member and reports progress`() = runTest {
        val id = (collections.createCollection(1L, "Offline", "") as CreateResult.Created).id
        collections.setMembership(id, 4, true)
        collections.setMembership(id, 7, true)
        val progress = mutableListOf<Pair<Int, Int>>()

        val report = sync.downloadCollection(id) { done, total -> progress += done to total }

        assertEquals(2, report.total)
        assertEquals(listOf(4, 7), pokemon.prefetchRequests.single())
        assertEquals(listOf(1 to 2, 2 to 2), progress)
    }
}
