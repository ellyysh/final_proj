package com.pokejournal.domain.model

enum class SortOrder { NUMBER, NAME, STATUS }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Настройки, которые принадлежат конкретному локальному профилю. */
data class UserSettings(
    val sortOrder: SortOrder = SortOrder.NUMBER,
    val pokedexSize: Int = DEFAULT_POKEDEX_SIZE,
    val historyLimit: Int = DEFAULT_HISTORY_LIMIT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    companion object {
        const val DEFAULT_POKEDEX_SIZE = 151
        const val DEFAULT_HISTORY_LIMIT = 50
        val POKEDEX_SIZES = listOf(151, 251, 386, 493, 649)
        val HISTORY_LIMITS = listOf(20, 50, 100)
    }
}

/** Глобальные настройки приложения: кэш, синхронизация, офлайн. */
data class AppSettings(
    val cacheTtlHours: Int = 24,
    val backgroundSyncEnabled: Boolean = true,
    val wifiOnly: Boolean = false,
    val syncIntervalHours: Int = 12,
    val offlinePrefetchEnabled: Boolean = true,
) {
    val cacheTtlMillis: Long get() = cacheTtlHours * HOUR_MS

    companion object {
        const val HOUR_MS = 60L * 60L * 1000L
        val TTL_OPTIONS = listOf(1, 6, 24, 168)
        val SYNC_INTERVALS = listOf(6, 12, 24)
    }
}
