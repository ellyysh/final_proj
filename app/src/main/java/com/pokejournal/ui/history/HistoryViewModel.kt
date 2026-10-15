package com.pokejournal.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokejournal.data.repository.HistoryRepository
import com.pokejournal.data.settings.SettingsRepository
import com.pokejournal.domain.logic.TimeProvider
import com.pokejournal.domain.model.HistoryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val isLoading: Boolean = true,
    val entries: List<HistoryEntry> = emptyList(),
    val now: Long = 0L,
)

sealed interface HistoryEvent {
    data class Remove(val pokemonId: Int) : HistoryEvent
    data object ClearAll : HistoryEvent
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = settingsRepository.activeProfileId
        .flatMapLatest { historyRepository.observeHistory(it) }
        .map { HistoryUiState(isLoading = false, entries = it, now = time.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun onEvent(event: HistoryEvent) {
        viewModelScope.launch {
            val profileId = settingsRepository.activeProfileId.first()
            when (event) {
                is HistoryEvent.Remove -> historyRepository.remove(profileId, event.pokemonId)
                HistoryEvent.ClearAll -> historyRepository.clear(profileId)
            }
        }
    }
}
