package com.sakura.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.sakura.di.AppContainer
import com.sakura.features.onboarding.OnboardingScreen
import com.sakura.features.onboarding.OnboardingViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,
    appContainer: AppContainer,
    startWithOnboarding: Boolean
) {
    NavHost(
        navController = navController,
        startDestination = if (startWithOnboarding) Onboarding else Main
    ) {
        composable<Onboarding> {
            val viewModel = OnboardingViewModel(appContainer.prefsRepo)
            OnboardingScreen(
                viewModel = viewModel,
                onOnboardingDone = {
                    navController.navigate(Main) {
                        popUpTo<Onboarding> { inclusive = true }
                    }
                }
            )
        }

        composable<Main> {
            SetupCompleteScreen(appContainer = appContainer)
        }
    }
}

/** Phase 1 placeholder main screen — shows setup confirmation. */
@Composable
private fun SetupCompleteScreen(appContainer: AppContainer) {
    val syncFolderPath by appContainer.prefsRepo.syncFolderPath
        .collectAsStateWithLifecycle(initialValue = null)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Sakura",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Setup complete",
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Setup Complete",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (syncFolderPath != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = syncFolderPath!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
