package com.pokejournal.ui.list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokejournal.domain.logic.RelativeTime
import com.pokejournal.domain.model.PokemonListItem
import com.pokejournal.domain.model.PokemonTypes
import com.pokejournal.domain.model.StatusFilter
import com.pokejournal.ui.components.EmptyState
import com.pokejournal.ui.components.ErrorState
import com.pokejournal.ui.components.LoadingState
import com.pokejournal.ui.components.OfflineBanner
import com.pokejournal.ui.components.PokemonImage
import com.pokejournal.ui.components.TestTags
import com.pokejournal.ui.components.TextInputDialog
import com.pokejournal.ui.components.TypeBadge
import com.pokejournal.ui.components.dexNumber
import com.pokejournal.ui.components.displayName
import com.pokejournal.ui.components.icon
import com.pokejournal.ui.components.label
import com.pokejournal.ui.theme.TypeColors

@Composable
fun PokemonListRoute(
    snackbarHostState: SnackbarHostState,
    onOpenDetail: (Int) -> Unit,
    viewModel: PokemonListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    PokemonListScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenDetail = onOpenDetail,
        now = System.currentTimeMillis(),
    )
}

/** Stateless-экран: всё состояние приходит из ViewModel, наружу — только события. */
@Composable
fun PokemonListScreen(
    state: PokemonListUiState,
    onEvent: (PokemonListEvent) -> Unit,
    onOpenDetail: (Int) -> Unit,
    now: Long,
) {
    var showSavePreset by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pokédex")
                        if (state.totalInDex > 0) {
                            Text(
                                "${state.totalInDex} покемонов · обновлено ${RelativeTime.format(state.lastSyncAt, now)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSavePreset = true }) {
                        Icon(Icons.Outlined.BookmarkAdd, contentDescription = "Сохранить фильтр")
                    }
                    IconButton(onClick = { onEvent(PokemonListEvent.Refresh) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(query = state.query, onQueryChange = { onEvent(PokemonListEvent.QueryChanged(it)) })
            FiltersRow(state = state, onEvent = onEvent)

            if (state.isOffline || state.refreshError != null) {
                val text = when {
                    state.isOffline -> "Нет сети — показаны сохранённые данные"
                    else -> "Не удалось обновить: ${state.refreshError}. Показаны сохранённые данные"
                }
                OfflineBanner(
                    text = text,
                    onRetry = { onEvent(PokemonListEvent.Retry) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { onEvent(PokemonListEvent.Refresh) },
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val content = state.content) {
                    ListContent.Loading -> LoadingState("Загружаем Pokédex…")
                    is ListContent.Error -> ErrorState(content.message, onRetry = { onEvent(PokemonListEvent.Retry) })
                    is ListContent.Empty -> when (content.reason) {
                        EmptyReason.NO_MATCHES -> EmptyState(
                            title = "Ничего не найдено",
                            message = "Попробуйте изменить запрос или фильтры",
                            actionLabel = "Сбросить фильтры",
                            onAction = { onEvent(PokemonListEvent.ClearFilters) },
                        )
                        EmptyReason.NO_DATA -> EmptyState(
                            title = "Пока пусто",
                            message = "Данных ещё нет. Подключитесь к сети и обновите",
                            actionLabel = "Обновить",
                            onAction = { onEvent(PokemonListEvent.Retry) },
                        )
                    }
                    is ListContent.Success -> PokemonGrid(
                        items = content.items,
                        onOpenDetail = onOpenDetail,
                        onTogglePinned = { id, pinned -> onEvent(PokemonListEvent.TogglePinned(id, pinned)) },
                    )
                }
            }
        }
    }

    if (showSavePreset) {
        TextInputDialog(
            title = "Сохранить фильтр",
            label = "Название пресета",
            confirmLabel = "Сохранить",
            onConfirm = { name, _ ->
                onEvent(PokemonListEvent.SavePreset(name))
                showSavePreset = false
            },
            onDismiss = { showSavePreset = false },
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Имя или номер, например pika или 25") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Clear, contentDescription = "Очистить")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag(TestTags.SEARCH),
    )
}

@Composable
private fun FiltersRow(state: PokemonListUiState, onEvent: (PokemonListEvent) -> Unit) {
    if (state.presets.isNotEmpty()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.presets, key = { it.id }) { preset ->
                InputChip(
                    selected = state.query == preset.query && state.filter == preset.filter,
                    onClick = { onEvent(PokemonListEvent.ApplyPreset(preset)) },
                    label = { Text(preset.name) },
                    trailingIcon = {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Удалить пресет",
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onEvent(PokemonListEvent.DeletePreset(preset.id)) },
                        )
                    },
                )
            }
        }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(StatusFilter.entries) { status ->
            FilterChip(
                selected = state.filter.status == status,
                onClick = { onEvent(PokemonListEvent.StatusSelected(status)) },
                label = { Text(status.label()) },
            )
        }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(PokemonTypes.ALL) { type ->
            val selected = state.filter.type == type
            FilterChip(
                selected = selected,
                onClick = { onEvent(PokemonListEvent.TypeSelected(if (selected) null else type)) },
                label = { Text(type.displayName()) },
                leadingIcon = {
                    val color = TypeColors.of(type)
                    Canvas(Modifier.size(10.dp)) { drawCircle(color) }
                },
            )
        }
    }
}

@Composable
private fun PokemonGrid(
    items: List<PokemonListItem>,
    onOpenDetail: (Int) -> Unit,
    onTogglePinned: (Int, Boolean) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag(TestTags.LIST),
    ) {
        items(items, key = { it.summary.id }) { item ->
            PokemonCard(item = item, onClick = { onOpenDetail(item.summary.id) }, onTogglePinned = onTogglePinned)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PokemonCard(
    item: PokemonListItem,
    onClick: () -> Unit,
    onTogglePinned: (Int, Boolean) -> Unit,
) {
    val summary = item.summary
    val accent = summary.types.firstOrNull()?.let(TypeColors::of) ?: MaterialTheme.colorScheme.surfaceVariant
    Card(
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.14f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.pokemon(summary.id))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onTogglePinned(summary.id, !item.state.pinned) },
            ),
    ) {
        Box {
            Column(Modifier.padding(12.dp)) {
                Text(
                    summary.id.dexNumber(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PokemonImage(
                    url = summary.imageUrl,
                    contentDescription = summary.name,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp),
                )
                Text(
                    summary.name.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    summary.types.forEach { TypeBadge(it) }
                }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                if (item.state.pinned) {
                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = "Закреплён",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                item.state.status.icon()?.let { icon ->
                    Icon(
                        icon,
                        contentDescription = item.state.status.label(),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
