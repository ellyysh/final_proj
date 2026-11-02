package com.pokejournal.domain.logic

fun interface TimeProvider {
    fun now(): Long
}

object RelativeTime {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    fun format(timestamp: Long?, now: Long): String {
        if (timestamp == null) return "никогда"
        val diff = (now - timestamp).coerceAtLeast(0)
        return when {
            diff < MINUTE -> "только что"
            diff < HOUR -> "${diff / MINUTE} мин назад"
            diff < DAY -> "${diff / HOUR} ч назад"
            else -> "${diff / DAY} дн назад"
        }
    }
}
