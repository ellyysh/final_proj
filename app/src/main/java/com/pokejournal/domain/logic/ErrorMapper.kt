package com.pokejournal.domain.logic

import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

class HttpStatusException(val code: Int) : IOException("HTTP $code")

class SpeciesUnavailableException(val pokemonId: Int) : Exception("Species of $pokemonId is unavailable")

fun Throwable.toUserMessage(): String = when (this) {
    is HttpException -> if (code() == 404) "Данные не найдены (404)" else "Ошибка сервера (${code()})"
    is HttpStatusException -> if (code == 404) "Данные не найдены (404)" else "Ошибка сервера ($code)"
    is SpeciesUnavailableException -> "Описание отсутствует в PokeAPI"
    is IOException -> "Нет подключения к сети"
    is SerializationException -> "Сервер вернул неожиданный ответ"
    else -> "Что-то пошло не так: ${message ?: javaClass.simpleName}"
}

fun Throwable.isRetryable(): Boolean = when (this) {
    is HttpException -> isRetryableHttpCode(code())
    is HttpStatusException -> isRetryableHttpCode(code)
    is IOException -> true
    else -> false
}

private fun isRetryableHttpCode(code: Int): Boolean = code == 408 || code == 429 || code >= 500
