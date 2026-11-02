package com.pokejournal.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pokejournal.domain.logic.isRetryable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

enum class WorkDecision { SUCCESS, RETRY, FAILURE }

object SyncRetryPolicy {
    const val MAX_ATTEMPTS = 3

    fun forSync(outcome: SyncOutcome, attempt: Int): WorkDecision = when (outcome) {
        is SyncOutcome.Failure ->
            if (outcome.error.isRetryable() && attempt < MAX_ATTEMPTS) WorkDecision.RETRY else WorkDecision.FAILURE
        is SyncOutcome.Success -> when {
            outcome.failedCount == 0 -> WorkDecision.SUCCESS
            outcome.hasRetryableFailures && attempt < MAX_ATTEMPTS -> WorkDecision.RETRY
            else -> WorkDecision.FAILURE
        }
    }

    fun forCollection(report: OfflineDownloadReport, attempt: Int): WorkDecision = when {
        report.failed == 0 -> WorkDecision.SUCCESS
        report.retryable > 0 && attempt < MAX_ATTEMPTS -> WorkDecision.RETRY
        else -> WorkDecision.FAILURE
    }
}

@HiltWorker
class CatalogSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val offlineSync: OfflineSync,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val outcome = offlineSync.syncCatalogAndPrefetch { done, total -> reportProgress(done, total) }
        val output = (outcome as? SyncOutcome.Success)?.let {
            workDataOf(
                WorkKeys.UPDATED to (it.prefetch?.updated ?: 0),
                WorkKeys.FAILED to it.failedCount,
            )
        }
        return when (SyncRetryPolicy.forSync(outcome, runAttemptCount)) {
            WorkDecision.SUCCESS -> if (output != null) Result.success(output) else Result.success()
            WorkDecision.RETRY -> Result.retry()
            WorkDecision.FAILURE -> if (output != null) Result.failure(output) else Result.failure()
        }
    }

    private suspend fun reportProgress(done: Int, total: Int) {
        setProgress(workDataOf(WorkKeys.DONE to done, WorkKeys.TOTAL to total))
    }

    companion object {
        const val MAX_ATTEMPTS = SyncRetryPolicy.MAX_ATTEMPTS
    }
}

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
            WorkKeys.UPDATED to report.ready,
            WorkKeys.FAILED to report.failed,
            WorkKeys.TOTAL to report.total,
        )
        return when (SyncRetryPolicy.forCollection(report, runAttemptCount)) {
            WorkDecision.SUCCESS -> Result.success(output)
            WorkDecision.RETRY -> Result.retry()
            WorkDecision.FAILURE -> Result.failure(output)
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
