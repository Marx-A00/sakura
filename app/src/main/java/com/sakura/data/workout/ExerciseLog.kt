package com.sakura.data.workout

/**
 * A single exercise entry within a workout session, containing per-set data.
 * Provides computed metrics for volume, reps, and hold time.
 */
data class ExerciseLog(
    val id: Long,                   // epoch millis, unique per exercise per session
    val name: String,
    val exerciseType: ExerciseType,
    val sets: List<SetLog>
) {
    /** Total volume for weighted exercises: sum(weight * reps) across all sets. */
    val volume: Double get() = sets
        .filter { it.unit != "bw" }
        .sumOf { it.weight * it.reps }

    /** Total reps for calisthenics. */
    val totalReps: Int get() = sets.sumOf { it.reps }

    /** Total time under tension for timed holds. */
    val totalHoldSecs: Int get() = sets.sumOf { it.holdSecs }
}
