package com.pokejournal.data.images

import coil.map.Mapper
import coil.request.Options
import com.pokejournal.domain.model.PokemonImages
import java.io.File

class LocalArtworkMapper(private val store: ArtworkStore) : Mapper<String, File> {
    override fun map(data: String, options: Options): File? {
        val id = PokemonImages.idFromArtworkUrl(data) ?: return null
        return store.localFile(id)
    }
}
