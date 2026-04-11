package com.sakura.data.workout

/**
 * A complete workout session for a given date and split day.
 * Contains the full set of exercises with per-set data.
 */
data class WorkoutSession(
    val date: java.time.LocalDate,
    val splitDay: SplitDay,
    val exercises: List<ExerciseLog>,
    val durationMin: Int = 0        // session duration in minutes (optional)
) {
    /** Total volume across all weighted exercises. */
    val totalVolume: Double get() = exercises.sumOf { it.volume }
}
