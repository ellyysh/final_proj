package com.pokejournal.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokejournal.data.repository.PokemonRepository
import com.pokejournal.data.settings.SettingsRepository
import com.pokejournal.domain.model.AppSettings
import com.pokejournal.domain.model.UserSettings
import com.pokejournal.sync.SyncScheduler
import com.pokejournal.sync.WorkStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = true,
    val user: UserSettings = UserSettings(),
    val app: AppSettings = AppSettings(),
    val syncStatus: WorkStatus = WorkStatus.Idle,
    val lastSyncAt: Long? = null,
    val offlineDetails: Int = 0,
)

sealed interface SettingsEvent {
    data class UpdateUser(val transform: (UserSettings) -> UserSettings) : SettingsEvent
    data class UpdateApp(val transform: (AppSettings) -> AppSettings) : SettingsEvent
    data object SyncNow : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val syncScheduler: SyncScheduler,
    pokemonRepository: PokemonRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.activeProfileId.flatMapLatest { settingsRepository.userSettings(it) },
        settingsRepository.appSettings,
        syncScheduler.observeSyncNow(),
        pokemonRepository.observeLastCatalogSync(),
        pokemonRepository.observeOfflineDetailCount(),
    ) { user, app, sync, lastSync, offline ->
        SettingsUiState(false, user, app, sync, lastSync, offline)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.UpdateUser -> viewModelScope.launch {
                settingsRepository.updateUserSettings(settingsRepository.activeProfileId.first(), event.transform)
            }
            // Перепланирование WorkManager произойдёт реактивно в SyncInitializer.
            is SettingsEvent.UpdateApp -> viewModelScope.launch {
                settingsRepository.updateAppSettings(event.transform)
            }
            SettingsEvent.SyncNow -> syncScheduler.syncNow()
        }
    }
}
