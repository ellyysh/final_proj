package com.pokejournal.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokejournal.data.repository.CreateResult
import com.pokejournal.data.repository.DeleteProfileResult
import com.pokejournal.data.repository.JournalRepository
import com.pokejournal.data.repository.PokemonRepository
import com.pokejournal.data.repository.ProfileRepository
import com.pokejournal.data.settings.SettingsRepository
import com.pokejournal.domain.logic.JournalStats
import com.pokejournal.domain.logic.StatsCalculator
import com.pokejournal.domain.logic.message
import com.pokejournal.domain.model.JournalNote
import com.pokejournal.domain.model.Profile
import com.pokejournal.ui.components.CreateDialogState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = true,
    val activeProfile: Profile? = null,
    val profiles: List<Profile> = emptyList(),
    val stats: JournalStats = JournalStats(),
    val notes: List<JournalNote> = emptyList(),
    val createDialog: CreateDialogState = CreateDialogState(),
)

sealed interface ProfileEvent {
    data class Switch(val profileId: Long) : ProfileEvent
    data object OpenCreateDialog : ProfileEvent
    data object DismissCreateDialog : ProfileEvent
    data class Create(val name: String, val avatar: String) : ProfileEvent
    data class Delete(val profileId: Long) : ProfileEvent
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    pokemonRepository: PokemonRepository,
    journalRepository: JournalRepository,
) : ViewModel() {

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    private val personal = settingsRepository.activeProfileId.flatMapLatest { pid ->
        combine(
            journalRepository.observeStates(pid),
            journalRepository.observeNotes(pid),
            settingsRepository.userSettings(pid),
        ) { states, notes, settings -> Triple(states, notes, settings) }
    }

    private val createDialog = MutableStateFlow(CreateDialogState())

    val uiState: StateFlow<ProfileUiState> = combine(
        profileRepository.observeProfiles(),
        settingsRepository.activeProfileId,
        pokemonRepository.observeCatalog(),
        personal,
        createDialog,
    ) { profiles, activeId, catalog, (states, notes, settings), dialog ->
        ProfileUiState(
            isLoading = false,
            activeProfile = profiles.firstOrNull { it.id == activeId },
            profiles = profiles,
            stats = StatsCalculator.calculate(catalog, states, settings.pokedexSize),
            notes = notes,
            createDialog = dialog,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun onEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.Switch -> viewModelScope.launch { settingsRepository.setActiveProfile(event.profileId) }
            ProfileEvent.OpenCreateDialog -> createDialog.value = CreateDialogState(visible = true)
            ProfileEvent.DismissCreateDialog -> createDialog.value = CreateDialogState()
            is ProfileEvent.Create -> {
                if (createDialog.value.submitting) return
                createDialog.update { it.copy(submitting = true) }
                createProfile(event)
            }
            is ProfileEvent.Delete -> viewModelScope.launch {
                when (profileRepository.deleteProfile(event.profileId)) {
                    DeleteProfileResult.Deleted -> messageChannel.send("Профиль удалён вместе с его данными")
                    DeleteProfileResult.LastProfile -> messageChannel.send("Нельзя удалить единственный профиль")
                }
            }
        }
    }

    private fun createProfile(event: ProfileEvent.Create) {
        viewModelScope.launch {
            when (val result = profileRepository.createProfile(event.name, event.avatar)) {
                is CreateResult.Created -> {
                    createDialog.value = CreateDialogState()
                    settingsRepository.setActiveProfile(result.id)
                    messageChannel.send("Профиль «${event.name.trim()}» создан и выбран")
                }
                is CreateResult.Rejected -> createDialog.update { current ->
                    if (current.visible) CreateDialogState(visible = true, error = result.error.message()) else current
                }
            }
        }
    }
}
