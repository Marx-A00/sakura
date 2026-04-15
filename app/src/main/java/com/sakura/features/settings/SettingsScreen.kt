package com.sakura.features.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.preferences.AppPreferencesRepository
import com.sakura.preferences.MacroTargets
import com.sakura.preferences.StorageMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefsRepo: AppPreferencesRepository,
    onNavigateBack: () -> Unit,
    onNavigateToMacros: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentTargets by prefsRepo.macroTargets.collectAsStateWithLifecycle(
        initialValue = MacroTargets(calories = 2000, protein = 150, carbs = 250, fat = 65)
    )
    val currentStorageMode by prefsRepo.storageMode.collectAsStateWithLifecycle(initialValue = null)

    var showMigrationDialog by remember { mutableStateOf(false) }

    // Theme pref
    val themeMode by prefsRepo.themeMode.collectAsStateWithLifecycle(initialValue = "DARK")

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
                title = { Text("Settings") },
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
            // ---------------------------------------------------------------
            // Macros — navigable row
            // ---------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToMacros)
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Macros",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${currentTargets.calories} kcal · ${currentTargets.protein}p · ${currentTargets.carbs}c · ${currentTargets.fat}f",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // ---------------------------------------------------------------
            // Theme section
            // ---------------------------------------------------------------

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("DARK" to "Dark", "LIGHT" to "Light", "SYSTEM" to "System")
                    .forEach { (mode, label) ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = {
                                scope.launch { prefsRepo.setThemeMode(mode) }
                            },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
            }

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider()

            // ---------------------------------------------------------------
            // Rest Timer section
            // ---------------------------------------------------------------

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

                // Background notification toggle
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
