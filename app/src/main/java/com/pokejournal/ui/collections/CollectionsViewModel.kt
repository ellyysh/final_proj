package com.pokejournal.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokejournal.data.repository.CollectionRepository
import com.pokejournal.data.repository.CreateResult
import com.pokejournal.data.settings.SettingsRepository
import com.pokejournal.domain.logic.message
import com.pokejournal.domain.model.CollectionSummary
import com.pokejournal.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateDialogState(
    val visible: Boolean = false,
    val error: String? = null,
)

data class CollectionsUiState(
    val isLoading: Boolean = true,
    val collections: List<CollectionSummary> = emptyList(),
    val dialog: CreateDialogState = CreateDialogState(),
)

sealed interface CollectionsEvent {
    data object OpenCreateDialog : CollectionsEvent
    data object DismissDialog : CollectionsEvent
    data class Create(val name: String, val description: String) : CollectionsEvent
    data class Delete(val collectionId: Long) : CollectionsEvent
    data class DownloadOffline(val collectionId: Long) : CollectionsEvent
}

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val collectionRepository: CollectionRepository,
    private val settingsRepository: SettingsRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val dialog = MutableStateFlow(CreateDialogState())

    val uiState: StateFlow<CollectionsUiState> = combine(
        settingsRepository.activeProfileId.flatMapLatest { collectionRepository.observeCollections(it) },
        dialog,
    ) { collections, dialogState ->
        CollectionsUiState(isLoading = false, collections = collections, dialog = dialogState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionsUiState())

    fun onEvent(event: CollectionsEvent) {
        when (event) {
            CollectionsEvent.OpenCreateDialog -> dialog.value = CreateDialogState(visible = true)
            CollectionsEvent.DismissDialog -> dialog.value = CreateDialogState()
            is CollectionsEvent.Create -> viewModelScope.launch {
                val profileId = settingsRepository.activeProfileId.first()
                val result = collectionRepository.createCollection(profileId, event.name, event.description)
                dialog.value = when (result) {
                    is CreateResult.Created -> CreateDialogState()
                    is CreateResult.Rejected -> CreateDialogState(visible = true, error = result.error.message())
                }
            }
            is CollectionsEvent.Delete -> viewModelScope.launch {
                collectionRepository.deleteCollection(event.collectionId)
            }
            is CollectionsEvent.DownloadOffline -> syncScheduler.downloadCollection(event.collectionId)
        }
    }
}
