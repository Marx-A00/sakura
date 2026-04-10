package com.sakura.orgengine

import java.time.LocalDate

/**
 * Top-level container for a parsed org log file (food-log.org or workout-log.org).
 */
data class OrgFile(
    val sections: List<OrgDateSection>
)

/**
 * A single date heading in an org log file.
 * A section contains either meals (food-log.org) or exercises (workout-log.org) — never both.
 * The fields that do not apply to a given file type will be empty lists.
 */
data class OrgDateSection(
    val date: LocalDate,
    val meals: List<OrgMealGroup>,        // populated for food-log.org
    val exercises: List<OrgExerciseEntry>  // populated for workout-log.org
)

/**
 * A meal grouping under a date section (e.g., Breakfast, Lunch, Dinner, Snacks).
 */
data class OrgMealGroup(
    val label: String,  // "Breakfast", "Lunch", "Dinner", "Snacks"
    val entries: List<OrgFoodEntry>
)

/**
 * A single food log entry with full macro breakdown.
 * All macro values are in grams; calories are kcal.
 *
 * New fields (id, servingSize, servingUnit, notes) have backward-compatible defaults
 * so existing test data (which does not include these fields) still compiles unchanged.
 *
 * id: epoch millis; 0L indicates a legacy entry without an assigned ID.
 */
data class OrgFoodEntry(
    val name: String,
    val protein: Int,           // grams
    val carbs: Int,             // grams
    val fat: Int,               // grams
    val calories: Int,          // kcal
    val id: Long = 0L,                  // epoch millis; 0 = legacy entry without ID
    val servingSize: String? = null,    // optional reference info, e.g., "200"
    val servingUnit: String? = null,    // optional unit, e.g., "g", "ml", "oz"
    val notes: String? = null           // optional free text
)

/**
 * A single exercise log entry within a workout session.
 * Weight is stored as Double to support fractional plates (e.g., 82.5 kg).
 */
data class OrgExerciseEntry(
    val name: String,
    val sets: Int,
    val reps: Int,
    val weight: Double,    // numeric weight value
    val unit: String = "kg" // weight unit — "kg" or "lbs"
)

// -------------------------------------------------------------------------
// Food library and template containers
// -------------------------------------------------------------------------

/**
 * A library item entry carrying a string UUID and macro data.
 * Separate from OrgFoodEntry because library items use UUID string ids (not epoch millis).
 */
data class OrgLibraryEntry(
    val id: String,             // UUID string for stable library identity
    val name: String,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val calories: Int,
    val servingSize: String? = null,
    val servingUnit: String? = null
)

/**
 * Top-level container for a parsed food-library.org file.
 * The file has a single "* Food Library" heading with items as level-2 headings.
 */
data class OrgLibraryFile(val items: List<OrgLibraryEntry>)

/**
 * Top-level container for a parsed meal-templates.org file.
 * The file has a single "* Meal Templates" heading with templates as level-2 headings.
 */
data class OrgTemplateFile(val templates: List<OrgMealTemplate>)

/**
 * A single meal template with a stable UUID id and a list of food items.
 * Template items are level-3 headings under the level-2 template heading.
 */
data class OrgMealTemplate(
    val id: String,                     // UUID string
    val name: String,                   // e.g., "Weekday Breakfast"
    val items: List<OrgLibraryEntry>    // items use the same OrgLibraryEntry model
)
