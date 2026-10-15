package com.pokejournal.domain.logic

import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

fun Throwable.toUserMessage(): String = when (this) {
    is HttpException -> if (code() == 404) "Данные не найдены (404)" else "Ошибка сервера (${code()})"
    is IOException -> "Нет подключения к сети"
    is SerializationException -> "Сервер вернул неожиданный ответ"
    else -> "Что-то пошло не так: ${message ?: javaClass.simpleName}"
}
