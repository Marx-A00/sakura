package com.sakura.data.workout

/**
 * Equipment/modality type for an exercise.
 * Stored as a string label in workout-log.org (:exercise_type: property).
 *
 * CARDIO and STRETCH were added in Phase 3 plan 02 to support those categories.
 * BARBELL/DUMBBELL/MACHINE/CALISTHENICS/TIMED retained for backward compat with
 * existing org files.
 */
enum class ExerciseType(val label: String) {
    BARBELL("barbell"),
    DUMBBELL("dumbbell"),
    MACHINE("machine"),
    CALISTHENICS("calisthenics"),
    TIMED("timed"),
    CARDIO("cardio"),
    STRETCH("stretch");

    companion object {
        fun fromLabel(label: String): ExerciseType =
            entries.firstOrNull { it.label == label.lowercase() } ?: BARBELL
    }
}

/**
 * Maps ExerciseType to its corresponding ExerciseCategory for UI field selection.
 * BARBELL/DUMBBELL/MACHINE -> WEIGHTED (weight + reps)
 * CALISTHENICS             -> BODYWEIGHT (reps only)
 * TIMED                    -> TIMED (hold duration)
 * CARDIO                   -> CARDIO (duration + optional distance)
 * STRETCH                  -> STRETCH (duration only)
 */
fun ExerciseType.toCategory(): ExerciseCategory = when (this) {
    ExerciseType.BARBELL, ExerciseType.DUMBBELL, ExerciseType.MACHINE -> ExerciseCategory.WEIGHTED
    ExerciseType.CALISTHENICS -> ExerciseCategory.BODYWEIGHT
    ExerciseType.TIMED -> ExerciseCategory.TIMED
    ExerciseType.CARDIO -> ExerciseCategory.CARDIO
    ExerciseType.STRETCH -> ExerciseCategory.STRETCH
}
