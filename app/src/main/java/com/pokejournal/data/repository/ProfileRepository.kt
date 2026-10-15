package com.pokejournal.data.repository

import com.pokejournal.data.local.dao.ProfileDao
import com.pokejournal.data.local.entity.ProfileEntity
import com.pokejournal.data.settings.SettingsRepository
import com.pokejournal.domain.logic.NameValidation
import com.pokejournal.domain.logic.NameValidator
import com.pokejournal.domain.logic.TimeProvider
import com.pokejournal.domain.model.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DeleteProfileResult {
    data object Deleted : DeleteProfileResult
    data object LastProfile : DeleteProfileResult
}

/**
 * Локальные профили без серверной авторизации. Все пользовательские таблицы ссылаются
 * на профиль (FK CASCADE), настройки в DataStore — по ключам с profileId.
 */
interface ProfileRepository {
    fun observeProfiles(): Flow<List<Profile>>
    fun observeProfile(id: Long): Flow<Profile?>
    suspend fun createProfile(name: String, avatar: String): CreateResult
    suspend fun deleteProfile(id: Long): DeleteProfileResult
}

@Singleton
class RoomProfileRepository @Inject constructor(
    private val dao: ProfileDao,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ProfileRepository {

    override fun observeProfiles(): Flow<List<Profile>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeProfile(id: Long): Flow<Profile?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun createProfile(name: String, avatar: String): CreateResult {
        val existing = dao.getAll().map { it.name }
        return when (val validation = NameValidator.validate(name, existing, maxLength = 24)) {
            is NameValidation.Invalid -> CreateResult.Rejected(validation.error)
            is NameValidation.Valid -> {
                val id = dao.insert(ProfileEntity(name = validation.name, avatar = avatar, createdAt = time.now()))
                CreateResult.Created(id)
            }
        }
    }

    override suspend fun deleteProfile(id: Long): DeleteProfileResult {
        val profiles = dao.getAll()
        if (profiles.size <= 1) return DeleteProfileResult.LastProfile
        val wasActive = settings.activeProfileId.first() == id
        dao.delete(id) // каскадно удаляет статусы, заметки, коллекции, историю, пресеты
        settings.clearUserSettings(id)
        if (wasActive) {
            val fallback = profiles.first { it.id != id }
            settings.setActiveProfile(fallback.id)
        }
        return DeleteProfileResult.Deleted
    }
}

private fun ProfileEntity.toDomain() = Profile(id, name, avatar, createdAt)
