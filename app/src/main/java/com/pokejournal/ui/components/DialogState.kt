package com.pokejournal.ui.components

/**
 * Состояние диалога создания сущности с валидацией имени (коллекция, профиль, пресет).
 * Живёт во ViewModel: диалог закрывается только после успешного результата,
 * при ошибке остаётся открытым с сообщением — введённый текст не теряется.
 * [submitting] блокирует повторное нажатие, пока идёт запрос в БД.
 */
data class CreateDialogState(
    val visible: Boolean = false,
    val error: String? = null,
    val submitting: Boolean = false,
)
