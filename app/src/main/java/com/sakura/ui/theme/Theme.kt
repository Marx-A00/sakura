package com.sakura.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider


private val SakuraDarkColorScheme = darkColorScheme(
    primary = Rose400,
    onPrimary = RoseOnPrimary,
    primaryContainer = Rose900,
    onPrimaryContainer = Rose200,
    secondary = Rose200,
    onSecondary = RoseOnPrimary,
    secondaryContainer = Rose900,
    onSecondaryContainer = Rose200,
    background = DarkBackground,
    onBackground = NearWhite,
    surface = DarkSurface,
    onSurface = NearWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = MediumGray,
)

private val SakuraLightColorScheme = lightColorScheme(
    primary = Rose400,
    onPrimary = WhiteCard,
    primaryContainer = PaleSakura,
    onPrimaryContainer = DeepRose,
    secondary = MutedSage,
    onSecondary = WhiteCard,
    secondaryContainer = PaleSakura,
    onSecondaryContainer = DeepRose,
    background = WarmCream,
    onBackground = LightOnBackground,
    surface = WarmCream,
    onSurface = LightOnBackground,
    surfaceVariant = CreamVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = WarmCream,
    surfaceContainerLow = WarmCream,
    surfaceContainerHigh = CreamVariant,
    surfaceContainerHighest = CreamVariant,
)

/**
 * Sakura app theme. Supports dark, light, and system-follow modes.
 * Provides [SakuraTheme.colors] via [LocalSakuraColors] for semantic color access.
 *
 * @param themeMode  one of "DARK", "LIGHT", or "SYSTEM"
 * @param palette    the active [SakuraPalette] (defaults to [ClassicPalette])
 */
@Composable
fun SakuraTheme(
    themeMode: String = "DARK",
    palette: SakuraPalette = ClassicPalette,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        "LIGHT" -> false
        "SYSTEM" -> isSystemInDarkTheme()
        else -> true
    }

    val sakuraColors = if (useDark) palette.dark else palette.light

    CompositionLocalProvider(LocalSakuraColors provides sakuraColors) {
        MaterialTheme(
            colorScheme = if (useDark) SakuraDarkColorScheme else SakuraLightColorScheme,
            typography = Typography,
            content = content
        )
    }
}
