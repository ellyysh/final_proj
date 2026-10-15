package com.pokejournal.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokejournal.domain.model.CollectionSummary
import com.pokejournal.sync.WorkStatus
import com.pokejournal.ui.components.ConfirmDialog
import com.pokejournal.ui.components.EmptyState
import com.pokejournal.ui.components.LoadingState
import com.pokejournal.ui.components.OfflineBanner
import com.pokejournal.ui.components.PokemonImage
import com.pokejournal.ui.components.TextInputDialog
import com.pokejournal.ui.components.dexNumber
import com.pokejournal.ui.components.displayName

/* ------------------------------ Список коллекций ------------------------------ */

@Composable
fun CollectionsRoute(
    onOpenCollection: (Long) -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CollectionsScreen(state, viewModel::onEvent, onOpenCollection)
}

@Composable
fun CollectionsScreen(
    state: CollectionsUiState,
    onEvent: (CollectionsEvent) -> Unit,
    onOpenCollection: (Long) -> Unit,
) {
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Мои коллекции") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEvent(CollectionsEvent.OpenCreateDialog) },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Коллекция") },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> LoadingState()
                state.collections.isEmpty() -> EmptyState(
                    title = "Коллекций пока нет",
                    message = "Собирайте свои команды, вишлисты и подборки. Их можно скачать для офлайна.",
                    actionLabel = "Создать коллекцию",
                    onAction = { onEvent(CollectionsEvent.OpenCreateDialog) },
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.collections, key = { it.id }) { collection ->
                        CollectionCard(
                            collection = collection,
                            onClick = { onOpenCollection(collection.id) },
                            onDownload = { onEvent(CollectionsEvent.DownloadOffline(collection.id)) },
                            onDelete = { pendingDelete = collection.id },
                        )
                    }
                }
            }
        }
    }

    if (state.dialog.visible) {
        TextInputDialog(
            title = "Новая коллекция",
            label = "Название",
            secondLabel = "Описание (необязательно)",
            confirmLabel = "Создать",
            error = state.dialog.error,
            onConfirm = { name, description -> onEvent(CollectionsEvent.Create(name, description)) },
            onDismiss = { onEvent(CollectionsEvent.DismissDialog) },
        )
    }
    pendingDelete?.let { id ->
        ConfirmDialog(
            title = "Удалить коллекцию?",
            message = "Сами покемоны и их статусы останутся в журнале.",
            confirmLabel = "Удалить",
            onConfirm = { onEvent(CollectionsEvent.Delete(id)) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun CollectionCard(
    collection: CollectionSummary,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(collection.name, style = MaterialTheme.typography.titleMedium)
                if (collection.description.isNotBlank()) {
                    Text(
                        collection.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${collection.itemCount} покемонов · офлайн: ${collection.offlineCount}/${collection.itemCount}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(onClick = onDownload, enabled = collection.itemCount > 0 && !collection.fullyOffline) {
                Icon(
                    if (collection.fullyOffline) Icons.Outlined.CloudDone else Icons.Outlined.CloudDownload,
                    contentDescription = "Скачать для офлайна",
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Удалить") }
        }
    }
}

/* ------------------------------ Содержимое коллекции ------------------------------ */

@Composable
fun CollectionDetailRoute(
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    viewModel: CollectionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted, state.isLoading) {
        if (!state.isLoading && state.deleted) onBack()
    }
    CollectionDetailScreen(state, viewModel::onEvent, onBack, onOpenDetail)
}

@Composable
fun CollectionDetailScreen(
    state: CollectionDetailUiState,
    onEvent: (CollectionDetailEvent) -> Unit,
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.collection?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад") }
                },
                actions = {
                    IconButton(
                        onClick = { onEvent(CollectionDetailEvent.DownloadOffline) },
                        enabled = state.members.isNotEmpty(),
                    ) { Icon(Icons.Outlined.CloudDownload, "Скачать для офлайна") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> LoadingState()
                else -> {
                    DownloadStatus(state)
                    if (state.members.isEmpty()) {
                        EmptyState(
                            title = "Коллекция пуста",
                            message = "Откройте карточку покемона и отметьте эту коллекцию.",
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                            items(state.members, key = { it.pokemonId }) { member ->
                                ListItem(
                                    modifier = Modifier.clickable { onOpenDetail(member.pokemonId) },
                                    leadingContent = {
                                        PokemonImage(member.imageUrl, member.name, Modifier.size(48.dp))
                                    },
                                    headlineContent = { Text(member.name.displayName()) },
                                    supportingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(member.pokemonId.dexNumber())
                                            Spacer(Modifier.width(8.dp))
                                            Icon(
                                                if (member.availableOffline) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Text(
                                                if (member.availableOffline) " доступен офлайн" else " нужна сеть",
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        IconButton(onClick = { onEvent(CollectionDetailEvent.Remove(member.pokemonId)) }) {
                                            Icon(Icons.Outlined.RemoveCircleOutline, "Убрать из коллекции")
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadStatus(state: CollectionDetailUiState) {
    val collection = state.collection ?: return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (state.isOffline) {
            OfflineBanner("Нет сети — доступны только скачанные карточки")
            Spacer(Modifier.height(8.dp))
        }
        val text = when (val download = state.download) {
            WorkStatus.Queued -> "Загрузка запланирована — начнётся, когда появится сеть"
            is WorkStatus.Running -> "Скачиваем для офлайна: ${download.done}/${download.total}"
            is WorkStatus.Succeeded -> "Офлайн: ${collection.offlineCount}/${collection.itemCount}"
            WorkStatus.Failed -> "Часть карточек не скачалась. Попробуйте ещё раз"
            WorkStatus.Idle -> "Офлайн: ${collection.offlineCount}/${collection.itemCount}"
        }
        Text(text, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        val download = state.download
        if (download is WorkStatus.Running && download.total > 0) {
            LinearProgressIndicator(
                progress = { download.done.toFloat() / download.total },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (collection.itemCount > 0) {
            LinearProgressIndicator(
                progress = { collection.offlineCount.toFloat() / collection.itemCount },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
