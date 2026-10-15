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
import java.io.IOException

/** Фейковое PokeAPI для интеграционных тестов data-слоя. */
class FakePokeApi(
    private val pokemon: List<Pair<Int, List<String>>> = listOf(
        1 to listOf("grass", "poison"),
        4 to listOf("fire"),
        7 to listOf("water"),
        25 to listOf("electric"),
    ),
) : PokeApi {
    private val names = mapOf(1 to "bulbasaur", 4 to "charmander", 7 to "squirtle", 25 to "pikachu")

    var offline = false
    var listCalls = 0
    var typeCalls = 0
    var pokemonCalls = 0
    var failingPokemonIds: Set<Int> = emptySet()

    private fun url(kind: String, id: Int) = "https://pokeapi.co/api/v2/$kind/$id/"
    private fun nameOf(id: Int) = names[id] ?: "pokemon-$id"

    private fun ensureOnline() {
        if (offline) throw IOException("offline")
    }

    override suspend fun getPokemonList(limit: Int, offset: Int): NamedResourceListDto {
        ensureOnline()
        listCalls++
        val results = pokemon.filter { it.first <= limit }.map { NamedResourceDto(nameOf(it.first), url("pokemon", it.first)) }
        return NamedResourceListDto(count = results.size, results = results)
    }

    override suspend fun getType(name: String): TypeDto {
        ensureOnline()
        typeCalls++
        val entries = pokemon.filter { name in it.second }.map { (id, types) ->
            TypePokemonDto(slot = types.indexOf(name) + 1, pokemon = NamedResourceDto(nameOf(id), url("pokemon", id)))
        } + TypePokemonDto(1, NamedResourceDto("mega-form", url("pokemon", 10_001))) // альт-формы отсекаются
        return TypeDto(name, entries)
    }

    override suspend fun getPokemon(id: Int): PokemonDto {
        ensureOnline()
        pokemonCalls++
        if (id in failingPokemonIds) throw IOException("broken $id")
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
        return SpeciesDto(
            genera = listOf(GenusDto("Seed Pokémon", NamedResourceDto("en", ""))),
            flavorTextEntries = listOf(FlavorTextDto("A strange seed was\nplanted\u000con its back.", NamedResourceDto("en", ""))),
        )
    }
}
