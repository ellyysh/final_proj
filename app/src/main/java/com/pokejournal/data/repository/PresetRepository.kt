package com.pokejournal.data.repository

import com.pokejournal.data.local.dao.FilterPresetDao
import com.pokejournal.data.local.entity.FilterPresetEntity
import com.pokejournal.domain.logic.NameValidation
import com.pokejournal.domain.logic.NameValidator
import com.pokejournal.domain.logic.TimeProvider
import com.pokejournal.domain.model.FilterPreset
import com.pokejournal.domain.model.ListFilter
import com.pokejournal.domain.model.StatusFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Сохранённые пресеты поиска: запрос + тип + статус. */
interface PresetRepository {
    fun observePresets(profileId: Long): Flow<List<FilterPreset>>
    suspend fun savePreset(profileId: Long, name: String, query: String, filter: ListFilter): CreateResult
    suspend fun deletePreset(id: Long)
}

@Singleton
class RoomPresetRepository @Inject constructor(
    private val dao: FilterPresetDao,
    private val time: TimeProvider,
) : PresetRepository {

    override fun observePresets(profileId: Long): Flow<List<FilterPreset>> =
        dao.observe(profileId).map { rows ->
            rows.map {
                FilterPreset(
                    id = it.id,
                    name = it.name,
                    query = it.query,
                    filter = ListFilter(
                        type = it.typeName,
                        status = StatusFilter.entries.firstOrNull { s -> s.name == it.status } ?: StatusFilter.ALL,
                    ),
                )
            }
        }

    override suspend fun savePreset(profileId: Long, name: String, query: String, filter: ListFilter): CreateResult =
        when (val validation = NameValidator.validate(name, dao.namesFor(profileId), maxLength = 24)) {
            is NameValidation.Invalid -> CreateResult.Rejected(validation.error)
            is NameValidation.Valid -> CreateResult.Created(
                dao.insert(
                    FilterPresetEntity(
                        profileId = profileId,
                        name = validation.name,
                        query = query.trim(),
                        typeName = filter.type,
                        status = filter.status.name,
                        createdAt = time.now(),
                    ),
                ),
            )
        }

    override suspend fun deletePreset(id: Long) = dao.delete(id)
}
