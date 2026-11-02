package com.pokejournal.testing

import com.pokejournal.data.remote.FlavorTextDto
import com.pokejournal.data.remote.GenusDto
import com.pokejournal.data.remote.NamedResourceDto
import com.pokejournal.data.remote.NamedResourceListDto
import com.pokejournal.data.remote.PokeApi
import com.pokejournal.data.remote.PokemonAbilityDto
import com.pokejournal.data.remote.PokemonDto
import com.pokejournal.data.remote.PokemonStatDto
import com.pokejournal.data.remote.PokemonTypeSlotDto
import com.pokejournal.data.remote.SpeciesDto
import com.pokejournal.data.remote.TypeDto
import com.pokejournal.data.remote.TypePokemonDto
import com.pokejournal.domain.model.PokemonTypes
import kotlinx.coroutines.delay
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class FakePokeApi(
    private val pokemon: List<Pair<Int, List<String>>> = listOf(
        1 to listOf("grass", "poison"),
        4 to listOf("fire"),
        7 to listOf("water"),
        25 to listOf("electric"),
    ),
) : PokeApi {
    private val names = mapOf(1 to "bulbasaur", 4 to "charmander", 7 to "squirtle", 25 to "pikachu")

    @Volatile var offline = false

    val failNextListCalls = AtomicInteger(0)

    @Volatile var failingPokemonIds: Set<Int> = emptySet()

    @Volatile var notFoundPokemonIds: Set<Int> = emptySet()

    @Volatile var failingSpeciesIds: Set<Int> = emptySet()

    @Volatile var notFoundSpeciesIds: Set<Int> = emptySet()

    @Volatile var pokemonDelayMillis = 0L

    private val listCallsCounter = AtomicInteger()
    private val listAttemptsCounter = AtomicInteger()
    private val typeCallsCounter = AtomicInteger()
    private val pokemonCallsCounter = AtomicInteger()
    private val inFlight = AtomicInteger()
    private val maxInFlightCounter = AtomicInteger()

    val listCalls: Int get() = listCallsCounter.get()

    val listAttempts: Int get() = listAttemptsCounter.get()
    val typeCalls: Int get() = typeCallsCounter.get()
    val pokemonCalls: Int get() = pokemonCallsCounter.get()

    val maxConcurrentPokemonCalls: Int get() = maxInFlightCounter.get()

    private fun url(kind: String, id: Int) = "https://pokeapi.co/api/v2/$kind/$id/"
    private fun nameOf(id: Int) = names[id] ?: "pokemon-$id"

    private fun ensureOnline() {
        if (offline) throw IOException("offline")
    }

    override suspend fun getPokemonList(limit: Int, offset: Int): NamedResourceListDto {
        listAttemptsCounter.incrementAndGet()
        ensureOnline()
        if (failNextListCalls.getAndUpdate { if (it > 0) it - 1 else 0 } > 0) throw IOException("flaky network")
        listCallsCounter.incrementAndGet()
        val results = pokemon.filter { it.first <= limit }.map { NamedResourceDto(nameOf(it.first), url("pokemon", it.first)) }
        return NamedResourceListDto(count = results.size, results = results)
    }

    override suspend fun getType(name: String): TypeDto {
        ensureOnline()
        typeCallsCounter.incrementAndGet()
        val entries = pokemon.filter { name in it.second }.map { (id, types) ->
            TypePokemonDto(slot = types.indexOf(name) + 1, pokemon = NamedResourceDto(nameOf(id), url("pokemon", id)))
        } + TypePokemonDto(1, NamedResourceDto("mega-form", url("pokemon", 10_001)))
        return TypeDto(name, entries)
    }

    override suspend fun getPokemon(id: Int): PokemonDto {
        ensureOnline()
        pokemonCallsCounter.incrementAndGet()
        val now = inFlight.incrementAndGet()
        maxInFlightCounter.updateAndGet { maxOf(it, now) }
        try {
            if (pokemonDelayMillis > 0) delay(pokemonDelayMillis)
            if (id in failingPokemonIds) throw IOException("broken $id")
            if (id in notFoundPokemonIds) throw notFound()
        } finally {
            inFlight.decrementAndGet()
        }
        val types = pokemon.firstOrNull { it.first == id }?.second ?: listOf("normal")
        return PokemonDto(
            id = id,
            name = nameOf(id),
            height = 7,
            weight = 69,
            baseExperience = 64,
            types = types.mapIndexed { i, t -> PokemonTypeSlotDto(i + 1, NamedResourceDto(t, url("type", i))) },
            stats = listOf(
                PokemonStatDto(45, NamedResourceDto("hp", "")),
                PokemonStatDto(49, NamedResourceDto("attack", "")),
            ),
            abilities = listOf(PokemonAbilityDto(NamedResourceDto("overgrow", ""))),
        )
    }

    override suspend fun getSpecies(id: Int): SpeciesDto {
        ensureOnline()
        if (id in failingSpeciesIds) throw IOException("species timeout $id")
        if (id in notFoundSpeciesIds) throw notFound()
        return SpeciesDto(
            genera = listOf(GenusDto("Seed Pokémon", NamedResourceDto("en", ""))),
            flavorTextEntries = listOf(FlavorTextDto("A strange seed was\nplanted\u000con its back.", NamedResourceDto("en", ""))),
        )
    }

    private fun notFound() = HttpException(Response.error<Unit>(404, "".toResponseBody(null)))

    companion object {
        fun withCatalog(size: Int) = FakePokeApi(
            pokemon = (1..size).map { id ->
                val primary = PokemonTypes.ALL[id % PokemonTypes.ALL.size]
                val types = if (id % 3 == 0) listOf(primary, PokemonTypes.ALL[(id + 7) % PokemonTypes.ALL.size]) else listOf(primary)
                id to types.distinct()
            },
        )
    }
}
