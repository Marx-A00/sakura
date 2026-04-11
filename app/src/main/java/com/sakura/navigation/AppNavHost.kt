package com.sakura.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.sakura.features.workoutlog.WorkoutSessionScreen

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
                onStartSession = { splitDay ->
                    workoutLogViewModel.startSession(splitDay)
                    navController.navigate(WorkoutSession)
                },
                onResumeSession = {
                    navController.navigate(WorkoutSession)
                },
                onNavigateToHistory = {
                    navController.navigate(WorkoutHistory)
                },
                onNavigateToFoodLog = {
                    navController.navigate(FoodLog) {
                        popUpTo<WorkoutLog> { inclusive = true }
                    }
                }
            )
        }

        composable<WorkoutSession> {
            // Share the same ViewModel instance from the WorkoutLog backstack entry
            // so that the session draft persists across navigation.
            val parentEntry = remember(it) {
                try {
                    navController.getBackStackEntry<WorkoutLog>()
                } catch (_: Exception) {
                    null
                }
            }
            val workoutLogViewModel: WorkoutLogViewModel = if (parentEntry != null) {
                viewModel(
                    viewModelStoreOwner = parentEntry,
                    factory = WorkoutLogViewModel.factory(
                        workoutRepo = appContainer.workoutRepository,
                        prefsRepo = appContainer.prefsRepo
                    )
                )
            } else {
                viewModel(
                    factory = WorkoutLogViewModel.factory(
                        workoutRepo = appContainer.workoutRepository,
                        prefsRepo = appContainer.prefsRepo
                    )
                )
            }
            WorkoutSessionScreen(
                viewModel = workoutLogViewModel,
                onSessionFinished = {
                    navController.popBackStack()
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
