package com.sakura.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation route for the onboarding flow. */
@Serializable
object Onboarding

/** Type-safe navigation route for the food log screen (Phase 2). */
@Serializable
object FoodLog

/** Type-safe navigation route for the settings screen (Phase 2). */
@Serializable
object Settings

/** Type-safe navigation route for the workout log screen (Phase 3). */
@Serializable
object WorkoutLog

/**
 * Type-safe navigation route for the workout history screen (Phase 3).
 * WorkoutSession route removed — the active session view is now integrated into WorkoutLogScreen.
 */
@Serializable
object WorkoutHistory

/** Type-safe navigation route for the progress screen. */
@Serializable
object Progress

/** Type-safe navigation route for the macro targets sub-screen. */
@Serializable
object MacroTargets

/** Type-safe navigation route for the food library screen. */
@Serializable
object FoodLibrary

/** Type-safe navigation route for the exercise library screen. */
@Serializable
object ExerciseLibrary

/** Type-safe navigation route for the home/dashboard screen (Phase 4). */
@Serializable
object Home

/** Type-safe navigation route for the workout template creator/editor. */
@Serializable
data class WorkoutTemplateCreator(val templateId: String? = null)
