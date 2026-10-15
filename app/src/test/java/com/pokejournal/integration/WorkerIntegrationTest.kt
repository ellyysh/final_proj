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
import com.pokejournal.data.local.AppDatabase
import com.pokejournal.data.local.entity.ProfileEntity
import com.pokejournal.data.repository.CreateResult
import com.pokejournal.data.repository.OfflineFirstPokemonRepository
import com.pokejournal.data.repository.RoomCollectionRepository
import com.pokejournal.data.repository.RoomImportantPokemonSource
import com.pokejournal.domain.model.CatchStatus
import com.pokejournal.data.repository.RoomJournalRepository
import com.pokejournal.sync.CatalogSyncWorker
import com.pokejournal.sync.CollectionDownloadWorker
import com.pokejournal.sync.OfflineSync
import com.pokejournal.sync.WorkKeys
import com.pokejournal.testing.FakePokeApi
import com.pokejournal.testing.FakeSettingsRepository
import com.pokejournal.testing.FakeTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Фоновая часть: настоящие воркеры поверх OfflineSync + Room + Fake API.
 * Проверяем, что после работы воркера данные доступны офлайн, а сбой сети даёт retry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkerIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val api = FakePokeApi()
    private val time = FakeTime()
    private val settings = FakeSettingsRepository()
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
        collections = RoomCollectionRepository(db.collectionDao(), time)
        offlineSync = OfflineSync(repository, collections, settings, RoomImportantPokemonSource(db.syncDao()))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `catalog sync worker refreshes catalog and prefetches pokemon from journal`() = runTest {
        RoomJournalRepository(db.userStateDao(), db.noteDao(), time).setStatus(1L, 25, CatchStatus.CAUGHT)
        val worker = TestListenableWorkerBuilder<CatalogSyncWorker>(context).setWorkerFactory(factory).build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(workDataOf(WorkKeys.UPDATED to 1, WorkKeys.FAILED to 0)), result)
        assertEquals(4, repository.observeCatalog().first().size)
        assertNotNull(repository.observeDetail(25).first()?.fetchedAt)
    }

    @Test
    fun `catalog sync worker asks for retry when network is down and gives up after max attempts`() = runTest {
        api.offline = true

        val first = TestListenableWorkerBuilder<CatalogSyncWorker>(context)
            .setWorkerFactory(factory)
            .setRunAttemptCount(0)
            .build()
            .doWork()
        val last = TestListenableWorkerBuilder<CatalogSyncWorker>(context)
            .setWorkerFactory(factory)
            .setRunAttemptCount(CatalogSyncWorker.MAX_ATTEMPTS)
            .build()
            .doWork()

        assertEquals(ListenableWorker.Result.retry(), first)
        assertEquals(ListenableWorker.Result.failure(), last)
    }

    @Test
    fun `collection download worker makes every member available offline`() = runTest {
        repository.refreshCatalog(151, 1_000_000L, force = false)
        val id = (collections.createCollection(1L, "Trip", "") as CreateResult.Created).id
        listOf(1, 4, 7).forEach { collections.setMembership(id, it, true) }
        assertEquals(0, collections.observeCollection(id).first()?.offlineCount)

        val worker = TestListenableWorkerBuilder<CollectionDownloadWorker>(
            context,
            inputData = workDataOf(WorkKeys.COLLECTION_ID to id),
        ).setWorkerFactory(factory).build()
        val result = worker.doWork()

        assertEquals(
            ListenableWorker.Result.success(workDataOf(WorkKeys.UPDATED to 3, WorkKeys.FAILED to 0, WorkKeys.TOTAL to 3)),
            result,
        )
        assertEquals(3, collections.observeCollection(id).first()?.offlineCount)
    }
}
