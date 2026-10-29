package com.pokejournal.data.repository

import androidx.room.withTransaction
import com.pokejournal.data.local.AppDatabase
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

interface ProfileRepository {
    fun observeProfiles(): Flow<List<Profile>>
    fun observeProfile(id: Long): Flow<Profile?>
    suspend fun createProfile(name: String, avatar: String): CreateResult
    suspend fun deleteProfile(id: Long): DeleteProfileResult
}

@Singleton
class RoomProfileRepository @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ProfileRepository {

    private val dao = db.profileDao()

    override fun observeProfiles(): Flow<List<Profile>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeProfile(id: Long): Flow<Profile?> = dao.observe(id).map { it?.toDomain() }
    override suspend fun createProfile(name: String, avatar: String): CreateResult = db.withTransaction {
        val existing = dao.getAll().map { it.name }
        when (val validation = NameValidator.validate(name, existing, maxLength = MAX_PROFILE_NAME)) {
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
        dao.delete(id)
        settings.clearUserSettings(id)
        if (wasActive) {
            val fallback = profiles.first { it.id != id }
            settings.setActiveProfile(fallback.id)
        }
        return DeleteProfileResult.Deleted
    }
}

private const val MAX_PROFILE_NAME = 24

private fun ProfileEntity.toDomain() = Profile(id, name, avatar, createdAt)
