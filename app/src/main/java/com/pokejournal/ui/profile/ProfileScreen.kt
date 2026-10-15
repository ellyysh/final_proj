package com.pokejournal.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokejournal.domain.logic.JournalStats
import com.pokejournal.domain.model.Profile
import com.pokejournal.ui.components.ConfirmDialog
import com.pokejournal.ui.components.LoadingState
import com.pokejournal.ui.components.dexNumber
import com.pokejournal.ui.components.displayName
import com.pokejournal.ui.theme.TypeColors

private val AVATARS = listOf("🔥", "💧", "🌿", "⚡", "🌙", "❄️", "🐉", "⭐")

@Composable
fun ProfileRoute(
    snackbarHostState: SnackbarHostState,
    onOpenDetail: (Int) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    ProfileScreen(state, viewModel::onEvent, onOpenDetail)
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onEvent: (ProfileEvent) -> Unit,
    onOpenDetail: (Int) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мой журнал") },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = "Новый профиль")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.padding(padding)) { LoadingState() }
        } else LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item { ProfileHeader(state.activeProfile, state.stats) }
            item { TypeProgressCard(state.stats) }
            item {
                Text("Профили на устройстве", style = MaterialTheme.typography.titleMedium)
                Text(
                    "У каждого профиля свои статусы, заметки, коллекции, история и настройки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.profiles, key = { "profile_${it.id}" }) { profile ->
                ListItem(
                    modifier = Modifier.clickable { onEvent(ProfileEvent.Switch(profile.id)) },
                    leadingContent = {
                        RadioButton(
                            selected = profile.id == state.activeProfile?.id,
                            onClick = { onEvent(ProfileEvent.Switch(profile.id)) },
                        )
                    },
                    headlineContent = { Text("${profile.avatar}  ${profile.name}") },
                    trailingContent = {
                        if (state.profiles.size > 1) {
                            IconButton(onClick = { pendingDelete = profile.id }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Удалить профиль")
                            }
                        }
                    },
                )
            }
            item {
                Text("Мои заметки", style = MaterialTheme.typography.titleMedium)
                if (state.notes.isEmpty()) {
                    Text(
                        "Заметки добавляются в карточке покемона.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.notes, key = { "note_${it.pokemonId}" }) { note ->
                Card(Modifier.fillMaxWidth().clickable { onOpenDetail(note.pokemonId) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${note.pokemonId.dexNumber()} ${note.pokemonName?.displayName().orEmpty()}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(note.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    if (creating) {
        CreateProfileDialog(
            onCreate = { name, avatar ->
                onEvent(ProfileEvent.Create(name, avatar))
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
    pendingDelete?.let { id ->
        ConfirmDialog(
            title = "Удалить профиль?",
            message = "Все статусы, заметки, коллекции и история этого профиля будут удалены.",
            confirmLabel = "Удалить",
            onConfirm = { onEvent(ProfileEvent.Delete(id)) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun ProfileHeader(profile: Profile?, stats: JournalStats) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                ) { Text(profile?.avatar ?: "?", fontSize = 28.sp) }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(profile?.name ?: "—", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Пойманы ${stats.caught} из ${stats.total} · ${stats.completionPercent}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { if (stats.total == 0) 0f else stats.caught.toFloat() / stats.total },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatCell("Хочу", stats.wanted)
                StatCell("Видел", stats.seen)
                StatCell("Закреплено", stats.pinned)
                StatCell("Скрыто", stats.hidden)
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TypeProgressCard(stats: JournalStats) {
    if (stats.byType.isEmpty()) return
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Прогресс по типам", style = MaterialTheme.typography.titleMedium)
            stats.byType.take(TOP_TYPES).forEach { progress ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(progress.type.displayName(), modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { if (progress.total == 0) 0f else progress.caught.toFloat() / progress.total },
                        color = TypeColors.of(progress.type),
                        modifier = Modifier.weight(1f).height(6.dp),
                    )
                    Text(
                        " ${progress.caught}/${progress.total}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(56.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateProfileDialog(onCreate: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var avatar by rememberSaveable { mutableStateOf(AVATARS.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый профиль") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя тренера") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AVATARS.forEach { option ->
                        FilterChip(selected = avatar == option, onClick = { avatar = option }, label = { Text(option) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, avatar) }) { Text("Создать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private const val TOP_TYPES = 8
