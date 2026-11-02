package com.pokejournal.integration

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pokejournal.data.local.AppDatabase
import com.pokejournal.domain.model.SpeciesState
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DatabaseMigrationTest {
    private lateinit var context: Context
    private var db: AppDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun `migration from version 1 keeps cached cards and user data`() = runTest {
        createVersion1 { raw ->
            raw.execSQL(detailV1(1, genus = "'Seed Pokémon'", flavor = "'Text'"))
            raw.execSQL(detailV1(4, genus = "NULL", flavor = "NULL"))
            raw.execSQL("INSERT INTO collections (id, profileId, name, description, createdAt) VALUES (1, 1, 'Trip', '', 1)")
            raw.execSQL("INSERT INTO collection_items (collectionId, pokemonId, addedAt) VALUES (1, 1, 1)")
        }

        val migrated = openWithRoom()

        val loaded = migrated.pokemonDao().getDetail(1)!!
        assertEquals(SpeciesState.LOADED.name, loaded.speciesState)
        assertEquals("Seed Pokémon", loaded.genus)
        assertEquals(SpeciesState.PENDING.name, migrated.pokemonDao().getDetail(4)!!.speciesState)
        assertEquals(listOf(1), migrated.collectionDao().memberIds(1))
        assertTrue(migrated.offlineArtworkDao().ids().isEmpty())
    }

    @Test
    fun `migration from version 2 maps species flag to state`() = runTest {
        createVersion1 { raw ->
            raw.execSQL("ALTER TABLE pokemon_details ADD COLUMN speciesLoaded INTEGER NOT NULL DEFAULT 1")
            raw.execSQL(detailV2(1, genus = "'Seed Pokémon'", flavor = "'Text'", speciesLoaded = 1))
            raw.execSQL(detailV2(4, genus = "'Lizard Pokémon'", flavor = "'Text'", speciesLoaded = 0))
            raw.execSQL(detailV2(7, genus = "NULL", flavor = "NULL", speciesLoaded = 1))
            raw.version = 2
        }

        val migrated = openWithRoom()

        assertEquals(SpeciesState.LOADED.name, migrated.pokemonDao().getDetail(1)!!.speciesState)
        assertEquals(SpeciesState.PENDING.name, migrated.pokemonDao().getDetail(4)!!.speciesState)
        assertEquals("Lizard Pokémon", migrated.pokemonDao().getDetail(4)!!.genus)
        assertEquals(SpeciesState.PENDING.name, migrated.pokemonDao().getDetail(7)!!.speciesState)
    }

    private fun createVersion1(fill: (SQLiteDatabase) -> Unit) {
        val file = context.getDatabasePath(TEST_DB).apply { parentFile?.mkdirs() }
        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            val schema = Json.parseToJsonElement(schemaFile().readText()).jsonObject.getValue("database").jsonObject
            schema.getValue("entities").jsonArray.forEach { element ->
                val entity = element.jsonObject
                val table = entity.getValue("tableName").jsonPrimitive.content
                raw.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach { index ->
                    raw.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach { raw.execSQL(it.jsonPrimitive.content) }
            raw.execSQL("INSERT INTO profiles (id, name, avatar, createdAt) VALUES (1, 'Ash', 'A', 1)")
            raw.version = 1
            fill(raw)
        } finally {
            raw.close()
        }
    }

    private fun openWithRoom(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
            .also { db = it }

    private fun schemaFile(): File =
        listOf(SCHEMA_PATH, "app/$SCHEMA_PATH").map { File(it) }.first { it.isFile }

    private fun detailV1(id: Int, genus: String, flavor: String) =
        "INSERT INTO pokemon_details (pokemonId, height, weight, baseExperience, genus, flavorText, abilities, fetchedAt) " +
            "VALUES ($id, 7, 69, 64, $genus, $flavor, 'overgrow', 100)"

    private fun detailV2(id: Int, genus: String, flavor: String, speciesLoaded: Int) =
        "INSERT INTO pokemon_details " +
            "(pokemonId, height, weight, baseExperience, genus, flavorText, abilities, fetchedAt, speciesLoaded) " +
            "VALUES ($id, 7, 69, 64, $genus, $flavor, 'overgrow', 100, $speciesLoaded)"

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val SCHEMA_PATH = "schemas/com.pokejournal.data.local.AppDatabase/1.json"
    }
}
