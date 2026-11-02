package com.pokejournal.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokejournal.data.network.NetworkMonitor
import com.pokejournal.data.repository.CreateResult
import com.pokejournal.data.repository.JournalRepository
import com.pokejournal.data.repository.PokemonRepository
import com.pokejournal.data.repository.PresetRepository
import com.pokejournal.data.repository.RefreshResult
import com.pokejournal.data.settings.SettingsRepository
import com.pokejournal.domain.logic.PokemonListBuilder
import com.pokejournal.domain.logic.message
import com.pokejournal.domain.logic.toUserMessage
import com.pokejournal.domain.model.FilterPreset
import com.pokejournal.domain.model.ListFilter
import com.pokejournal.domain.model.PokemonListItem
import com.pokejournal.ui.components.CreateDialogState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PokemonListViewModel @Inject constructor(
    private val pokemonRepository: PokemonRepository,
    private val journalRepository: JournalRepository,
    private val presetRepository: PresetRepository,
    private val settingsRepository: SettingsRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val queryInput = MutableStateFlow("")
    private val filter = MutableStateFlow(ListFilter())

    private val refreshRequests = MutableSharedFlow<RefreshRequest>(extraBufferCapacity = 8)

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    private val profileId = settingsRepository.activeProfileId
    private val userSettings = profileId.flatMapLatest { settingsRepository.userSettings(it) }
    private val isOnline = networkMonitor.isOnline.distinctUntilChanged()

    private val searchQuery: Flow<String> = queryInput
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .map { PokemonListBuilder.normalizeQuery(it) }
        .distinctUntilChanged()

    private val refreshTriggers: Flow<RefreshRequest> = merge(
        refreshRequests,
        userSettings.map { it.pokedexSize }.distinctUntilChanged().drop(1).map { RefreshRequest(force = false) },
        isOnline.drop(1).filter { it }.map { RefreshRequest(force = false) },
    ).onStart { emit(RefreshRequest(force = false)) }

    private val refreshState: StateFlow<RefreshState> = refreshTriggers
        .flatMapLatest { request ->
            flow {
                emit(RefreshState.Running)
                val limit = userSettings.first().pokedexSize
                val ttl = settingsRepository.appSettings.first().cacheTtlMillis
                when (val result = pokemonRepository.refreshCatalog(limit, ttl, request.force)) {
                    is RefreshResult.Failed -> emit(RefreshState.Failed(result.error.toUserMessage()))
                    RefreshResult.Updated, RefreshResult.UpToDate, is RefreshResult.Partial -> emit(RefreshState.Idle)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RefreshState.Running)

    private val listData: Flow<ListData> = combine(
        pokemonRepository.observeCatalog(),
        profileId.flatMapLatest { journalRepository.observeStates(it) },
        searchQuery,
        filter,
        userSettings,
    ) { catalog, states, query, currentFilter, settings ->
        ListData(
            totalInDex = catalog.count { it.id <= settings.pokedexSize },
            items = PokemonListBuilder.build(
                catalog = catalog,
                states = states,
                query = query,
                filter = currentFilter,
                sortOrder = settings.sortOrder,
                pokedexSize = settings.pokedexSize,
            ),
            hasCriteria = query.isNotEmpty() || currentFilter.isActive,
        )
    }

    private val presets: Flow<List<FilterPreset>> = profileId.flatMapLatest { presetRepository.observePresets(it) }

    private val savePresetDialog = MutableStateFlow(CreateDialogState())

    val uiState: StateFlow<PokemonListUiState> = combine(
        combine(
            listData,
            refreshState,
            isOnline,
            pokemonRepository.observeLastCatalogSync(),
            presets,
        ) { data, refresh, online, lastSync, presetList ->
            reduce(data, refresh, online, lastSync, presetList)
        },
        queryInput,
        filter,
        savePresetDialog,
    ) { state, query, currentFilter, dialog ->
        state.copy(query = query, filter = currentFilter, savePresetDialog = dialog)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PokemonListUiState())

    fun onEvent(event: PokemonListEvent) {
        when (event) {
            is PokemonListEvent.QueryChanged -> queryInput.value = event.query
            is PokemonListEvent.TypeSelected -> filter.update { it.copy(type = event.type) }
            is PokemonListEvent.StatusSelected -> filter.update { it.copy(status = event.status) }
            PokemonListEvent.Refresh, PokemonListEvent.Retry -> refreshRequests.tryEmit(RefreshRequest(force = true))
            PokemonListEvent.ClearFilters -> {
                queryInput.value = ""
                filter.value = ListFilter()
            }
            is PokemonListEvent.TogglePinned -> viewModelScope.launch {
                journalRepository.setPinned(profileId.first(), event.pokemonId, event.pinned)
            }
            PokemonListEvent.OpenSavePresetDialog -> savePresetDialog.value = CreateDialogState(visible = true)
            PokemonListEvent.DismissSavePresetDialog -> savePresetDialog.value = CreateDialogState()
            is PokemonListEvent.SavePreset -> {
                if (savePresetDialog.value.submitting) return
                savePresetDialog.update { it.copy(submitting = true) }
                viewModelScope.launch { savePreset(event.name) }
            }
            is PokemonListEvent.ApplyPreset -> {
                queryInput.value = event.preset.query
                filter.value = event.preset.filter
            }
            is PokemonListEvent.DeletePreset -> viewModelScope.launch {
                presetRepository.deletePreset(event.presetId)
            }
        }
    }

    private suspend fun savePreset(name: String) {
        when (val result = presetRepository.savePreset(profileId.first(), name, queryInput.value, filter.value)) {
            is CreateResult.Created -> {
                savePresetDialog.value = CreateDialogState()
                messageChannel.send("Фильтр «${name.trim()}» сохранён")
            }
            is CreateResult.Rejected -> savePresetDialog.update { current ->
                if (current.visible) CreateDialogState(visible = true, error = result.error.message()) else current
            }
        }
    }

    private data class RefreshRequest(val force: Boolean)

    private data class ListData(
        val totalInDex: Int,
        val items: List<PokemonListItem>,
        val hasCriteria: Boolean,
    )

    private fun reduce(
        data: ListData,
        refresh: RefreshState,
        online: Boolean,
        lastSync: Long?,
        presetList: List<FilterPreset>,
    ): PokemonListUiState {
        val hasCache = data.totalInDex > 0
        val content = when {
            !hasCache && refresh is RefreshState.Running -> ListContent.Loading
            !hasCache && refresh is RefreshState.Failed -> ListContent.Error(refresh.message)
            !hasCache -> ListContent.Empty(EmptyReason.NO_DATA)
            data.items.isEmpty() -> ListContent.Empty(
                if (data.hasCriteria) EmptyReason.NO_MATCHES else EmptyReason.NO_DATA,
            )
            else -> ListContent.Success(data.items)
        }
        return PokemonListUiState(
            content = content,
            isRefreshing = hasCache && refresh is RefreshState.Running,
            refreshError = if (hasCache) (refresh as? RefreshState.Failed)?.message else null,
            isOffline = !online,
            lastSyncAt = lastSync,
            totalInDex = data.totalInDex,
            presets = presetList,
        )
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
