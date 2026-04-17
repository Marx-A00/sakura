package com.sakura.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Blend [this] color toward [target] by [ratio] (0 = this, 1 = target).
 */
private fun Color.blend(target: Color, ratio: Float): Color = Color(
    red = red + (target.red - red) * ratio,
    green = green + (target.green - green) * ratio,
    blue = blue + (target.blue - blue) * ratio,
    alpha = 1f,
)

/**
 * Sakura app theme. Supports dark, light, and system-follow modes.
 * Provides [SakuraTheme.colors] via [LocalSakuraColors] for semantic color access.
 *
 * Material `primary` is wired to the user's palette accent so that all Material3
 * components (switches, chips, nav indicators, etc.) automatically use the chosen color.
 * The brand pink (CherryBlossomPink) is always available via [SakuraTheme.colors.brand].
 *
 * @param themeMode  one of "DARK", "LIGHT", or "SYSTEM"
 * @param palette    the active [SakuraPalette] (defaults to [ClassicPalette])
 */
@Composable
fun SakuraTheme(
    themeMode: String = "DARK",
    palette: SakuraPalette = SakuraPinkPalette,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        "LIGHT" -> false
        "SYSTEM" -> isSystemInDarkTheme()
        else -> true
    }

    val sakuraColors = if (useDark) palette.dark else palette.light
    val accent = sakuraColors.accent

    val colorScheme = remember(useDark, accent) {
        if (useDark) {
            darkColorScheme(
                primary = accent,
                onPrimary = NearWhite,
                primaryContainer = accent.blend(Color(0xFF1A1A1A), 0.7f),
                onPrimaryContainer = accent.blend(NearWhite, 0.4f),
                secondary = accent.blend(NearWhite, 0.5f),
                onSecondary = RoseOnPrimary,
                secondaryContainer = accent.blend(Color(0xFF1A1A1A), 0.7f),
                onSecondaryContainer = accent.blend(NearWhite, 0.4f),
                background = DarkBackground,
                onBackground = NearWhite,
                surface = DarkBackground,
                onSurface = NearWhite,
                surfaceVariant = DarkSurfaceVariant,
                onSurfaceVariant = MediumGray,
                surfaceContainer = DarkBackground,
                surfaceContainerLow = DarkBackground,
                surfaceContainerHigh = DarkSurfaceVariant,
                surfaceContainerHighest = DarkSurfaceVariant,
            )
        } else {
            lightColorScheme(
                primary = accent,
                onPrimary = WhiteCard,
                primaryContainer = accent.blend(Color.White, 0.8f),
                onPrimaryContainer = accent.blend(Color.Black, 0.3f),
                secondary = accent,
                onSecondary = WhiteCard,
                secondaryContainer = accent.blend(Color.White, 0.8f),
                onSecondaryContainer = accent.blend(Color.Black, 0.3f),
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
        }
    }

    CompositionLocalProvider(LocalSakuraColors provides sakuraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
