package com.pokejournal.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pokejournal.data.local.AppDatabase
import com.pokejournal.domain.model.AppSettings
import com.pokejournal.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Настройки и предпочтения (DataStore). Пользовательские данные сюда не кладутся —
 * они живут в Room. Настройки профиля хранятся под ключами с префиксом profileId.
 */
interface SettingsRepository {
    val activeProfileId: Flow<Long>
    val appSettings: Flow<AppSettings>
    fun userSettings(profileId: Long): Flow<UserSettings>
    suspend fun setActiveProfile(profileId: Long)
    suspend fun updateUserSettings(profileId: Long, transform: (UserSettings) -> UserSettings)
    suspend fun updateAppSettings(transform: (AppSettings) -> AppSettings)
    suspend fun clearUserSettings(profileId: Long)
}

/** Настройки активного профиля — автоматически переключаются при смене профиля. */
val SettingsRepository.activeUserSettings: Flow<UserSettings>
    get() = activeProfileId.flatMapLatest { userSettings(it) }

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private val data: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val activeProfileId: Flow<Long> = data
        .map { it[ACTIVE_PROFILE] ?: AppDatabase.DEFAULT_PROFILE_ID }
        .distinctUntilChanged()

    override val appSettings: Flow<AppSettings> = data
        .map { readApp(it) }
        .distinctUntilChanged()

    override fun userSettings(profileId: Long): Flow<UserSettings> = data
        .map { readUser(it, profileId) }
        .distinctUntilChanged()

    override suspend fun setActiveProfile(profileId: Long) {
        dataStore.edit { it[ACTIVE_PROFILE] = profileId }
    }

    override suspend fun updateUserSettings(profileId: Long, transform: (UserSettings) -> UserSettings) {
        dataStore.edit { prefs ->
            val updated = transform(readUser(prefs, profileId))
            val keys = UserKeys(profileId)
            prefs[keys.sort] = updated.sortOrder.name
            prefs[keys.pokedexSize] = updated.pokedexSize
            prefs[keys.historyLimit] = updated.historyLimit
            prefs[keys.theme] = updated.themeMode.name
        }
    }

    override suspend fun updateAppSettings(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val updated = transform(readApp(prefs))
            prefs[CACHE_TTL] = updated.cacheTtlHours
            prefs[SYNC_ENABLED] = updated.backgroundSyncEnabled
            prefs[WIFI_ONLY] = updated.wifiOnly
            prefs[SYNC_INTERVAL] = updated.syncIntervalHours
            prefs[PREFETCH] = updated.offlinePrefetchEnabled
        }
    }

    override suspend fun clearUserSettings(profileId: Long) {
        dataStore.edit { prefs -> UserKeys(profileId).removeFrom(prefs) }
    }

    private fun readApp(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            cacheTtlHours = prefs[CACHE_TTL] ?: defaults.cacheTtlHours,
            backgroundSyncEnabled = prefs[SYNC_ENABLED] ?: defaults.backgroundSyncEnabled,
            wifiOnly = prefs[WIFI_ONLY] ?: defaults.wifiOnly,
            syncIntervalHours = prefs[SYNC_INTERVAL] ?: defaults.syncIntervalHours,
            offlinePrefetchEnabled = prefs[PREFETCH] ?: defaults.offlinePrefetchEnabled,
        )
    }

    private fun readUser(prefs: Preferences, profileId: Long): UserSettings {
        val keys = UserKeys(profileId)
        val defaults = UserSettings()
        return UserSettings(
            sortOrder = prefs[keys.sort].toEnumOr(defaults.sortOrder),
            pokedexSize = prefs[keys.pokedexSize] ?: defaults.pokedexSize,
            historyLimit = prefs[keys.historyLimit] ?: defaults.historyLimit,
            themeMode = prefs[keys.theme].toEnumOr(defaults.themeMode),
        )
    }

    private class UserKeys(profileId: Long) {
        val sort = stringPreferencesKey("profile_${profileId}_sort")
        val pokedexSize = intPreferencesKey("profile_${profileId}_pokedex_size")
        val historyLimit = intPreferencesKey("profile_${profileId}_history_limit")
        val theme = stringPreferencesKey("profile_${profileId}_theme")

        fun removeFrom(prefs: MutablePreferences) {
            prefs.remove(sort)
            prefs.remove(pokedexSize)
            prefs.remove(historyLimit)
            prefs.remove(theme)
        }
    }

    private companion object {
        val ACTIVE_PROFILE = longPreferencesKey("active_profile_id")
        val CACHE_TTL = intPreferencesKey("cache_ttl_hours")
        val SYNC_ENABLED = booleanPreferencesKey("background_sync_enabled")
        val WIFI_ONLY = booleanPreferencesKey("sync_wifi_only")
        val SYNC_INTERVAL = intPreferencesKey("sync_interval_hours")
        val PREFETCH = booleanPreferencesKey("offline_prefetch_enabled")
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOr(default: T): T =
    this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default
