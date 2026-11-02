package com.pokejournal.integration

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.pokejournal.data.images.FileArtworkStore
import com.pokejournal.data.local.AppDatabase
import com.pokejournal.data.local.entity.ProfileEntity
import com.pokejournal.data.repository.CreateResult
import com.pokejournal.data.repository.OfflineFirstPokemonRepository
import com.pokejournal.data.repository.RoomCollectionRepository
import com.pokejournal.data.repository.RoomImportantPokemonSource
import com.pokejournal.data.repository.RoomJournalRepository
import com.pokejournal.domain.model.CatchStatus
import com.pokejournal.domain.model.SpeciesState
import com.pokejournal.sync.CatalogSyncWorker
import com.pokejournal.sync.CollectionDownloadWorker
import com.pokejournal.sync.OfflineSync
import com.pokejournal.sync.WorkKeys
import com.pokejournal.testing.FakeArtworkServer
import com.pokejournal.testing.FakePokeApi
import com.pokejournal.testing.FakeSettingsRepository
import com.pokejournal.testing.FakeTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkerIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val api = FakePokeApi()
    private val time = FakeTime()
    private val settings = FakeSettingsRepository()
    private val artworkServer = FakeArtworkServer()
    private lateinit var artwork: FileArtworkStore
    private lateinit var offlineSync: OfflineSync
    private lateinit var repository: OfflineFirstPokemonRepository
    private lateinit var collections: RoomCollectionRepository

    private val factory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? = when (workerClassName) {
            CatalogSyncWorker::class.java.name -> CatalogSyncWorker(appContext, workerParameters, offlineSync)
            CollectionDownloadWorker::class.java.name -> CollectionDownloadWorker(appContext, workerParameters, offlineSync)
            else -> null
        }
    }

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.profileDao().insert(ProfileEntity(id = 1, name = "Ash", avatar = "🔥", createdAt = 1))
        repository = OfflineFirstPokemonRepository(api, db, time)
        collections = RoomCollectionRepository(db, time)
        artwork = FileArtworkStore(context, artworkServer.client, db, time)
        offlineSync = OfflineSync(repository, collections, settings, RoomImportantPokemonSource(db.syncDao()), artwork)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun catalogWorker(attempt: Int = 0) = TestListenableWorkerBuilder<CatalogSyncWorker>(context)
        .setWorkerFactory(factory)
        .setRunAttemptCount(attempt)
        .build()

    private fun collectionWorker(collectionId: Long, attempt: Int = 0) =
        TestListenableWorkerBuilder<CollectionDownloadWorker>(
            context,
            inputData = workDataOf(WorkKeys.COLLECTION_ID to collectionId),
        ).setWorkerFactory(factory).setRunAttemptCount(attempt).build()

    private suspend fun collectionOf(vararg ids: Int): Long {
        repository.refreshCatalog(151, 1_000_000L, force = false)
        val id = (collections.createCollection(1L, "Trip", "") as CreateResult.Created).id
        ids.forEach { collections.setMembership(id, it, true) }
        return id
    }

    @Test
    fun `catalog sync worker refreshes catalog and prefetches pokemon from journal`() = runTest {
        RoomJournalRepository(db, time).setStatus(1L, 25, CatchStatus.CAUGHT)

        val result = catalogWorker().doWork()

        assertEquals(ListenableWorker.Result.success(workDataOf(WorkKeys.UPDATED to 1, WorkKeys.FAILED to 0)), result)
        assertEquals(4, repository.observeCatalog().first().size)
        assertNotNull(repository.observeDetail(25).first()?.fetchedAt)
    }

    @Test
    fun `catalog sync worker asks for retry when network is down and gives up after max attempts`() = runTest {
        api.offline = true

        assertEquals(ListenableWorker.Result.retry(), catalogWorker(0).doWork())
        assertEquals(ListenableWorker.Result.failure(), catalogWorker(CatalogSyncWorker.MAX_ATTEMPTS).doWork())
    }

    @Test
    fun `partial prefetch failure makes worker retry and fails with error count after max attempts`() = runTest {
        val journal = RoomJournalRepository(db, time)
        journal.setStatus(1L, 1, CatchStatus.CAUGHT)
        journal.setStatus(1L, 25, CatchStatus.CAUGHT)
        api.failingPokemonIds = setOf(25)

        assertEquals(ListenableWorker.Result.retry(), catalogWorker(0).doWork())
        assertNotNull(repository.observeDetail(1).first()?.fetchedAt)

        assertEquals(
            ListenableWorker.Result.failure(workDataOf(WorkKeys.UPDATED to 0, WorkKeys.FAILED to 1)),
            catalogWorker(CatalogSyncWorker.MAX_ATTEMPTS).doWork(),
        )
    }

    @Test
    fun `permanent prefetch failure fails the sync without retry`() = runTest {
        RoomJournalRepository(db, time).setStatus(1L, 25, CatchStatus.CAUGHT)
        api.notFoundPokemonIds = setOf(25)

        assertEquals(
            ListenableWorker.Result.failure(workDataOf(WorkKeys.UPDATED to 0, WorkKeys.FAILED to 1)),
            catalogWorker(0).doWork(),
        )
    }

    @Test
    fun `missing species keeps sync failed without requesting it again before ttl`() = runTest {
        RoomJournalRepository(db, time).setStatus(1L, 1, CatchStatus.CAUGHT)
        api.notFoundSpeciesIds = setOf(1)

        val first = catalogWorker(0).doWork()
        val second = catalogWorker(0).doWork()

        assertEquals(ListenableWorker.Result.failure(workDataOf(WorkKeys.UPDATED to 0, WorkKeys.FAILED to 1)), first)
        assertEquals(ListenableWorker.Result.failure(workDataOf(WorkKeys.UPDATED to 0, WorkKeys.FAILED to 1)), second)
        assertEquals(1, api.pokemonCalls)
        val detail = repository.observeDetail(1).first()!!
        assertEquals(SpeciesState.MISSING, detail.speciesState)
        assertFalse(detail.isComplete)
    }

    @Test
    fun `retry after partial failure downloads only what is missing`() = runTest {
        RoomJournalRepository(db, time).setStatus(1L, 1, CatchStatus.CAUGHT)
        RoomJournalRepository(db, time).setStatus(1L, 25, CatchStatus.CAUGHT)
        api.failingPokemonIds = setOf(25)
        catalogWorker(0).doWork()
        val callsAfterFirst = api.pokemonCalls
        val listCallsAfterFirst = api.listCalls

        api.failingPokemonIds = emptySet()
        val second = catalogWorker(1).doWork()

        assertEquals(ListenableWorker.Result.success(workDataOf(WorkKeys.UPDATED to 1, WorkKeys.FAILED to 0)), second)
        assertEquals(callsAfterFirst + 1, api.pokemonCalls)
        assertEquals(listCallsAfterFirst, api.listCalls)
    }

    @Test
    fun `collection download worker makes every member available offline`() = runTest {
        val id = collectionOf(1, 4, 7)
        assertEquals(0, collections.observeCollection(id).first()?.offlineCount)

        val result = collectionWorker(id).doWork()

        assertEquals(
            ListenableWorker.Result.success(workDataOf(WorkKeys.UPDATED to 3, WorkKeys.FAILED to 0, WorkKeys.TOTAL to 3)),
            result,
        )
        val summary = collections.observeCollection(id).first()!!
        assertEquals(3, summary.offlineCount)
        assertTrue(summary.fullyOffline)
        assertTrue(collections.observeMembers(id).first().all { it.availableOffline })
    }

    @Test
    fun `collection download retries when images failed and fails after max attempts`() = runTest {
        val id = collectionOf(1, 4)
        artworkServer.failingIds = setOf(4)

        assertEquals(ListenableWorker.Result.retry(), collectionWorker(id, 0).doWork())
        assertEquals(
            ListenableWorker.Result.failure(workDataOf(WorkKeys.UPDATED to 1, WorkKeys.FAILED to 1, WorkKeys.TOTAL to 2)),
            collectionWorker(id, CatalogSyncWorker.MAX_ATTEMPTS).doWork(),
        )
    }

    @Test
    fun `collection with details but without downloaded image is not fully offline and can be completed`() = runTest {
        val id = collectionOf(1, 4)
        artworkServer.failingIds = setOf(4)

        collectionWorker(id, CatalogSyncWorker.MAX_ATTEMPTS).doWork()

        assertNotNull(repository.observeDetail(4).first()?.fetchedAt)
        val partial = collections.observeCollection(id).first()!!
        assertEquals(1, partial.offlineCount)
        assertFalse(partial.fullyOffline)
        val members = collections.observeMembers(id).first().associate { it.pokemonId to it.availableOffline }
        assertEquals(mapOf(1 to true, 4 to false), members)

        artworkServer.failingIds = emptySet()
        val retry = collectionWorker(id, 0).doWork()

        assertEquals(
            ListenableWorker.Result.success(workDataOf(WorkKeys.UPDATED to 2, WorkKeys.FAILED to 0, WorkKeys.TOTAL to 2)),
            retry,
        )
        assertTrue(collections.observeCollection(id).first()!!.fullyOffline)
    }

    @Test
    fun `member with pending description is not counted as offline`() = runTest {
        val id = collectionOf(1, 4)
        api.failingSpeciesIds = setOf(4)

        assertEquals(ListenableWorker.Result.retry(), collectionWorker(id, 0).doWork())

        val summary = collections.observeCollection(id).first()!!
        assertEquals(1, summary.offlineCount)
        assertFalse(summary.fullyOffline)
    }

    @Test
    fun `member whose description does not exist on server is still available offline`() = runTest {
        val id = collectionOf(1, 4)
        api.notFoundSpeciesIds = setOf(4)

        val result = collectionWorker(id, 0).doWork()

        assertEquals(
            ListenableWorker.Result.success(workDataOf(WorkKeys.UPDATED to 2, WorkKeys.FAILED to 0, WorkKeys.TOTAL to 2)),
            result,
        )
        assertTrue(collections.observeCollection(id).first()!!.fullyOffline)
        assertFalse(repository.observeDetail(4).first()!!.isComplete)
    }

    @Test
    fun `removing pokemon from every collection drops its offline image and readding requires download`() = runTest {
        val id = collectionOf(1, 4)
        collectionWorker(id).doWork()
        assertTrue(collections.observeCollection(id).first()!!.fullyOffline)

        collections.setMembership(id, 4, false)
        Thread.sleep(5)
        catalogWorker().doWork()
        collections.setMembership(id, 4, true)

        val summary = collections.observeCollection(id).first()!!
        assertEquals(1, summary.offlineCount)
        assertFalse(summary.fullyOffline)
    }
}
