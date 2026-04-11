package com.sakura.features.workoutlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakura.data.workout.ExerciseCategory
import com.sakura.data.workout.SetLog
import com.sakura.ui.theme.CherryBlossomPink
import com.sakura.ui.theme.ForestGreen
import kotlinx.coroutines.launch

/**
 * Category-aware set input bottom sheet.
 *
 * Matches 03-set-input-sheet.png:
 * - Title: "Log Set" + X close button
 * - Subtitle: "{exercise name} · Set {N}"
 * - Fields adapt by ExerciseCategory:
 *   WEIGHTED:   Weight field (large) + Unit toggle (kg/lbs) + Reps field + pre-fill info
 *   BODYWEIGHT: Reps field + RPE selector
 *   TIMED:      Hold duration (seconds) field
 *   CARDIO:     Duration (minutes) + Distance (km, optional)
 *   STRETCH:    Duration (minutes) field
 * - Green "Log Set" button (matches mockup)
 *
 * Uses invokeOnCompletion dismiss pattern (02-02 decision).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetInputSheet(
    sheetState: SheetState,
    exerciseName: String,
    category: ExerciseCategory,
    setNumber: Int,
    prefillSet: SetLog?,           // previous session's set at this position
    previousSets: List<SetLog>,    // all previous sets for the pre-fill info line
    onLogSet: (SetLog) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // --- Pre-fill state from previous session ---
    var weightText by remember {
        mutableStateOf(
            if (prefillSet != null && category == ExerciseCategory.WEIGHTED) {
                prefillSet.weight.let { if (it == 0.0) "" else formatPrefillWeight(it) }
            } else ""
        )
    }
    var unit by remember {
        mutableStateOf(prefillSet?.unit?.takeIf { it != "bw" } ?: "kg")
    }
    var repsText by remember {
        mutableStateOf(
            if (prefillSet != null && (category == ExerciseCategory.WEIGHTED || category == ExerciseCategory.BODYWEIGHT)) {
                prefillSet.reps.let { if (it == 0) "" else it.toString() }
            } else ""
        )
    }
    var holdSecsText by remember {
        mutableStateOf(
            if (prefillSet != null && category == ExerciseCategory.TIMED) {
                prefillSet.holdSecs.let { if (it == 0) "" else it.toString() }
            } else ""
        )
    }
    var durationMinText by remember {
        mutableStateOf(
            if (prefillSet != null && (category == ExerciseCategory.CARDIO || category == ExerciseCategory.STRETCH)) {
                prefillSet.durationMin?.let { if (it == 0) "" else it.toString() } ?: ""
            } else ""
        )
    }
    var distanceKmText by remember {
        mutableStateOf(
            if (prefillSet != null && category == ExerciseCategory.CARDIO) {
                prefillSet.distanceKm?.let { if (it == 0.0) "" else it.toString() } ?: ""
            } else ""
        )
    }
    var selectedRpe by remember { mutableIntStateOf(prefillSet?.rpe ?: 0) }

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    fun buildSet(): SetLog = when (category) {
        ExerciseCategory.WEIGHTED -> SetLog(
            setNumber = setNumber,
            reps = repsText.toIntOrNull() ?: 0,
            weight = weightText.toDoubleOrNull() ?: 0.0,
            unit = unit,
            rpe = selectedRpe.takeIf { it > 0 }
        )
        ExerciseCategory.BODYWEIGHT -> SetLog(
            setNumber = setNumber,
            reps = repsText.toIntOrNull() ?: 0,
            weight = 0.0,
            unit = "bw",
            rpe = selectedRpe.takeIf { it > 0 }
        )
        ExerciseCategory.TIMED -> SetLog(
            setNumber = setNumber,
            reps = 1,
            weight = 0.0,
            unit = "bw",
            holdSecs = holdSecsText.toIntOrNull() ?: 0
        )
        ExerciseCategory.CARDIO -> SetLog(
            setNumber = setNumber,
            reps = 0,
            weight = 0.0,
            unit = "bw",
            durationMin = durationMinText.toIntOrNull() ?: 0,
            distanceKm = distanceKmText.toDoubleOrNull()
        )
        ExerciseCategory.STRETCH -> SetLog(
            setNumber = setNumber,
            reps = 0,
            weight = 0.0,
            unit = "bw",
            durationMin = durationMinText.toIntOrNull() ?: 0
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title row: "Log Set" + close button (matches mockup)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Log Set",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { dismiss() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            // Subtitle: "Bench Press · Set 3" (matches mockup)
            Text(
                text = "$exerciseName · Set $setNumber",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Input fields — adapt by category
            when (category) {
                ExerciseCategory.WEIGHTED -> {
                    // Weight + unit toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Weight", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = weightText,
                                onValueChange = { weightText = it.filter { c -> c.isDigit() || c == '.' } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        Column(modifier = Modifier.padding(top = 20.dp)) {
                            Text("Unit", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = unit == "kg",
                                    onClick = { unit = "kg" },
                                    label = { Text("kg") }
                                )
                                FilterChip(
                                    selected = unit == "lbs",
                                    onClick = { unit = "lbs" },
                                    label = { Text("lbs") }
                                )
                            }
                        }
                    }
                    // Reps field
                    Column {
                        Text("Reps", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = repsText,
                            onValueChange = { repsText = it.filter { c -> c.isDigit() } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    // Pre-fill info line (matches mockup)
                    if (prefillSet != null) {
                        PreFillInfoRow(prefillSet = prefillSet, category = category)
                    }
                }

                ExerciseCategory.BODYWEIGHT -> {
                    // Reps only
                    Column {
                        Text("Reps", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = repsText,
                            onValueChange = { repsText = it.filter { c -> c.isDigit() } },
                            placeholder = { Text("reps") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    if (prefillSet != null) {
                        PreFillInfoRow(prefillSet = prefillSet, category = category)
                    }
                    RpeSelector(selectedRpe = selectedRpe, onSelect = { selectedRpe = it })
                }

                ExerciseCategory.TIMED -> {
                    // Hold duration in seconds
                    Column {
                        Text("Hold duration (seconds)", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = holdSecsText,
                            onValueChange = { holdSecsText = it.filter { c -> c.isDigit() } },
                            placeholder = { Text("e.g. 30") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    if (prefillSet != null) {
                        PreFillInfoRow(prefillSet = prefillSet, category = category)
                    }
                }

                ExerciseCategory.CARDIO -> {
                    // Duration + optional distance
                    Column {
                        Text("Duration (minutes)", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = durationMinText,
                            onValueChange = { durationMinText = it.filter { c -> c.isDigit() } },
                            placeholder = { Text("e.g. 30") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    Column {
                        Text("Distance (km, optional)", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = distanceKmText,
                            onValueChange = { distanceKmText = it.filter { c -> c.isDigit() || c == '.' } },
                            placeholder = { Text("e.g. 3.5") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    if (prefillSet != null) {
                        PreFillInfoRow(prefillSet = prefillSet, category = category)
                    }
                }

                ExerciseCategory.STRETCH -> {
                    // Duration only
                    Column {
                        Text("Duration (minutes)", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = durationMinText,
                            onValueChange = { durationMinText = it.filter { c -> c.isDigit() } },
                            placeholder = { Text("e.g. 10") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    if (prefillSet != null) {
                        PreFillInfoRow(prefillSet = prefillSet, category = category)
                    }
                }
            }

            // Log Set button (green — matches mockup)
            Button(
                onClick = {
                    onLogSet(buildSet())
                    dismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
            ) {
                Text("Log Set", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Pre-fill info row (matches mockup: clock icon + "Pre-filled from last session: 82.5kg × 5")
// ---------------------------------------------------------------------------

@Composable
private fun PreFillInfoRow(prefillSet: SetLog, category: ExerciseCategory) {
    val text = when (category) {
        ExerciseCategory.WEIGHTED ->
            "Pre-filled from last session: ${formatPrefillWeight(prefillSet.weight)}kg × ${prefillSet.reps}"
        ExerciseCategory.BODYWEIGHT ->
            "Pre-filled from last session: ${prefillSet.reps} reps"
        ExerciseCategory.TIMED ->
            "Pre-filled from last session: ${prefillSet.holdSecs}s hold"
        ExerciseCategory.CARDIO ->
            "Pre-filled from last session: ${prefillSet.durationMin ?: 0}min"
        ExerciseCategory.STRETCH ->
            "Pre-filled from last session: ${prefillSet.durationMin ?: 0}min"
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// RPE selector (for weighted and bodyweight)
// ---------------------------------------------------------------------------

@Composable
private fun RpeSelector(
    selectedRpe: Int,
    onSelect: (Int) -> Unit
) {
    Column {
        Text(
            "RPE (optional)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            (6..10).forEach { rpe ->
                FilterChip(
                    selected = selectedRpe == rpe,
                    onClick = {
                        onSelect(if (selectedRpe == rpe) 0 else rpe)
                    },
                    label = { Text(rpe.toString()) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CherryBlossomPink,
                        selectedLabelColor = androidx.compose.ui.graphics.Color.White
                    )
                )
            }
        }
    }
}

private fun formatPrefillWeight(weight: Double): String =
    if (weight == weight.toLong().toDouble()) weight.toLong().toString()
    else "%.1f".format(weight)
