package com.pokejournal.data.remote


object DtoMappers {


    fun idFromUrl(url: String): Int? = url.trimEnd('/').substringAfterLast('/').toIntOrNull()


    fun cleanFlavorText(raw: String): String =
        raw.replace('\n', ' ').replace('\u000c', ' ').replace(Regex("\\s+"), " ").trim()

    fun SpeciesDto.englishGenus(): String? =
        genera.firstOrNull { it.language.name == "en" }?.genus

    fun SpeciesDto.englishFlavorText(): String? =
        flavorTextEntries.lastOrNull { it.language.name == "en" }?.flavorText?.let(::cleanFlavorText)
}
