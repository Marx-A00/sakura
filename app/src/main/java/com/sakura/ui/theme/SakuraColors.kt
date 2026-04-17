package com.sakura.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic color roles for the Sakura app.
 *
 * All composables should reference these roles (via [SakuraTheme.colors]) instead of
 * importing raw Color values. Each [SakuraPalette] provides dark and light variants
 * so colors render correctly in both theme modes.
 */
data class SakuraColors(
    // ── Brand ── always CherryBlossomPink, constant across palettes
    val brand: Color,
    val brandLight: Color,   // subtle highlights, badge/tag backgrounds
    val brandDark: Color,    // text on pink backgrounds, deep accents

    // ── Accent ── the customizable secondary color (varies by palette)
    val accent: Color,       // confirm buttons, positive indicators, completion states

    // ── Semantic ──
    val positive: Color,     // success, remaining budget — typically = accent
    val negative: Color,     // over-budget, error, warning

    // ── Nutrition data viz ──
    val proteinBar: Color,   // protein progress bars & labels
    val carbsBar: Color,     // carbohydrate bars & labels
    val fatBar: Color,       // fat bars & labels
    val calorieBar: Color,   // calorie ring/bar — typically = brand

    // ── Workout data viz ──
    val workoutRing: Color,  // workout consistency ring
    val trendLine: Color,    // chart trend/average lines

    // ── Calendar split day colors ──
    val splitA: Color,       // lift / push days
    val splitB: Color,       // calisthenics / pull days
    val splitC: Color,       // leg days
    val splitDefault: Color, // other / unmatched
)

/**
 * A named color palette with tuned dark and light variants.
 * Pink brand identity stays constant — only the accent family changes.
 */
data class SakuraPalette(
    val id: String,
    val displayName: String,
    val dark: SakuraColors,
    val light: SakuraColors,
)

/** CompositionLocal backing [SakuraTheme.colors]. */
val LocalSakuraColors = staticCompositionLocalOf { SakuraPinkPalette.dark }

/**
 * Convenience accessor for semantic colors within @Composable scope.
 *
 * Usage: `SakuraTheme.colors.accent`, `SakuraTheme.colors.accent`, etc.
 */
object SakuraTheme {
    val colors: SakuraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSakuraColors.current
}

// ─────────────────────────────────────────────────────────────────────────────
// Palette builder — most fields are derived from brand + accent, so presets
// only need to specify the accent for dark and light.
// ─────────────────────────────────────────────────────────────────────────────

/** Coral/orange used for carbs across all palettes. */
private val CarbsCoral = Color(0xFFE8735A)

fun sakuraColors(
    isDark: Boolean,
    accent: Color,
): SakuraColors = SakuraColors(
    brand = CherryBlossomPink,
    brandLight = if (isDark) Color(0xFF3D2433) else PaleSakura,
    brandDark = DeepRose,
    accent = accent,
    positive = accent,
    negative = DeepRose,
    proteinBar = accent,
    carbsBar = CarbsCoral,
    fatBar = WarmBrown,
    calorieBar = CherryBlossomPink,
    workoutRing = WorkoutBlue,
    trendLine = accent,
    splitA = CherryBlossomPink,
    splitB = accent,
    splitC = WarmBrown,
    splitDefault = DeepRose,
)

// ─────────────────────────────────────────────────────────────────────────────
// Preset palettes
// ─────────────────────────────────────────────────────────────────────────────

/** Sakura: brand cherry-blossom pink as accent — the default. */
val SakuraPinkPalette = SakuraPalette(
    id = "SAKURA",
    displayName = "Sakura",
    dark = sakuraColors(isDark = true, accent = CherryBlossomPink),
    light = sakuraColors(isDark = false, accent = CherryBlossomPink),
)

/** Classic: deep greens — the original Sakura look. */
val ClassicPalette = SakuraPalette(
    id = "CLASSIC",
    displayName = "Classic",
    dark = sakuraColors(isDark = true, accent = Color(0xFF5A8A66)),   // brighter green for dark bg
    light = sakuraColors(isDark = false, accent = ForestGreen),        // original ForestGreen
)

/** Sage: soft earthy greens — gentler than Classic, great on light backgrounds. */
val SagePalette = SakuraPalette(
    id = "SAGE",
    displayName = "Sage",
    dark = sakuraColors(isDark = true, accent = Color(0xFF96AD89)),   // lighter sage for dark bg
    light = sakuraColors(isDark = false, accent = MutedSage),          // MutedSage #7A8B6F
)

/** Amber: warm golden tones. */
val AmberPalette = SakuraPalette(
    id = "AMBER",
    displayName = "Amber",
    dark = sakuraColors(isDark = true, accent = Color(0xFFD4B87A)),   // bright gold
    light = sakuraColors(isDark = false, accent = Color(0xFFA88540)), // deeper amber
)

/** Mauve: dusty rose-purple — harmonizes with the pink brand. */
val MauvePalette = SakuraPalette(
    id = "MAUVE",
    displayName = "Mauve",
    dark = sakuraColors(isDark = true, accent = Color(0xFFB08999)),   // soft mauve
    light = sakuraColors(isDark = false, accent = Color(0xFF8E6B7F)), // deeper mauve
)

/** Terracotta: earthy warm orange. */
val TerracottaPalette = SakuraPalette(
    id = "TERRACOTTA",
    displayName = "Terracotta",
    dark = sakuraColors(isDark = true, accent = Color(0xFFD49B82)),   // warm terracotta
    light = sakuraColors(isDark = false, accent = Color(0xFFB07156)), // deeper
)

/** Slate: cool blue-gray — modern and neutral. */
val SlatePalette = SakuraPalette(
    id = "SLATE",
    displayName = "Slate",
    dark = sakuraColors(isDark = true, accent = Color(0xFF8BA0C0)),   // soft blue
    light = sakuraColors(isDark = false, accent = Color(0xFF5E7A9A)), // deeper blue
)

/** Plum: deep purple — rich and distinctive. */
val PlumPalette = SakuraPalette(
    id = "PLUM",
    displayName = "Plum",
    dark = sakuraColors(isDark = true, accent = Color(0xFF9B7BA0)),   // soft plum
    light = sakuraColors(isDark = false, accent = Color(0xFF7A5B7E)), // deeper plum
)

/** All available preset palettes, in display order. */
val AllPalettes: List<SakuraPalette> = listOf(
    SakuraPinkPalette,
    ClassicPalette,
    SagePalette,
    AmberPalette,
    MauvePalette,
    TerracottaPalette,
    SlatePalette,
    PlumPalette,
)

/** Look up a palette by its [SakuraPalette.id], falling back to Classic. */
fun paletteById(id: String): SakuraPalette =
    AllPalettes.find { it.id == id } ?: SakuraPinkPalette

/** Build a custom palette from a user-provided accent hex (e.g. "#7A8B6F"). */
fun customPalette(accentHex: String): SakuraPalette {
    val color = try {
        Color(android.graphics.Color.parseColor(accentHex))
    } catch (_: Exception) {
        MutedSage // safe fallback
    }
    // For custom, derive a brighter variant for dark mode
    val darkAccent = lightenColor(color, 0.2f)
    return SakuraPalette(
        id = "CUSTOM",
        displayName = "Custom",
        dark = sakuraColors(isDark = true, accent = darkAccent),
        light = sakuraColors(isDark = false, accent = color),
    )
}

/**
 * Naive lightening: blend toward white by [fraction].
 * Good enough for generating a dark-mode accent from a user-picked light-mode color.
 */
private fun lightenColor(color: Color, fraction: Float): Color = Color(
    red = color.red + (1f - color.red) * fraction,
    green = color.green + (1f - color.green) * fraction,
    blue = color.blue + (1f - color.blue) * fraction,
    alpha = color.alpha,
)
