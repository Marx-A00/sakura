package com.sakura.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                }
            )
        }

        composable<Settings> {
            MacroTargetsScreen(
                prefsRepo = appContainer.prefsRepo,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
