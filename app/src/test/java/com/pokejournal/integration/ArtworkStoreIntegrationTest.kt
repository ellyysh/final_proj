package com.pokejournal.integration

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pokejournal.data.images.FileArtworkStore
import com.pokejournal.data.local.AppDatabase
import com.pokejournal.domain.logic.HttpStatusException
import com.pokejournal.domain.logic.isRetryable
import com.pokejournal.testing.FakeArtworkServer
import com.pokejournal.testing.FakeTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ArtworkStoreIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val server = FakeArtworkServer()
    private lateinit var store: FileArtworkStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        store = FileArtworkStore(context, server.client, db, FakeTime())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun savedIds() = db.offlineArtworkDao().ids().toSet()

    @Test
    fun `successful download stores file and marks image as saved`() = runTest {
        assertNull(store.ensure(25))

        assertNotNull(store.localFile(25))
        assertEquals(setOf(25), savedIds())
    }

    @Test
    fun `failed download leaves no file and no saved mark`() = runTest {
        server.failingIds = setOf(25)
        server.notFoundIds = setOf(4)

        val transient = store.ensure(25)
        val permanent = store.ensure(4)

        assertTrue(transient is IOException && transient.isRetryable())
        assertTrue(permanent is HttpStatusException && !permanent.isRetryable())
        assertNull(store.localFile(25))
        assertNull(store.localFile(4))
        assertTrue(savedIds().isEmpty())
    }

    @Test
    fun `existing file is not downloaded again`() = runTest {
        store.ensure(25)
        store.ensure(25)

        assertEquals(1, server.requests)
        assertEquals(setOf(25), savedIds())
    }

    @Test
    fun `lost file is downloaded again even if it was marked as saved`() = runTest {
        store.ensure(25)
        store.localFile(25)!!.delete()

        assertNull(store.ensure(25))

        assertEquals(2, server.requests)
        assertNotNull(store.localFile(25))
        assertEquals(setOf(25), savedIds())
    }

    @Test
    fun `lost file whose download fails is no longer marked as saved`() = runTest {
        store.ensure(25)
        store.localFile(25)!!.delete()
        server.failingIds = setOf(25)

        assertNotNull(store.ensure(25))

        assertFalse(25 in savedIds())
    }

    @Test
    fun `cleanup removes files and marks of pokemon outside collections`() = runTest {
        store.ensure(1)
        store.ensure(4)
        Thread.sleep(5)

        store.retainOnly { listOf(1) }

        assertNotNull(store.localFile(1))
        assertNull(store.localFile(4))
        assertEquals(setOf(1), savedIds())
    }

    @Test
    fun `cleanup drops marks whose files were removed outside the app`() = runTest {
        store.ensure(1)
        store.ensure(4)
        store.localFile(4)!!.delete()

        store.retainOnly { listOf(1, 4) }

        assertEquals(setOf(1), savedIds())
    }
}
