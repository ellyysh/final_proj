package com.pokejournal.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokejournal.domain.logic.RelativeTime
import com.pokejournal.domain.model.AppSettings
import com.pokejournal.domain.model.SortOrder
import com.pokejournal.domain.model.ThemeMode
import com.pokejournal.domain.model.UserSettings
import com.pokejournal.sync.WorkStatus
import com.pokejournal.ui.components.label
import com.pokejournal.ui.components.pokedexSizeLabel
import com.pokejournal.ui.components.ttlLabel

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(state, viewModel::onEvent, now = System.currentTimeMillis())
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    now: Long,
) {
    val user = state.user
    val app = state.app
    fun updateUser(transform: (UserSettings) -> UserSettings) = onEvent(SettingsEvent.UpdateUser(transform))
    fun updateApp(transform: (AppSettings) -> AppSettings) = onEvent(SettingsEvent.UpdateApp(transform))

    Scaffold(topBar = { TopAppBar(title = { Text("Настройки") }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Каталог (настройки текущего профиля)")
            ChoiceRow("Сортировка", SortOrder.entries, user.sortOrder, { it.label() }) { v ->
                updateUser { it.copy(sortOrder = v) }
            }
            ChoiceRow("Размер Pokédex", UserSettings.POKEDEX_SIZES, user.pokedexSize, ::pokedexSizeLabel) { v ->
                updateUser { it.copy(pokedexSize = v) }
            }
            ChoiceRow("Хранить в истории", UserSettings.HISTORY_LIMITS, user.historyLimit, { "$it" }) { v ->
                updateUser { it.copy(historyLimit = v) }
            }
            ChoiceRow("Тема", ThemeMode.entries, user.themeMode, { it.label() }) { v ->
                updateUser { it.copy(themeMode = v) }
            }

            HorizontalDivider()
            SectionTitle("Данные и офлайн")
            ChoiceRow("Срок жизни кэша", AppSettings.TTL_OPTIONS, app.cacheTtlHours, ::ttlLabel) { v ->
                updateApp { it.copy(cacheTtlHours = v) }
            }
            SwitchRow(
                title = "Фоновое обновление",
                subtitle = "WorkManager периодически актуализирует каталог",
                checked = app.backgroundSyncEnabled,
            ) { v -> updateApp { it.copy(backgroundSyncEnabled = v) } }
            if (app.backgroundSyncEnabled) {
                ChoiceRow("Интервал", AppSettings.SYNC_INTERVALS, app.syncIntervalHours, { "$it ч" }) { v ->
                    updateApp { it.copy(syncIntervalHours = v) }
                }
                SwitchRow(
                    title = "Только по Wi-Fi",
                    subtitle = "Не тратить мобильный трафик",
                    checked = app.wifiOnly,
                ) { v -> updateApp { it.copy(wifiOnly = v) } }
            }
            SwitchRow(
                title = "Предзагрузка для офлайна",
                subtitle = "Скачивать карточки из коллекций, журнала и истории",
                checked = app.offlinePrefetchEnabled,
            ) { v -> updateApp { it.copy(offlinePrefetchEnabled = v) } }

            HorizontalDivider()
            SectionTitle("Синхронизация")
            Text("Каталог обновлён: ${RelativeTime.format(state.lastSyncAt, now)}", style = MaterialTheme.typography.bodyMedium)
            Text("Карточек доступно офлайн: ${state.offlineDetails}", style = MaterialTheme.typography.bodyMedium)
            Text(syncStatusText(state.syncStatus), style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { onEvent(SettingsEvent.SyncNow) },
                enabled = state.syncStatus !is WorkStatus.Running,
            ) {
                Icon(Icons.Outlined.Sync, contentDescription = null)
                Text("  Синхронизировать сейчас")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun syncStatusText(status: WorkStatus): String = when (status) {
    WorkStatus.Idle -> "Фоновая задача ожидает расписания"
    WorkStatus.Queued -> "Синхронизация поставлена в очередь (ждём сеть)"
    is WorkStatus.Running ->
        if (status.total > 0) "Синхронизация… ${status.done}/${status.total}" else "Синхронизация…"
    is WorkStatus.Succeeded -> "Готово: обновлено ${status.updated}, ошибок ${status.failed}"
    WorkStatus.Failed -> "Синхронизация не удалась, повторим позже"
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
