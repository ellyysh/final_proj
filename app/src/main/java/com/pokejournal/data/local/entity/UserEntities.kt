package com.pokejournal.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/* ---------- Пользовательские данные (разделены по локальным профилям) ---------- */

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatar: String,
    val createdAt: Long,
)

@Entity(
    tableName = "pokemon_user_state",
    primaryKeys = ["profileId", "pokemonId"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PokemonUserStateEntity(
    val profileId: Long,
    val pokemonId: Int,
    val status: String,
    val pinned: Boolean,
    val hidden: Boolean,
    val updatedAt: Long,
)

@Entity(
    tableName = "notes",
    primaryKeys = ["profileId", "pokemonId"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class NoteEntity(
    val profileId: Long,
    val pokemonId: Int,
    val text: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "collections",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    val description: String,
    val createdAt: Long,
)

/** Связь many-to-many: коллекция <-> покемон. */
@Entity(
    tableName = "collection_items",
    primaryKeys = ["collectionId", "pokemonId"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("pokemonId")],
)
data class CollectionItemEntity(
    val collectionId: Long,
    val pokemonId: Int,
    val addedAt: Long,
)

@Entity(
    tableName = "history",
    primaryKeys = ["profileId", "pokemonId"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class HistoryEntity(
    val profileId: Long,
    val pokemonId: Int,
    val viewedAt: Long,
)

@Entity(
    tableName = "filter_presets",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
data class FilterPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    val query: String,
    val typeName: String?,
    val status: String,
    val createdAt: Long,
)

/* ---------- Проекции для запросов с JOIN ---------- */

data class CollectionSummaryRow(
    val id: Long,
    val name: String,
    val description: String,
    val createdAt: Long,
    val itemCount: Int,
    val offlineCount: Int,
)

data class CollectionMemberRow(
    val pokemonId: Int,
    val name: String,
    val imageUrl: String,
    val detailFetchedAt: Long?,
    val addedAt: Long,
)

data class HistoryRow(
    val pokemonId: Int,
    val name: String?,
    val viewedAt: Long,
)

data class NoteRow(
    val pokemonId: Int,
    val name: String?,
    val text: String,
    val updatedAt: Long,
)
