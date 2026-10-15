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

/**
 * Экран каталога собирается из независимых потоков:
 *  - ввод поиска (debounce + distinctUntilChanged)
 *  - выбранный фильтр
 *  - каталог из Room (кэш PokeAPI)
 *  - личные состояния профиля из Room (статусы, закрепление, скрытие)
 *  - настройки профиля из DataStore (сортировка, размер Pokédex)
 *  - поток действий обновления (Refresh/Retry + смена размера Pokédex + восстановление сети)
 *  - состояние сети, время последней синхронизации, пресеты фильтров
 */
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

    /** Поток пользовательских действий «обнови». SharedFlow — это именно поток событий. */
    private val refreshRequests = MutableSharedFlow<RefreshRequest>(extraBufferCapacity = 8)

    /** One-time события (snackbar) отделены от состояния экрана. */
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    private val profileId = settingsRepository.activeProfileId
    private val userSettings = profileId.flatMapLatest { settingsRepository.userSettings(it) }
    private val isOnline = networkMonitor.isOnline.distinctUntilChanged()

    /** Пустой запрос применяется сразу, остальное — с задержкой 300 мс. */
    private val searchQuery: Flow<String> = queryInput
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .map { PokemonListBuilder.normalizeQuery(it) }
        .distinctUntilChanged()

    private val refreshTriggers: Flow<RefreshRequest> = merge(
        refreshRequests,
        // Пользователь расширил Pokédex в настройках — догружаем каталог.
        userSettings.map { it.pokedexSize }.distinctUntilChanged().drop(1).map { RefreshRequest(force = false) },
        // Сеть появилась — пробуем актуализировать (TTL всё равно проверится).
        isOnline.drop(1).filter { it }.map { RefreshRequest(force = false) },
    ).onStart { emit(RefreshRequest(force = false)) }

    /** flatMapLatest отменяет устаревший запрос, если пришёл новый (Retry во время загрузки). */
    private val refreshState: StateFlow<RefreshState> = refreshTriggers
        .flatMapLatest { request ->
            flow {
                emit(RefreshState.Running)
                val limit = userSettings.first().pokedexSize
                val ttl = settingsRepository.appSettings.first().cacheTtlMillis
                when (val result = pokemonRepository.refreshCatalog(limit, ttl, request.force)) {
                    is RefreshResult.Failed -> emit(RefreshState.Failed(result.error.toUserMessage()))
                    RefreshResult.Updated, RefreshResult.UpToDate -> emit(RefreshState.Idle)
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
    ) { state, query, currentFilter -> state.copy(query = query, filter = currentFilter) }
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
            is PokemonListEvent.SavePreset -> viewModelScope.launch { savePreset(event.name) }
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
        val result = presetRepository.savePreset(profileId.first(), name, queryInput.value, filter.value)
        val message = when (result) {
            is CreateResult.Created -> "Фильтр «${name.trim()}» сохранён"
            is CreateResult.Rejected -> result.error.message()
        }
        messageChannel.send(message)
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
