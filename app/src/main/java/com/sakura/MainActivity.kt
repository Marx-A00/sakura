package com.sakura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.sakura.navigation.AppNavHost
import com.sakura.ui.theme.SakuraTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as SakuraApplication).container

        setContent {
            SakuraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Use initialValue = null to represent "still loading from DataStore".
                    // This prevents a flash where NavHost renders with the wrong start destination.
                    val onboardingDone by container.prefsRepo.onboardingComplete
                        .collectAsStateWithLifecycle(initialValue = null)

                    // Wait until DataStore emits before rendering NavHost
                    if (onboardingDone != null) {
                        val navController = rememberNavController()
                        AppNavHost(
                            navController = navController,
                            appContainer = container,
                            startWithOnboarding = onboardingDone != true
                        )
                    }
                    // else: blank screen while DataStore initializes — prevents flash
                }
            }
        }
    }
}
