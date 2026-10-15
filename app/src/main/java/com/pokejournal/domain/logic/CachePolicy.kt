package com.pokejournal.domain.logic

/** Политика актуальности локальных данных (TTL кэша). */
object CachePolicy {

    fun isStale(updatedAt: Long?, now: Long, ttlMillis: Long): Boolean {
        if (updatedAt == null) return true
        if (updatedAt > now) return false // часы откатили назад — не перезагружаем лишний раз
        return now - updatedAt >= ttlMillis
    }

    /**
     * Каталог нужно обновить, если он устарел по TTL или если пользователь
     * расширил Pokédex и закэшированных записей меньше, чем требуется.
     */
    fun needsCatalogRefresh(
        updatedAt: Long?,
        cachedLimit: Int,
        requiredLimit: Int,
        now: Long,
        ttlMillis: Long,
    ): Boolean = cachedLimit < requiredLimit || isStale(updatedAt, now, ttlMillis)
}
