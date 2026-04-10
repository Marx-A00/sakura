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
 *   *** Chicken and rice       <- food item heading (level 3)
 *   :PROPERTIES:
 *   :protein: 42
 *   :carbs: 55
 *   :fat: 8
 *   :calories: 460
 *   :END:
 *   ** Workout                 <- exercise group heading (level 2, workout-log.org)
 *   *** Bench Press            <- exercise heading (level 3)
 *   :PROPERTIES:
 *   :sets: 3
 *   :reps: 5
 *   :weight: 80
 *   :unit: kg
 *   :END:
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
     * Example output: "** Workout"
     */
    fun formatExerciseGroupHeading(label: String): String = "** $label"

    // -------------------------------------------------------------------------
    // Property drawer parsing constants
    // -------------------------------------------------------------------------

    /** Opening line of an org property drawer. */
    const val PROPERTIES_START = ":PROPERTIES:"

    /** Closing line of an org property drawer. */
    const val PROPERTIES_END = ":END:"

    /** Regex to match a single property line inside a drawer: :key: value */
    val PROPERTY_REGEX = Regex("""^:(\w+):\s+(.+)$""")

    /** Regex to match and capture a level-3 item heading (food name or exercise name). */
    val ITEM_HEADING_REGEX = Regex("""^\*\*\* (.+)$""")

    // -------------------------------------------------------------------------
    // Food entries
    // -------------------------------------------------------------------------

    /**
     * Serializes a food entry to org property drawer format.
     *
     * Example output:
     *   *** Chicken and rice
     *   :PROPERTIES:
     *   :protein: 42
     *   :carbs: 55
     *   :fat: 8
     *   :calories: 460
     *   :END:
     *
     * Property drawers are a first-class org-mode structural element, natively
     * parseable by any org tool (org-element, org-ql, etc.).
     */
    fun formatFoodEntry(entry: OrgFoodEntry): String =
        "*** ${entry.name}\n" +
        "$PROPERTIES_START\n" +
        ":protein: ${entry.protein}\n" +
        ":carbs: ${entry.carbs}\n" +
        ":fat: ${entry.fat}\n" +
        ":calories: ${entry.calories}\n" +
        PROPERTIES_END

    // -------------------------------------------------------------------------
    // Exercise entries
    // -------------------------------------------------------------------------

    /**
     * Serializes an exercise entry to org property drawer format.
     *
     * Example output:
     *   *** Bench Press
     *   :PROPERTIES:
     *   :sets: 3
     *   :reps: 5
     *   :weight: 80
     *   :unit: kg
     *   :END:
     *
     * Weight formatting: integer weights display without decimal (80 not 80.0);
     * fractional weights display as-is (82.5).
     */
    fun formatExerciseEntry(entry: OrgExerciseEntry): String =
        "*** ${entry.name}\n" +
        "$PROPERTIES_START\n" +
        ":sets: ${entry.sets}\n" +
        ":reps: ${entry.reps}\n" +
        ":weight: ${formatWeight(entry.weight)}\n" +
        ":unit: ${entry.unit}\n" +
        PROPERTIES_END

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Formats a weight Double without a trailing ".0" for whole numbers.
     * 80.0 -> "80", 82.5 -> "82.5"
     */
    fun formatWeight(weight: Double): String =
        if (weight == weight.toLong().toDouble()) weight.toLong().toString()
        else weight.toString()
}
