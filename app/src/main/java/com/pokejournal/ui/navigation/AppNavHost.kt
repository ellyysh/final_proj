package com.pokejournal.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pokejournal.ui.collections.CollectionDetailRoute
import com.pokejournal.ui.collections.CollectionsRoute
import com.pokejournal.ui.detail.PokemonDetailRoute
import com.pokejournal.ui.history.HistoryRoute
import com.pokejournal.ui.list.PokemonListRoute
import com.pokejournal.ui.profile.ProfileRoute
import com.pokejournal.ui.settings.SettingsRoute

@Composable
fun PokeJournalAppContent(
    navController: NavHostController = rememberNavController(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIST,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.LIST) {
                PokemonListRoute(
                    snackbarHostState = snackbarHostState,
                    onOpenDetail = { navController.navigate(Routes.detail(it)) },
                )
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.IntType }),
            ) {
                PokemonDetailRoute(
                    snackbarHostState = snackbarHostState,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.COLLECTIONS) {
                CollectionsRoute(onOpenCollection = { navController.navigate(Routes.collection(it)) })
            }
            composable(
                route = Routes.COLLECTION,
                arguments = listOf(navArgument(Routes.ARG_COLLECTION_ID) { type = NavType.LongType }),
            ) {
                CollectionDetailRoute(
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { navController.navigate(Routes.detail(it)) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryRoute(onOpenDetail = { navController.navigate(Routes.detail(it)) })
            }
            composable(Routes.PROFILE) {
                ProfileRoute(
                    snackbarHostState = snackbarHostState,
                    onOpenDetail = { navController.navigate(Routes.detail(it)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsRoute()
            }
        }
    }
}
