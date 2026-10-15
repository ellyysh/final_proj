package com.pokejournal.domain.model

/** Краткая карточка покемона для каталога (живёт в Room, приходит из API). */
data class PokemonSummary(
    val id: Int,
    val name: String,
    val imageUrl: String,
    val types: List<String>,
)

data class PokemonStat(val name: String, val value: Int)

/**
 * Полная информация о покемоне. Если [fetchedAt] == null — детали ещё не загружались,
 * доступны только базовые поля из каталога.
 */
data class PokemonDetail(
    val id: Int,
    val name: String,
    val imageUrl: String,
    val types: List<String>,
    val height: Int?,
    val weight: Int?,
    val baseExperience: Int?,
    val genus: String?,
    val flavorText: String?,
    val abilities: List<String>,
    val stats: List<PokemonStat>,
    val fetchedAt: Long?,
) {
    val isComplete: Boolean get() = fetchedAt != null
}

/** Личный статус покемона в журнале пользователя. */
enum class CatchStatus { NONE, SEEN, WANTED, CAUGHT }

/** Пользовательское состояние покемона внутри профиля. */
data class PokemonUserState(
    val pokemonId: Int,
    val status: CatchStatus = CatchStatus.NONE,
    val pinned: Boolean = false,
    val hidden: Boolean = false,
) {
    val isDefault: Boolean get() = status == CatchStatus.NONE && !pinned && !hidden
}

enum class StatusFilter { ALL, CAUGHT, WANTED, SEEN, UNTRACKED, PINNED, HIDDEN }

data class ListFilter(
    val type: String? = null,
    val status: StatusFilter = StatusFilter.ALL,
) {
    val isActive: Boolean get() = type != null || status != StatusFilter.ALL
}

data class PokemonListItem(
    val summary: PokemonSummary,
    val state: PokemonUserState,
)

data class Profile(
    val id: Long,
    val name: String,
    val avatar: String,
    val createdAt: Long,
)

data class CollectionSummary(
    val id: Long,
    val name: String,
    val description: String,
    val itemCount: Int,
    val offlineCount: Int,
    val createdAt: Long,
) {
    val fullyOffline: Boolean get() = itemCount > 0 && offlineCount >= itemCount
}

data class CollectionMember(
    val pokemonId: Int,
    val name: String,
    val imageUrl: String,
    val availableOffline: Boolean,
    val addedAt: Long,
)

data class HistoryEntry(
    val pokemonId: Int,
    val name: String?,
    val imageUrl: String,
    val viewedAt: Long,
)

data class JournalNote(
    val pokemonId: Int,
    val pokemonName: String?,
    val text: String,
    val updatedAt: Long,
)

/** Сохранённый пресет поиска: запрос + фильтр. */
data class FilterPreset(
    val id: Long,
    val name: String,
    val query: String,
    val filter: ListFilter,
)

object PokemonTypes {
    val ALL: List<String> = listOf(
        "normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground",
        "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy",
    )
}

object PokemonImages {
    fun artworkUrl(id: Int): String =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/$id.png"
}
