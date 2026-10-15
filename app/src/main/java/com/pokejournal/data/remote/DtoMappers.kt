package com.pokejournal.data.remote

/** Утилиты преобразования сетевых моделей. */
object DtoMappers {

    /** "https://pokeapi.co/api/v2/pokemon/25/" -> 25 */
    fun idFromUrl(url: String): Int? = url.trimEnd('/').substringAfterLast('/').toIntOrNull()

    /** В PokeAPI flavor text содержит переносы строк и form-feed — чистим. */
    fun cleanFlavorText(raw: String): String =
        raw.replace('\n', ' ').replace('\u000c', ' ').replace(Regex("\\s+"), " ").trim()

    fun SpeciesDto.englishGenus(): String? =
        genera.firstOrNull { it.language.name == "en" }?.genus

    fun SpeciesDto.englishFlavorText(): String? =
        flavorTextEntries.lastOrNull { it.language.name == "en" }?.flavorText?.let(::cleanFlavorText)
}
