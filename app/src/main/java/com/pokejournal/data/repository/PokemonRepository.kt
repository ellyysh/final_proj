package com.pokejournal.data.repository

import androidx.room.withTransaction
import com.pokejournal.data.local.AppDatabase
import com.pokejournal.data.local.entity.CacheMetaEntity
import com.pokejournal.data.local.entity.PokemonDetailEntity
import com.pokejournal.data.local.entity.PokemonEntity
import com.pokejournal.data.local.entity.PokemonFull
import com.pokejournal.data.local.entity.PokemonStatEntity
import com.pokejournal.data.local.entity.PokemonTypeEntity
import com.pokejournal.data.local.entity.PokemonWithTypes
import com.pokejournal.data.remote.DtoMappers
import com.pokejournal.data.remote.DtoMappers.englishFlavorText
import com.pokejournal.data.remote.DtoMappers.englishGenus
import com.pokejournal.data.remote.PokeApi
import com.pokejournal.data.remote.PokemonDto
import com.pokejournal.data.remote.SpeciesDto
import com.pokejournal.domain.logic.CachePolicy
import com.pokejournal.domain.logic.TimeProvider
import com.pokejournal.domain.model.PokemonDetail
import com.pokejournal.domain.model.PokemonImages
import com.pokejournal.domain.model.PokemonStat
import com.pokejournal.domain.model.PokemonSummary
import com.pokejournal.domain.model.PokemonTypes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RefreshResult {
    /** Данные скачаны и записаны в Room. */
    data object Updated : RefreshResult

    /** Кэш свежий по TTL — сеть не трогали. */
    data object UpToDate : RefreshResult

    data class Failed(val error: Throwable) : RefreshResult
}

data class PrefetchReport(val total: Int, val updated: Int, val upToDate: Int, val failed: Int)

/**
 * Offline-first репозиторий контента: UI читает только Room (Flow),
 * сеть используется для обновления локальной копии.
 */
interface PokemonRepository {
    fun observeCatalog(): Flow<List<PokemonSummary>>
    fun observeDetail(id: Int): Flow<PokemonDetail?>
    fun observeLastCatalogSync(): Flow<Long?>
    fun observeOfflineDetailCount(): Flow<Int>
    suspend fun refreshCatalog(limit: Int, ttlMillis: Long, force: Boolean): RefreshResult
    suspend fun refreshDetail(id: Int, ttlMillis: Long, force: Boolean): RefreshResult
    suspend fun prefetchDetails(
        ids: List<Int>,
        ttlMillis: Long,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): PrefetchReport
}

@Singleton
class OfflineFirstPokemonRepository @Inject constructor(
    private val api: PokeApi,
    private val db: AppDatabase,
    private val time: TimeProvider,
) : PokemonRepository {

    private val pokemonDao = db.pokemonDao()
    private val metaDao = db.cacheMetaDao()

    override fun observeCatalog(): Flow<List<PokemonSummary>> =
        pokemonDao.observeCatalog().map { list -> list.map { it.toSummary() } }

    override fun observeDetail(id: Int): Flow<PokemonDetail?> =
        pokemonDao.observeFull(id).map { it?.toDetail() }

    override fun observeLastCatalogSync(): Flow<Long?> =
        metaDao.observe(KEY_CATALOG).map { it?.updatedAt }

    override fun observeOfflineDetailCount(): Flow<Int> = pokemonDao.observeDetailCount()

    override suspend fun refreshCatalog(limit: Int, ttlMillis: Long, force: Boolean): RefreshResult {
        val meta = metaDao.get(KEY_CATALOG)
        val stale = CachePolicy.needsCatalogRefresh(
            updatedAt = meta?.updatedAt,
            cachedLimit = meta?.itemCount ?: 0,
            requiredLimit = limit,
            now = time.now(),
            ttlMillis = ttlMillis,
        )
        if (!force && !stale) return RefreshResult.UpToDate

        return safeCall {
            val (list, typeResponses) = coroutineScope {
                val listCall = async { api.getPokemonList(limit = limit) }
                val typeCalls = PokemonTypes.ALL.map { type -> async { api.getType(type) } }
                listCall.await() to typeCalls.awaitAll()
            }

            val pokemon = list.results.mapNotNull { dto ->
                val id = DtoMappers.idFromUrl(dto.url) ?: return@mapNotNull null
                PokemonEntity(id = id, name = dto.name, imageUrl = PokemonImages.artworkUrl(id))
            }
            val ids = pokemon.map { it.id }.toSet()
            val types = typeResponses.flatMap { type ->
                type.pokemon.mapNotNull { entry ->
                    val id = DtoMappers.idFromUrl(entry.pokemon.url)
                    if (id != null && id in ids) PokemonTypeEntity(id, type.name, entry.slot) else null
                }
            }

            db.withTransaction {
                pokemonDao.upsertPokemon(pokemon)
                // SQLite на старых API ограничивает число параметров (999) — режем на чанки.
                ids.chunked(SQL_CHUNK).forEach { pokemonDao.deleteTypesFor(it) }
                pokemonDao.insertTypes(types)
                metaDao.upsert(CacheMetaEntity(KEY_CATALOG, time.now(), maxOf(limit, meta?.itemCount ?: 0)))
            }
        }
    }

    override suspend fun refreshDetail(id: Int, ttlMillis: Long, force: Boolean): RefreshResult {
        val fetchedAt = pokemonDao.detailFetchedAt(id)
        if (!force && !CachePolicy.isStale(fetchedAt, time.now(), ttlMillis)) return RefreshResult.UpToDate

        return safeCall {
            val (pokemon, species) = coroutineScope {
                val pokemonCall = async { api.getPokemon(id) }
                // Species — дополнительная информация, её ошибка не должна ломать карточку.
                val speciesCall = async {
                    try {
                        api.getSpecies(id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
                pokemonCall.await() to speciesCall.await()
            }
            savePokemonDetail(pokemon, species)
        }
    }

    override suspend fun prefetchDetails(
        ids: List<Int>,
        ttlMillis: Long,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ): PrefetchReport {
        val unique = ids.distinct()
        var updated = 0
        var upToDate = 0
        var failed = 0
        unique.forEachIndexed { index, id ->
            when (refreshDetail(id, ttlMillis, force = false)) {
                RefreshResult.Updated -> updated++
                RefreshResult.UpToDate -> upToDate++
                is RefreshResult.Failed -> failed++
            }
            onProgress(index + 1, unique.size)
        }
        return PrefetchReport(unique.size, updated, upToDate, failed)
    }

    private suspend fun savePokemonDetail(dto: PokemonDto, species: SpeciesDto?) {
        val now = time.now()
        db.withTransaction {
            pokemonDao.upsertPokemon(
                listOf(PokemonEntity(dto.id, dto.name, PokemonImages.artworkUrl(dto.id))),
            )
            pokemonDao.upsertDetail(
                PokemonDetailEntity(
                    pokemonId = dto.id,
                    height = dto.height,
                    weight = dto.weight,
                    baseExperience = dto.baseExperience,
                    genus = species?.englishGenus(),
                    flavorText = species?.englishFlavorText(),
                    abilities = dto.abilities.joinToString(ABILITY_SEPARATOR) { it.ability.name },
                    fetchedAt = now,
                ),
            )
            pokemonDao.deleteStats(dto.id)
            pokemonDao.insertStats(
                dto.stats.mapIndexed { index, stat ->
                    PokemonStatEntity(dto.id, stat.stat.name, stat.baseStat, index)
                },
            )
            pokemonDao.deleteTypesFor(listOf(dto.id))
            pokemonDao.insertTypes(dto.types.map { PokemonTypeEntity(dto.id, it.type.name, it.slot) })
        }
    }

    private suspend fun safeCall(block: suspend () -> Unit): RefreshResult = try {
        block()
        RefreshResult.Updated
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RefreshResult.Failed(e)
    }

    companion object {
        const val KEY_CATALOG = "catalog"
        private const val SQL_CHUNK = 500
        private const val ABILITY_SEPARATOR = ","
    }
}

internal fun PokemonWithTypes.toSummary() = PokemonSummary(
    id = pokemon.id,
    name = pokemon.name,
    imageUrl = pokemon.imageUrl,
    types = types.sortedBy { it.slot }.map { it.typeName },
)

internal fun PokemonFull.toDetail() = PokemonDetail(
    id = pokemon.id,
    name = pokemon.name,
    imageUrl = pokemon.imageUrl,
    types = types.sortedBy { it.slot }.map { it.typeName },
    height = detail?.height,
    weight = detail?.weight,
    baseExperience = detail?.baseExperience,
    genus = detail?.genus,
    flavorText = detail?.flavorText,
    abilities = detail?.abilities?.split(",")?.filter { it.isNotBlank() }.orEmpty(),
    stats = stats.sortedBy { it.position }.map { PokemonStat(it.statName, it.value) },
    fetchedAt = detail?.fetchedAt,
)
