package com.pokejournal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokejournal.data.repository.ProfileRepository
import com.pokejournal.data.settings.SettingsRepository
import com.pokejournal.data.settings.activeUserSettings
import com.pokejournal.domain.model.Profile
import com.pokejournal.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.activeUserSettings
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val activeProfile: StateFlow<Profile?> = settingsRepository.activeProfileId
        .flatMapLatest { profileRepository.observeProfile(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
