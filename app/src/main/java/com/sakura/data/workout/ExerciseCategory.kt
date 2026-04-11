package com.sakura.data.workout

/**
 * Extensible category system for exercises. Determines which input fields appear in the UI.
 *
 * Categories (not ExerciseType) drive UI behavior:
 *   WEIGHTED   -> weight + reps fields
 *   BODYWEIGHT -> reps field only
 *   TIMED      -> hold duration field
 *   CARDIO     -> duration + optional distance fields
 *   STRETCH    -> duration field only
 *
 * Note: We keep this as an enum since the categories are stable and determine UI behavior.
 * The "extensible" aspect is that exercises (not categories) are user-creatable.
 * If truly custom categories are needed later, this can be migrated to a sealed class.
 */
enum class ExerciseCategory(val label: String, val displayName: String) {
    WEIGHTED("weighted", "Weighted"),        // barbell, dumbbell, machine -> weight + reps
    BODYWEIGHT("bodyweight", "Bodyweight"),  // pull-ups, push-ups -> reps only
    TIMED("timed", "Timed"),                // planks, dead hangs -> hold duration
    CARDIO("cardio", "Cardio"),             // walking, running -> duration + optional distance
    STRETCH("stretch", "Stretch");          // stretching -> duration only

    companion object {
        fun fromLabel(label: String): ExerciseCategory =
            entries.firstOrNull { it.label == label.lowercase() } ?: WEIGHTED
    }
}
