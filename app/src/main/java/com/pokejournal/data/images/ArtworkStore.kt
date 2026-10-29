package com.pokejournal.data.images

import android.content.Context
import com.pokejournal.domain.logic.HttpStatusException
import com.pokejournal.domain.model.PokemonImages
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton


interface ArtworkStore {
    fun localFile(pokemonId: Int): File?

    suspend fun ensure(pokemonId: Int): Throwable?

    suspend fun retainOnly(loadKeepIds: suspend () -> Collection<Int>)
}

@Singleton
class FileArtworkStore @Inject constructor(
    @ApplicationContext context: Context,
    private val client: OkHttpClient,
) : ArtworkStore {

    private val dir = File(context.filesDir, DIR_NAME)

    override fun localFile(pokemonId: Int): File? =
        fileFor(pokemonId).takeIf { it.isFile && it.length() > 0 }

    override suspend fun ensure(pokemonId: Int): Throwable? = withContext(Dispatchers.IO) {
        if (localFile(pokemonId) != null) return@withContext null
        try {
            download(pokemonId)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }
    }

    override suspend fun retainOnly(loadKeepIds: suspend () -> Collection<Int>) {
        val startedAt = System.currentTimeMillis()
        val keep = loadKeepIds().toSet()
        withContext(Dispatchers.IO) {
            dir.listFiles().orEmpty()
                .filter { file ->
                    val id = file.name.removeSuffix(EXTENSION).toIntOrNull()
                    (id == null || id !in keep) && file.lastModified() < startedAt
                }
                .forEach { it.delete() }
        }
    }

    private fun download(pokemonId: Int) {
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create $dir")
        val request = Request.Builder().url(PokemonImages.artworkUrl(pokemonId)).build()
        val tmp = File(dir, "$pokemonId$EXTENSION.part")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            val body = response.body ?: throw IOException("Empty body")
            tmp.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
        }
        if (!tmp.renameTo(fileFor(pokemonId))) {
            tmp.delete()
            throw IOException("Cannot store artwork $pokemonId")
        }
    }

    private fun fileFor(pokemonId: Int) = File(dir, "$pokemonId$EXTENSION")

    private companion object {
        const val DIR_NAME = "artwork"
        const val EXTENSION = ".png"
    }
}
