package com.pokejournal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pokejournal.domain.model.ThemeMode

private val PokeRed = Color(0xFFD7263D)
private val DeepRed = Color(0xFF8E1020)
private val Ink = Color(0xFF1B1B21)

private val LightColors = lightColorScheme(
    primary = PokeRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD8),
    onPrimaryContainer = Color(0xFF410006),
    secondary = Color(0xFF2E5AAC),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE2FF),
    onSecondaryContainer = Color(0xFF001946),
    tertiary = Color(0xFFB8860B),
    background = Color(0xFFFFF8F7),
    onBackground = Ink,
    surface = Color(0xFFFFF8F7),
    onSurface = Ink,
    surfaceVariant = Color(0xFFF4DDDB),
    onSurfaceVariant = Color(0xFF534342),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB3AF),
    onPrimary = Color(0xFF68000F),
    primaryContainer = DeepRed,
    onPrimaryContainer = Color(0xFFFFDAD8),
    secondary = Color(0xFFB1C5FF),
    onSecondary = Color(0xFF002C70),
    secondaryContainer = Color(0xFF15418F),
    onSecondaryContainer = Color(0xFFDAE2FF),
    tertiary = Color(0xFFF1C050),
    background = Color(0xFF151213),
    onBackground = Color(0xFFEDE0DE),
    surface = Color(0xFF151213),
    onSurface = Color(0xFFEDE0DE),
    surfaceVariant = Color(0xFF534342),
    onSurfaceVariant = Color(0xFFD8C2BF),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

@Composable
fun PokeJournalTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

/** Фирменные цвета типов покемонов. */
object TypeColors {
    private val colors = mapOf(
        "normal" to Color(0xFFA8A77A),
        "fire" to Color(0xFFEE8130),
        "water" to Color(0xFF6390F0),
        "grass" to Color(0xFF7AC74C),
        "electric" to Color(0xFFE5B800),
        "ice" to Color(0xFF66C8C2),
        "fighting" to Color(0xFFC22E28),
        "poison" to Color(0xFFA33EA1),
        "ground" to Color(0xFFD0A84E),
        "flying" to Color(0xFF8F7CE6),
        "psychic" to Color(0xFFF95587),
        "bug" to Color(0xFF94A31A),
        "rock" to Color(0xFFB6A136),
        "ghost" to Color(0xFF735797),
        "dragon" to Color(0xFF6F35FC),
        "dark" to Color(0xFF705746),
        "steel" to Color(0xFF8E8EA8),
        "fairy" to Color(0xFFD685AD),
    )

    fun of(type: String): Color = colors[type] ?: Color(0xFF9E9E9E)
}
