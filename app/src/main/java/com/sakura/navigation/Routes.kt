package com.sakura.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation route for the onboarding flow. */
@Serializable
object Onboarding

/** Type-safe navigation route for the main app screen. */
@Serializable
object Main

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

/** Type-safe navigation route for the home/dashboard screen (Phase 4). */
@Serializable
object Home
