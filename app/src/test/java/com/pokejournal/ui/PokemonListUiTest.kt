package com.pokejournal.ui

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.testing.TestNavHostController
import com.pokejournal.data.repository.RefreshResult
import com.pokejournal.domain.model.PokemonListItem
import com.pokejournal.domain.model.PokemonUserState
import com.pokejournal.testing.FakeJournalRepository
import com.pokejournal.testing.FakeNetworkMonitor
import com.pokejournal.testing.FakePokemonRepository
import com.pokejournal.testing.FakePresetRepository
import com.pokejournal.testing.FakeSettingsRepository
import com.pokejournal.testing.MainDispatcherRule
import com.pokejournal.testing.TestData
import com.pokejournal.ui.components.TestTags
import com.pokejournal.ui.list.ListContent
import com.pokejournal.ui.list.PokemonListEvent
import com.pokejournal.ui.list.PokemonListScreen
import com.pokejournal.ui.list.PokemonListUiState
import com.pokejournal.ui.list.PokemonListViewModel
import com.pokejournal.ui.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** UI-интеграция: Compose-экраны, навигация и связка экран + ViewModel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PokemonListUiTest {

    @get:Rule(order = 0)
    val mainRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val successState = PokemonListUiState(
        content = ListContent.Success(
            listOf(TestData.pikachu, TestData.bulbasaur).map { PokemonListItem(it, PokemonUserState(it.id)) },
        ),
        totalInDex = 2,
    )

    @Test
    fun clickOnPokemon_opensDetailRouteForThatId() {
        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            NavHost(navController = navController, startDestination = Routes.LIST) {
                composable(Routes.LIST) {
                    PokemonListScreen(
                        state = successState,
                        onEvent = {},
                        onOpenDetail = { navController.navigate(Routes.detail(it)) },
                        now = 0L,
                    )
                }
                composable(
                    route = Routes.DETAIL,
                    arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.IntType }),
                ) { entry ->
                    Text("Detail ${entry.arguments?.getInt(Routes.ARG_ID)}")
                }
            }
        }

        composeRule.onNodeWithTag(TestTags.pokemon(25)).performClick()

        assertEquals(Routes.DETAIL, navController.currentBackStackEntry?.destination?.route)
        assertEquals(25, navController.currentBackStackEntry?.arguments?.getInt(Routes.ARG_ID))
        composeRule.onNodeWithText("Detail 25").assertIsDisplayed()
    }

    @Test
    fun errorState_retryButtonSendsRetryEvent() {
        val events = mutableListOf<PokemonListEvent>()
        composeRule.setContent {
            PokemonListScreen(
                state = PokemonListUiState(content = ListContent.Error("Нет подключения к сети")),
                onEvent = { events += it },
                onOpenDetail = {},
                now = 0L,
            )
        }

        composeRule.onNodeWithText("Нет подключения к сети").assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.RETRY).performClick()

        assertEquals(listOf<PokemonListEvent>(PokemonListEvent.Retry), events)
    }

    @Test
    fun screenWithViewModel_errorThenRetry_showsLoadedList() {
        val pokemon = FakePokemonRepository()
        var attempt = 0
        pokemon.onRefreshCatalog = {
            if (attempt++ == 0) {
                RefreshResult.Failed(IOException())
            } else {
                pokemon.catalog.value = TestData.catalog
                RefreshResult.Updated
            }
        }
        val vm = PokemonListViewModel(
            pokemon,
            FakeJournalRepository(),
            FakePresetRepository(),
            FakeSettingsRepository(),
            FakeNetworkMonitor(),
        )
        val scheduler = mainRule.dispatcher.scheduler

        composeRule.setContent {
            val state by vm.uiState.collectAsState()
            PokemonListScreen(state = state, onEvent = vm::onEvent, onOpenDetail = {}, now = 0L)
        }
        composeRule.waitForIdle()
        scheduler.advanceUntilIdle()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.ERROR).assertIsDisplayed()

        composeRule.onNodeWithTag(TestTags.RETRY).performClick()
        scheduler.advanceUntilIdle()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TestTags.LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.pokemon(1)).assertIsDisplayed()
    }
}
