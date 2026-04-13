package com.sakura.features.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentTargets by prefsRepo.macroTargets.collectAsStateWithLifecycle(
        initialValue = MacroTargets(calories = 2000, protein = 150, carbs = 250, fat = 65)
    )
    val currentStorageMode by prefsRepo.storageMode.collectAsStateWithLifecycle(initialValue = null)

    var calories by remember(currentTargets) { mutableStateOf(currentTargets.calories.toString()) }
    var protein by remember(currentTargets) { mutableStateOf(currentTargets.protein.toString()) }
    var carbs by remember(currentTargets) { mutableStateOf(currentTargets.carbs.toString()) }
    var fat by remember(currentTargets) { mutableStateOf(currentTargets.fat.toString()) }

    var showMigrationDialog by remember { mutableStateOf(false) }

    // Rest timer prefs
    val timerEnabled by prefsRepo.timerEnabled.collectAsStateWithLifecycle(initialValue = true)
    val timerAutoStart by prefsRepo.timerAutoStart.collectAsStateWithLifecycle(initialValue = true)
    val defaultRestSecs by prefsRepo.defaultRestTimerSecs.collectAsStateWithLifecycle(initialValue = 90)
    val notifType by prefsRepo.timerNotificationType.collectAsStateWithLifecycle(initialValue = "VIBRATION")
    val bgNotifEnabled by prefsRepo.timerBgNotification.collectAsStateWithLifecycle(initialValue = false)
    var defaultRestSecsInput by remember(defaultRestSecs) { mutableStateOf(defaultRestSecs.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Macro Targets") },
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

            // ---------------------------------------------------------------
            // Rest Timer section
            // ---------------------------------------------------------------

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Rest Timer",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Master toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rest Timer Enabled",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = timerEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { prefsRepo.setTimerEnabled(enabled) }
                    }
                )
            }

            if (timerEnabled) {
                Spacer(modifier = Modifier.height(8.dp))

                // Auto-start toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto-start after logging set",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = timerAutoStart,
                        onCheckedChange = { autoStart ->
                            scope.launch { prefsRepo.setTimerAutoStart(autoStart) }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Default duration field
                OutlinedTextField(
                    value = defaultRestSecsInput,
                    onValueChange = { newVal ->
                        val digits = newVal.filter { c -> c.isDigit() }
                        defaultRestSecsInput = digits
                        val parsed = digits.toIntOrNull()
                        if (parsed != null) {
                            scope.launch { prefsRepo.setDefaultRestTimerSecs(parsed.coerceIn(5, 600)) }
                        }
                    },
                    label = { Text("Default Rest Duration (seconds)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Notification type selector
                Text(
                    text = "Completion Alert",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("VIBRATION" to "Vibrate", "SOUND" to "Sound", "BOTH" to "Both", "NONE" to "None")
                        .forEach { (type, label) ->
                            FilterChip(
                                selected = notifType == type,
                                onClick = {
                                    scope.launch { prefsRepo.setTimerNotificationType(type) }
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Background notification toggle (off by default, requires POST_NOTIFICATIONS on API 33+)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Background Notification",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = bgNotifEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                // On API 33+ check POST_NOTIFICATIONS permission before enabling
                                val activity = context as? ComponentActivity
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!granted) {
                                        ActivityCompat.requestPermissions(
                                            activity,
                                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                            7001
                                        )
                                        // Persist the enabled state — service will only show notification
                                        // if permission is granted at runtime (Android enforces this)
                                    }
                                }
                                scope.launch { prefsRepo.setTimerBgNotification(true) }
                            } else {
                                scope.launch { prefsRepo.setTimerBgNotification(false) }
                            }
                        }
                    )
                }
                Text(
                    text = "Shows timer in notification bar when app is backgrounded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---------------------------------------------------------------
            // Storage section
            // ---------------------------------------------------------------

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Storage",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))

            val modeLabel = when (currentStorageMode) {
                StorageMode.LOCAL -> "Just me (local storage)"
                StorageMode.SYNCTHING -> "Sync across devices"
                null -> "Not configured"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Current mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = modeLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (currentStorageMode != null) {
                    OutlinedButton(
                        onClick = { showMigrationDialog = true },
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Text("Change")
                    }
                }
            }
        }
    }

    // Migration confirmation dialog
    val resolvedMode = currentStorageMode
    if (showMigrationDialog && resolvedMode != null) {
        val targetMode = when (resolvedMode) {
            StorageMode.LOCAL -> StorageMode.SYNCTHING
            StorageMode.SYNCTHING -> StorageMode.LOCAL
        }
        val dialogTitle = when (resolvedMode) {
            StorageMode.LOCAL -> "Switch to Sync mode?"
            StorageMode.SYNCTHING -> "Switch to local storage?"
        }
        val dialogMessage = when (resolvedMode) {
            StorageMode.LOCAL ->
                "To switch to Sync mode, you'll need to grant storage access. This will restart the app."
            StorageMode.SYNCTHING ->
                "Your org files will be copied to local storage on this device."
        }

        AlertDialog(
            onDismissRequest = { showMigrationDialog = false },
            title = { Text(dialogTitle) },
            text = { Text(dialogMessage) },
            dismissButton = {
                TextButton(onClick = { showMigrationDialog = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMigrationDialog = false
                        scope.launch {
                            migrateStorage(
                                context = context,
                                prefsRepo = prefsRepo,
                                targetMode = targetMode
                            )
                        }
                    }
                ) {
                    Text("Continue")
                }
            }
        )
    }
}

/**
 * Migrates the app's storage mode to [targetMode].
 *
 * - SYNCTHING -> LOCAL: copies .org files from the sync folder to filesDir, switches mode, restarts.
 * - LOCAL -> SYNCTHING: sets mode, clears onboarding flag, restarts so user goes through
 *   Syncthing permission/folder onboarding. File copying happens after folder selection.
 */
private suspend fun migrateStorage(
    context: android.content.Context,
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
