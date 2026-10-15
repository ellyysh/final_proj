package com.pokejournal.data.repository

import com.pokejournal.data.local.dao.NoteDao
import com.pokejournal.data.local.dao.UserStateDao
import com.pokejournal.data.local.entity.NoteEntity
import com.pokejournal.data.local.entity.PokemonUserStateEntity
import com.pokejournal.domain.logic.TimeProvider
import com.pokejournal.domain.model.CatchStatus
import com.pokejournal.domain.model.JournalNote
import com.pokejournal.domain.model.PokemonUserState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Личный журнал профиля: статусы (поймал/хочу/видел), закрепление, скрытие, заметки. */
interface JournalRepository {
    fun observeStates(profileId: Long): Flow<Map<Int, PokemonUserState>>
    fun observeState(profileId: Long, pokemonId: Int): Flow<PokemonUserState>
    suspend fun setStatus(profileId: Long, pokemonId: Int, status: CatchStatus)
    suspend fun setPinned(profileId: Long, pokemonId: Int, pinned: Boolean)
    suspend fun setHidden(profileId: Long, pokemonId: Int, hidden: Boolean)
    fun observeNote(profileId: Long, pokemonId: Int): Flow<String>
    suspend fun saveNote(profileId: Long, pokemonId: Int, text: String)
    fun observeNotes(profileId: Long): Flow<List<JournalNote>>
}

@Singleton
class RoomJournalRepository @Inject constructor(
    private val stateDao: UserStateDao,
    private val noteDao: NoteDao,
    private val time: TimeProvider,
) : JournalRepository {

    override fun observeStates(profileId: Long): Flow<Map<Int, PokemonUserState>> =
        stateDao.observeAll(profileId).map { list -> list.associate { it.pokemonId to it.toDomain() } }

    override fun observeState(profileId: Long, pokemonId: Int): Flow<PokemonUserState> =
        stateDao.observe(profileId, pokemonId).map { it?.toDomain() ?: PokemonUserState(pokemonId) }

    override suspend fun setStatus(profileId: Long, pokemonId: Int, status: CatchStatus) =
        update(profileId, pokemonId) { it.copy(status = status) }

    override suspend fun setPinned(profileId: Long, pokemonId: Int, pinned: Boolean) =
        update(profileId, pokemonId) { it.copy(pinned = pinned) }

    override suspend fun setHidden(profileId: Long, pokemonId: Int, hidden: Boolean) =
        update(profileId, pokemonId) { it.copy(hidden = hidden, pinned = if (hidden) false else it.pinned) }

    override fun observeNote(profileId: Long, pokemonId: Int): Flow<String> =
        noteDao.observe(profileId, pokemonId).map { it?.text.orEmpty() }

    override suspend fun saveNote(profileId: Long, pokemonId: Int, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            noteDao.delete(profileId, pokemonId)
        } else {
            noteDao.upsert(NoteEntity(profileId, pokemonId, trimmed, time.now()))
        }
    }

    override fun observeNotes(profileId: Long): Flow<List<JournalNote>> =
        noteDao.observeAll(profileId).map { rows ->
            rows.map { JournalNote(it.pokemonId, it.name, it.text, it.updatedAt) }
        }

    /** Пустое состояние (без статуса, флагов) не храним — удаляем строку. */
    private suspend fun update(
        profileId: Long,
        pokemonId: Int,
        transform: (PokemonUserState) -> PokemonUserState,
    ) {
        val current = stateDao.get(profileId, pokemonId)?.toDomain() ?: PokemonUserState(pokemonId)
        val updated = transform(current)
        if (updated.isDefault) {
            stateDao.delete(profileId, pokemonId)
        } else {
            stateDao.upsert(
                PokemonUserStateEntity(
                    profileId = profileId,
                    pokemonId = pokemonId,
                    status = updated.status.name,
                    pinned = updated.pinned,
                    hidden = updated.hidden,
                    updatedAt = time.now(),
                ),
            )
        }
    }
}

private fun PokemonUserStateEntity.toDomain() = PokemonUserState(
    pokemonId = pokemonId,
    status = CatchStatus.entries.firstOrNull { it.name == status } ?: CatchStatus.NONE,
    pinned = pinned,
    hidden = hidden,
)
