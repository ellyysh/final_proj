package com.pokejournal.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokejournal.domain.logic.RelativeTime
import com.pokejournal.domain.model.CatchStatus
import com.pokejournal.domain.model.PokemonDetail
import com.pokejournal.ui.components.ErrorState
import com.pokejournal.ui.components.LoadingState
import com.pokejournal.ui.components.OfflineBanner
import com.pokejournal.ui.components.PokemonImage
import com.pokejournal.ui.components.TextInputDialog
import com.pokejournal.ui.components.TypeBadge
import com.pokejournal.ui.components.dexNumber
import com.pokejournal.ui.components.displayName
import com.pokejournal.ui.components.label
import com.pokejournal.ui.components.statLabel
import com.pokejournal.ui.theme.TypeColors

@Composable
fun PokemonDetailRoute(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    viewModel: PokemonDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    PokemonDetailScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, now = System.currentTimeMillis())
}

@Composable
fun PokemonDetailScreen(
    state: PokemonDetailUiState,
    onEvent: (PokemonDetailEvent) -> Unit,
    onBack: () -> Unit,
    now: Long,
) {
    var editingNote by rememberSaveable { mutableStateOf(false) }
    var creatingCollection by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.title.isEmpty()) state.pokemonId.dexNumber()
                        else "${state.pokemonId.dexNumber()} ${state.title.displayName()}",
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(PokemonDetailEvent.TogglePinned) }) {
                        Icon(
                            if (state.userState.pinned) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (state.userState.pinned) "Открепить" else "Закрепить",
                        )
                    }
                    IconButton(onClick = { onEvent(PokemonDetailEvent.ToggleHidden) }) {
                        Icon(
                            if (state.userState.hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (state.userState.hidden) "Показать в каталоге" else "Скрыть из каталога",
                        )
                    }
                    IconButton(onClick = { onEvent(PokemonDetailEvent.Refresh) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val content = state.content) {
                DetailContent.Loading -> LoadingState("Загружаем карточку…")
                is DetailContent.Error -> ErrorState(content.message, onRetry = { onEvent(PokemonDetailEvent.Retry) })
                is DetailContent.Success -> DetailBody(
                    pokemon = content.pokemon,
                    state = state,
                    now = now,
                    onEvent = onEvent,
                    onEditNote = { editingNote = true },
                    onCreateCollection = { creatingCollection = true },
                )
            }
        }
    }

    if (editingNote) {
        TextInputDialog(
            title = "Заметка",
            label = "Где встретил, планы, мысли…",
            confirmLabel = "Сохранить",
            initial = state.note,
            singleLine = false,
            onConfirm = { text, _ ->
                onEvent(PokemonDetailEvent.SaveNote(text))
                editingNote = false
            },
            onDismiss = { editingNote = false },
        )
    }
    if (creatingCollection) {
        TextInputDialog(
            title = "Новая коллекция",
            label = "Название",
            confirmLabel = "Создать и добавить",
            onConfirm = { name, _ ->
                onEvent(PokemonDetailEvent.CreateCollectionWithPokemon(name))
                creatingCollection = false
            },
            onDismiss = { creatingCollection = false },
        )
    }
}

@Composable
private fun DetailBody(
    pokemon: PokemonDetail,
    state: PokemonDetailUiState,
    now: Long,
    onEvent: (PokemonDetailEvent) -> Unit,
    onEditNote: () -> Unit,
    onCreateCollection: () -> Unit,
) {
    val accent = pokemon.types.firstOrNull()?.let(TypeColors::of) ?: MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0.05f)))),
        ) {
            PokemonImage(pokemon.imageUrl, pokemon.name, Modifier.size(200.dp))
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.isOffline || state.refreshError != null) {
                OfflineBanner(
                    text = "Офлайн-копия от ${RelativeTime.format(pokemon.fetchedAt, now)}" +
                        (state.refreshError?.let { " · $it" } ?: ""),
                    onRetry = { onEvent(PokemonDetailEvent.Retry) },
                )
            }

            Column {
                Text(pokemon.name.displayName(), style = MaterialTheme.typography.headlineMedium)
                pokemon.genus?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { pokemon.types.forEach { TypeBadge(it) } }
            }

            SectionCard("Мой статус") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val statuses = CatchStatus.entries
                    statuses.forEachIndexed { index, status ->
                        SegmentedButton(
                            selected = state.userState.status == status,
                            onClick = { onEvent(PokemonDetailEvent.SetStatus(status)) },
                            shape = SegmentedButtonDefaults.itemShape(index, statuses.size),
                        ) { Text(status.label(), maxLines = 1) }
                    }
                }
            }

            SectionCard("Основное") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    InfoCell("Рост", pokemon.height?.let { "%.1f м".format(it / 10.0) } ?: "—")
                    InfoCell("Вес", pokemon.weight?.let { "%.1f кг".format(it / 10.0) } ?: "—")
                    InfoCell("Опыт", pokemon.baseExperience?.toString() ?: "—")
                }
                pokemon.flavorText?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                }
                if (pokemon.abilities.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pokemon.abilities.forEach { AssistChip(onClick = {}, label = { Text(it.displayName()) }) }
                    }
                }
            }

            SectionCard("Характеристики") {
                pokemon.stats.forEach { stat ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(statLabel(stat.name), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(96.dp))
                        Text(
                            stat.value.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(36.dp),
                        )
                        LinearProgressIndicator(
                            progress = { (stat.value / MAX_STAT).coerceIn(0f, 1f) },
                            color = accent,
                            modifier = Modifier.weight(1f).height(8.dp),
                        )
                    }
                }
            }

            SectionCard(
                title = "Моя заметка",
                action = { TextButton(onClick = onEditNote) { Icon(Icons.Outlined.Edit, null); Text(" Изменить") } },
            ) {
                Text(
                    state.note.ifBlank { "Заметки пока нет. Добавьте, где встретили покемона или зачем он вам." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.note.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }

            SectionCard(
                title = "Коллекции",
                action = { TextButton(onClick = onCreateCollection) { Icon(Icons.Outlined.Add, null); Text(" Новая") } },
            ) {
                if (state.collections.isEmpty()) {
                    Text(
                        "Создайте коллекцию, например «Команда мечты», и добавьте покемона в неё.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.collections.forEach { choice ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = choice.selected,
                            onCheckedChange = { onEvent(PokemonDetailEvent.ToggleCollection(choice.id, it)) },
                        )
                        Text(choice.name)
                    }
                }
            }

            Text(
                "Данные обновлены ${RelativeTime.format(pokemon.fetchedAt, now)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                action?.invoke()
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val MAX_STAT = 200f
