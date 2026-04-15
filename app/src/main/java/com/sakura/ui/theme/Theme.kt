package com.sakura.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable


private val SakuraDarkColorScheme = darkColorScheme(
    primary = Rose400,
    onPrimary = RoseOnPrimary,
    primaryContainer = Rose900,
    onPrimaryContainer = Rose200,
    secondary = Rose200,
    onSecondary = RoseOnPrimary,
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
    secondary = CherryBlossomPink,
    onSecondary = WhiteCard,
    background = WarmCream,
    onBackground = LightOnBackground,
    surface = WhiteCard,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = LightSurfaceVariant,
)

/**
 * Sakura app theme. Supports dark, light, and system-follow modes.
 *
 * @param themeMode one of "DARK", "LIGHT", or "SYSTEM"
 */
@Composable
fun SakuraTheme(
    themeMode: String = "DARK",
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        "LIGHT" -> false
        "SYSTEM" -> isSystemInDarkTheme()
        else -> true
    }

    MaterialTheme(
        colorScheme = if (useDark) SakuraDarkColorScheme else SakuraLightColorScheme,
        typography = Typography,
        content = content
    )
}
