package com.pokejournal.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pokejournal.data.local.dao.CacheMetaDao
import com.pokejournal.data.local.dao.CollectionDao
import com.pokejournal.data.local.dao.FilterPresetDao
import com.pokejournal.data.local.dao.HistoryDao
import com.pokejournal.data.local.dao.NoteDao
import com.pokejournal.data.local.dao.PokemonDao
import com.pokejournal.data.local.dao.ProfileDao
import com.pokejournal.data.local.dao.SyncDao
import com.pokejournal.data.local.dao.UserStateDao
import com.pokejournal.data.local.entity.CacheMetaEntity
import com.pokejournal.data.local.entity.CollectionEntity
import com.pokejournal.data.local.entity.CollectionItemEntity
import com.pokejournal.data.local.entity.FilterPresetEntity
import com.pokejournal.data.local.entity.HistoryEntity
import com.pokejournal.data.local.entity.NoteEntity
import com.pokejournal.data.local.entity.PokemonDetailEntity
import com.pokejournal.data.local.entity.PokemonEntity
import com.pokejournal.data.local.entity.PokemonStatEntity
import com.pokejournal.data.local.entity.PokemonTypeEntity
import com.pokejournal.data.local.entity.PokemonUserStateEntity
import com.pokejournal.data.local.entity.ProfileEntity

@Database(
    entities = [
        PokemonEntity::class,
        PokemonTypeEntity::class,
        PokemonDetailEntity::class,
        PokemonStatEntity::class,
        CacheMetaEntity::class,
        ProfileEntity::class,
        PokemonUserStateEntity::class,
        NoteEntity::class,
        CollectionEntity::class,
        CollectionItemEntity::class,
        HistoryEntity::class,
        FilterPresetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pokemonDao(): PokemonDao
    abstract fun cacheMetaDao(): CacheMetaDao
    abstract fun profileDao(): ProfileDao
    abstract fun userStateDao(): UserStateDao
    abstract fun noteDao(): NoteDao
    abstract fun collectionDao(): CollectionDao
    abstract fun historyDao(): HistoryDao
    abstract fun filterPresetDao(): FilterPresetDao
    abstract fun syncDao(): SyncDao

    companion object {
        const val NAME = "pokejournal.db"
        const val DEFAULT_PROFILE_ID = 1L

        /** При первом создании БД заводим профиль по умолчанию. */
        val seedCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "INSERT INTO profiles (id, name, avatar, createdAt) VALUES (?, ?, ?, ?)",
                    arrayOf<Any>(DEFAULT_PROFILE_ID, "Тренер", "🔥", System.currentTimeMillis()),
                )
            }
        }
    }
}
