package com.pokejournal.data.repository

import com.pokejournal.data.local.dao.SyncDao
import javax.inject.Inject

interface ImportantPokemonSource {

    suspend fun importantIds(): List<Int>

    suspend fun collectionIds(): List<Int>
}

class RoomImportantPokemonSource @Inject constructor(
    private val dao: SyncDao,
) : ImportantPokemonSource {
    override suspend fun importantIds(): List<Int> = dao.importantPokemonIds()

    override suspend fun collectionIds(): List<Int> = dao.collectionPokemonIds()
}
