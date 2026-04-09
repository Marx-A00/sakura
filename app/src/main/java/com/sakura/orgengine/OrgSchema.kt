package com.sakura.orgengine

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Constants and format functions for the Sakura org file format.
 *
 * This object IS the format specification. OrgWriter and OrgParser both reference these
 * constants and functions exclusively — no format strings are duplicated elsewhere.
 *
 * Format overview:
 *   * <2026-04-09 Thu>         <- date heading (level 1)
 *   ** Breakfast               <- meal group heading (level 2, food-log.org)
 *   - Chicken and rice  |P: 42g  C: 55g  F: 8g  Cal: 460|  <- food entry
 *   ** Push Day                <- exercise group heading (level 2, workout-log.org)
 *   - Bench Press  |3x8  80kg|  <- exercise entry
 */
object OrgSchema {

    // -------------------------------------------------------------------------
    // Date heading
    // -------------------------------------------------------------------------

    /**
     * Formats date headings as: yyyy-MM-dd EEE
     * Example: "2026-04-09 Thu"
     *
     * IMPORTANT: Locale.ENGLISH is mandatory. Without it, day-of-week abbreviations
     * use the device locale — e.g., Portuguese produces "Qui" instead of "Thu",
     * which Emacs either ignores silently or fails to parse.
     * See Phase 1 research Pitfall 2.
     */
    val ORG_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd EEE", Locale.ENGLISH)

    /**
     * Formats a LocalDate into an org level-1 date heading.
     * Example output: "* <2026-04-09 Thu>"
     */
    fun formatDateHeading(date: LocalDate): String =
        "* <${date.format(ORG_DATE_FORMATTER)}>"

    /** Regex to match and capture the date part of a level-1 date heading. */
    val DATE_HEADING_REGEX = Regex("""^\* <(\d{4}-\d{2}-\d{2}) \w{3}>$""")

    /** Formatter for parsing the ISO date portion back to LocalDate. */
    val DATE_PARSE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // -------------------------------------------------------------------------
    // Meal group headings (food-log.org)
    // -------------------------------------------------------------------------

    /** Canonical meal labels in display order. */
    val MEAL_LABELS = listOf("Breakfast", "Lunch", "Dinner", "Snacks")

    /**
     * Formats a meal label into an org level-2 heading.
     * Example output: "** Breakfast"
     */
    fun formatMealHeading(label: String): String = "** $label"

    /** Regex to match and capture a level-2 meal group heading. */
    val MEAL_HEADING_REGEX = Regex("""^\*\* (\w+)$""")

    // -------------------------------------------------------------------------
    // Exercise group headings (workout-log.org)
    // -------------------------------------------------------------------------

    /**
     * Formats an exercise group label into an org level-2 heading.
     * Example output: "** Push Day"
     */
    fun formatExerciseGroupHeading(label: String): String = "** $label"

    // -------------------------------------------------------------------------
    // Food entries
    // -------------------------------------------------------------------------

    /**
     * Serializes a food entry to org list item format.
     * Example output: "- Chicken and rice  |P: 42g  C: 55g  F: 8g  Cal: 460|"
     *
     * Format rationale: inline pipe notation passes org-lint (plain text in list items
     * is valid org), is compact, human-readable in Emacs, and avoids heavier property
     * drawers. Double-space + pipe delimiter is unambiguous for regex parsing.
     */
    fun formatFoodEntry(entry: OrgFoodEntry): String =
        "- ${entry.name}  |P: ${entry.protein}g  C: ${entry.carbs}g  F: ${entry.fat}g  Cal: ${entry.calories}|"

    /**
     * Regex to parse a food entry list item back into its component fields.
     * Matches the exact format produced by [formatFoodEntry].
     */
    val FOOD_ENTRY_REGEX = Regex(
        """^- (.+?)\s{2}\|P: (\d+)g\s{2}C: (\d+)g\s{2}F: (\d+)g\s{2}Cal: (\d+)\|$"""
    )

    // -------------------------------------------------------------------------
    // Exercise entries
    // -------------------------------------------------------------------------

    /**
     * Serializes an exercise entry to org list item format.
     * Example output: "- Bench Press  |3x8  80kg|"
     *
     * Weight formatting: integer weights display without decimal (80kg not 80.0kg);
     * fractional weights display as-is (82.5kg).
     */
    fun formatExerciseEntry(entry: OrgExerciseEntry): String =
        "- ${entry.name}  |${entry.sets}x${entry.reps}  ${formatWeight(entry.weight)}${entry.unit}|"

    /**
     * Regex to parse an exercise entry list item back into its component fields.
     * Captures: name, sets, reps, weight (numeric), unit (kg or lbs).
     * Matches the exact format produced by [formatExerciseEntry].
     */
    val EXERCISE_ENTRY_REGEX = Regex(
        """^- (.+?)\s{2}\|(\d+)x(\d+)\s{2}([\d.]+)(kg|lbs)\|$"""
    )

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Formats a weight Double without a trailing ".0" for whole numbers.
     * 80.0 -> "80", 82.5 -> "82.5"
     */
    private fun formatWeight(weight: Double): String =
        if (weight == weight.toLong().toDouble()) weight.toLong().toString()
        else weight.toString()
}
