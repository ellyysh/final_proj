package com.pokejournal.integration

import android.app.Application
import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import com.pokejournal.data.local.AppDatabase
import com.pokejournal.data.local.entity.ProfileEntity
import com.pokejournal.data.repository.OfflineFirstPokemonRepository
import com.pokejournal.data.repository.RoomCollectionRepository
import com.pokejournal.data.repository.RoomImportantPokemonSource
import com.pokejournal.domain.model.AppSettings
import com.pokejournal.sync.CatalogSyncWorker
import com.pokejournal.sync.CollectionDownloadWorker
import com.pokejournal.sync.OfflineSync
import com.pokejournal.sync.WorkManagerSyncScheduler
import com.pokejournal.testing.FakeArtworkStore
import com.pokejournal.testing.FakePokeApi
import com.pokejournal.testing.FakeSettingsRepository
import com.pokejournal.testing.FakeTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.TimeUnit


@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkSchedulingTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var workManager: WorkManager
    private lateinit var driver: TestDriver
    private lateinit var scheduler: WorkManagerSyncScheduler
    private lateinit var repository: OfflineFirstPokemonRepository
    private val api = FakePokeApi()
    private val time = FakeTime()
    private val settings = FakeSettingsRepository()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        runBlocking { db.profileDao().insert(ProfileEntity(id = 1, name = "Ash", avatar = "🔥", createdAt = 1)) }
        repository = OfflineFirstPokemonRepository(api, db, time)
        val offlineSync = OfflineSync(
            repository,
            RoomCollectionRepository(db, time),
            settings,
            RoomImportantPokemonSource(db.syncDao()),
            FakeArtworkStore(),
        )
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? = when (workerClassName) {
                CatalogSyncWorker::class.java.name -> CatalogSyncWorker(appContext, workerParameters, offlineSync)
                CollectionDownloadWorker::class.java.name ->
                    CollectionDownloadWorker(appContext, workerParameters, offlineSync)
                else -> null
            }
        }
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(factory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        driver = WorkManagerTestInitHelper.getTestDriver(context)!!
        scheduler = WorkManagerSyncScheduler(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `periodic sync is scheduled with settings-driven constraints and interval`() {
        scheduler.schedulePeriodicSync(AppSettings(wifiOnly = true, syncIntervalHours = 24))

        val info = periodicInfo()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(NetworkType.UNMETERED, info.constraints.requiredNetworkType)
        assertTrue(info.constraints.requiresBatteryNotLow())
        assertEquals(TimeUnit.HOURS.toMillis(24), info.periodicityInfo?.repeatIntervalMillis)


        scheduler.schedulePeriodicSync(AppSettings(wifiOnly = false, syncIntervalHours = 6))
        val updated = periodicInfo()
        assertEquals(info.id, updated.id)
        assertEquals(NetworkType.CONNECTED, updated.constraints.requiredNetworkType)


        scheduler.schedulePeriodicSync(AppSettings(backgroundSyncEnabled = false))
        assertEquals(WorkInfo.State.CANCELLED, periodicInfo().state)
    }

    @Test
    fun `periodic sync does not run until network constraint is met`() {
        scheduler.schedulePeriodicSync(AppSettings())
        val id = periodicInfo().id

        driver.setPeriodDelayMet(id)
        idleMainLooper()
        assertEquals(0, api.listAttempts)

        driver.setAllConstraintsMet(id)
        awaitCondition { api.listCalls == 1 }

        val catalog = runBlocking { db.pokemonDao().count() }
        assertEquals(4, catalog)
    }

    @Test
    fun `one-time sync waits for network and is re-run by WorkManager after a transient failure`() {
        api.failNextListCalls.set(1)

        scheduler.syncNow()
        val id = oneTimeInfo().id
        assertEquals(NetworkType.CONNECTED, oneTimeInfo().constraints.requiredNetworkType)
        idleMainLooper()
        assertEquals(0, api.listAttempts)

        driver.setAllConstraintsMet(id)
        val retried = awaitWork(id) { it.runAttemptCount >= 1 && it.state != WorkInfo.State.RUNNING }
        assertFalse(retried.state == WorkInfo.State.FAILED)

        if (retried.state == WorkInfo.State.ENQUEUED) driver.setInitialDelayMet(id)
        val done = awaitWork(id) { it.state.isFinished }

        assertEquals(WorkInfo.State.SUCCEEDED, done.state)
        assertEquals(2, api.listAttempts)
        assertEquals(1, api.listCalls)
    }

    @Test
    fun `one-time sync gives up after max attempts when network keeps failing`() {
        api.failNextListCalls.set(Int.MAX_VALUE)

        scheduler.syncNow()
        val id = oneTimeInfo().id
        driver.setAllConstraintsMet(id)

        var info = awaitWork(id) { it.state != WorkInfo.State.RUNNING && it.runAttemptCount >= 1 }
        while (!info.state.isFinished) {
            driver.setInitialDelayMet(id)
            val attempt = info.runAttemptCount
            info = awaitWork(id) {
                it.state != WorkInfo.State.RUNNING && (it.runAttemptCount > attempt || it.state.isFinished)
            }
        }

        assertEquals(WorkInfo.State.FAILED, info.state)
        assertEquals(CatalogSyncWorker.MAX_ATTEMPTS + 1, api.listAttempts)
    }

    private fun periodicInfo(): WorkInfo =
        workManager.getWorkInfosForUniqueWork(WorkManagerSyncScheduler.PERIODIC_SYNC).get().single()

    private fun oneTimeInfo(): WorkInfo =
        workManager.getWorkInfosForUniqueWork(WorkManagerSyncScheduler.ONE_TIME_SYNC).get().single()

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    private fun awaitWork(id: UUID, predicate: (WorkInfo) -> Boolean): WorkInfo {
        var last: WorkInfo? = null
        awaitCondition {
            last = workManager.getWorkInfoById(id).get()
            last?.let(predicate) == true
        }
        return last!!
    }

    private fun awaitCondition(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            idleMainLooper()
            if (condition()) return
            check(System.currentTimeMillis() < deadline) { "Условие не выполнилось за $timeoutMs мс" }
            Thread.sleep(POLL_MS)
        }
    }

    private companion object {
        const val POLL_MS = 20L
    }
}
