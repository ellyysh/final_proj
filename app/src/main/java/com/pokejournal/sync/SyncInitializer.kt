package com.pokejournal.sync

import com.pokejournal.data.settings.SettingsRepository
import com.pokejournal.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Реактивно перепланирует фоновую синхронизацию при изменении настроек. */
class SyncInitializer @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val scheduler: SyncScheduler,
) {
    fun start() {
        scope.launch {
            settingsRepository.appSettings
                .distinctUntilChanged()
                .collect { scheduler.schedulePeriodicSync(it) }
        }
    }
}
