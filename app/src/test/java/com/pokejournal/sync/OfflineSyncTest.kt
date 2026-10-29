package com.pokejournal.sync

import com.pokejournal.data.repository.CreateResult
import com.pokejournal.data.repository.ImportantPokemonSource
import com.pokejournal.data.repository.PrefetchReport
import com.pokejournal.data.repository.RefreshResult
import com.pokejournal.domain.logic.HttpStatusException
import com.pokejournal.testing.FakeArtworkStore
import com.pokejournal.testing.FakeCollectionRepository
import com.pokejournal.testing.FakePokemonRepository
import com.pokejournal.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class OfflineSyncTest {
    private val pokemon = FakePokemonRepository()
    private val collections = FakeCollectionRepository()
    private val settings = FakeSettingsRepository()
    private val artwork = FakeArtworkStore()
    private var collectionIds = listOf(4, 7)
    private val important = object : ImportantPokemonSource {
        override suspend fun importantIds(): List<Int> = listOf(25, 1, 25)
        override suspend fun collectionIds(): List<Int> = collectionIds
    }
    private val sync = OfflineSync(pokemon, collections, settings, important, artwork)

    @Test
    fun `catalog failure stops sync before prefetch`() = runTest {
        pokemon.onRefreshCatalog = { RefreshResult.Failed(IOException()) }
        val outcome = sync.syncCatalogAndPrefetch()
        assertTrue(outcome is SyncOutcome.Failure)
        assertTrue(pokemon.prefetchRequests.isEmpty())
        assertTrue(artwork.ensureCalls.isEmpty())
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
    fun `partial prefetch failure is reported as retryable instead of plain success`() = runTest {
        pokemon.onRefreshDetail = { id, _ ->
            when (id) {
                25 -> RefreshResult.Failed(IOException("timeout"))
                1 -> RefreshResult.Failed(HttpStatusException(404))
                else -> RefreshResult.Updated
            }
        }

        val outcome = sync.syncCatalogAndPrefetch() as SyncOutcome.Success

        assertEquals(2, outcome.failedCount)
        assertTrue(outcome.hasRetryableFailures)
        assertEquals(1, outcome.prefetch?.retryable)
    }

    @Test
    fun `only permanent failures do not request retry`() = runTest {
        pokemon.onRefreshDetail = { _, _ -> RefreshResult.Failed(HttpStatusException(404)) }

        val outcome = sync.syncCatalogAndPrefetch() as SyncOutcome.Success

        assertTrue(outcome.failedCount > 0)
        assertFalse(outcome.hasRetryableFailures)
    }

    @Test
    fun `sync keeps artwork of collection members and removes orphaned files`() = runTest {
        artwork.stored += 150

        val outcome = sync.syncCatalogAndPrefetch() as SyncOutcome.Success

        assertEquals(listOf(4, 7), artwork.ensureCalls)
        assertEquals(setOf(4, 7), artwork.stored)
        assertEquals(setOf(4, 7), artwork.retainedWith)
        assertEquals(0, outcome.artwork?.failed)
    }

    @Test
    fun `download collection fetches card and image of every member and reports progress`() = runTest {
        val id = (collections.createCollection(1L, "Offline", "") as CreateResult.Created).id
        collections.setMembership(id, 4, true)
        collections.setMembership(id, 7, true)
        val progress = mutableListOf<Pair<Int, Int>>()

        val report = sync.downloadCollection(id) { done, total -> progress += done to total }

        assertEquals(OfflineDownloadReport(total = 2, ready = 2, retryable = 0), report)
        assertEquals(setOf(4, 7), pokemon.detailCalls.toSet())
        assertEquals(setOf(4, 7), artwork.stored)
        assertEquals(listOf(1 to 2, 2 to 2), progress)
    }

    @Test
    fun `collection member without image is not counted as available offline`() = runTest {
        val id = (collections.createCollection(1L, "Offline", "") as CreateResult.Created).id
        listOf(1, 4, 7).forEach { collections.setMembership(id, it, true) }
        artwork.failing = mapOf(4 to IOException("no network"), 7 to HttpStatusException(404))
        pokemon.onRefreshDetail = { pid, _ ->
            if (pid == 1) RefreshResult.Partial(IOException("species")) else RefreshResult.Updated
        }

        val report = sync.downloadCollection(id)

        assertEquals(0, report.ready)
        assertEquals(3, report.failed)
        assertEquals(2, report.retryable)
    }

    @Test
    fun `prefetch report counts every kind of result`() {
        val report = PrefetchReport.from(
            listOf(
                RefreshResult.Updated,
                RefreshResult.UpToDate,
                RefreshResult.Partial(IOException()),
                RefreshResult.Failed(IOException()),
                RefreshResult.Failed(HttpStatusException(404)),
            ),
        )
        assertEquals(PrefetchReport(total = 5, updated = 1, upToDate = 1, failed = 2, partial = 1, retryable = 2), report)
    }
}
