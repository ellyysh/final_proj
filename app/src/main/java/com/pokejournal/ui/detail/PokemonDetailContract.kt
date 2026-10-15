package com.pokejournal.ui.detail

import com.pokejournal.domain.model.CatchStatus
import com.pokejournal.domain.model.PokemonDetail
import com.pokejournal.domain.model.PokemonUserState

sealed interface DetailContent {
    data object Loading : DetailContent
    data class Error(val message: String) : DetailContent
    data class Success(val pokemon: PokemonDetail) : DetailContent
}

data class CollectionChoice(val id: Long, val name: String, val selected: Boolean)

data class PokemonDetailUiState(
    val pokemonId: Int = 0,
    val title: String = "",
    val content: DetailContent = DetailContent.Loading,
    val isRefreshing: Boolean = false,
    val refreshError: String? = null,
    val isOffline: Boolean = false,
    val userState: PokemonUserState = PokemonUserState(0),
    val note: String = "",
    val collections: List<CollectionChoice> = emptyList(),
)

sealed interface PokemonDetailEvent {
    data object Retry : PokemonDetailEvent
    data object Refresh : PokemonDetailEvent
    data class SetStatus(val status: CatchStatus) : PokemonDetailEvent
    data object TogglePinned : PokemonDetailEvent
    data object ToggleHidden : PokemonDetailEvent
    data class SaveNote(val text: String) : PokemonDetailEvent
    data class ToggleCollection(val collectionId: Long, val selected: Boolean) : PokemonDetailEvent
    data class CreateCollectionWithPokemon(val name: String) : PokemonDetailEvent
}
