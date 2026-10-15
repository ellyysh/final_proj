package com.pokejournal.integration

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pokejournal.data.local.AppDatabase
import com.pokejournal.data.local.entity.PokemonDetailEntity
import com.pokejournal.data.local.entity.PokemonEntity
import com.pokejournal.data.local.entity.ProfileEntity
import com.pokejournal.data.repository.CreateResult
import com.pokejournal.data.repository.DeleteProfileResult
import com.pokejournal.data.repository.RoomCollectionRepository
import com.pokejournal.data.repository.RoomHistoryRepository
import com.pokejournal.data.repository.RoomImportantPokemonSource
import com.pokejournal.data.repository.RoomJournalRepository
import com.pokejournal.data.repository.RoomPresetRepository
import com.pokejournal.data.repository.RoomProfileRepository
import com.pokejournal.domain.logic.NameError
import com.pokejournal.domain.model.CatchStatus
import com.pokejournal.domain.model.ListFilter
import com.pokejournal.domain.model.PokemonImages
import com.pokejournal.domain.model.StatusFilter
import com.pokejournal.testing.FakeSettingsRepository
import com.pokejournal.testing.FakeTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Пользовательская локальная модель на настоящей Room: коллекции, профили, журнал, история. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UserDataIntegrationTest {

    private lateinit var db: AppDatabase
    private val time = FakeTime()
    private val settings = FakeSettingsRepository(activeId = 1L)

    private lateinit var collections: RoomCollectionRepository
    private lateinit var journal: RoomJournalRepository
    private lateinit var history: RoomHistoryRepository
    private lateinit var profiles: RoomProfileRepository
    private lateinit var presets: RoomPresetRepository

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        collections = RoomCollectionRepository(db.collectionDao(), time)
        journal = RoomJournalRepository(db.userStateDao(), db.noteDao(), time)
        history = RoomHistoryRepository(db.historyDao(), time)
        profiles = RoomProfileRepository(db.profileDao(), settings, time)
        presets = RoomPresetRepository(db.filterPresetDao(), time)

        db.profileDao().insert(ProfileEntity(id = 1, name = "Ash", avatar = "🔥", createdAt = 1))
        db.profileDao().insert(ProfileEntity(id = 2, name = "Misty", avatar = "💧", createdAt = 2))
        db.pokemonDao().upsertPokemon(
            listOf(1 to "bulbasaur", 4 to "charmander", 7 to "squirtle").map { (id, name) ->
                PokemonEntity(id, name, PokemonImages.artworkUrl(id))
            },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `adding the same pokemon twice does not create duplicate and offline count is tracked`() = runTest {
        val id = (collections.createCollection(1L, "Starters", "first partners") as CreateResult.Created).id
        collections.setMembership(id, 1, true)
        time.advance(10)
        collections.setMembership(id, 1, true)
        collections.setMembership(id, 4, true)
        db.pokemonDao().upsertDetail(PokemonDetailEntity(4, 6, 85, 62, null, null, "", fetchedAt = 1))

        val summary = collections.observeCollection(id).first()!!
        assertEquals(2, summary.itemCount)
        assertEquals(1, summary.offlineCount)
        assertEquals(listOf(4, 1), collections.observeMembers(id).first().map { it.pokemonId })
        assertEquals(setOf(id), collections.observeCollectionIdsFor(1L, 1).first())
    }

    @Test
    fun `collection names are unique per profile only`() = runTest {
        collections.createCollection(1L, "Favourites", "")
        assertEquals(
            CreateResult.Rejected(NameError.DUPLICATE),
            collections.createCollection(1L, "favourites", ""),
        )
        assertTrue(collections.createCollection(2L, "Favourites", "") is CreateResult.Created)
        assertEquals(1, collections.observeCollections(1L).first().size)
    }

    @Test
    fun `journal state is separated per profile and default state is not stored`() = runTest {
        journal.setStatus(1L, 7, CatchStatus.CAUGHT)
        journal.setPinned(2L, 7, true)

        assertEquals(CatchStatus.CAUGHT, journal.observeState(1L, 7).first().status)
        assertEquals(false, journal.observeState(1L, 7).first().pinned)
        assertEquals(CatchStatus.NONE, journal.observeState(2L, 7).first().status)

        journal.setStatus(1L, 7, CatchStatus.NONE)
        assertTrue(journal.observeStates(1L).first().isEmpty())

        // Скрытие снимает закрепление.
        journal.setHidden(2L, 7, true)
        val hidden = journal.observeState(2L, 7).first()
        assertTrue(hidden.hidden && !hidden.pinned)
    }

    @Test
    fun `history keeps unique entries, newest first, trimmed to limit`() = runTest {
        listOf(1, 4, 7).forEach {
            history.record(1L, it, limit = 2)
            time.advance(1_000)
        }
        assertEquals(listOf(7, 4), history.observeHistory(1L).first().map { it.pokemonId })

        history.record(1L, 4, limit = 2)
        assertEquals(listOf(4, 7), history.observeHistory(1L).first().map { it.pokemonId })
        assertTrue(history.observeHistory(2L).first().isEmpty())
    }

    @Test
    fun `deleting a profile cascades its data and switches active profile`() = runTest {
        settings.setActiveProfile(2L)
        val collectionId = (collections.createCollection(2L, "Water", "") as CreateResult.Created).id
        collections.setMembership(collectionId, 7, true)
        journal.saveNote(2L, 7, "Starmie next")
        history.record(2L, 7, 10)
        presets.savePreset(2L, "Water only", "", ListFilter(type = "water", status = StatusFilter.ALL))
        settings.updateUserSettings(2L) { it.copy(historyLimit = 100) }

        assertEquals(DeleteProfileResult.Deleted, profiles.deleteProfile(2L))

        assertEquals(1L, settings.active.value)
        assertTrue(collections.observeCollections(2L).first().isEmpty())
        assertTrue(journal.observeNotes(2L).first().isEmpty())
        assertTrue(history.observeHistory(2L).first().isEmpty())
        assertTrue(presets.observePresets(2L).first().isEmpty())
        assertTrue(settings.users.value[2L] == null)
        // Удалить последний профиль нельзя.
        assertEquals(DeleteProfileResult.LastProfile, profiles.deleteProfile(1L))
    }

    @Test
    fun `important ids for offline prefetch come from all personal sources`() = runTest {
        val collectionId = (collections.createCollection(1L, "Team", "") as CreateResult.Created).id
        collections.setMembership(collectionId, 1, true)
        journal.setStatus(2L, 4, CatchStatus.WANTED)
        history.record(1L, 7, 10)
        journal.setHidden(1L, 25, true) // только скрытие — не повод качать офлайн

        val ids = RoomImportantPokemonSource(db.syncDao()).importantIds().sorted()

        assertEquals(listOf(1, 4, 7), ids)
    }
}
