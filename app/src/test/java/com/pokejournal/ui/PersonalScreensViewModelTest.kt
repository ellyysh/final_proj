package com.pokejournal.ui

import com.pokejournal.testing.FakeHistoryRepository
import com.pokejournal.testing.FakeJournalRepository
import com.pokejournal.testing.FakePokemonRepository
import com.pokejournal.testing.FakeProfileRepository
import com.pokejournal.testing.FakeSettingsRepository
import com.pokejournal.testing.MainDispatcherRule
import com.pokejournal.ui.components.CreateDialogState
import com.pokejournal.ui.profile.ProfileEvent
import com.pokejournal.ui.profile.ProfileViewModel
import com.pokejournal.ui.settings.SettingsEvent
import com.pokejournal.ui.settings.SettingsViewModel
import com.pokejournal.sync.SyncScheduler
import com.pokejournal.sync.WorkStatus
import com.pokejournal.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PersonalScreensViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val profiles = FakeProfileRepository()
    private val settings = FakeSettingsRepository()
    private val history = FakeHistoryRepository()

    private fun TestScope.profileViewModel(): ProfileViewModel {
        val vm = ProfileViewModel(profiles, settings, FakePokemonRepository(), FakeJournalRepository())
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `invalid profile name keeps dialog open with error, valid one closes it and activates profile`() = runTest {
        val vm = profileViewModel()
        advanceUntilIdle()

        vm.onEvent(ProfileEvent.OpenCreateDialog)
        vm.onEvent(ProfileEvent.Create(" ash ", "💧"))
        advanceUntilIdle()
        assertEquals(
            CreateDialogState(visible = true, error = "Такое название уже есть"),
            vm.uiState.value.createDialog,
        )
        assertEquals(1L, settings.active.value)

        vm.onEvent(ProfileEvent.Create("", "💧"))
        advanceUntilIdle()
        assertEquals("Название не может быть пустым", vm.uiState.value.createDialog.error)

        vm.onEvent(ProfileEvent.Create("Misty", "💧"))
        advanceUntilIdle()
        assertEquals(CreateDialogState(), vm.uiState.value.createDialog)
        assertEquals(2L, settings.active.value)
    }

    @Test
    fun `double tap creates a single profile`() = runTest {
        val vm = profileViewModel()
        advanceUntilIdle()

        vm.onEvent(ProfileEvent.OpenCreateDialog)
        vm.onEvent(ProfileEvent.Create("Brock", "🌿"))
        vm.onEvent(ProfileEvent.Create("Brock", "🌿"))
        advanceUntilIdle()

        assertEquals(1, profiles.createCalls)
        assertEquals(CreateDialogState(), vm.uiState.value.createDialog)
    }

    @Test
    fun `lowering history limit in settings trims history of the active profile right away`() = runTest {
        settings.setActiveProfile(2L)
        val vm = SettingsViewModel(settings, NoopScheduler, history, FakePokemonRepository())

        vm.onEvent(SettingsEvent.SetHistoryLimit(20))
        advanceUntilIdle()

        assertEquals(20, settings.users.value[2L]?.historyLimit)
        assertEquals(listOf(2L to 20), history.appliedLimits)
    }

    private object NoopScheduler : SyncScheduler {
        override fun schedulePeriodicSync(settings: AppSettings) = Unit
        override fun syncNow() = Unit
        override fun downloadCollection(collectionId: Long) = Unit
        override fun observeSyncNow(): Flow<WorkStatus> = flowOf(WorkStatus.Idle)
        override fun observeCollectionDownload(collectionId: Long): Flow<WorkStatus> = flowOf(WorkStatus.Idle)
    }
}
