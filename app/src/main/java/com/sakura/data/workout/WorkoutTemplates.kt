package com.sakura.data.workout

/**
 * Hardcoded workout templates for all 4 split days.
 * Encodes the user's hybrid lifting + calisthenics program from 03-CONTEXT.md.
 * Templates are not user-editable in Phase 3.
 */
object WorkoutTemplates {

    val MONDAY_LIFT = WorkoutTemplate(
        splitDay = SplitDay.MONDAY_LIFT,
        exercises = listOf(
            ExerciseDefinition("Hack Squat", alternatives = listOf("Front Squat"), targetSets = 4, targetReps = 5, exerciseType = ExerciseType.MACHINE),
            ExerciseDefinition("Bench Press", targetSets = 4, targetReps = 5, exerciseType = ExerciseType.BARBELL),
            ExerciseDefinition("Barbell Row", alternatives = listOf("Lat Pulldown"), targetSets = 3, targetReps = 8, exerciseType = ExerciseType.BARBELL),
            ExerciseDefinition("DB RDL", targetSets = 3, targetReps = 8, exerciseType = ExerciseType.DUMBBELL),
            ExerciseDefinition("Face Pulls", targetSets = 3, targetReps = 12, exerciseType = ExerciseType.MACHINE)
        )
    )

    val TUESDAY_CALISTHENICS = WorkoutTemplate(
        splitDay = SplitDay.TUESDAY_CALISTHENICS,
        exercises = listOf(
            ExerciseDefinition("Pull-ups", targetSets = 3, targetReps = -1, exerciseType = ExerciseType.CALISTHENICS),
            ExerciseDefinition("Pike Push-ups", targetSets = 3, targetReps = 10, exerciseType = ExerciseType.CALISTHENICS),
            ExerciseDefinition("Ring Rows", targetSets = 3, targetReps = 12, exerciseType = ExerciseType.CALISTHENICS),
            ExerciseDefinition("Pistol Squat Progression", targetSets = 3, targetReps = 5, exerciseType = ExerciseType.CALISTHENICS, perSide = true),
            ExerciseDefinition("Hollow Body Hold", targetSets = 3, targetReps = 1, targetHoldSecs = 25, exerciseType = ExerciseType.TIMED),
            ExerciseDefinition("Dead Hangs", targetSets = 2, targetReps = 1, targetHoldSecs = 37, exerciseType = ExerciseType.TIMED)
        )
    )

    val THURSDAY_LIFT = WorkoutTemplate(
        splitDay = SplitDay.THURSDAY_LIFT,
        exercises = listOf(
            ExerciseDefinition("OHP", targetSets = 4, targetReps = 6, exerciseType = ExerciseType.BARBELL),
            ExerciseDefinition("Goblet Squat", alternatives = listOf("Bulgarian Split Squat"), targetSets = 3, targetReps = 8, exerciseType = ExerciseType.DUMBBELL),
            ExerciseDefinition("Lat Pulldown", targetSets = 3, targetReps = 8, exerciseType = ExerciseType.MACHINE),
            ExerciseDefinition("Hip Thrusts", targetSets = 3, targetReps = 10, exerciseType = ExerciseType.BARBELL),
            ExerciseDefinition("Curls", alternatives = listOf("Tricep Work"), targetSets = 3, targetReps = 10, exerciseType = ExerciseType.DUMBBELL),
            ExerciseDefinition("Lateral Raises", targetSets = 3, targetReps = 12, exerciseType = ExerciseType.DUMBBELL)
        )
    )

    val FRIDAY_CALISTHENICS = WorkoutTemplate(
        splitDay = SplitDay.FRIDAY_CALISTHENICS,
        exercises = listOf(
            ExerciseDefinition("Ring Dips", alternatives = listOf("Rack Dips"), targetSets = 3, targetReps = -1, exerciseType = ExerciseType.CALISTHENICS),
            ExerciseDefinition("Archer Push-ups", alternatives = listOf("Diamond Push-ups"), targetSets = 3, targetReps = 10, exerciseType = ExerciseType.CALISTHENICS),
            ExerciseDefinition("Single-leg Glute Bridges", targetSets = 3, targetReps = 12, exerciseType = ExerciseType.CALISTHENICS, perSide = true),
            ExerciseDefinition("L-sit Progression", targetSets = 3, targetReps = 1, targetHoldSecs = 15, exerciseType = ExerciseType.TIMED),
            ExerciseDefinition("Single-leg RDL", targetSets = 3, targetReps = 8, exerciseType = ExerciseType.CALISTHENICS, perSide = true),
            ExerciseDefinition("Hanging Knee Raises", alternatives = listOf("Hanging Leg Raises"), targetSets = 3, targetReps = 10, exerciseType = ExerciseType.CALISTHENICS)
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
