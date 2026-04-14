package com.sakura.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.sakura.di.AppContainer
import com.sakura.features.dashboard.DashboardScreen
import com.sakura.features.dashboard.DashboardViewModel
import com.sakura.features.foodlog.FoodLogScreen
import com.sakura.features.foodlog.FoodLogViewModel
import com.sakura.features.onboarding.OnboardingScreen
import com.sakura.features.onboarding.OnboardingViewModel
import com.sakura.features.settings.MacroTargetsScreen
import com.sakura.features.workoutlog.WorkoutHistoryScreen
import com.sakura.features.workoutlog.WorkoutLogScreen
import com.sakura.features.workoutlog.WorkoutLogViewModel
import com.sakura.ui.theme.CherryBlossomPink

/**
 * App navigation host.
 *
 * Phase 4 changes:
 * - MainScaffold with shared NavigationBar across all tab destinations (FOOD/WORKOUT/HOME/SETTINGS)
 * - Home is the startDestination for returning users; Onboarding for first-run
 * - Onboarding completion navigates to Home (not FoodLog)
 * - Per-screen NavigationBar removed from FoodLogScreen and WorkoutLogScreen
 * - Tab navigation uses canonical M3 pattern: popUpTo startDestination, saveState, restoreState
 * - NavigationBar hidden on Onboarding, Settings (detail), and WorkoutHistory screens
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    appContainer: AppContainer,
    startWithOnboarding: Boolean
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Hide bottom bar on non-tab screens
    val showBottomBar = currentDestination.isTabDestination()

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                MainNavigationBar(
                    currentDestination = currentDestination,
                    onNavigateTo = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (startWithOnboarding) Onboarding else Home,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            composable<Onboarding> {
                val onboardingViewModel = OnboardingViewModel(appContainer.prefsRepo)
                OnboardingScreen(
                    viewModel = onboardingViewModel,
                    onOnboardingDone = {
                        navController.navigate(Home) {
                            popUpTo<Onboarding> { inclusive = true }
                        }
                    }
                )
            }

            composable<Home> {
                val dashboardViewModel: DashboardViewModel = viewModel(
                    factory = DashboardViewModel.factory(
                        foodRepo = appContainer.foodRepository,
                        workoutRepo = appContainer.workoutRepository,
                        prefsRepo = appContainer.prefsRepo,
                        syncBackend = appContainer.syncBackend
                    )
                )
                DashboardScreen(viewModel = dashboardViewModel)
            }

            composable<FoodLog> {
                val foodLogViewModel: FoodLogViewModel = viewModel(
                    factory = FoodLogViewModel.factory(
                        foodRepo = appContainer.foodRepository,
                        prefsRepo = appContainer.prefsRepo
                    )
                )
                FoodLogScreen(viewModel = foodLogViewModel)
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
                    onNavigateToHistory = {
                        navController.navigate(WorkoutHistory)
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
}

/**
 * Returns true if the destination is one of the four main tab screens
 * (HOME, FOOD, WORKOUT) that should show the shared NavigationBar.
 * Settings, WorkoutHistory, and Onboarding have their own back-nav.
 */
private fun NavDestination?.isTabDestination(): Boolean {
    if (this == null) return false
    return hasRoute<Home>() || hasRoute<FoodLog>() || hasRoute<WorkoutLog>() || hasRoute<Settings>()
}

/**
 * Shared bottom navigation bar (FOOD | WORKOUT | HOME | SETTINGS).
 * Canonical M3 tab pattern: popUpTo startDestination, saveState, restoreState.
 */
@Composable
private fun MainNavigationBar(
    currentDestination: NavDestination?,
    onNavigateTo: (Any) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        NavigationBarItem(
            selected = currentDestination?.hasRoute<FoodLog>() == true,
            onClick = { onNavigateTo(FoodLog) },
            icon = {
                Icon(
                    Icons.Filled.DateRange,
                    contentDescription = "Food",
                    modifier = Modifier.size(20.dp)
                )
            },
            label = { Text("FOOD", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = CherryBlossomPink,
                selectedIconColor = Color.White,
                selectedTextColor = CherryBlossomPink
            )
        )
        NavigationBarItem(
            selected = currentDestination?.hasRoute<WorkoutLog>() == true,
            onClick = { onNavigateTo(WorkoutLog) },
            icon = {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Workout",
                    modifier = Modifier.size(20.dp)
                )
            },
            label = { Text("WORKOUT", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = CherryBlossomPink,
                selectedIconColor = Color.White,
                selectedTextColor = CherryBlossomPink
            )
        )
        NavigationBarItem(
            selected = currentDestination?.hasRoute<Home>() == true,
            onClick = { onNavigateTo(Home) },
            icon = {
                Icon(
                    Icons.Filled.Home,
                    contentDescription = "Home",
                    modifier = Modifier.size(20.dp)
                )
            },
            label = { Text("HOME", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = CherryBlossomPink,
                selectedIconColor = Color.White,
                selectedTextColor = CherryBlossomPink
            )
        )
        NavigationBarItem(
            selected = currentDestination?.hasRoute<Settings>() == true,
            onClick = { onNavigateTo(Settings) },
            icon = {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(20.dp)
                )
            },
            label = { Text("SETTINGS", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = CherryBlossomPink,
                selectedIconColor = Color.White,
                selectedTextColor = CherryBlossomPink
            )
        )
    }
}
