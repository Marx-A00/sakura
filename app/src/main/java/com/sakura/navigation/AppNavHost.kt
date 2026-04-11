package com.sakura.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sakura.di.AppContainer
import com.sakura.features.foodlog.FoodLogScreen
import com.sakura.features.foodlog.FoodLogViewModel
import com.sakura.features.onboarding.OnboardingScreen
import com.sakura.features.onboarding.OnboardingViewModel
import com.sakura.features.settings.MacroTargetsScreen
import com.sakura.features.workoutlog.WorkoutHistoryScreen
import com.sakura.features.workoutlog.WorkoutLogScreen
import com.sakura.features.workoutlog.WorkoutLogViewModel

/**
 * App navigation host.
 *
 * Phase 3 changes:
 * - WorkoutSession route removed — active workout view is now inline in WorkoutLogScreen
 * - WorkoutLogScreen receives simple callbacks: onNavigateToFoodLog, onNavigateToHistory, onNavigateToSettings
 * - No shared ViewModel pattern needed (no WorkoutSession backstack entry)
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    appContainer: AppContainer,
    startWithOnboarding: Boolean
) {
    NavHost(
        navController = navController,
        startDestination = if (startWithOnboarding) Onboarding else FoodLog
    ) {
        composable<Onboarding> {
            val onboardingViewModel = OnboardingViewModel(appContainer.prefsRepo)
            OnboardingScreen(
                viewModel = onboardingViewModel,
                onOnboardingDone = {
                    navController.navigate(FoodLog) {
                        popUpTo<Onboarding> { inclusive = true }
                    }
                }
            )
        }

        composable<FoodLog> {
            val foodLogViewModel: FoodLogViewModel = viewModel(
                factory = FoodLogViewModel.factory(
                    foodRepo = appContainer.foodRepository,
                    prefsRepo = appContainer.prefsRepo
                )
            )
            FoodLogScreen(
                viewModel = foodLogViewModel,
                onNavigateToSettings = {
                    navController.navigate(Settings)
                },
                onNavigateToWorkout = {
                    navController.navigate(WorkoutLog) {
                        popUpTo<FoodLog> { inclusive = true }
                    }
                }
            )
        }

        composable<Settings> {
            MacroTargetsScreen(
                prefsRepo = appContainer.prefsRepo,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<WorkoutLog> {
            val workoutLogViewModel: WorkoutLogViewModel = viewModel(
                factory = WorkoutLogViewModel.factory(
                    workoutRepo = appContainer.workoutRepository,
                    prefsRepo = appContainer.prefsRepo
                )
            )
            WorkoutLogScreen(
                viewModel = workoutLogViewModel,
                onNavigateToFoodLog = {
                    // Replace WorkoutLog with FoodLog — no back-stack accumulation
                    navController.navigate(FoodLog) {
                        popUpTo<WorkoutLog> { inclusive = true }
                    }
                },
                onNavigateToHistory = {
                    navController.navigate(WorkoutHistory)
                },
                onNavigateToSettings = {
                    navController.navigate(Settings)
                }
            )
        }

        composable<WorkoutHistory> {
            val workoutLogViewModel: WorkoutLogViewModel = viewModel(
                factory = WorkoutLogViewModel.factory(
                    workoutRepo = appContainer.workoutRepository,
                    prefsRepo = appContainer.prefsRepo
                )
            )
            WorkoutHistoryScreen(
                viewModel = workoutLogViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
