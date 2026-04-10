package com.sakura.orgengine

import com.sakura.data.food.FoodLibraryItem
import com.sakura.data.food.MealTemplate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Constants and format functions for the Sakura org file format.
 *
 * This object IS the format specification. OrgWriter and OrgParser both reference these
 * constants and functions exclusively — no format strings are duplicated elsewhere.
 *
 * Format overview (food-log.org):
 *   * <2026-04-09 Thu>         <- date heading (level 1)
 *   ** Breakfast               <- meal group heading (level 2)
 *   *** Chicken and rice       <- food item heading (level 3)
 *   :PROPERTIES:
 *   :id: 1712661600000
 *   :protein: 42
 *   :carbs: 55
 *   :fat: 8
 *   :calories: 460
 *   :END:
 *
 * Format overview (food-library.org):
 *   * Food Library
 *   ** Chicken Breast
 *   :PROPERTIES:
 *   :id: a1b2c3d4
 *   :protein: 31
 *   :carbs: 0
 *   :fat: 3
 *   :calories: 165
 *   :serving_size: 100
 *   :serving_unit: g
 *   :END:
 *
 * Format overview (meal-templates.org):
 *   * Meal Templates
 *   ** Weekday Breakfast
 *   :PROPERTIES:
 *   :id: e5f6g7h8
 *   :END:
 *   *** Oatmeal
 *   :PROPERTIES:
 *   :protein: 5
 *   :carbs: 27
 *   :fat: 3
 *   :calories: 150
 *   :serving_size: 40
 *   :serving_unit: g
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
    // Property key constants
    // -------------------------------------------------------------------------

    const val PROP_PROTEIN = "protein"
    const val PROP_CARBS = "carbs"
    const val PROP_FAT = "fat"
    const val PROP_CALORIES = "calories"
    const val PROP_SETS = "sets"
    const val PROP_REPS = "reps"
    const val PROP_WEIGHT = "weight"
    const val PROP_UNIT = "unit"
    const val PROP_ID = "id"
    const val PROP_SERVING_SIZE = "serving_size"
    const val PROP_SERVING_UNIT = "serving_unit"
    const val PROP_NOTES = "notes"

    // -------------------------------------------------------------------------
    // Food entries (food-log.org)
    // -------------------------------------------------------------------------

    /**
     * Serializes a food log entry to org property drawer format.
     * Always writes :id: (as epoch millis).
     * Only writes :serving_size:, :serving_unit:, :notes: when non-null.
     * Substitutes "Unnamed" for blank entry names.
     *
     * Example output:
     *   *** Chicken and rice
     *   :PROPERTIES:
     *   :id: 1712661600000
     *   :protein: 42
     *   :carbs: 55
     *   :fat: 8
     *   :calories: 460
     *   :END:
     */
    fun formatFoodEntry(entry: OrgFoodEntry): String {
        val name = if (entry.name.isBlank()) "Unnamed" else entry.name
        val sb = StringBuilder()
        sb.append("*** $name\n")
        sb.append("$PROPERTIES_START\n")
        sb.append(":$PROP_ID: ${entry.id}\n")
        sb.append(":$PROP_PROTEIN: ${entry.protein}\n")
        sb.append(":$PROP_CARBS: ${entry.carbs}\n")
        sb.append(":$PROP_FAT: ${entry.fat}\n")
        sb.append(":$PROP_CALORIES: ${entry.calories}\n")
        entry.servingSize?.let { sb.append(":$PROP_SERVING_SIZE: $it\n") }
        entry.servingUnit?.let { sb.append(":$PROP_SERVING_UNIT: $it\n") }
        entry.notes?.let { sb.append(":$PROP_NOTES: $it\n") }
        sb.append(PROPERTIES_END)
        return sb.toString()
    }

    // -------------------------------------------------------------------------
    // Food library (food-library.org)
    // -------------------------------------------------------------------------

    /** Level-1 heading for the food library file. */
    const val LIBRARY_HEADING = "* Food Library"

    /** Regex to match a level-2 library item heading. */
    val LIBRARY_ITEM_HEADING_REGEX = Regex("""^\*\* (.+)$""")

    /**
     * Serializes a library item to org level-2 heading + property drawer format.
     * Uses the library UUID as the :id: value.
     *
     * Example output:
     *   ** Chicken Breast
     *   :PROPERTIES:
     *   :id: a1b2c3d4
     *   :protein: 31
     *   :carbs: 0
     *   :fat: 3
     *   :calories: 165
     *   :serving_size: 100
     *   :serving_unit: g
     *   :END:
     */
    fun formatLibraryEntry(item: FoodLibraryItem): String {
        val sb = StringBuilder()
        sb.append("** ${item.name}\n")
        sb.append("$PROPERTIES_START\n")
        sb.append(":$PROP_ID: ${item.id}\n")
        sb.append(":$PROP_PROTEIN: ${item.protein}\n")
        sb.append(":$PROP_CARBS: ${item.carbs}\n")
        sb.append(":$PROP_FAT: ${item.fat}\n")
        sb.append(":$PROP_CALORIES: ${item.calories}\n")
        item.servingSize?.let { sb.append(":$PROP_SERVING_SIZE: $it\n") }
        item.servingUnit?.let { sb.append(":$PROP_SERVING_UNIT: $it\n") }
        sb.append(PROPERTIES_END)
        return sb.toString()
    }

    // -------------------------------------------------------------------------
    // Meal templates (meal-templates.org)
    // -------------------------------------------------------------------------

    /** Level-1 heading for the meal templates file. */
    const val TEMPLATES_HEADING = "* Meal Templates"

    /** Regex to match a level-2 template heading. */
    val TEMPLATE_HEADING_REGEX = Regex("""^\*\* (.+)$""")

    /** Regex to match a level-3 template item heading. */
    val TEMPLATE_ITEM_HEADING_REGEX = Regex("""^\*\*\* (.+)$""")

    /**
     * Serializes a template level-2 heading with its :id: property drawer.
     *
     * Example output:
     *   ** Weekday Breakfast
     *   :PROPERTIES:
     *   :id: e5f6g7h8
     *   :END:
     */
    fun formatTemplateHeading(template: MealTemplate): String =
        "** ${template.name}\n" +
        "$PROPERTIES_START\n" +
        ":$PROP_ID: ${template.id}\n" +
        PROPERTIES_END

    /**
     * Serializes a template item (FoodLibraryItem) to level-3 heading + property drawer.
     *
     * Example output:
     *   *** Oatmeal
     *   :PROPERTIES:
     *   :protein: 5
     *   :carbs: 27
     *   :fat: 3
     *   :calories: 150
     *   :serving_size: 40
     *   :serving_unit: g
     *   :END:
     */
    fun formatTemplateItem(item: FoodLibraryItem): String {
        val sb = StringBuilder()
        sb.append("*** ${item.name}\n")
        sb.append("$PROPERTIES_START\n")
        sb.append(":$PROP_PROTEIN: ${item.protein}\n")
        sb.append(":$PROP_CARBS: ${item.carbs}\n")
        sb.append(":$PROP_FAT: ${item.fat}\n")
        sb.append(":$PROP_CALORIES: ${item.calories}\n")
        item.servingSize?.let { sb.append(":$PROP_SERVING_SIZE: $it\n") }
        item.servingUnit?.let { sb.append(":$PROP_SERVING_UNIT: $it\n") }
        sb.append(PROPERTIES_END)
        return sb.toString()
    }

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
