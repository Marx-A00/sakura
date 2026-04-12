package com.sakura.features.dashboard

import com.sakura.data.workout.ExerciseLog
import com.sakura.data.workout.SplitDay
import com.sakura.sync.SyncStatus
import java.time.LocalDate

/**
 * UI state for the today/home dashboard screen.
 * Aggregates food macro progress, workout summary, and sync status for the current day.
 */
data class DashboardTodayState(
    val isLoading: Boolean = true,
    // Food macros
    val totalCalories: Int = 0,
    val totalProtein: Int = 0,
    val totalCarbs: Int = 0,
    val totalFat: Int = 0,
    val targetCalories: Int = 2000,
    val targetProtein: Int = 150,
    val targetCarbs: Int = 250,
    val targetFat: Int = 65,
    // Recent food days for sparkline
    val recentFoodDays: List<RecentDay> = emptyList(),
    // Workout summary
    val splitDay: SplitDay? = null,
    val templateName: String? = null,
    val exercises: List<ExerciseLog> = emptyList(),
    val isWorkoutComplete: Boolean = false,
    val hasWorkout: Boolean = false,
    val recentWorkoutDays: List<RecentWorkoutDay> = emptyList(),
    // Sync status
    val syncStatus: SyncStatus = SyncStatus(null, false, false)
)

/** A single day's calorie total for the recent history row. */
data class RecentDay(
    val date: LocalDate,
    val totalCalories: Int
)

/** A single workout day summary for the recent history row. */
data class RecentWorkoutDay(
    val date: LocalDate,
    val splitName: String?,
    val isComplete: Boolean
)

// -------------------------------------------------------------------------
// Weekly analytics state (stub — Plan 02 will populate)
// -------------------------------------------------------------------------

/**
 * UI state for the weekly analytics charts (Plan 02).
 * Stub until Plan 02 implements chart data loading.
 */
data class WeeklyAnalyticsState(
    val isLoading: Boolean = true,
    val macroData: List<DailyMacros> = emptyList(),
    val volumeData: List<DailyVolume> = emptyList(),
    val selectedWeeks: Int = 1
)

/** Daily macro totals for the food chart. */
data class DailyMacros(
    val date: LocalDate,
    val protein: Int,
    val carbs: Int,
    val fat: Int
)

/** Daily workout volume with trend overlay. */
data class DailyVolume(
    val date: LocalDate,
    val volume: Double,
    val trendValue: Double = 0.0
)
