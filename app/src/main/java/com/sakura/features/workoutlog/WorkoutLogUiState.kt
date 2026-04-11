package com.sakura.features.workoutlog

import com.sakura.data.workout.ExerciseDefinition
import com.sakura.data.workout.SetLog
import com.sakura.data.workout.SplitDay
import com.sakura.data.workout.WorkoutSession

sealed interface WorkoutLogUiState {
    data object Loading : WorkoutLogUiState
    data class Ready(
        val nextSplitDay: SplitDay?,          // suggested next workout day (null if no history)
        val lastSession: WorkoutSession?,      // most recent session for summary display
        val hasActiveSession: Boolean          // true if a session draft is in progress
    ) : WorkoutLogUiState
    sealed interface Error : WorkoutLogUiState {
        data object FolderUnavailable : Error
        data class Generic(val message: String) : Error
    }
}

sealed interface SessionUiState {
    data object Inactive : SessionUiState
    data class Active(
        val splitDay: SplitDay,
        val exercises: List<SessionExercise>,   // current exercise list with logged sets
        val restSecondsRemaining: Int,
        val prDetected: PrNotification?,        // non-null when a PR was just detected
        val startTimeMillis: Long               // session start time for duration calculation
    ) : SessionUiState
}

data class SessionExercise(
    val definition: ExerciseDefinition,
    val selectedAlternative: String?,           // non-null if user picked an alternative
    val loggedSets: List<SetLog>,
    val previousSets: List<SetLog>              // auto-filled from last session (empty if first time)
) {
    val displayName: String get() = selectedAlternative ?: definition.name
}

data class PrNotification(
    val exerciseName: String,
    val prType: String                          // "Weight", "Reps", or "Hold"
)
