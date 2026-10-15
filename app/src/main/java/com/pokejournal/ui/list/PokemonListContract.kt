package com.pokejournal.ui.list

import com.pokejournal.domain.model.FilterPreset
import com.pokejournal.domain.model.ListFilter
import com.pokejournal.domain.model.PokemonListItem
import com.pokejournal.domain.model.StatusFilter

sealed interface ListContent {
    data object Loading : ListContent
    data class Error(val message: String) : ListContent
    data class Empty(val reason: EmptyReason) : ListContent
    data class Success(val items: List<PokemonListItem>) : ListContent
}

enum class EmptyReason { NO_DATA, NO_MATCHES }

data class PokemonListUiState(
    val query: String = "",
    val filter: ListFilter = ListFilter(),
    val content: ListContent = ListContent.Loading,
    val isRefreshing: Boolean = false,
    val refreshError: String? = null,
    val isOffline: Boolean = false,
    val lastSyncAt: Long? = null,
    val totalInDex: Int = 0,
    val presets: List<FilterPreset> = emptyList(),
)

sealed interface PokemonListEvent {
    data class QueryChanged(val query: String) : PokemonListEvent
    data class TypeSelected(val type: String?) : PokemonListEvent
    data class StatusSelected(val status: StatusFilter) : PokemonListEvent
    data object Refresh : PokemonListEvent
    data object Retry : PokemonListEvent
    data object ClearFilters : PokemonListEvent
    data class TogglePinned(val pokemonId: Int, val pinned: Boolean) : PokemonListEvent
    data class SavePreset(val name: String) : PokemonListEvent
    data class ApplyPreset(val preset: FilterPreset) : PokemonListEvent
    data class DeletePreset(val presetId: Long) : PokemonListEvent
}

/** Внутреннее состояние ручного/авто обновления каталога. */
sealed interface RefreshState {
    data object Running : RefreshState
    data object Idle : RefreshState
    data class Failed(val message: String) : RefreshState
}
