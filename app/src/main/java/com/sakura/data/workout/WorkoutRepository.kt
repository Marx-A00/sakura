package com.sakura.data.workout

/**
 * Interface for workout data operations.
 * Implementations back this with workout-log.org via SyncBackend.
 * Mirrors the FoodRepository pattern from Phase 2.
 */
interface WorkoutRepository {

    /** Save a complete workout session atomically. */
    suspend fun saveSession(session: WorkoutSession): Result<Unit>

    /** Load a single session for a given date. Returns null if none exists. */
    suspend fun loadSession(date: java.time.LocalDate): WorkoutSession?

    /** Load all sessions, most recent first. */
    suspend fun loadHistory(): List<WorkoutSession>

    /**
     * Find the personal best for an exercise across all sessions.
     * Returns null if no history exists (first session for this exercise).
     * Uses normalized (lowercase trimmed) name for comparison.
     */
    suspend fun findPersonalBest(exerciseName: String): PersonalBest?

    /**
     * Load the most recent session for a given split day.
     * Used for auto-filling sets/reps/weight from previous session.
     * Returns null if no prior session exists for that split day.
     */
    suspend fun loadLastSessionForSplitDay(splitDay: SplitDay): WorkoutSession?
}
