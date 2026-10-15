package com.pokejournal.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pokejournal.domain.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface WorkStatus {
    data object Idle : WorkStatus
    data object Queued : WorkStatus
    data class Running(val done: Int, val total: Int) : WorkStatus
    data class Succeeded(val updated: Int, val failed: Int) : WorkStatus
    data object Failed : WorkStatus
}

interface SyncScheduler {
    fun schedulePeriodicSync(settings: AppSettings)
    fun syncNow()
    fun downloadCollection(collectionId: Long)
    fun observeSyncNow(): Flow<WorkStatus>
    fun observeCollectionDownload(collectionId: Long): Flow<WorkStatus>
}

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncScheduler {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override fun schedulePeriodicSync(settings: AppSettings) {
        if (!settings.backgroundSyncEnabled) {
            workManager.cancelUniqueWork(PERIODIC_SYNC)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<CatalogSyncWorker>(
            settings.syncIntervalHours.toLong(),
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_SYNC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun syncNow() {
        val request = OneTimeWorkRequestBuilder<CatalogSyncWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(ONE_TIME_SYNC, ExistingWorkPolicy.REPLACE, request)
    }

    override fun downloadCollection(collectionId: Long) {
        val request = OneTimeWorkRequestBuilder<CollectionDownloadWorker>()
            .setInputData(workDataOf(WorkKeys.COLLECTION_ID to collectionId))
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(collectionWorkName(collectionId), ExistingWorkPolicy.KEEP, request)
    }

    override fun observeSyncNow(): Flow<WorkStatus> =
        workManager.getWorkInfosForUniqueWorkFlow(ONE_TIME_SYNC).map { it.toStatus() }

    override fun observeCollectionDownload(collectionId: Long): Flow<WorkStatus> =
        workManager.getWorkInfosForUniqueWorkFlow(collectionWorkName(collectionId)).map { it.toStatus() }

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    companion object {
        const val PERIODIC_SYNC = "periodic_catalog_sync"
        const val ONE_TIME_SYNC = "one_time_catalog_sync"
        private const val BACKOFF_SECONDS = 30L
        fun collectionWorkName(id: Long) = "collection_download_$id"
    }
}

internal fun List<WorkInfo>.toStatus(): WorkStatus {
    val info = firstOrNull { !it.state.isFinished } ?: firstOrNull() ?: return WorkStatus.Idle
    return when (info.state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> WorkStatus.Queued
        WorkInfo.State.RUNNING -> WorkStatus.Running(
            done = info.progress.getInt(WorkKeys.DONE, 0),
            total = info.progress.getInt(WorkKeys.TOTAL, 0),
        )
        WorkInfo.State.SUCCEEDED -> WorkStatus.Succeeded(
            updated = info.outputData.getInt(WorkKeys.UPDATED, 0),
            failed = info.outputData.getInt(WorkKeys.FAILED, 0),
        )
        WorkInfo.State.FAILED -> WorkStatus.Failed
        WorkInfo.State.CANCELLED -> WorkStatus.Idle
    }
}
