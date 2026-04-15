package com.sakura.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
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
import com.sakura.features.progress.ProgressScreen
import com.sakura.features.onboarding.OnboardingViewModel
import com.sakura.features.settings.MacroTargetsScreen
import com.sakura.features.workoutlog.WorkoutHistoryScreen
import com.sakura.features.workoutlog.WorkoutLogScreen
import com.sakura.features.workoutlog.WorkoutLogViewModel
import com.sakura.ui.theme.CherryBlossomPink

@Composable
fun AppNavHost(
    navController: NavHostController,
    appContainer: AppContainer,
    startWithOnboarding: Boolean
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination.isTabDestination()

    // Radial context — determines which options the radial menu shows
    val radialContext = when {
        currentDestination?.hasRoute<FoodLog>() == true -> RadialContext.FOOD
        currentDestination?.hasRoute<WorkoutLog>() == true -> RadialContext.WORKOUT
        else -> RadialContext.DEFAULT
    }

    // Trigger for page-specific radial actions (increment to fire)
    var addFoodEntryTrigger by remember { mutableIntStateOf(0) }

    fun navigateToTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                SakuraBottomBar(
                    currentDestination = currentDestination,
                    onNavigateTo = ::navigateToTab,
                    radialContext = radialContext,
                    onRadialAction = { action ->
                        when (action) {
                            // Default navigation
                            RadialAction.NAV_FOOD -> navigateToTab(FoodLog)
                            RadialAction.NAV_EXERCISE -> navigateToTab(WorkoutLog)
                            RadialAction.NAV_SETTINGS -> navigateToTab(Settings)
                            // Food page actions
                            RadialAction.FOOD_ADD_ENTRY -> addFoodEntryTrigger++
                            RadialAction.FOOD_FROM_LIBRARY -> {} // placeholder
                            RadialAction.FOOD_QUICK_ADD -> {} // placeholder
                            // Workout page actions
                            RadialAction.WORKOUT_ADD_EXERCISE -> {} // placeholder
                            RadialAction.WORKOUT_FROM_TEMPLATE -> {} // placeholder
                            RadialAction.WORKOUT_LOG_WEIGHT -> {} // placeholder
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
                FoodLogScreen(
                    viewModel = foodLogViewModel,
                    addEntryTrigger = addFoodEntryTrigger
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
                    onNavigateToHistory = {
                        navController.navigate(WorkoutHistory)
                    }
                )
            }

            composable<Progress> {
                ProgressScreen()
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

private fun NavDestination?.isTabDestination(): Boolean {
    if (this == null) return false
    return hasRoute<Home>() || hasRoute<FoodLog>() || hasRoute<WorkoutLog>() || hasRoute<Progress>() || hasRoute<Settings>()
}

// ---------------------------------------------------------------------------
// Custom bottom bar: FOOD | WORKOUT | SAKURA | PROGRESS | SETTINGS
// ---------------------------------------------------------------------------

@Composable
private fun SakuraBottomBar(
    currentDestination: NavDestination?,
    onNavigateTo: (Any) -> Unit,
    radialContext: RadialContext = RadialContext.DEFAULT,
    onRadialAction: (RadialAction) -> Unit
) {
    val barHeight = 64.dp
    val branchOverflow = 86.dp // branch is 150dp, bar is 64dp → 86dp overflow

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(barHeight + branchOverflow)
    ) {
        // Gray bar background — only the bottom portion
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .align(Alignment.BottomCenter)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
        )

        // Nav tabs row — aligned to bottom bar area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavTab(
                icon = Icons.Filled.DateRange,
                label = "FOOD",
                selected = currentDestination?.hasRoute<FoodLog>() == true,
                onClick = { onNavigateTo(FoodLog) },
                modifier = Modifier.weight(1f)
            )
            NavTab(
                icon = Icons.Filled.Star,
                label = "WORKOUT",
                selected = currentDestination?.hasRoute<WorkoutLog>() == true,
                onClick = { onNavigateTo(WorkoutLog) },
                modifier = Modifier.weight(1f)
            )

            // Spacer for the branch
            Spacer(Modifier.weight(1f))

            NavTab(
                icon = Icons.AutoMirrored.Filled.ShowChart,
                label = "PROGRESS",
                selected = currentDestination?.hasRoute<Progress>() == true,
                onClick = { onNavigateTo(Progress) },
                modifier = Modifier.weight(1f)
            )
            NavTab(
                icon = Icons.Filled.Settings,
                label = "SETTINGS",
                selected = currentDestination?.hasRoute<Settings>() == true,
                onClick = { onNavigateTo(Settings) },
                modifier = Modifier.weight(1f)
            )
        }

        // Sakura branch — anchored at bottom, overflows above the bar
        CenterHomeButton(
            onHomeTap = { onNavigateTo(Home) },
            onRadialAction = onRadialAction,
            radialContext = radialContext,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun NavTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (selected) CherryBlossomPink else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = contentColor
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = contentColor,
            letterSpacing = 0.5.sp
        )
    }
}
