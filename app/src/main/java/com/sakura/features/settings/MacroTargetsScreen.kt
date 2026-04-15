package com.sakura.features.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.preferences.AppPreferencesRepository
import com.sakura.preferences.MacroTargets
import com.sakura.preferences.StorageMode
import com.sakura.ui.theme.CherryBlossomPink
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroTargetsScreen(
    prefsRepo: AppPreferencesRepository,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentTargets by prefsRepo.macroTargets.collectAsStateWithLifecycle(
        initialValue = MacroTargets(calories = 2000, protein = 150, carbs = 250, fat = 65)
    )

    var calories by remember(currentTargets) { mutableStateOf(currentTargets.calories.toString()) }
    var protein by remember(currentTargets) { mutableStateOf(currentTargets.protein.toString()) }
    var carbs by remember(currentTargets) { mutableStateOf(currentTargets.carbs.toString()) }
    var fat by remember(currentTargets) { mutableStateOf(currentTargets.fat.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Macros") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = calories,
                onValueChange = { calories = it },
                label = { Text("Daily Calories (kcal)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = protein,
                onValueChange = { protein = it },
                label = { Text("Protein target (g)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = carbs,
                onValueChange = { carbs = it },
                label = { Text("Carbs target (g)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = fat,
                onValueChange = { fat = it },
                label = { Text("Fat target (g)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        prefsRepo.setMacroTargets(
                            MacroTargets(
                                calories = calories.toIntOrNull() ?: 2000,
                                protein = protein.toIntOrNull() ?: 150,
                                carbs = carbs.toIntOrNull() ?: 250,
                                fat = fat.toIntOrNull() ?: 65
                            )
                        )
                        onNavigateBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CherryBlossomPink)
            ) {
                Text("Save", color = Color.White)
            }
        }
    }
}

/**
 * Migrates the app's storage mode to [targetMode].
 *
 * - SYNCTHING -> LOCAL: copies .org files from the sync folder to filesDir, switches mode, restarts.
 * - LOCAL -> SYNCTHING: sets mode, clears onboarding flag, restarts so user goes through
 *   Syncthing permission/folder onboarding. File copying happens after folder selection.
 */
internal suspend fun migrateStorage(
    context: Context,
    prefsRepo: AppPreferencesRepository,
    targetMode: StorageMode
) {
    when (targetMode) {
        StorageMode.LOCAL -> {
            // Copy .org files from sync folder to internal storage
            val syncPath = prefsRepo.syncFolderPath.first()
            if (syncPath != null) {
                val syncDir = File(syncPath)
                if (syncDir.exists() && syncDir.isDirectory) {
                    syncDir.listFiles { file -> file.name.endsWith(".org") }
                        ?.forEach { orgFile ->
                            val dest = File(context.filesDir, orgFile.name)
                            orgFile.copyTo(dest, overwrite = true)
                        }
                }
            }
            prefsRepo.setStorageMode(StorageMode.LOCAL)
        }
        StorageMode.SYNCTHING -> {
            // Switch mode and clear onboarding so the Syncthing permission/folder flow runs on restart
            prefsRepo.setStorageMode(StorageMode.SYNCTHING)
            prefsRepo.clearOnboardingComplete()
        }
    }
    // Recreate the activity so AppContainer picks up the new mode
    (context as? ComponentActivity)?.recreate()
}
