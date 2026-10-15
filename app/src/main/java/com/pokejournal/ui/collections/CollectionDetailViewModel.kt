package com.pokejournal.ui.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokejournal.data.network.NetworkMonitor
import com.pokejournal.data.repository.CollectionRepository
import com.pokejournal.domain.model.CollectionMember
import com.pokejournal.domain.model.CollectionSummary
import com.pokejournal.sync.SyncScheduler
import com.pokejournal.sync.WorkStatus
import com.pokejournal.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectionDetailUiState(
    val isLoading: Boolean = true,
    val collection: CollectionSummary? = null,
    val members: List<CollectionMember> = emptyList(),
    val download: WorkStatus = WorkStatus.Idle,
    val isOffline: Boolean = false,
    val deleted: Boolean = false,
)

sealed interface CollectionDetailEvent {
    data class Remove(val pokemonId: Int) : CollectionDetailEvent
    data object DownloadOffline : CollectionDetailEvent
}

/**
 * Состав коллекции (Room) + статус фоновой загрузки (WorkManager) + сеть:
 * пользователь видит, сколько покемонов уже доступно офлайн, и прогресс докачки.
 */
@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val collectionRepository: CollectionRepository,
    private val syncScheduler: SyncScheduler,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    val collectionId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.ARG_COLLECTION_ID))

    val uiState: StateFlow<CollectionDetailUiState> = combine(
        collectionRepository.observeCollection(collectionId),
        collectionRepository.observeMembers(collectionId),
        syncScheduler.observeCollectionDownload(collectionId),
        networkMonitor.isOnline,
    ) { collection, members, download, online ->
        CollectionDetailUiState(
            isLoading = false,
            collection = collection,
            members = members,
            download = download,
            isOffline = !online,
            deleted = collection == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionDetailUiState())

    fun onEvent(event: CollectionDetailEvent) {
        when (event) {
            is CollectionDetailEvent.Remove -> viewModelScope.launch {
                collectionRepository.setMembership(collectionId, event.pokemonId, member = false)
            }
            CollectionDetailEvent.DownloadOffline -> syncScheduler.downloadCollection(collectionId)
        }
    }
}
