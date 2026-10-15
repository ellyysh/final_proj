package com.pokejournal.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NamedResourceDto(val name: String, val url: String)

@Serializable
data class NamedResourceListDto(val count: Int, val results: List<NamedResourceDto>)

@Serializable
data class TypeDto(val name: String, val pokemon: List<TypePokemonDto> = emptyList())

@Serializable
data class TypePokemonDto(val slot: Int, val pokemon: NamedResourceDto)

@Serializable
data class PokemonDto(
    val id: Int,
    val name: String,
    val height: Int,
    val weight: Int,
    @SerialName("base_experience") val baseExperience: Int? = null,
    val types: List<PokemonTypeSlotDto> = emptyList(),
    val stats: List<PokemonStatDto> = emptyList(),
    val abilities: List<PokemonAbilityDto> = emptyList(),
)

@Serializable
data class PokemonTypeSlotDto(val slot: Int, val type: NamedResourceDto)

@Serializable
data class PokemonStatDto(
    @SerialName("base_stat") val baseStat: Int,
    val stat: NamedResourceDto,
)

@Serializable
data class PokemonAbilityDto(
    val ability: NamedResourceDto,
    @SerialName("is_hidden") val isHidden: Boolean = false,
)

@Serializable
data class SpeciesDto(
    val genera: List<GenusDto> = emptyList(),
    @SerialName("flavor_text_entries") val flavorTextEntries: List<FlavorTextDto> = emptyList(),
)

@Serializable
data class GenusDto(val genus: String, val language: NamedResourceDto)

@Serializable
data class FlavorTextDto(
    @SerialName("flavor_text") val flavorText: String,
    val language: NamedResourceDto,
)
