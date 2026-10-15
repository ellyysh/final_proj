package com.pokejournal.data.repository

import com.pokejournal.data.local.dao.HistoryDao
import com.pokejournal.data.local.entity.HistoryEntity
import com.pokejournal.domain.logic.TimeProvider
import com.pokejournal.domain.model.HistoryEntry
import com.pokejournal.domain.model.PokemonImages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** История просмотров карточек (recent) — без дублей, с лимитом из настроек профиля. */
interface HistoryRepository {
    fun observeHistory(profileId: Long): Flow<List<HistoryEntry>>
    suspend fun record(profileId: Long, pokemonId: Int, limit: Int)
    suspend fun remove(profileId: Long, pokemonId: Int)
    suspend fun clear(profileId: Long)
}

@Singleton
class RoomHistoryRepository @Inject constructor(
    private val dao: HistoryDao,
    private val time: TimeProvider,
) : HistoryRepository {

    override fun observeHistory(profileId: Long): Flow<List<HistoryEntry>> =
        dao.observe(profileId).map { rows ->
            rows.map {
                HistoryEntry(it.pokemonId, it.name, PokemonImages.artworkUrl(it.pokemonId), it.viewedAt)
            }
        }

    override suspend fun record(profileId: Long, pokemonId: Int, limit: Int) {
        dao.upsert(HistoryEntity(profileId, pokemonId, time.now()))
        dao.trim(profileId, limit.coerceAtLeast(1))
    }

    override suspend fun remove(profileId: Long, pokemonId: Int) = dao.delete(profileId, pokemonId)

    override suspend fun clear(profileId: Long) = dao.clear(profileId)
}
