package com.pokejournal.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pokejournal.data.local.dao.CacheMetaDao
import com.pokejournal.data.local.dao.CollectionDao
import com.pokejournal.data.local.dao.FilterPresetDao
import com.pokejournal.data.local.dao.HistoryDao
import com.pokejournal.data.local.dao.NoteDao
import com.pokejournal.data.local.dao.OfflineArtworkDao
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
import com.pokejournal.data.local.entity.OfflineArtworkEntity
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
        OfflineArtworkEntity::class,
    ],
    version = 3,
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
    abstract fun offlineArtworkDao(): OfflineArtworkDao

    companion object {
        const val NAME = "pokejournal.db"
        const val DEFAULT_PROFILE_ID = 1L

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pokemon_details ADD COLUMN speciesLoaded INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pokemon_details_new` (
                        `pokemonId` INTEGER NOT NULL,
                        `height` INTEGER NOT NULL,
                        `weight` INTEGER NOT NULL,
                        `baseExperience` INTEGER,
                        `genus` TEXT,
                        `flavorText` TEXT,
                        `abilities` TEXT NOT NULL,
                        `fetchedAt` INTEGER NOT NULL,
                        `speciesState` TEXT NOT NULL,
                        PRIMARY KEY(`pokemonId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `pokemon_details_new`
                        (pokemonId, height, weight, baseExperience, genus, flavorText, abilities, fetchedAt, speciesState)
                    SELECT pokemonId, height, weight, baseExperience, genus, flavorText, abilities, fetchedAt,
                        CASE
                            WHEN speciesLoaded = 0 THEN 'PENDING'
                            WHEN genus IS NULL AND flavorText IS NULL THEN 'PENDING'
                            ELSE 'LOADED'
                        END
                    FROM `pokemon_details`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `pokemon_details`")
                db.execSQL("ALTER TABLE `pokemon_details_new` RENAME TO `pokemon_details`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `offline_artwork` " +
                        "(`pokemonId` INTEGER NOT NULL, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`pokemonId`))",
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

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
