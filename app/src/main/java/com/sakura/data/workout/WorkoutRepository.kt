package com.sakura.data.workout

import java.time.LocalDate

/**
 * Interface for workout data operations.
 * Implementations back this with workout-log.org via SyncBackend.
 * Mirrors the FoodRepository pattern from Phase 2.
 *
 * The incremental write methods (addExercise, addSet, etc.) replace the old
 * atomic saveSession pattern for the day-based UX. saveSession is kept for
 * backward compat and batch operations.
 */
interface WorkoutRepository {

    // -------------------------------------------------------------------------
    // Atomic session operations (legacy / batch)
    // -------------------------------------------------------------------------

    /** Save a complete workout session atomically. */
    suspend fun saveSession(session: WorkoutSession): Result<Unit>

    /** Load a single session for a given date. Returns null if none exists. */
    suspend fun loadSession(date: LocalDate): WorkoutSession?

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

    // -------------------------------------------------------------------------
    // Incremental write methods (day-based UX)
    // -------------------------------------------------------------------------

    /**
     * Add an exercise to a date's workout.
     * Creates the date section and "** Workout" heading if they don't exist.
     * Writes immediately to workout-log.org.
     */
    suspend fun addExercise(date: LocalDate, exercise: ExerciseLog): Result<Unit>

    /**
     * Remove an exercise and all its sets from a date's workout, by exercise id.
     * No-op if the exercise is not found.
     */
    suspend fun removeExercise(date: LocalDate, exerciseId: Long): Result<Unit>

    /**
     * Add a set to an existing exercise in a date's workout.
     * Writes immediately to workout-log.org (no draft/finish lifecycle).
     * Returns failure if the exercise is not found.
     */
    suspend fun addSet(date: LocalDate, exerciseId: Long, set: SetLog): Result<Unit>

    /**
     * Remove a set from an exercise by set number.
     * Renumbers remaining sets to stay 1-indexed.
     * Returns failure if the exercise or set is not found.
     */
    suspend fun removeSet(date: LocalDate, exerciseId: Long, setNumber: Int): Result<Unit>

    /**
     * Mark a day's workout as complete or incomplete (soft flag).
     * Writes the :complete: property on the ** Workout heading.
     */
    suspend fun markComplete(date: LocalDate, complete: Boolean): Result<Unit>

    /**
     * Load the previous session's sets for a specific exercise (by normalized name).
     * Returns empty list if no prior history.
     * Used for auto-fill in the set logging UI.
     */
    suspend fun loadPreviousSetsForExercise(exerciseName: String): List<SetLog>

    // -------------------------------------------------------------------------
    // User exercise library persistence
    // -------------------------------------------------------------------------

    /** Save the full user exercise list to persistent storage (DataStore JSON). */
    suspend fun saveUserExercises(exercises: List<LibraryExercise>)

    /** Load user exercises from persistent storage (DataStore JSON). */
    suspend fun loadUserExercises(): List<LibraryExercise>
}
