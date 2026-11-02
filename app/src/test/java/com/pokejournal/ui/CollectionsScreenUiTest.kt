package com.pokejournal.ui

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.pokejournal.domain.model.CollectionSummary
import com.pokejournal.ui.collections.CollectionsEvent
import com.pokejournal.ui.collections.CollectionsScreen
import com.pokejournal.ui.collections.CollectionsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CollectionsScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun render(collection: CollectionSummary): MutableList<CollectionsEvent> {
        val events = mutableListOf<CollectionsEvent>()
        composeRule.setContent {
            CollectionsScreen(
                state = CollectionsUiState(isLoading = false, collections = listOf(collection)),
                onEvent = { events += it },
                onOpenCollection = {},
            )
        }
        return events
    }

    @Test
    fun `partially offline collection can download missing images`() {
        val events = render(CollectionSummary(7, "Trip", "", itemCount = 3, offlineCount = 2, createdAt = 0))

        composeRule.onNodeWithContentDescription("Докачать для офлайна").assertIsEnabled().performClick()

        assertEquals(listOf<CollectionsEvent>(CollectionsEvent.DownloadOffline(7)), events)
    }

    @Test
    fun `fully offline collection still allows refreshing its offline copy`() {
        val events = render(CollectionSummary(7, "Trip", "", itemCount = 3, offlineCount = 3, createdAt = 0))

        composeRule.onNodeWithContentDescription("Обновить офлайн-копию").assertIsEnabled().performClick()

        assertEquals(listOf<CollectionsEvent>(CollectionsEvent.DownloadOffline(7)), events)
    }

    @Test
    fun `collection without offline data offers download`() {
        render(CollectionSummary(7, "Trip", "", itemCount = 3, offlineCount = 0, createdAt = 0))

        composeRule.onNodeWithContentDescription("Скачать для офлайна").assertIsEnabled()
    }
}
