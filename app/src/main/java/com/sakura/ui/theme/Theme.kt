package com.sakura.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

/**
 * Sakura app theme. Dark theme only for v1 — warm pink/rose identity on dark backgrounds.
 */
@Composable
fun SakuraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SakuraDarkColorScheme,
        typography = Typography,
        content = content
    )
}
