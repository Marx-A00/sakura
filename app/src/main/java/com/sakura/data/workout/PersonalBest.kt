package com.sakura.data.workout

/**
 * The personal best for an exercise across all logged sessions.
 * Weight is 0.0 for bodyweight-only exercises.
 * HoldSecs is 0 for non-timed exercises.
 */
data class PersonalBest(
    val weight: Double,             // best weight lifted (0.0 if bw only)
    val reps: Int,                  // best reps achieved (for calisthenics or at a given weight)
    val holdSecs: Int               // best hold duration (for timed exercises)
)
