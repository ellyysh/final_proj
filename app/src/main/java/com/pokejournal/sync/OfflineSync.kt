package com.pokejournal.sync

import com.pokejournal.data.images.ArtworkStore
import com.pokejournal.data.repository.CollectionRepository
import com.pokejournal.data.repository.ImportantPokemonSource
import com.pokejournal.data.repository.PokemonRepository
import com.pokejournal.data.repository.PrefetchReport
import com.pokejournal.data.repository.RefreshResult
import com.pokejournal.data.repository.errorOrNull
import com.pokejournal.data.settings.SettingsRepository
import com.pokejournal.domain.logic.ProgressCounter
import com.pokejournal.domain.logic.isRetryable
import com.pokejournal.domain.logic.mapConcurrently
import kotlinx.coroutines.flow.first
import javax.inject.Inject


data class ArtworkReport(val total: Int, val failed: Int, val retryable: Int) {
    companion object {
        fun from(errors: List<Throwable?>) = ArtworkReport(
            total = errors.size,
            failed = errors.count { it != null },
            retryable = errors.count { it?.isRetryable() == true },
        )
    }
}

sealed interface SyncOutcome {
    data class Success(
        val catalog: RefreshResult,
        val prefetch: PrefetchReport?,
        val artwork: ArtworkReport? = null,
    ) : SyncOutcome {
        val failedCount: Int
            get() = (prefetch?.let { it.failed + it.partial } ?: 0) + (artwork?.failed ?: 0)

        val hasRetryableFailures: Boolean
            get() = (prefetch?.retryable ?: 0) > 0 || (artwork?.retryable ?: 0) > 0
    }

    data class Failure(val error: Throwable) : SyncOutcome
}


data class OfflineDownloadReport(val total: Int, val ready: Int, val retryable: Int) {
    val failed: Int get() = total - ready
}

class OfflineSync @Inject constructor(
    private val pokemonRepository: PokemonRepository,
    private val collectionRepository: CollectionRepository,
    private val settingsRepository: SettingsRepository,
    private val importantSource: ImportantPokemonSource,
    private val artworkStore: ArtworkStore,
) {

    suspend fun syncCatalogAndPrefetch(
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): SyncOutcome {
        val app = settingsRepository.appSettings.first()
        val profileId = settingsRepository.activeProfileId.first()
        val user = settingsRepository.userSettings(profileId).first()

        val catalog = pokemonRepository.refreshCatalog(user.pokedexSize, app.cacheTtlMillis, force = false)
        if (catalog is RefreshResult.Failed) return SyncOutcome.Failure(catalog.error)

        var prefetch: PrefetchReport? = null
        var artwork: ArtworkReport? = null
        if (app.offlinePrefetchEnabled) {
            prefetch = pokemonRepository.prefetchDetails(importantSource.importantIds(), app.cacheTtlMillis, onProgress)
            artwork = ensureArtwork(importantSource.collectionIds())
        }
        artworkStore.retainOnly { importantSource.collectionIds() }
        return SyncOutcome.Success(catalog, prefetch, artwork)
    }

    suspend fun downloadCollection(
        collectionId: Long,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): OfflineDownloadReport {
        val ttl = settingsRepository.appSettings.first().cacheTtlMillis
        val ids = collectionRepository.memberIds(collectionId).distinct()
        val progress = ProgressCounter(ids.size, onProgress)

        val errors = ids.mapConcurrently(DOWNLOAD_CONCURRENCY) { id ->
            val detailError = pokemonRepository.refreshDetail(id, ttl, force = false).errorOrNull
            val imageError = artworkStore.ensure(id)
            progress.step()
            listOfNotNull(detailError, imageError)
        }
        return OfflineDownloadReport(
            total = ids.size,
            ready = errors.count { it.isEmpty() },
            retryable = errors.count { list -> list.any { it.isRetryable() } },
        )
    }

    private suspend fun ensureArtwork(ids: List<Int>): ArtworkReport =
        ArtworkReport.from(ids.distinct().mapConcurrently(DOWNLOAD_CONCURRENCY) { artworkStore.ensure(it) })

    companion object {
        const val DOWNLOAD_CONCURRENCY = 4
    }
}
