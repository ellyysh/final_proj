package com.pokejournal.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.pokejournal.data.local.entity.CacheMetaEntity
import com.pokejournal.data.local.entity.PokemonDetailEntity
import com.pokejournal.data.local.entity.PokemonEntity
import com.pokejournal.data.local.entity.PokemonFull
import com.pokejournal.data.local.entity.PokemonStatEntity
import com.pokejournal.data.local.entity.PokemonTypeEntity
import com.pokejournal.data.local.entity.PokemonWithTypes
import kotlinx.coroutines.flow.Flow

@Dao
interface PokemonDao {

    @Transaction
    @Query("SELECT * FROM pokemon ORDER BY id")
    fun observeCatalog(): Flow<List<PokemonWithTypes>>

    @Transaction
    @Query("SELECT * FROM pokemon WHERE id = :id")
    fun observeFull(id: Int): Flow<PokemonFull?>

    @Query("SELECT fetchedAt FROM pokemon_details WHERE pokemonId = :id")
    suspend fun detailFetchedAt(id: Int): Long?

    @Query("SELECT COUNT(*) FROM pokemon")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertPokemon(items: List<PokemonEntity>)

    @Query("DELETE FROM pokemon_types WHERE pokemonId IN (:ids)")
    suspend fun deleteTypesFor(ids: List<Int>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTypes(types: List<PokemonTypeEntity>)

    @Upsert
    suspend fun upsertDetail(detail: PokemonDetailEntity)

    @Query("DELETE FROM pokemon_stats WHERE pokemonId = :id")
    suspend fun deleteStats(id: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(stats: List<PokemonStatEntity>)

    @Query("SELECT COUNT(*) FROM pokemon_details")
    fun observeDetailCount(): Flow<Int>
}

@Dao
interface CacheMetaDao {
    @Query("SELECT * FROM cache_meta WHERE `key` = :key")
    suspend fun get(key: String): CacheMetaEntity?

    @Query("SELECT * FROM cache_meta WHERE `key` = :key")
    fun observe(key: String): Flow<CacheMetaEntity?>

    @Upsert
    suspend fun upsert(meta: CacheMetaEntity)
}
