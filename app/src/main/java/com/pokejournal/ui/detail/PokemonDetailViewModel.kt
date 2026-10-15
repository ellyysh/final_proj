package com.pokejournal.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokejournal.data.network.NetworkMonitor
import com.pokejournal.data.repository.CollectionRepository
import com.pokejournal.data.repository.CreateResult
import com.pokejournal.data.repository.HistoryRepository
import com.pokejournal.data.repository.JournalRepository
import com.pokejournal.data.repository.PokemonRepository
import com.pokejournal.data.repository.RefreshResult
import com.pokejournal.data.settings.SettingsRepository
import com.pokejournal.domain.logic.message
import com.pokejournal.domain.logic.toUserMessage
import com.pokejournal.domain.model.PokemonUserState
import com.pokejournal.ui.list.RefreshState
import com.pokejournal.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PokemonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pokemonRepository: PokemonRepository,
    private val journalRepository: JournalRepository,
    private val collectionRepository: CollectionRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    val pokemonId: Int = checkNotNull(savedStateHandle.get<Int>(Routes.ARG_ID)) { "Не передан id покемона" }

    private val profileId = settingsRepository.activeProfileId
    private val refreshRequests = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    private val refreshState: StateFlow<RefreshState> = refreshRequests
        .onStart { emit(false) }
        .flatMapLatest { force ->
            flow {
                emit(RefreshState.Running)
                val ttl = settingsRepository.appSettings.first().cacheTtlMillis
                when (val result = pokemonRepository.refreshDetail(pokemonId, ttl, force)) {
                    is RefreshResult.Failed -> emit(RefreshState.Failed(result.error.toUserMessage()))
                    else -> emit(RefreshState.Idle)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RefreshState.Running)

    /** Личные данные профиля о покемоне: статус, заметка, коллекции. */
    private val userPart: Flow<UserPart> = profileId.flatMapLatest { pid ->
        combine(
            journalRepository.observeState(pid, pokemonId),
            journalRepository.observeNote(pid, pokemonId),
            collectionRepository.observeCollections(pid),
            collectionRepository.observeCollectionIdsFor(pid, pokemonId),
        ) { state, note, collections, memberOf ->
            UserPart(
                state = state,
                note = note,
                collections = collections.map { CollectionChoice(it.id, it.name, it.id in memberOf) },
            )
        }
    }

    val uiState: StateFlow<PokemonDetailUiState> = combine(
        pokemonRepository.observeDetail(pokemonId),
        refreshState,
        userPart,
        networkMonitor.isOnline,
    ) { detail, refresh, user, online ->
        val hasDetail = detail?.isComplete == true
        val content = when {
            hasDetail -> DetailContent.Success(detail!!)
            refresh is RefreshState.Failed -> DetailContent.Error(refresh.message)
            else -> DetailContent.Loading
        }
        PokemonDetailUiState(
            pokemonId = pokemonId,
            title = detail?.name.orEmpty(),
            content = content,
            isRefreshing = hasDetail && refresh is RefreshState.Running,
            refreshError = if (hasDetail) (refresh as? RefreshState.Failed)?.message else null,
            isOffline = !online,
            userState = user.state,
            note = user.note,
            collections = user.collections,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        PokemonDetailUiState(pokemonId = pokemonId, userState = PokemonUserState(pokemonId)),
    )

    init {
        // Каждое открытие карточки попадает в историю текущего профиля.
        viewModelScope.launch {
            profileId.distinctUntilChanged().collect { pid ->
                val limit = settingsRepository.userSettings(pid).first().historyLimit
                historyRepository.record(pid, pokemonId, limit)
            }
        }
    }

    fun onEvent(event: PokemonDetailEvent) {
        when (event) {
            PokemonDetailEvent.Retry, PokemonDetailEvent.Refresh -> refreshRequests.tryEmit(true)
            is PokemonDetailEvent.SetStatus -> withProfile { pid ->
                journalRepository.setStatus(pid, pokemonId, event.status)
            }
            PokemonDetailEvent.TogglePinned -> withProfile { pid ->
                journalRepository.setPinned(pid, pokemonId, !uiState.value.userState.pinned)
            }
            PokemonDetailEvent.ToggleHidden -> withProfile { pid ->
                val hide = !uiState.value.userState.hidden
                journalRepository.setHidden(pid, pokemonId, hide)
                messageChannel.send(if (hide) "Покемон скрыт из каталога" else "Покемон снова в каталоге")
            }
            is PokemonDetailEvent.SaveNote -> withProfile { pid ->
                journalRepository.saveNote(pid, pokemonId, event.text)
                messageChannel.send(if (event.text.isBlank()) "Заметка удалена" else "Заметка сохранена")
            }
            is PokemonDetailEvent.ToggleCollection -> withProfile {
                collectionRepository.setMembership(event.collectionId, pokemonId, event.selected)
            }
            is PokemonDetailEvent.CreateCollectionWithPokemon -> withProfile { pid ->
                when (val result = collectionRepository.createCollection(pid, event.name, description = "")) {
                    is CreateResult.Created -> {
                        collectionRepository.setMembership(result.id, pokemonId, member = true)
                        messageChannel.send("Добавлено в «${event.name.trim()}»")
                    }
                    is CreateResult.Rejected -> messageChannel.send(result.error.message())
                }
            }
        }
    }

    private fun withProfile(block: suspend (Long) -> Unit) {
        viewModelScope.launch { block(profileId.first()) }
    }

    private data class UserPart(
        val state: PokemonUserState,
        val note: String,
        val collections: List<CollectionChoice>,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
