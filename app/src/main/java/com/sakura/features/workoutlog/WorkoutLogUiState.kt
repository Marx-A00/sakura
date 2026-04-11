package com.sakura.features.workoutlog

import com.sakura.data.workout.ExerciseCategory
import com.sakura.data.workout.ExerciseLog
import com.sakura.data.workout.SetLog
import java.time.LocalDate

/**
 * Day-based UI state for the workout log tab.
 * Replaces the old session-based state (Ready/SessionUiState) with a single
 * DayLoaded state that shows exercises and logged sets inline.
 *
 * This mirrors FoodLogUiState.Success in concept — a "loaded day" model with
 * incremental writes instead of draft-then-save.
 */
sealed interface WorkoutLogUiState {
    data object Loading : WorkoutLogUiState

    data class DayLoaded(
        val date: LocalDate,
        val isToday: Boolean,
        val templateName: String?,                     // e.g., "Monday — Lift" if loaded from template
        val exercises: List<DayExercise>,              // exercises for this day
        val isComplete: Boolean,                       // soft complete flag
        val totalVolume: Double,                       // total volume in kg for the day (WORK-09)
        val previousSetsMap: Map<String, List<SetLog>> // exercise name -> previous sets (for auto-fill)
    ) : WorkoutLogUiState

    sealed interface Error : WorkoutLogUiState {
        data object FolderUnavailable : Error
        data class Generic(val message: String) : Error
    }
}

/**
 * An exercise entry for today's day view, wrapping the persisted ExerciseLog
 * with UI-facing metadata (template targets, previous sets, category).
 */
data class DayExercise(
    val exerciseLog: ExerciseLog,              // the persisted exercise with its sets
    val targetSets: Int?,                      // from template (null if freestyle)
    val targetReps: Int?,                      // from template
    val targetHoldSecs: Int?,                  // from template (timed exercises)
    val previousSets: List<SetLog>,            // from last session for auto-fill reference
    val category: ExerciseCategory             // determines which input fields to show
)

/**
 * PR notification displayed as a dismissible AlertDialog.
 * Emitted after addSet detects a new personal record.
 */
data class PrNotification(
    val exerciseName: String,
    val prType: String                         // "Weight", "Reps", or "Hold"
)
