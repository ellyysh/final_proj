package com.pokejournal.domain.logic

import com.pokejournal.domain.model.CatchStatus
import com.pokejournal.domain.model.ListFilter
import com.pokejournal.domain.model.PokemonListItem
import com.pokejournal.domain.model.PokemonSummary
import com.pokejournal.domain.model.PokemonUserState
import com.pokejournal.domain.model.SortOrder
import com.pokejournal.domain.model.StatusFilter

/**
 * Чистая бизнес-логика списка: объединяет каталог из API-кэша с личными данными
 * профиля и применяет поиск, фильтры, скрытие, закрепление и сортировку.
 */
object PokemonListBuilder {

    fun build(
        catalog: List<PokemonSummary>,
        states: Map<Int, PokemonUserState>,
        query: String,
        filter: ListFilter,
        sortOrder: SortOrder,
        pokedexSize: Int,
    ): List<PokemonListItem> {
        val normalizedQuery = normalizeQuery(query)
        return catalog.asSequence()
            .filter { it.id <= pokedexSize }
            .map { PokemonListItem(it, states[it.id] ?: PokemonUserState(it.id)) }
            .filter { matchesStatus(it.state, filter.status) }
            .filter { filter.type == null || filter.type in it.summary.types }
            .filter { matchesQuery(it.summary, normalizedQuery) }
            .sortedWith(comparator(sortOrder))
            .toList()
    }

    fun normalizeQuery(query: String): String = query.trim().lowercase().removePrefix("#")

    private fun matchesQuery(summary: PokemonSummary, query: String): Boolean {
        if (query.isEmpty()) return true
        val asNumber = query.toIntOrNull()
        if (asNumber != null) return summary.id == asNumber
        return summary.name.contains(query)
    }

    private fun matchesStatus(state: PokemonUserState, filter: StatusFilter): Boolean {
        // Скрытые покемоны видны только в специальном фильтре HIDDEN.
        if (filter == StatusFilter.HIDDEN) return state.hidden
        if (state.hidden) return false
        return when (filter) {
            StatusFilter.ALL -> true
            StatusFilter.CAUGHT -> state.status == CatchStatus.CAUGHT
            StatusFilter.WANTED -> state.status == CatchStatus.WANTED
            StatusFilter.SEEN -> state.status == CatchStatus.SEEN
            StatusFilter.UNTRACKED -> state.status == CatchStatus.NONE
            StatusFilter.PINNED -> state.pinned
            StatusFilter.HIDDEN -> state.hidden
        }
    }

    private fun statusRank(status: CatchStatus): Int = when (status) {
        CatchStatus.CAUGHT -> 0
        CatchStatus.WANTED -> 1
        CatchStatus.SEEN -> 2
        CatchStatus.NONE -> 3
    }

    private fun comparator(sortOrder: SortOrder): Comparator<PokemonListItem> {
        val pinnedFirst = compareByDescending<PokemonListItem> { it.state.pinned }
        val secondary: Comparator<PokemonListItem> = when (sortOrder) {
            SortOrder.NUMBER -> compareBy { it.summary.id }
            SortOrder.NAME -> compareBy<PokemonListItem> { it.summary.name }.thenBy { it.summary.id }
            SortOrder.STATUS -> compareBy<PokemonListItem> { statusRank(it.state.status) }.thenBy { it.summary.id }
        }
        return pinnedFirst.then(secondary)
    }
}
