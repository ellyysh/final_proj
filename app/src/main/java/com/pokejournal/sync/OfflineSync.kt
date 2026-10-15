package com.pokejournal.sync

import com.pokejournal.data.repository.CollectionRepository
import com.pokejournal.data.repository.ImportantPokemonSource
import com.pokejournal.data.repository.PokemonRepository
import com.pokejournal.data.repository.PrefetchReport
import com.pokejournal.data.repository.RefreshResult
import com.pokejournal.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

sealed interface SyncOutcome {
    data class Success(val catalog: RefreshResult, val prefetch: PrefetchReport?) : SyncOutcome
    data class Failure(val error: Throwable) : SyncOutcome
}

/**
 * Логика фоновой синхронизации, вынесенная из воркеров ради тестируемости:
 * 1) актуализировать каталог по TTL;
 * 2) предзагрузить детали «важных» покемонов, чтобы они были доступны офлайн.
 */
class OfflineSync @Inject constructor(
    private val pokemonRepository: PokemonRepository,
    private val collectionRepository: CollectionRepository,
    private val settingsRepository: SettingsRepository,
    private val importantSource: ImportantPokemonSource,
) {

    suspend fun syncCatalogAndPrefetch(
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): SyncOutcome {
        val app = settingsRepository.appSettings.first()
        val profileId = settingsRepository.activeProfileId.first()
        val user = settingsRepository.userSettings(profileId).first()

        val catalog = pokemonRepository.refreshCatalog(user.pokedexSize, app.cacheTtlMillis, force = false)
        if (catalog is RefreshResult.Failed) return SyncOutcome.Failure(catalog.error)

        val prefetch = if (app.offlinePrefetchEnabled) {
            pokemonRepository.prefetchDetails(importantSource.importantIds(), app.cacheTtlMillis, onProgress)
        } else {
            null
        }
        return SyncOutcome.Success(catalog, prefetch)
    }

    /** Сделать коллекцию полностью доступной офлайн. */
    suspend fun downloadCollection(
        collectionId: Long,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): PrefetchReport {
        val ttl = settingsRepository.appSettings.first().cacheTtlMillis
        val ids = collectionRepository.memberIds(collectionId)
        return pokemonRepository.prefetchDetails(ids, ttl, onProgress)
    }
}
