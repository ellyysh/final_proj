package com.pokejournal.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Периодическая (и ручная) синхронизация каталога + офлайн-предзагрузка. */
@HiltWorker
class CatalogSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val offlineSync: OfflineSync,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        when (val outcome = offlineSync.syncCatalogAndPrefetch { done, total -> reportProgress(done, total) }) {
            is SyncOutcome.Failure ->
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            is SyncOutcome.Success -> Result.success(
                workDataOf(
                    WorkKeys.UPDATED to (outcome.prefetch?.updated ?: 0),
                    WorkKeys.FAILED to (outcome.prefetch?.failed ?: 0),
                ),
            )
        }

    private suspend fun reportProgress(done: Int, total: Int) {
        setProgress(workDataOf(WorkKeys.DONE to done, WorkKeys.TOTAL to total))
    }

    companion object {
        const val MAX_ATTEMPTS = 3
    }
}

/** Скачивает детали всех покемонов коллекции, чтобы она работала без сети. */
@HiltWorker
class CollectionDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val offlineSync: OfflineSync,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val collectionId = inputData.getLong(WorkKeys.COLLECTION_ID, -1L)
        if (collectionId < 0) return Result.failure()

        val report = offlineSync.downloadCollection(collectionId) { done, total ->
            setProgress(workDataOf(WorkKeys.DONE to done, WorkKeys.TOTAL to total))
        }
        val output = workDataOf(
            WorkKeys.UPDATED to report.updated,
            WorkKeys.FAILED to report.failed,
            WorkKeys.TOTAL to report.total,
        )
        return when {
            report.failed == 0 -> Result.success(output)
            runAttemptCount < CatalogSyncWorker.MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure(output)
        }
    }
}

object WorkKeys {
    const val COLLECTION_ID = "collection_id"
    const val DONE = "done"
    const val TOTAL = "total"
    const val UPDATED = "updated"
    const val FAILED = "failed"
}
