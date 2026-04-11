package com.sakura.features.workoutlog

import com.sakura.data.workout.ExerciseDefinition
import com.sakura.data.workout.ExerciseLog
import com.sakura.data.workout.ExerciseType
import com.sakura.data.workout.SetLog
import com.sakura.data.workout.SplitDay
import com.sakura.data.workout.WorkoutSession
import java.time.LocalDate

/**
 * In-memory mutable session draft that lives in the ViewModel.
 * NOT written to org until finishSession() is called.
 */
data class SessionDraft(
    val splitDay: SplitDay,
    val exercises: MutableList<DraftExercise>,
    val startTimeMillis: Long = System.currentTimeMillis()
) {
    fun addSet(exerciseIndex: Int, set: SetLog): SessionDraft {
        val exercise = exercises[exerciseIndex]
        val updatedSets = exercise.loggedSets + set
        exercises[exerciseIndex] = exercise.copy(loggedSets = updatedSets)
        return this.copy()  // triggers StateFlow emission
    }

    fun selectAlternative(exerciseIndex: Int, alternativeName: String): SessionDraft {
        exercises[exerciseIndex] = exercises[exerciseIndex].copy(selectedAlternative = alternativeName)
        return this.copy()
    }

    fun toWorkoutSession(date: LocalDate): WorkoutSession {
        val durationMin = ((System.currentTimeMillis() - startTimeMillis) / 60_000).toInt()
        return WorkoutSession(
            date = date,
            splitDay = splitDay,
            exercises = exercises.map { it.toExerciseLog() },
            durationMin = durationMin
        )
    }
}

data class DraftExercise(
    val definition: ExerciseDefinition,
    val selectedAlternative: String? = null,
    val loggedSets: List<SetLog> = emptyList(),
    val previousSets: List<SetLog> = emptyList()
) {
    val displayName: String get() = selectedAlternative ?: definition.name

    fun toExerciseLog(): ExerciseLog = ExerciseLog(
        id = System.currentTimeMillis(),
        name = displayName,
        exerciseType = definition.exerciseType,
        sets = loggedSets
    )
}
