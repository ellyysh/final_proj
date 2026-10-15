package com.pokejournal.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CatchingPokemon
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val LIST = "list"
    const val ARG_ID = "id"
    const val DETAIL = "detail/{$ARG_ID}"
    fun detail(id: Int) = "detail/$id"

    const val COLLECTIONS = "collections"
    const val ARG_COLLECTION_ID = "collectionId"
    const val COLLECTION = "collection/{$ARG_COLLECTION_ID}"
    fun collection(id: Long) = "collection/$id"

    const val HISTORY = "history"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
}

enum class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    POKEDEX(Routes.LIST, "Pokédex", Icons.Outlined.CatchingPokemon),
    COLLECTIONS(Routes.COLLECTIONS, "Коллекции", Icons.Outlined.CollectionsBookmark),
    HISTORY(Routes.HISTORY, "История", Icons.Outlined.History),
    PROFILE(Routes.PROFILE, "Профиль", Icons.Outlined.Person),
    SETTINGS(Routes.SETTINGS, "Настройки", Icons.Outlined.Settings),
}
