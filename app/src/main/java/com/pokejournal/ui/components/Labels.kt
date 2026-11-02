package com.pokejournal.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CatchingPokemon
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.pokejournal.domain.model.CatchStatus
import com.pokejournal.domain.model.SortOrder
import com.pokejournal.domain.model.StatusFilter
import com.pokejournal.domain.model.ThemeMode

fun CatchStatus.label(): String = when (this) {
    CatchStatus.NONE -> "Нет"
    CatchStatus.SEEN -> "Видел"
    CatchStatus.WANTED -> "Хочу"
    CatchStatus.CAUGHT -> "Поймал"
}

fun CatchStatus.icon(): ImageVector? = when (this) {
    CatchStatus.NONE -> null
    CatchStatus.SEEN -> Icons.Outlined.RemoveRedEye
    CatchStatus.WANTED -> Icons.Outlined.Star
    CatchStatus.CAUGHT -> Icons.Outlined.CatchingPokemon
}

fun StatusFilter.label(): String = when (this) {
    StatusFilter.ALL -> "Все"
    StatusFilter.CAUGHT -> "Пойманы"
    StatusFilter.WANTED -> "Хочу"
    StatusFilter.SEEN -> "Видел"
    StatusFilter.UNTRACKED -> "Без статуса"
    StatusFilter.PINNED -> "Закреплённые"
    StatusFilter.HIDDEN -> "Скрытые"
}

fun SortOrder.label(): String = when (this) {
    SortOrder.NUMBER -> "По номеру"
    SortOrder.NAME -> "По имени"
    SortOrder.STATUS -> "По статусу"
}

fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "Как в системе"
    ThemeMode.LIGHT -> "Светлая"
    ThemeMode.DARK -> "Тёмная"
}

val PinIcon: ImageVector = Icons.Outlined.Bookmark

fun ttlLabel(hours: Int): String = when {
    hours < 24 -> "$hours ч"
    hours % 168 == 0 -> "${hours / 168} нед"
    else -> "${hours / 24} дн"
}

fun pokedexSizeLabel(size: Int): String = when (size) {
    151 -> "Kanto · 151"
    251 -> "Johto · 251"
    386 -> "Hoenn · 386"
    493 -> "Sinnoh · 493"
    649 -> "Unova · 649"
    else -> size.toString()
}
