package com.pokejournal.data.repository

import com.pokejournal.data.local.dao.CollectionDao
import com.pokejournal.data.local.entity.CollectionEntity
import com.pokejournal.data.local.entity.CollectionItemEntity
import com.pokejournal.data.local.entity.CollectionSummaryRow
import com.pokejournal.domain.logic.NameError
import com.pokejournal.domain.logic.NameValidation
import com.pokejournal.domain.logic.NameValidator
import com.pokejournal.domain.logic.TimeProvider
import com.pokejournal.domain.model.CollectionMember
import com.pokejournal.domain.model.CollectionSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CreateResult {
    data class Created(val id: Long) : CreateResult
    data class Rejected(val error: NameError) : CreateResult
}

/** Пользовательские коллекции покемонов (many-to-many). */
interface CollectionRepository {
    fun observeCollections(profileId: Long): Flow<List<CollectionSummary>>
    fun observeCollection(collectionId: Long): Flow<CollectionSummary?>
    fun observeMembers(collectionId: Long): Flow<List<CollectionMember>>
    fun observeCollectionIdsFor(profileId: Long, pokemonId: Int): Flow<Set<Long>>
    suspend fun createCollection(profileId: Long, name: String, description: String): CreateResult
    suspend fun deleteCollection(collectionId: Long)
    suspend fun setMembership(collectionId: Long, pokemonId: Int, member: Boolean)
    suspend fun memberIds(collectionId: Long): List<Int>
}

@Singleton
class RoomCollectionRepository @Inject constructor(
    private val dao: CollectionDao,
    private val time: TimeProvider,
) : CollectionRepository {

    override fun observeCollections(profileId: Long): Flow<List<CollectionSummary>> =
        dao.observeSummaries(profileId).map { rows -> rows.map { it.toDomain() } }

    override fun observeCollection(collectionId: Long): Flow<CollectionSummary?> =
        dao.observeSummary(collectionId).map { it?.toDomain() }

    override fun observeMembers(collectionId: Long): Flow<List<CollectionMember>> =
        dao.observeMembers(collectionId).map { rows ->
            rows.map {
                CollectionMember(
                    pokemonId = it.pokemonId,
                    name = it.name,
                    imageUrl = it.imageUrl,
                    availableOffline = it.detailFetchedAt != null,
                    addedAt = it.addedAt,
                )
            }
        }

    override fun observeCollectionIdsFor(profileId: Long, pokemonId: Int): Flow<Set<Long>> =
        dao.observeCollectionIdsFor(profileId, pokemonId).map { it.toSet() }

    override suspend fun createCollection(profileId: Long, name: String, description: String): CreateResult =
        when (val validation = NameValidator.validate(name, dao.namesFor(profileId))) {
            is NameValidation.Invalid -> CreateResult.Rejected(validation.error)
            is NameValidation.Valid -> {
                val id = dao.insert(
                    CollectionEntity(
                        profileId = profileId,
                        name = validation.name,
                        description = description.trim(),
                        createdAt = time.now(),
                    ),
                )
                CreateResult.Created(id)
            }
        }

    override suspend fun deleteCollection(collectionId: Long) = dao.delete(collectionId)

    override suspend fun setMembership(collectionId: Long, pokemonId: Int, member: Boolean) {
        if (member) {
            dao.addItem(CollectionItemEntity(collectionId, pokemonId, time.now()))
        } else {
            dao.removeItem(collectionId, pokemonId)
        }
    }

    override suspend fun memberIds(collectionId: Long): List<Int> = dao.memberIds(collectionId)
}

private fun CollectionSummaryRow.toDomain() = CollectionSummary(
    id = id,
    name = name,
    description = description,
    itemCount = itemCount,
    offlineCount = offlineCount,
    createdAt = createdAt,
)
