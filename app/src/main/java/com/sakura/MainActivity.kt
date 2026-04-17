package com.sakura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.sakura.di.AppContainer
import com.sakura.navigation.AppNavHost
import com.sakura.preferences.StorageMode
import com.sakura.ui.theme.SakuraTheme
import com.sakura.ui.theme.customPalette
import com.sakura.ui.theme.paletteById

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefsRepo = (application as SakuraApplication).prefsRepo

        setContent {
            val themeMode by prefsRepo.themeMode
                .collectAsStateWithLifecycle(initialValue = "DARK")
            val paletteId by prefsRepo.colorPalette
                .collectAsStateWithLifecycle(initialValue = "SAGE")
            val customHex by prefsRepo.customAccentHex
                .collectAsStateWithLifecycle(initialValue = "#7A8B6F")
            val palette = remember(paletteId, customHex) {
                if (paletteId == "CUSTOM") customPalette(customHex)
                else paletteById(paletteId)
            }

            SakuraTheme(themeMode = themeMode, palette = palette) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Use initialValue = null to represent "still loading from DataStore".
                    // This prevents a flash where NavHost renders with the wrong start destination.
                    val onboardingDone by prefsRepo.onboardingComplete
                        .collectAsStateWithLifecycle(initialValue = null)

                    val storageMode by prefsRepo.storageMode
                        .collectAsStateWithLifecycle(initialValue = null)

                    // Wait until onboarding status is known before rendering.
                    // storageMode may be null on first launch (user hasn't chosen yet) —
                    // that's fine, we fall back to LOCAL so the app can show onboarding.
                    if (onboardingDone != null) {
                        // New user: storageMode not yet set. Default to LOCAL so onboarding
                        // can proceed and later call setStorageMode() to persist the choice.
                        val resolvedMode = storageMode ?: StorageMode.LOCAL
                        val container = remember(resolvedMode) {
                            AppContainer(applicationContext, resolvedMode)
                        }
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
