package com.sakura.data.workout

/**
 * Hardcoded workout templates for all 4 split days.
 * Encodes the user's hybrid lifting + calisthenics program from 03-CONTEXT.md.
 * Templates are not user-editable in Phase 3.
 *
 * category defaults to exerciseType.toCategory() for all definitions — explicitly
 * omitted here to keep the template definitions concise. muscleGroups are included
 * to drive exercise picker subtitles in the UI (03-03).
 */
object WorkoutTemplates {

    val MONDAY_LIFT = WorkoutTemplate(
        splitDay = SplitDay.MONDAY_LIFT,
        exercises = listOf(
            ExerciseDefinition("Hack Squat", alternatives = listOf("Front Squat"), targetSets = 4, targetReps = 5, exerciseType = ExerciseType.MACHINE, muscleGroups = listOf("Quads", "Glutes")),
            ExerciseDefinition("Bench Press", targetSets = 4, targetReps = 5, exerciseType = ExerciseType.BARBELL, muscleGroups = listOf("Chest", "Triceps")),
            ExerciseDefinition("Barbell Row", alternatives = listOf("Lat Pulldown"), targetSets = 3, targetReps = 8, exerciseType = ExerciseType.BARBELL, muscleGroups = listOf("Back", "Biceps")),
            ExerciseDefinition("DB RDL", targetSets = 3, targetReps = 8, exerciseType = ExerciseType.DUMBBELL, muscleGroups = listOf("Hamstrings", "Glutes")),
            ExerciseDefinition("Face Pulls", targetSets = 3, targetReps = 12, exerciseType = ExerciseType.MACHINE, muscleGroups = listOf("Rear Delts", "Upper Back"))
        )
    )

    val TUESDAY_CALISTHENICS = WorkoutTemplate(
        splitDay = SplitDay.TUESDAY_CALISTHENICS,
        exercises = listOf(
            ExerciseDefinition("Pull-ups", targetSets = 3, targetReps = -1, exerciseType = ExerciseType.CALISTHENICS, muscleGroups = listOf("Back", "Biceps")),
            ExerciseDefinition("Pike Push-ups", targetSets = 3, targetReps = 10, exerciseType = ExerciseType.CALISTHENICS, muscleGroups = listOf("Shoulders", "Triceps")),
            ExerciseDefinition("Ring Rows", targetSets = 3, targetReps = 12, exerciseType = ExerciseType.CALISTHENICS, muscleGroups = listOf("Back", "Biceps")),
            ExerciseDefinition("Pistol Squat Progression", targetSets = 3, targetReps = 5, exerciseType = ExerciseType.CALISTHENICS, perSide = true, muscleGroups = listOf("Quads", "Balance")),
            ExerciseDefinition("Hollow Body Hold", targetSets = 3, targetReps = 1, targetHoldSecs = 25, exerciseType = ExerciseType.TIMED, muscleGroups = listOf("Core")),
            ExerciseDefinition("Dead Hangs", targetSets = 2, targetReps = 1, targetHoldSecs = 37, exerciseType = ExerciseType.TIMED, muscleGroups = listOf("Grip", "Shoulders"))
        )
    )

    val THURSDAY_LIFT = WorkoutTemplate(
        splitDay = SplitDay.THURSDAY_LIFT,
        exercises = listOf(
            ExerciseDefinition("OHP", targetSets = 4, targetReps = 6, exerciseType = ExerciseType.BARBELL, muscleGroups = listOf("Shoulders", "Triceps")),
            ExerciseDefinition("Goblet Squat", alternatives = listOf("Bulgarian Split Squat"), targetSets = 3, targetReps = 8, exerciseType = ExerciseType.DUMBBELL, muscleGroups = listOf("Quads", "Core")),
            ExerciseDefinition("Lat Pulldown", targetSets = 3, targetReps = 8, exerciseType = ExerciseType.MACHINE, muscleGroups = listOf("Back", "Biceps")),
            ExerciseDefinition("Hip Thrusts", targetSets = 3, targetReps = 10, exerciseType = ExerciseType.BARBELL, muscleGroups = listOf("Glutes", "Hamstrings")),
            ExerciseDefinition("Curls", alternatives = listOf("Tricep Work"), targetSets = 3, targetReps = 10, exerciseType = ExerciseType.DUMBBELL, muscleGroups = listOf("Biceps")),
            ExerciseDefinition("Lateral Raises", targetSets = 3, targetReps = 12, exerciseType = ExerciseType.DUMBBELL, muscleGroups = listOf("Shoulders"))
        )
    )

    val FRIDAY_CALISTHENICS = WorkoutTemplate(
        splitDay = SplitDay.FRIDAY_CALISTHENICS,
        exercises = listOf(
            ExerciseDefinition("Ring Dips", alternatives = listOf("Rack Dips"), targetSets = 3, targetReps = -1, exerciseType = ExerciseType.CALISTHENICS, muscleGroups = listOf("Chest", "Triceps")),
            ExerciseDefinition("Archer Push-ups", alternatives = listOf("Diamond Push-ups"), targetSets = 3, targetReps = 10, exerciseType = ExerciseType.CALISTHENICS, muscleGroups = listOf("Chest", "Triceps")),
            ExerciseDefinition("Single-leg Glute Bridges", targetSets = 3, targetReps = 12, exerciseType = ExerciseType.CALISTHENICS, perSide = true, muscleGroups = listOf("Glutes")),
            ExerciseDefinition("L-sit Progression", targetSets = 3, targetReps = 1, targetHoldSecs = 15, exerciseType = ExerciseType.TIMED, muscleGroups = listOf("Core", "Hip Flexors")),
            ExerciseDefinition("Single-leg RDL", targetSets = 3, targetReps = 8, exerciseType = ExerciseType.CALISTHENICS, perSide = true, muscleGroups = listOf("Hamstrings", "Balance")),
            ExerciseDefinition("Hanging Knee Raises", alternatives = listOf("Hanging Leg Raises"), targetSets = 3, targetReps = 10, exerciseType = ExerciseType.CALISTHENICS, muscleGroups = listOf("Core"))
        )
    )

    /** All templates indexed by split day for easy lookup. */
    val ALL = mapOf(
        SplitDay.MONDAY_LIFT to MONDAY_LIFT,
        SplitDay.TUESDAY_CALISTHENICS to TUESDAY_CALISTHENICS,
        SplitDay.THURSDAY_LIFT to THURSDAY_LIFT,
        SplitDay.FRIDAY_CALISTHENICS to FRIDAY_CALISTHENICS
    )

    /** Get template for a split day. */
    fun forDay(day: SplitDay): WorkoutTemplate = ALL.getValue(day)
}
