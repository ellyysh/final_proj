package com.pokejournal.testing

import com.pokejournal.data.network.NetworkMonitor
import com.pokejournal.data.repository.CollectionRepository
import com.pokejournal.data.repository.CreateResult
import com.pokejournal.data.repository.HistoryRepository
import com.pokejournal.data.repository.JournalRepository
import com.pokejournal.data.repository.PokemonRepository
import com.pokejournal.data.repository.PrefetchReport
import com.pokejournal.data.repository.PresetRepository
import com.pokejournal.data.repository.RefreshResult
import com.pokejournal.data.settings.SettingsRepository
import com.pokejournal.domain.logic.NameValidation
import com.pokejournal.domain.logic.NameValidator
import com.pokejournal.domain.model.AppSettings
import com.pokejournal.domain.model.CatchStatus
import com.pokejournal.domain.model.CollectionMember
import com.pokejournal.domain.model.CollectionSummary
import com.pokejournal.domain.model.FilterPreset
import com.pokejournal.domain.model.HistoryEntry
import com.pokejournal.domain.model.JournalNote
import com.pokejournal.domain.model.ListFilter
import com.pokejournal.domain.model.PokemonDetail
import com.pokejournal.domain.model.PokemonSummary
import com.pokejournal.domain.model.PokemonUserState
import com.pokejournal.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(activeId: Long = 1L) : SettingsRepository {
    val active = MutableStateFlow(activeId)
    val app = MutableStateFlow(AppSettings())
    val users = MutableStateFlow<Map<Long, UserSettings>>(emptyMap())

    override val activeProfileId: Flow<Long> = active
    override val appSettings: Flow<AppSettings> = app
    override fun userSettings(profileId: Long): Flow<UserSettings> =
        users.map { it[profileId] ?: UserSettings() }.distinctUntilChanged()

    override suspend fun setActiveProfile(profileId: Long) {
        active.value = profileId
    }

    override suspend fun updateUserSettings(profileId: Long, transform: (UserSettings) -> UserSettings) {
        users.update { it + (profileId to transform(it[profileId] ?: UserSettings())) }
    }

    override suspend fun updateAppSettings(transform: (AppSettings) -> AppSettings) {
        app.update(transform)
    }

    override suspend fun clearUserSettings(profileId: Long) {
        users.update { it - profileId }
    }
}

class FakeNetworkMonitor(online: Boolean = true) : NetworkMonitor {
    val online = MutableStateFlow(online)
    override val isOnline: Flow<Boolean> = this.online
}

data class CatalogCall(val limit: Int, val force: Boolean)

class FakePokemonRepository : PokemonRepository {
    val catalog = MutableStateFlow<List<PokemonSummary>>(emptyList())
    val details = MutableStateFlow<Map<Int, PokemonDetail>>(emptyMap())
    val lastSync = MutableStateFlow<Long?>(null)

    val catalogCalls = mutableListOf<CatalogCall>()
    val detailCalls = mutableListOf<Int>()
    val prefetchRequests = mutableListOf<List<Int>>()

    /** Поведение обновления каталога можно подменить в тесте. */
    var onRefreshCatalog: suspend (CatalogCall) -> RefreshResult = {
        catalog.value = TestData.catalog
        RefreshResult.Updated
    }

    var onRefreshDetail: suspend (id: Int, force: Boolean) -> RefreshResult = { id, _ ->
        details.update { it + (id to TestData.detail(id)) }
        RefreshResult.Updated
    }

    override fun observeCatalog(): Flow<List<PokemonSummary>> = catalog
    override fun observeDetail(id: Int): Flow<PokemonDetail?> = details.map { it[id] }.distinctUntilChanged()
    override fun observeLastCatalogSync(): Flow<Long?> = lastSync
    override fun observeOfflineDetailCount(): Flow<Int> = details.map { it.size }

    override suspend fun refreshCatalog(limit: Int, ttlMillis: Long, force: Boolean): RefreshResult {
        val call = CatalogCall(limit, force)
        catalogCalls += call
        return onRefreshCatalog(call)
    }

    override suspend fun refreshDetail(id: Int, ttlMillis: Long, force: Boolean): RefreshResult {
        detailCalls += id
        return onRefreshDetail(id, force)
    }

    override suspend fun prefetchDetails(
        ids: List<Int>,
        ttlMillis: Long,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ): PrefetchReport {
        prefetchRequests += ids
        var updated = 0
        var failed = 0
        ids.forEachIndexed { index, id ->
            when (refreshDetail(id, ttlMillis, force = false)) {
                is RefreshResult.Failed -> failed++
                else -> updated++
            }
            onProgress(index + 1, ids.size)
        }
        return PrefetchReport(ids.size, updated, 0, failed)
    }
}

class FakeJournalRepository : JournalRepository {
    /** profileId -> (pokemonId -> state) */
    val states = MutableStateFlow<Map<Long, Map<Int, PokemonUserState>>>(emptyMap())
    val notes = MutableStateFlow<Map<Pair<Long, Int>, String>>(emptyMap())

    override fun observeStates(profileId: Long): Flow<Map<Int, PokemonUserState>> =
        states.map { it[profileId].orEmpty() }.distinctUntilChanged()

    override fun observeState(profileId: Long, pokemonId: Int): Flow<PokemonUserState> =
        observeStates(profileId).map { it[pokemonId] ?: PokemonUserState(pokemonId) }.distinctUntilChanged()

    override suspend fun setStatus(profileId: Long, pokemonId: Int, status: CatchStatus) =
        update(profileId, pokemonId) { it.copy(status = status) }

    override suspend fun setPinned(profileId: Long, pokemonId: Int, pinned: Boolean) =
        update(profileId, pokemonId) { it.copy(pinned = pinned) }

    override suspend fun setHidden(profileId: Long, pokemonId: Int, hidden: Boolean) =
        update(profileId, pokemonId) { it.copy(hidden = hidden) }

    override fun observeNote(profileId: Long, pokemonId: Int): Flow<String> =
        notes.map { it[profileId to pokemonId].orEmpty() }.distinctUntilChanged()

    override suspend fun saveNote(profileId: Long, pokemonId: Int, text: String) {
        notes.update { if (text.isBlank()) it - (profileId to pokemonId) else it + ((profileId to pokemonId) to text) }
    }

    override fun observeNotes(profileId: Long): Flow<List<JournalNote>> = notes.map { map ->
        map.filterKeys { it.first == profileId }.map { (key, text) -> JournalNote(key.second, null, text, 0L) }
    }

    private fun update(profileId: Long, pokemonId: Int, transform: (PokemonUserState) -> PokemonUserState) {
        states.update { all ->
            val profileStates = all[profileId].orEmpty()
            val current = profileStates[pokemonId] ?: PokemonUserState(pokemonId)
            all + (profileId to (profileStates + (pokemonId to transform(current))))
        }
    }
}

class FakePresetRepository : PresetRepository {
    val presets = MutableStateFlow<Map<Long, List<FilterPreset>>>(emptyMap())
    private var nextId = 1L

    override fun observePresets(profileId: Long): Flow<List<FilterPreset>> = presets.map { it[profileId].orEmpty() }

    override suspend fun savePreset(profileId: Long, name: String, query: String, filter: ListFilter): CreateResult {
        val existing = presets.value[profileId].orEmpty().map { it.name }
        return when (val v = NameValidator.validate(name, existing)) {
            is NameValidation.Invalid -> CreateResult.Rejected(v.error)
            is NameValidation.Valid -> {
                val preset = FilterPreset(nextId++, v.name, query, filter)
                presets.update { it + (profileId to (it[profileId].orEmpty() + preset)) }
                CreateResult.Created(preset.id)
            }
        }
    }

    override suspend fun deletePreset(id: Long) {
        presets.update { all -> all.mapValues { (_, list) -> list.filterNot { it.id == id } } }
    }
}

class FakeCollectionRepository : CollectionRepository {
    data class Stored(val id: Long, val profileId: Long, val name: String, val members: Set<Int>)

    val collections = MutableStateFlow<List<Stored>>(emptyList())
    private var nextId = 1L

    override fun observeCollections(profileId: Long): Flow<List<CollectionSummary>> = collections.map { list ->
        list.filter { it.profileId == profileId }.map { CollectionSummary(it.id, it.name, "", it.members.size, 0, 0L) }
    }

    override fun observeCollection(collectionId: Long): Flow<CollectionSummary?> = collections.map { list ->
        list.firstOrNull { it.id == collectionId }?.let { CollectionSummary(it.id, it.name, "", it.members.size, 0, 0L) }
    }

    override fun observeMembers(collectionId: Long): Flow<List<CollectionMember>> = collections.map { list ->
        list.firstOrNull { it.id == collectionId }?.members.orEmpty().map { CollectionMember(it, "p$it", "", false, 0L) }
    }

    override fun observeCollectionIdsFor(profileId: Long, pokemonId: Int): Flow<Set<Long>> = collections.map { list ->
        list.filter { it.profileId == profileId && pokemonId in it.members }.map { it.id }.toSet()
    }

    override suspend fun createCollection(profileId: Long, name: String, description: String): CreateResult {
        val existing = collections.value.filter { it.profileId == profileId }.map { it.name }
        return when (val v = NameValidator.validate(name, existing)) {
            is NameValidation.Invalid -> CreateResult.Rejected(v.error)
            is NameValidation.Valid -> {
                val id = nextId++
                collections.update { it + Stored(id, profileId, v.name, emptySet()) }
                CreateResult.Created(id)
            }
        }
    }

    override suspend fun deleteCollection(collectionId: Long) {
        collections.update { list -> list.filterNot { it.id == collectionId } }
    }

    override suspend fun setMembership(collectionId: Long, pokemonId: Int, member: Boolean) {
        collections.update { list ->
            list.map {
                if (it.id != collectionId) it
                else it.copy(members = if (member) it.members + pokemonId else it.members - pokemonId)
            }
        }
    }

    override suspend fun memberIds(collectionId: Long): List<Int> =
        collections.value.firstOrNull { it.id == collectionId }?.members?.sorted().orEmpty()
}

class FakeHistoryRepository : HistoryRepository {
    data class Record(val profileId: Long, val pokemonId: Int, val limit: Int)

    val records = mutableListOf<Record>()
    private val entries = MutableStateFlow<List<Record>>(emptyList())

    override fun observeHistory(profileId: Long): Flow<List<HistoryEntry>> = entries.map { list ->
        list.filter { it.profileId == profileId }.map { HistoryEntry(it.pokemonId, null, "", 0L) }
    }

    override suspend fun record(profileId: Long, pokemonId: Int, limit: Int) {
        val record = Record(profileId, pokemonId, limit)
        records += record
        entries.update { it + record }
    }

    override suspend fun remove(profileId: Long, pokemonId: Int) {
        entries.update { list -> list.filterNot { it.profileId == profileId && it.pokemonId == pokemonId } }
    }

    override suspend fun clear(profileId: Long) {
        entries.update { list -> list.filterNot { it.profileId == profileId } }
    }
}
