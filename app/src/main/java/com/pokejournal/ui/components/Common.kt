package com.pokejournal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pokejournal.ui.theme.TypeColors
import java.util.Locale

object TestTags {
    const val LOADING = "loading"
    const val ERROR = "error"
    const val EMPTY = "empty"
    const val RETRY = "retry"
    const val LIST = "pokemon_list"
    const val SEARCH = "search"
    fun pokemon(id: Int) = "pokemon_$id"
}

@Composable
fun LoadingState(text: String = "Загружаем…", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().testTag(TestTags.LOADING),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    MessageState(
        icon = Icons.Outlined.ErrorOutline,
        title = "Не удалось загрузить",
        message = message,
        modifier = modifier.testTag(TestTags.ERROR),
        action = {
            Button(onClick = onRetry, modifier = Modifier.testTag(TestTags.RETRY)) { Text("Повторить") }
        },
    )
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    MessageState(
        icon = Icons.Outlined.Inbox,
        title = title,
        message = message,
        modifier = modifier.testTag(TestTags.EMPTY),
        action = if (actionLabel != null) {
            { OutlinedButton(onClick = onAction) { Text(actionLabel) } }
        } else {
            null
        },
    )
}

@Composable
private fun MessageState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** Баннер офлайн-режима / ошибки фонового обновления поверх закэшированных данных. */
@Composable
fun OfflineBanner(
    text: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("Обновить") }
            }
        }
    }
}

@Composable
fun TypeBadge(type: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(TypeColors.of(type))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = type.replaceFirstChar { it.titlecase(Locale.ROOT) },
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun PokemonImage(url: String, contentDescription: String?, modifier: Modifier = Modifier) {
    AsyncImage(model = url, contentDescription = contentDescription, modifier = modifier)
}

fun String.displayName(): String =
    split('-').joinToString(" ") { part -> part.replaceFirstChar { it.titlecase(Locale.ROOT) } }

fun Int.dexNumber(): String = "#" + toString().padStart(3, '0')

fun statLabel(stat: String): String = when (stat) {
    "hp" -> "HP"
    "attack" -> "Атака"
    "defense" -> "Защита"
    "special-attack" -> "Сп. атака"
    "special-defense" -> "Сп. защита"
    "speed" -> "Скорость"
    else -> stat.displayName()
}
