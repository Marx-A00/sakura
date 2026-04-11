package com.sakura.data.workout

/**
 * A single logged set within an exercise.
 * Weight is 0.0 for bodyweight exercises.
 * Unit is "bw" for bodyweight, "kg" or "lbs" for weighted.
 */
data class SetLog(
    val setNumber: Int,             // 1-indexed
    val reps: Int,
    val weight: Double,             // 0.0 for bodyweight
    val unit: String,               // "kg", "lbs", "bw"
    val holdSecs: Int = 0,          // >0 for timed holds
    val rpe: Int? = null,           // optional RPE 6-10
    val isPr: Boolean = false
)
