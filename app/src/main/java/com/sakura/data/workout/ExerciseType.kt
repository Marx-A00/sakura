package com.sakura.data.workout

enum class ExerciseType(val label: String) {
    BARBELL("barbell"),
    DUMBBELL("dumbbell"),
    MACHINE("machine"),
    CALISTHENICS("calisthenics"),
    TIMED("timed");

    companion object {
        fun fromLabel(label: String): ExerciseType =
            entries.firstOrNull { it.label == label.lowercase() } ?: BARBELL
    }
}
