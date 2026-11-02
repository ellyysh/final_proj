package com.pokejournal.domain.logic

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

suspend fun <T, R> List<T>.mapConcurrently(limit: Int, transform: suspend (T) -> R): List<R> {
    require(limit > 0) { "limit must be positive" }
    val semaphore = Semaphore(limit)
    return coroutineScope {
        map { item -> async { semaphore.withPermit { transform(item) } } }.awaitAll()
    }
}

class ProgressCounter(
    private val total: Int,
    private val onProgress: suspend (done: Int, total: Int) -> Unit,
) {
    private val mutex = Mutex()
    private var done = 0

    suspend fun step() = mutex.withLock {
        done++
        onProgress(done, total)
    }
}
