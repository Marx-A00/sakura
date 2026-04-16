package com.sakura.data.workout

/**
 * A single exercise entry within a [UserWorkoutTemplate], carrying the exercise
 * identity (name, category, muscle groups) plus optional training targets.
 *
 * Targets default to 0 meaning "no target set". -1 for [targetReps] means max reps.
 */
data class TemplateExercise(
    val name: String,
    val category: ExerciseCategory,
    val muscleGroups: List<String> = emptyList(),
    val targetSets: Int = 0,
    val targetReps: Int = 0,
    val targetHoldSecs: Int = 0
)

/**
 * A user-created workout template — a named collection of exercises with targets
 * that can be browsed/managed in the Exercise Library and applied to a workout day.
 * Persisted to workout-templates.org.
 *
 * Distinct from [WorkoutTemplate] which is a hardcoded template tied to a [SplitDay].
 */
data class UserWorkoutTemplate(
    val id: String,                          // UUID string
    val name: String,                        // e.g., "Push Day"
    val exercises: List<TemplateExercise>    // exercises with targets
)

/** Convert a [LibraryExercise] to a [TemplateExercise] with optional targets. */
fun LibraryExercise.toTemplateExercise(
    targetSets: Int = 0,
    targetReps: Int = 0,
    targetHoldSecs: Int = 0
): TemplateExercise = TemplateExercise(
    name = name,
    category = category,
    muscleGroups = muscleGroups,
    targetSets = targetSets,
    targetReps = targetReps,
    targetHoldSecs = targetHoldSecs
)

/** Convert a [TemplateExercise] back to a [LibraryExercise] (drops target data). */
fun TemplateExercise.toLibraryExercise(): LibraryExercise = LibraryExercise(
    name = name,
    category = category,
    muscleGroups = muscleGroups,
    isBuiltIn = false
)
