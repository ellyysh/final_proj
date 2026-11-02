package com.pokejournal.domain.logic

import com.pokejournal.domain.model.SpeciesState

object CachePolicy {
    fun isStale(updatedAt: Long?, now: Long, ttlMillis: Long): Boolean {
        if (updatedAt == null) return true
        if (updatedAt > now) return false
        return now - updatedAt >= ttlMillis
    }

    fun needsCatalogRefresh(
        updatedAt: Long?,
        cachedLimit: Int,
        requiredLimit: Int,
        now: Long,
        ttlMillis: Long,
    ): Boolean = cachedLimit < requiredLimit || isStale(updatedAt, now, ttlMillis)

    fun needsDetailRefresh(fetchedAt: Long?, speciesState: SpeciesState, now: Long, ttlMillis: Long): Boolean =
        speciesState.needsEarlyRefresh || isStale(fetchedAt, now, ttlMillis)
}
