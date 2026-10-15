package com.pokejournal.domain.logic

import com.pokejournal.domain.model.CatchStatus
import com.pokejournal.domain.model.PokemonSummary
import com.pokejournal.domain.model.PokemonUserState

data class TypeProgress(val type: String, val caught: Int, val total: Int)

data class JournalStats(
    val total: Int = 0,
    val caught: Int = 0,
    val wanted: Int = 0,
    val seen: Int = 0,
    val pinned: Int = 0,
    val hidden: Int = 0,
    val byType: List<TypeProgress> = emptyList(),
) {
    val completionPercent: Int get() = if (total == 0) 0 else caught * 100 / total
}

/** Считает прогресс личного Pokédex в рамках выбранного размера. */
object StatsCalculator {

    fun calculate(
        catalog: List<PokemonSummary>,
        states: Map<Int, PokemonUserState>,
        pokedexSize: Int,
    ): JournalStats {
        val inDex = catalog.filter { it.id <= pokedexSize }
        val stateOf: (Int) -> PokemonUserState = { id -> states[id] ?: PokemonUserState(id) }

        val byType = inDex
            .flatMap { summary -> summary.types.map { type -> type to summary.id } }
            .groupBy({ it.first }, { it.second })
            .map { (type, ids) ->
                TypeProgress(
                    type = type,
                    caught = ids.count { stateOf(it).status == CatchStatus.CAUGHT },
                    total = ids.size,
                )
            }
            .sortedWith(compareByDescending<TypeProgress> { it.caught }.thenBy { it.type })

        return JournalStats(
            total = inDex.size,
            caught = inDex.count { stateOf(it.id).status == CatchStatus.CAUGHT },
            wanted = inDex.count { stateOf(it.id).status == CatchStatus.WANTED },
            seen = inDex.count { stateOf(it.id).status == CatchStatus.SEEN },
            pinned = inDex.count { stateOf(it.id).pinned },
            hidden = inDex.count { stateOf(it.id).hidden },
            byType = byType,
        )
    }
}
