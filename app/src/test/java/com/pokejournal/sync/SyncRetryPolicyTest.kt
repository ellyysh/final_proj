package com.pokejournal.sync

import com.pokejournal.data.repository.PrefetchReport
import com.pokejournal.data.repository.RefreshResult
import com.pokejournal.domain.logic.HttpStatusException
import com.pokejournal.domain.logic.isRetryable
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class SyncRetryPolicyTest {

    private val max = SyncRetryPolicy.MAX_ATTEMPTS

    private fun success(prefetch: PrefetchReport? = null, artwork: ArtworkReport? = null) =
        SyncOutcome.Success(RefreshResult.UpToDate, prefetch, artwork)

    @Test
    fun `clean sync succeeds`() {
        val outcome = success(PrefetchReport(total = 3, updated = 3, upToDate = 0, failed = 0))
        assertEquals(WorkDecision.SUCCESS, SyncRetryPolicy.forSync(outcome, attempt = 0))
    }

    @Test
    fun `partial transient failures are retried while attempts remain, then reported as success`() {
        val outcome = success(PrefetchReport(total = 3, updated = 2, upToDate = 0, failed = 1, retryable = 1))
        assertEquals(WorkDecision.RETRY, SyncRetryPolicy.forSync(outcome, attempt = 0))
        assertEquals(WorkDecision.RETRY, SyncRetryPolicy.forSync(outcome, attempt = max - 1))
        assertEquals(WorkDecision.SUCCESS, SyncRetryPolicy.forSync(outcome, attempt = max))
    }

    @Test
    fun `artwork failures also trigger retry`() {
        val outcome = success(artwork = ArtworkReport(total = 2, failed = 1, retryable = 1))
        assertEquals(WorkDecision.RETRY, SyncRetryPolicy.forSync(outcome, attempt = 0))
    }

    @Test
    fun `permanent failures are not retried`() {
        val partial = success(PrefetchReport(total = 2, updated = 1, upToDate = 0, failed = 1, retryable = 0))
        assertEquals(WorkDecision.SUCCESS, SyncRetryPolicy.forSync(partial, attempt = 0))

        val catalog404 = SyncOutcome.Failure(HttpStatusException(404))
        assertEquals(WorkDecision.FAILURE, SyncRetryPolicy.forSync(catalog404, attempt = 0))
    }

    @Test
    fun `catalog network failure is retried up to the limit`() {
        val outcome = SyncOutcome.Failure(IOException())
        assertEquals(WorkDecision.RETRY, SyncRetryPolicy.forSync(outcome, attempt = 0))
        assertEquals(WorkDecision.FAILURE, SyncRetryPolicy.forSync(outcome, attempt = max))
    }

    @Test
    fun `collection download decision`() {
        assertEquals(
            WorkDecision.SUCCESS,
            SyncRetryPolicy.forCollection(OfflineDownloadReport(total = 2, ready = 2, retryable = 0), attempt = 0),
        )
        val transient = OfflineDownloadReport(total = 2, ready = 1, retryable = 1)
        assertEquals(WorkDecision.RETRY, SyncRetryPolicy.forCollection(transient, attempt = 0))
        assertEquals(WorkDecision.FAILURE, SyncRetryPolicy.forCollection(transient, attempt = max))
        val permanent = OfflineDownloadReport(total = 2, ready = 1, retryable = 0)
        assertEquals(WorkDecision.FAILURE, SyncRetryPolicy.forCollection(permanent, attempt = 0))
    }

    @Test
    fun `error classification`() {
        assertTrue(IOException().isRetryable())
        assertTrue(SocketTimeoutException().isRetryable())
        assertTrue(HttpStatusException(503).isRetryable())
        assertTrue(HttpStatusException(429).isRetryable())
        assertFalse(HttpStatusException(404).isRetryable())
        assertFalse(SerializationException("bad json").isRetryable())
    }
}
