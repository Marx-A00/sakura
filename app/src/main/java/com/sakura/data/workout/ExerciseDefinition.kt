package com.sakura.data.workout

/**
 * Definition of an exercise within a workout template.
 *
 * category defaults to exerciseType.toCategory() for backward compat with
 * existing template definitions that only specify exerciseType.
 * muscleGroups drives the subtitle in the exercise picker.
 */
data class ExerciseDefinition(
    val name: String,                        // primary exercise name
    val alternatives: List<String> = emptyList(),  // alternative exercise names (user picks one per session)
    val targetSets: Int,
    val targetReps: Int,                     // -1 = "max reps" (calisthenics)
    val targetHoldSecs: Int = 0,             // >0 for timed holds
    val exerciseType: ExerciseType,
    val category: ExerciseCategory = exerciseType.toCategory(),
    val perSide: Boolean = false,            // true for unilateral exercises (e.g., "3x5/leg")
    val muscleGroups: List<String> = emptyList()  // e.g., ["Chest", "Triceps"] — for exercise picker subtitle
)
