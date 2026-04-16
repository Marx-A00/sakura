package com.sakura.data.workout

/**
 * A single exercise entry in the exercise library.
 * Library exercises are what users browse and select when logging a workout.
 * isBuiltIn = false for user-created exercises.
 */
data class LibraryExercise(
    val name: String,
    val category: ExerciseCategory,
    val muscleGroups: List<String> = emptyList(),
    val isBuiltIn: Boolean = true,
    val restSecs: Int? = null
)

/**
 * The exercise library provides all available exercises (built-in + user-created).
 *
 * Built-in exercises are derived from WorkoutTemplates + common exercises covering
 * all categories visible in the exercise picker.
 *
 * User-created exercises are held in a mutable list and persist across restarts via
 * DataStore (JSON) — see OrgWorkoutRepository.saveUserExercises / loadUserExercises.
 *
 * Thread safety: addUserExercise and loadUserExercises are @Synchronized because
 * ExerciseLibrary is a singleton object with mutable state (_userExercises). These
 * methods may be called from background coroutines during startup or when the user
 * creates an exercise from the UI.
 */
object ExerciseLibrary {

    // Built-in exercises covering all categories visible in the exercise picker
    private val builtInExercises: List<LibraryExercise> = listOf(
        // Weighted
        LibraryExercise("Bench Press", ExerciseCategory.WEIGHTED, listOf("Chest", "Triceps")),
        LibraryExercise("Hack Squat", ExerciseCategory.WEIGHTED, listOf("Quads", "Glutes")),
        LibraryExercise("Front Squat", ExerciseCategory.WEIGHTED, listOf("Quads", "Core")),
        LibraryExercise("Barbell Row", ExerciseCategory.WEIGHTED, listOf("Back", "Biceps")),
        LibraryExercise("DB RDL", ExerciseCategory.WEIGHTED, listOf("Hamstrings", "Glutes")),
        LibraryExercise("Face Pulls", ExerciseCategory.WEIGHTED, listOf("Rear Delts", "Upper Back")),
        LibraryExercise("OHP", ExerciseCategory.WEIGHTED, listOf("Shoulders", "Triceps")),
        LibraryExercise("Goblet Squat", ExerciseCategory.WEIGHTED, listOf("Quads", "Core")),
        LibraryExercise("Bulgarian Split Squat", ExerciseCategory.WEIGHTED, listOf("Quads", "Glutes")),
        LibraryExercise("Lat Pulldown", ExerciseCategory.WEIGHTED, listOf("Back", "Biceps")),
        LibraryExercise("Hip Thrusts", ExerciseCategory.WEIGHTED, listOf("Glutes", "Hamstrings")),
        LibraryExercise("Curls", ExerciseCategory.WEIGHTED, listOf("Biceps")),
        LibraryExercise("Tricep Work", ExerciseCategory.WEIGHTED, listOf("Triceps")),
        LibraryExercise("Lateral Raises", ExerciseCategory.WEIGHTED, listOf("Shoulders")),
        // Bodyweight
        LibraryExercise("Pull-ups", ExerciseCategory.BODYWEIGHT, listOf("Back", "Biceps")),
        LibraryExercise("Pike Push-ups", ExerciseCategory.BODYWEIGHT, listOf("Shoulders", "Triceps")),
        LibraryExercise("Ring Rows", ExerciseCategory.BODYWEIGHT, listOf("Back", "Biceps")),
        LibraryExercise("Pistol Squat Progression", ExerciseCategory.BODYWEIGHT, listOf("Quads", "Balance")),
        LibraryExercise("Ring Dips", ExerciseCategory.BODYWEIGHT, listOf("Chest", "Triceps")),
        LibraryExercise("Rack Dips", ExerciseCategory.BODYWEIGHT, listOf("Chest", "Triceps")),
        LibraryExercise("Archer Push-ups", ExerciseCategory.BODYWEIGHT, listOf("Chest", "Triceps")),
        LibraryExercise("Diamond Push-ups", ExerciseCategory.BODYWEIGHT, listOf("Triceps", "Chest")),
        LibraryExercise("Single-leg Glute Bridges", ExerciseCategory.BODYWEIGHT, listOf("Glutes")),
        LibraryExercise("Single-leg RDL", ExerciseCategory.BODYWEIGHT, listOf("Hamstrings", "Balance")),
        LibraryExercise("Hanging Knee Raises", ExerciseCategory.BODYWEIGHT, listOf("Core")),
        LibraryExercise("Hanging Leg Raises", ExerciseCategory.BODYWEIGHT, listOf("Core")),
        // Timed
        LibraryExercise("Hollow Body Hold", ExerciseCategory.TIMED, listOf("Core")),
        LibraryExercise("Dead Hangs", ExerciseCategory.TIMED, listOf("Grip", "Shoulders")),
        LibraryExercise("L-sit Progression", ExerciseCategory.TIMED, listOf("Core", "Hip Flexors")),
        // Cardio
        LibraryExercise("Walking", ExerciseCategory.CARDIO, listOf("Cardio")),
        LibraryExercise("Running", ExerciseCategory.CARDIO, listOf("Cardio")),
        LibraryExercise("Cycling", ExerciseCategory.CARDIO, listOf("Cardio", "Legs")),
        // Stretch
        LibraryExercise("Stretching", ExerciseCategory.STRETCH, listOf("Full Body")),
        LibraryExercise("Foam Rolling", ExerciseCategory.STRETCH, listOf("Recovery")),
    )

    // User-created exercises — mutable, loaded from/saved to DataStore
    private val _userExercises = mutableListOf<LibraryExercise>()

    /** All exercises: built-in first, then user-created. */
    fun allExercises(): List<LibraryExercise> = builtInExercises + _userExercises

    /**
     * Search exercises by query and optional category filter.
     * Empty query matches all exercises in the given category (or all if category is null).
     */
    fun search(query: String, category: ExerciseCategory? = null): List<LibraryExercise> {
        val all = allExercises()
        return all.filter { ex ->
            (category == null || ex.category == category) &&
            (query.isBlank() || ex.name.contains(query, ignoreCase = true))
        }
    }

    /**
     * Add a user-created exercise to the library.
     * Synchronized because this may be called from background coroutines.
     * Always marks the exercise as user-created (isBuiltIn = false).
     */
    @Synchronized
    fun addUserExercise(exercise: LibraryExercise) {
        _userExercises.add(exercise.copy(isBuiltIn = false))
    }

    /**
     * Replace the user exercise list from persistent storage on startup.
     * Synchronized because this may run in a background coroutine while the
     * UI already holds a reference to ExerciseLibrary.
     */
    @Synchronized
    fun loadUserExercises(exercises: List<LibraryExercise>) {
        _userExercises.clear()
        _userExercises.addAll(exercises)
    }

    /** Remove a user-created exercise by name. No-op if not found or if built-in. */
    @Synchronized
    fun deleteUserExercise(name: String) {
        _userExercises.removeAll { it.name == name }
    }

    /** Replace a user-created exercise by old name. No-op if not found. */
    @Synchronized
    fun updateUserExercise(oldName: String, updated: LibraryExercise) {
        val index = _userExercises.indexOfFirst { it.name == oldName }
        if (index >= 0) {
            _userExercises[index] = updated.copy(isBuiltIn = false)
        }
    }

    /** Snapshot of user exercises (returns a copy to avoid mutation by caller). */
    fun userExercises(): List<LibraryExercise> = _userExercises.toList()
}
