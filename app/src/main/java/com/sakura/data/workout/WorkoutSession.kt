package com.sakura.data.workout

/**
 * A workout session for a given date, with optional split day assignment.
 * Contains the full set of exercises with per-set data.
 *
 * splitDay is nullable to support freestyle days (user logs without a template).
 * templateName is a display-only string — not persisted separately, derived from splitDay.
 * isComplete is the soft "Complete" flag that can be set without affecting the data.
 */
data class WorkoutSession(
    val date: java.time.LocalDate,
    val splitDay: SplitDay? = null,        // nullable: freestyle day has no split
    val templateName: String? = null,       // e.g., "Monday — Lift" (display only)
    val exercises: List<ExerciseLog>,
    val durationMin: Int = 0,              // session duration in minutes (optional)
    val isComplete: Boolean = false        // soft "Complete" flag
) {
    /** Total volume across all weighted exercises: sum(weight * reps). */
    val totalVolume: Double get() = exercises.sumOf { it.volume }
}
