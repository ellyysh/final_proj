package com.pokejournal.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/* ---------- Кэш контента из PokeAPI (общий для всех профилей) ---------- */

@Entity(tableName = "pokemon")
data class PokemonEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val imageUrl: String,
)

@Entity(
    tableName = "pokemon_types",
    primaryKeys = ["pokemonId", "typeName"],
    indices = [Index("typeName")],
)
data class PokemonTypeEntity(
    val pokemonId: Int,
    val typeName: String,
    val slot: Int,
)

/** Детали хранятся отдельно, чтобы обновление каталога не затирало их. */
@Entity(tableName = "pokemon_details")
data class PokemonDetailEntity(
    @PrimaryKey val pokemonId: Int,
    val height: Int,
    val weight: Int,
    val baseExperience: Int?,
    val genus: String?,
    val flavorText: String?,
    val abilities: String,
    val fetchedAt: Long,
)

@Entity(tableName = "pokemon_stats", primaryKeys = ["pokemonId", "statName"])
data class PokemonStatEntity(
    val pokemonId: Int,
    val statName: String,
    val value: Int,
    val position: Int,
)

/** Метаданные актуальности кэша: когда и в каком объёме обновляли. */
@Entity(tableName = "cache_meta")
data class CacheMetaEntity(
    @PrimaryKey val key: String,
    val updatedAt: Long,
    val itemCount: Int,
)

data class PokemonWithTypes(
    @Embedded val pokemon: PokemonEntity,
    @Relation(parentColumn = "id", entityColumn = "pokemonId")
    val types: List<PokemonTypeEntity>,
)

data class PokemonFull(
    @Embedded val pokemon: PokemonEntity,
    @Relation(parentColumn = "id", entityColumn = "pokemonId")
    val types: List<PokemonTypeEntity>,
    @Relation(parentColumn = "id", entityColumn = "pokemonId")
    val stats: List<PokemonStatEntity>,
    @Relation(parentColumn = "id", entityColumn = "pokemonId")
    val detail: PokemonDetailEntity?,
)
