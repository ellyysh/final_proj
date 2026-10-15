package com.pokejournal.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** PokeAPI v2 — открытое API без ключа. */
interface PokeApi {

    @GET("pokemon")
    suspend fun getPokemonList(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int = 0,
    ): NamedResourceListDto

    @GET("type/{name}")
    suspend fun getType(@Path("name") name: String): TypeDto

    @GET("pokemon/{id}")
    suspend fun getPokemon(@Path("id") id: Int): PokemonDto

    @GET("pokemon-species/{id}")
    suspend fun getSpecies(@Path("id") id: Int): SpeciesDto

    companion object {
        const val BASE_URL = "https://pokeapi.co/api/v2/"
    }
}
