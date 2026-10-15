package com.pokejournal.data.repository

import com.pokejournal.data.local.dao.SyncDao
import javax.inject.Inject

/** Источник id покемонов, которые стоит держать офлайн. */
interface ImportantPokemonSource {
    suspend fun importantIds(): List<Int>
}

class RoomImportantPokemonSource @Inject constructor(
    private val dao: SyncDao,
) : ImportantPokemonSource {
    override suspend fun importantIds(): List<Int> = dao.importantPokemonIds()
}
