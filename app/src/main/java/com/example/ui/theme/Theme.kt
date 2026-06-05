package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CobaltColorScheme = darkColorScheme(
    primary = CobaltSecondary,
    secondary = CobaltPrimary,
    tertiary = CobaltAccent,
    background = CobaltBackground,
    surface = CobaltSurface,
    onBackground = CobaltOnSurface,
    onSurface = CobaltOnSurface,
    primaryContainer = CobaltPrimary,
    onPrimaryContainer = Color.White
)

private val SophisticatedColorScheme = darkColorScheme(
    primary = SophisticatedPrimary,
    secondary = SophisticatedSecondary,
    tertiary = SophisticatedAccent,
    background = SophisticatedBackground,
    surface = SophisticatedSurface,
    onBackground = SophisticatedOnSurface,
    onSurface = SophisticatedOnSurface,
    primaryContainer = SophisticatedSurface,
    onPrimaryContainer = SophisticatedPrimary,
    outline = SophisticatedOutline
)

private val LuxuryColorScheme = darkColorScheme(
    primary = LuxuryPrimary,
    secondary = LuxurySecondary,
    tertiary = LuxuryAccent,
    background = LuxuryBackground,
    surface = LuxurySurface,
    onBackground = LuxuryOnSurface,
    onSurface = LuxuryOnSurface,
    primaryContainer = Color(0xFF27272A),
    onPrimaryContainer = LuxuryPrimary
)

private val CrimsonColorScheme = darkColorScheme(
    primary = CrimsonSecondary,
    secondary = CrimsonPrimary,
    tertiary = CrimsonAccent,
    background = CrimsonBackground,
    surface = CrimsonSurface,
    onBackground = CrimsonOnSurface,
    onSurface = CrimsonOnSurface,
    primaryContainer = CrimsonPrimary,
    onPrimaryContainer = Color.White
)

private val OliveColorScheme = darkColorScheme(
    primary = OliveSecondary,
    secondary = OlivePrimary,
    tertiary = OliveAccent,
    background = OliveBackground,
    surface = OliveSurface,
    onBackground = OliveOnSurface,
    onSurface = OliveOnSurface,
    primaryContainer = OlivePrimary,
    onPrimaryContainer = Color.White
)

@Composable
fun MyApplicationTheme(
    selectedTheme: String = "Sophisticated Dark",
    content: @Composable () -> Unit,
) {
    val colorScheme = when (selectedTheme) {
        "Sophisticated Dark" -> SophisticatedColorScheme
        "Luxury Dark" -> LuxuryColorScheme
        "Crimson Velvet" -> CrimsonColorScheme
        "Olive Grove" -> OliveColorScheme
        "Cobalt Blue" -> CobaltColorScheme
        else -> SophisticatedColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
