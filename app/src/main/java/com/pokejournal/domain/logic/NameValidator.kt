package com.pokejournal.domain.logic

enum class NameError { EMPTY, TOO_LONG, DUPLICATE }

sealed interface NameValidation {
    data class Valid(val name: String) : NameValidation
    data class Invalid(val error: NameError) : NameValidation
}


object NameValidator {
    const val MAX_LENGTH = 40

    fun validate(raw: String, existingNames: Collection<String>, maxLength: Int = MAX_LENGTH): NameValidation {
        val name = raw.trim().replace(Regex("\\s+"), " ")
        return when {
            name.isEmpty() -> NameValidation.Invalid(NameError.EMPTY)
            name.length > maxLength -> NameValidation.Invalid(NameError.TOO_LONG)
            existingNames.any { it.trim().equals(name, ignoreCase = true) } ->
                NameValidation.Invalid(NameError.DUPLICATE)
            else -> NameValidation.Valid(name)
        }
    }
}

fun NameError.message(): String = when (this) {
    NameError.EMPTY -> "Название не может быть пустым"
    NameError.TOO_LONG -> "Слишком длинное название"
    NameError.DUPLICATE -> "Такое название уже есть"
}
