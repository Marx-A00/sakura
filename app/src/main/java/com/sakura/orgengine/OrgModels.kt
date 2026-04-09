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
 */
data class OrgFoodEntry(
    val name: String,
    val protein: Int,  // grams
    val carbs: Int,    // grams
    val fat: Int,      // grams
    val calories: Int  // kcal
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
