package com.sakura.data.workout

data class ExerciseDefinition(
    val name: String,                        // primary exercise name
    val alternatives: List<String> = emptyList(),  // alternative exercise names (user picks one per session)
    val targetSets: Int,
    val targetReps: Int,                     // -1 = "max reps" (calisthenics)
    val targetHoldSecs: Int = 0,             // >0 for timed holds
    val exerciseType: ExerciseType,
    val perSide: Boolean = false             // true for unilateral exercises (e.g., "3x5/leg")
)
