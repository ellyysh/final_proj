package com.pokejournal.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokejournal.domain.logic.RelativeTime
import com.pokejournal.ui.components.ConfirmDialog
import com.pokejournal.ui.components.EmptyState
import com.pokejournal.ui.components.LoadingState
import com.pokejournal.ui.components.PokemonImage
import com.pokejournal.ui.components.dexNumber
import com.pokejournal.ui.components.displayName

@Composable
fun HistoryRoute(
    onOpenDetail: (Int) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(state, viewModel::onEvent, onOpenDetail)
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onEvent: (HistoryEvent) -> Unit,
    onOpenDetail: (Int) -> Unit,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Недавно просмотренные") },
                actions = {
                    if (state.entries.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Очистить историю")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> LoadingState()
                state.entries.isEmpty() -> EmptyState(
                    title = "История пуста",
                    message = "Здесь появятся карточки, которые вы открывали. Они доступны и без сети.",
                )
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(state.entries, key = { it.pokemonId }) { entry ->
                        ListItem(
                            modifier = Modifier.clickable { onOpenDetail(entry.pokemonId) },
                            leadingContent = { PokemonImage(entry.imageUrl, entry.name, Modifier.size(48.dp)) },
                            headlineContent = { Text(entry.name?.displayName() ?: entry.pokemonId.dexNumber()) },
                            supportingContent = {
                                Text("${entry.pokemonId.dexNumber()} · ${RelativeTime.format(entry.viewedAt, state.now)}")
                            },
                            trailingContent = {
                                IconButton(onClick = { onEvent(HistoryEvent.Remove(entry.pokemonId)) }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Убрать из истории")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Очистить историю?",
            message = "История текущего профиля будет удалена.",
            confirmLabel = "Очистить",
            onConfirm = { onEvent(HistoryEvent.ClearAll) },
            onDismiss = { confirmClear = false },
        )
    }
}
