package com.pokejournal.testing

import com.pokejournal.domain.model.PokemonImages
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class FakeArtworkServer {
    @Volatile var failingIds: Set<Int> = emptySet()

    @Volatile var notFoundIds: Set<Int> = emptySet()

    private val requestCounter = AtomicInteger()
    val requests: Int get() = requestCounter.get()

    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            requestCounter.incrementAndGet()
            val request = chain.request()
            val id = PokemonImages.idFromArtworkUrl(request.url.toString())
            if (id != null && id in failingIds) throw IOException("artwork timeout $id")
            val code = if (id == null || id in notFoundIds) 404 else 200
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Not Found")
                .body(PNG_BYTES.toResponseBody("image/png".toMediaType()))
                .build()
        }
        .build()

    private companion object {
        val PNG_BYTES = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
