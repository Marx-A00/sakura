package com.sakura.features.progress

/**
 * UI state for the Progress screen.
 * Holds streak count and weekly consistency metrics.
 */
data class ProgressUiState(
    val isLoading: Boolean = true,
    /** Consecutive days (ending today or yesterday) with food logged. */
    val streakDays: Int = 0,
    /** Exactly 7 booleans: index 0 = 6 days ago, index 6 = today. true = food logged. */
    val last7DaysLogged: List<Boolean> = List(7) { false },
    /** Days with at least one food entry in the last 7 days. */
    val foodDaysCount: Int = 0,
    /** Days with at least one WorkoutSession in the last 7 days. */
    val workoutDaysCount: Int = 0,
    /** Days where all 4 macros >= 90% of targets in the last 7 days. */
    val macrosHitDaysCount: Int = 0
)
