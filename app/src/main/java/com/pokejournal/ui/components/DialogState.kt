package com.pokejournal.ui.components

data class CreateDialogState(
    val visible: Boolean = false,
    val error: String? = null,
    val submitting: Boolean = false,
)
