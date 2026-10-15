package com.pokejournal.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.pokejournal.data.local.entity.CollectionEntity
import com.pokejournal.data.local.entity.CollectionItemEntity
import com.pokejournal.data.local.entity.CollectionMemberRow
import com.pokejournal.data.local.entity.CollectionSummaryRow
import com.pokejournal.data.local.entity.FilterPresetEntity
import com.pokejournal.data.local.entity.HistoryEntity
import com.pokejournal.data.local.entity.HistoryRow
import com.pokejournal.data.local.entity.NoteEntity
import com.pokejournal.data.local.entity.NoteRow
import com.pokejournal.data.local.entity.PokemonUserStateEntity
import com.pokejournal.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAt, id")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY createdAt, id")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observe(id: Long): Flow<ProfileEntity?>

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int
}

@Dao
interface UserStateDao {
    @Query("SELECT * FROM pokemon_user_state WHERE profileId = :profileId")
    fun observeAll(profileId: Long): Flow<List<PokemonUserStateEntity>>

    @Query("SELECT * FROM pokemon_user_state WHERE profileId = :profileId AND pokemonId = :pokemonId")
    fun observe(profileId: Long, pokemonId: Int): Flow<PokemonUserStateEntity?>

    @Query("SELECT * FROM pokemon_user_state WHERE profileId = :profileId AND pokemonId = :pokemonId")
    suspend fun get(profileId: Long, pokemonId: Int): PokemonUserStateEntity?

    @Upsert
    suspend fun upsert(state: PokemonUserStateEntity)

    @Query("DELETE FROM pokemon_user_state WHERE profileId = :profileId AND pokemonId = :pokemonId")
    suspend fun delete(profileId: Long, pokemonId: Int)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE profileId = :profileId AND pokemonId = :pokemonId")
    fun observe(profileId: Long, pokemonId: Int): Flow<NoteEntity?>

    @Query(
        """
        SELECT n.pokemonId AS pokemonId, p.name AS name, n.text AS text, n.updatedAt AS updatedAt
        FROM notes n LEFT JOIN pokemon p ON p.id = n.pokemonId
        WHERE n.profileId = :profileId
        ORDER BY n.updatedAt DESC
        """,
    )
    fun observeAll(profileId: Long): Flow<List<NoteRow>>

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM notes WHERE profileId = :profileId AND pokemonId = :pokemonId")
    suspend fun delete(profileId: Long, pokemonId: Int)
}

@Dao
interface CollectionDao {
    @Query(
        """
        SELECT c.id AS id, c.name AS name, c.description AS description, c.createdAt AS createdAt,
               COUNT(i.pokemonId) AS itemCount, COUNT(d.pokemonId) AS offlineCount
        FROM collections c
        LEFT JOIN collection_items i ON i.collectionId = c.id
        LEFT JOIN pokemon_details d ON d.pokemonId = i.pokemonId
        WHERE c.profileId = :profileId
        GROUP BY c.id
        ORDER BY c.createdAt DESC, c.id DESC
        """,
    )
    fun observeSummaries(profileId: Long): Flow<List<CollectionSummaryRow>>

    @Query(
        """
        SELECT c.id AS id, c.name AS name, c.description AS description, c.createdAt AS createdAt,
               COUNT(i.pokemonId) AS itemCount, COUNT(d.pokemonId) AS offlineCount
        FROM collections c
        LEFT JOIN collection_items i ON i.collectionId = c.id
        LEFT JOIN pokemon_details d ON d.pokemonId = i.pokemonId
        WHERE c.id = :collectionId
        GROUP BY c.id
        """,
    )
    fun observeSummary(collectionId: Long): Flow<CollectionSummaryRow?>

    @Query(
        """
        SELECT p.id AS pokemonId, p.name AS name, p.imageUrl AS imageUrl,
               d.fetchedAt AS detailFetchedAt, i.addedAt AS addedAt
        FROM collection_items i
        JOIN pokemon p ON p.id = i.pokemonId
        LEFT JOIN pokemon_details d ON d.pokemonId = i.pokemonId
        WHERE i.collectionId = :collectionId
        ORDER BY i.addedAt DESC, p.id
        """,
    )
    fun observeMembers(collectionId: Long): Flow<List<CollectionMemberRow>>

    @Query(
        """
        SELECT i.collectionId FROM collection_items i
        JOIN collections c ON c.id = i.collectionId
        WHERE c.profileId = :profileId AND i.pokemonId = :pokemonId
        """,
    )
    fun observeCollectionIdsFor(profileId: Long, pokemonId: Int): Flow<List<Long>>

    @Query("SELECT name FROM collections WHERE profileId = :profileId")
    suspend fun namesFor(profileId: Long): List<String>

    @Query("SELECT pokemonId FROM collection_items WHERE collectionId = :collectionId")
    suspend fun memberIds(collectionId: Long): List<Int>

    @Insert
    suspend fun insert(collection: CollectionEntity): Long

    @Query("DELETE FROM collections WHERE id = :collectionId")
    suspend fun delete(collectionId: Long)

    /** IGNORE — повторное добавление не создаёт дубль. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItem(item: CollectionItemEntity): Long

    @Query("DELETE FROM collection_items WHERE collectionId = :collectionId AND pokemonId = :pokemonId")
    suspend fun removeItem(collectionId: Long, pokemonId: Int)
}

@Dao
interface HistoryDao {
    @Query(
        """
        SELECT h.pokemonId AS pokemonId, p.name AS name, h.viewedAt AS viewedAt
        FROM history h LEFT JOIN pokemon p ON p.id = h.pokemonId
        WHERE h.profileId = :profileId
        ORDER BY h.viewedAt DESC
        """,
    )
    fun observe(profileId: Long): Flow<List<HistoryRow>>

    @Upsert
    suspend fun upsert(entry: HistoryEntity)

    @Query(
        """
        DELETE FROM history WHERE profileId = :profileId AND pokemonId NOT IN (
            SELECT pokemonId FROM history WHERE profileId = :profileId
            ORDER BY viewedAt DESC LIMIT :limit
        )
        """,
    )
    suspend fun trim(profileId: Long, limit: Int)

    @Query("DELETE FROM history WHERE profileId = :profileId AND pokemonId = :pokemonId")
    suspend fun delete(profileId: Long, pokemonId: Int)

    @Query("DELETE FROM history WHERE profileId = :profileId")
    suspend fun clear(profileId: Long)
}

@Dao
interface FilterPresetDao {
    @Query("SELECT * FROM filter_presets WHERE profileId = :profileId ORDER BY createdAt, id")
    fun observe(profileId: Long): Flow<List<FilterPresetEntity>>

    @Query("SELECT name FROM filter_presets WHERE profileId = :profileId")
    suspend fun namesFor(profileId: Long): List<String>

    @Insert
    suspend fun insert(preset: FilterPresetEntity): Long

    @Query("DELETE FROM filter_presets WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SyncDao {
    /**
     * Покемоны, важные пользователю (во всех профилях): в коллекциях, с личным статусом,
     * закреплённые и из истории. Их детали предзагружаются для офлайна.
     */
    @Query(
        """
        SELECT pokemonId FROM collection_items
        UNION SELECT pokemonId FROM pokemon_user_state WHERE status != 'NONE' OR pinned = 1
        UNION SELECT pokemonId FROM history
        UNION SELECT pokemonId FROM notes
        """,
    )
    suspend fun importantPokemonIds(): List<Int>
}
