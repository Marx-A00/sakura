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
 *
 * exerciseLogs: new per-set workout format (Phase 3, level 3+4 structure).
 * exercises: old flat format — kept for backward-compat parsing only.
 * splitDay, volume, durationMin, complete: workout session metadata from ** Workout property drawer.
 */
data class OrgDateSection(
    val date: LocalDate,
    val meals: List<OrgMealGroup>,                         // populated for food-log.org
    val exercises: List<OrgExerciseEntry>,                  // old flat format (backward compat)
    val exerciseLogs: List<OrgExerciseLog> = emptyList(),  // new per-set format (Phase 3)
    val splitDay: String? = null,                           // e.g., "monday-lift"
    val volume: Int? = null,                                // total session volume
    val durationMin: Int? = null,                           // session duration in minutes
    val complete: Boolean = false                           // soft "workout complete" flag
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
 * A single exercise log entry within a workout session (legacy flat format).
 * Weight is stored as Double to support fractional plates (e.g., 82.5 kg).
 * Kept for backward-compat parsing of old workout-log.org entries.
 */
data class OrgExerciseEntry(
    val name: String,
    val sets: Int,
    val reps: Int,
    val weight: Double,    // numeric weight value
    val unit: String = "kg" // weight unit — "kg" or "lbs"
)

/**
 * A single exercise log in the new per-set workout format (Phase 3).
 * Level-3 heading in workout-log.org, with level-4 Set headings as children.
 *
 * category: the ExerciseCategory label string (e.g., "weighted", "cardio") — stored as
 * :category: property in the org file alongside :exercise_type: for UI field selection.
 * Defaults to null for backward compat with existing org files that lack this property.
 */
data class OrgExerciseLog(
    val name: String,
    val id: Long,                          // epoch millis
    val exerciseType: String,              // "barbell", "dumbbell", "machine", "calisthenics", "timed", "cardio", "stretch"
    val sets: List<OrgSetEntry>,
    val category: String? = null           // ExerciseCategory label, e.g. "weighted", "cardio"
)

/**
 * A single set within an OrgExerciseLog. Level-4 heading in workout-log.org.
 *
 * durationMin and distanceKm are nullable cardio-only fields.
 * They are omitted from the org file when null (backward compat).
 */
data class OrgSetEntry(
    val setNumber: Int,                    // 1-indexed, matches heading "Set N"
    val reps: Int,
    val weight: Double,                    // 0.0 for bodyweight
    val unit: String,                      // "kg", "lbs", "bw"
    val holdSecs: Int = 0,                 // 0 for non-timed
    val rpe: Int? = null,                  // optional 6-10
    val isPr: Boolean = false,
    val durationMin: Int? = null,          // for cardio: duration in minutes
    val distanceKm: Double? = null         // for cardio: distance in km (optional)
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

// -------------------------------------------------------------------------
// User workout template containers
// -------------------------------------------------------------------------

/**
 * Top-level container for a parsed workout-templates.org file.
 * The file has a single "* Workout Templates" heading with templates as level-2 headings.
 */
data class OrgWorkoutTemplateFile(val templates: List<OrgWorkoutTemplate>)

/**
 * A single user workout template with a stable UUID id and a list of exercises.
 * Template exercises are level-3 headings under the level-2 template heading.
 */
data class OrgWorkoutTemplate(
    val id: String,
    val name: String,
    val exercises: List<OrgWorkoutTemplateExercise>
)

/**
 * An exercise entry within a user workout template.
 * Stores category label and muscle groups as org-serializable strings.
 */
data class OrgWorkoutTemplateExercise(
    val name: String,
    val categoryLabel: String,          // e.g., "weighted", "bodyweight"
    val muscleGroups: String,           // comma-separated, e.g., "Chest, Triceps"
    val targetSets: Int = 0,
    val targetReps: Int = 0,
    val targetHoldSecs: Int = 0
)
