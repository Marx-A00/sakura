package com.sakura.features.workoutlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakura.data.workout.ExerciseCategory
import com.sakura.data.workout.SetLog
import com.sakura.ui.theme.SakuraTheme
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
        mutableStateOf(prefillSet?.unit?.takeIf { it != "bw" } ?: "lbs")
    }
    var repsText by remember {
        mutableStateOf(
            if (prefillSet != null && (category == ExerciseCategory.WEIGHTED || category == ExerciseCategory.BODYWEIGHT)) {
                prefillSet.reps.let { if (it == 0) "" else it.toString() }
            } else ""
        )
    }
    var repsValue by remember {
        mutableIntStateOf(
            if (prefillSet != null && (category == ExerciseCategory.WEIGHTED || category == ExerciseCategory.BODYWEIGHT)) {
                prefillSet.reps.coerceIn(1, 99)
            } else 10
        )
    }
    var repsManualEntry by remember { mutableStateOf(false) }
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
    var showPlateCalc by remember { mutableStateOf(false) }

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    fun resolvedReps(): Int = if (repsManualEntry) repsText.toIntOrNull() ?: 0 else repsValue

    fun buildSet(): SetLog = when (category) {
        ExerciseCategory.WEIGHTED -> SetLog(
            setNumber = setNumber,
            reps = resolvedReps(),
            weight = weightText.toDoubleOrNull() ?: 0.0,
            unit = unit,
            rpe = selectedRpe.takeIf { it > 0 }
        )
        ExerciseCategory.BODYWEIGHT -> SetLog(
            setNumber = setNumber,
            reps = resolvedReps(),
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
                .imePadding()
                .verticalScroll(rememberScrollState())
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
                    // Weight label + unit toggle + plate calc toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Weight", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = unit == "kg",
                                onClick = { unit = "kg" },
                                label = { Text("kg") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                            FilterChip(
                                selected = unit == "lbs",
                                onClick = { unit = "lbs" },
                                label = { Text("lbs") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = { showPlateCalc = !showPlateCalc },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Filled.FitnessCenter,
                            contentDescription = if (showPlateCalc) "Type weight" else "Plate calculator",
                            tint = if (showPlateCalc) SakuraTheme.colors.brand
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (showPlateCalc) {
                        PlateCalculator(
                            unit = unit,
                            onTotalChanged = { total ->
                                weightText = formatPrefillWeight(total)
                            },
                            onConfirm = { showPlateCalc = false }
                        )
                    } else {
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
                    // Reps — hidden during plate calculator
                    if (!showPlateCalc)
                    RepsInput(
                        repsValue = repsValue,
                        repsText = repsText,
                        isManualEntry = repsManualEntry,
                        onWheelChange = { repsValue = it },
                        onTextChange = { repsText = it },
                        onToggleMode = {
                            repsManualEntry = !repsManualEntry
                            if (repsManualEntry) repsText = repsValue.toString()
                            else repsValue = repsText.toIntOrNull()?.coerceIn(1, 99) ?: 10
                        }
                    )
                    // Pre-fill info line
                    if (prefillSet != null) {
                        PreFillInfoRow(prefillSet = prefillSet, category = category)
                    }
                }

                ExerciseCategory.BODYWEIGHT -> {
                    // Reps — wheel picker or manual text entry
                    RepsInput(
                        repsValue = repsValue,
                        repsText = repsText,
                        isManualEntry = repsManualEntry,
                        onWheelChange = { repsValue = it },
                        onTextChange = { repsText = it },
                        onToggleMode = {
                            repsManualEntry = !repsManualEntry
                            if (repsManualEntry) repsText = repsValue.toString()
                            else repsValue = repsText.toIntOrNull()?.coerceIn(1, 99) ?: 10
                        }
                    )
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
                colors = ButtonDefaults.buttonColors(containerColor = SakuraTheme.colors.accent)
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
            "Pre-filled from last session: ${formatPrefillWeight(prefillSet.weight)}${prefillSet.unit} × ${prefillSet.reps}"
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
                        selectedContainerColor = SakuraTheme.colors.brand,
                        selectedLabelColor = androidx.compose.ui.graphics.Color.White
                    )
                )
            }
        }
    }
}

/**
 * Reps input that toggles between a scroll wheel picker and manual text entry.
 * Tap the "type it" / "use wheel" link to switch modes.
 */
@Composable
private fun RepsInput(
    repsValue: Int,
    repsText: String,
    isManualEntry: Boolean,
    onWheelChange: (Int) -> Unit,
    onTextChange: (String) -> Unit,
    onToggleMode: () -> Unit
) {
    Column {
        Text("Reps", style = MaterialTheme.typography.labelMedium)

        if (isManualEntry) {
            OutlinedTextField(
                value = repsText,
                onValueChange = { onTextChange(it.filter { c -> c.isDigit() }) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        } else {
            ScrollWheelPicker(
                range = 1..99,
                selectedValue = repsValue,
                onValueChange = onWheelChange,
                modifier = Modifier.fillMaxWidth(),
                visibleCount = 3,
                label = "reps"
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Plate calculator — tap plates to build up bar weight, no mental math
// ---------------------------------------------------------------------------

private val LBS_PLATES = listOf(45.0, 25.0, 10.0, 5.0, 2.5)
private val KG_PLATES = listOf(20.0, 10.0, 5.0, 2.5, 1.25)
private const val LBS_BAR = 45.0
private const val KG_BAR = 20.0

@Composable
private fun PlateCalculator(
    unit: String,
    onTotalChanged: (Double) -> Unit,
    onConfirm: () -> Unit
) {
    val plates = if (unit == "kg") KG_PLATES else LBS_PLATES
    val barWeight = if (unit == "kg") KG_BAR else LBS_BAR
    val plateCounts = remember { mutableStateMapOf<Double, Int>() }

    fun total(): Double {
        return barWeight + plateCounts.entries.sumOf { (plate, count) -> plate * count * 2 }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Bar weight label
            Text(
                text = "Bar: ${formatPrefillWeight(barWeight)} $unit",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Plate rows
            plates.forEach { plate ->
                val count = plateCounts[plate] ?: 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Minus button
                    OutlinedButton(
                        onClick = {
                            if (count > 0) {
                                plateCounts[plate] = count - 1
                                onTotalChanged(total())
                            }
                        },
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        enabled = count > 0
                    ) {
                        Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }

                    // Plate label + count
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatPrefillWeight(plate),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (count > 0) {
                            Text(
                                text = "×$count each side",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Plus button
                    FilledTonalButton(
                        onClick = {
                            plateCounts[plate] = count + 1
                            onTotalChanged(total())
                        },
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Total display + confirm
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total: ${formatPrefillWeight(total())} $unit",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = SakuraTheme.colors.brand
                )
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SakuraTheme.colors.accent
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 4.dp
                    )
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Confirm weight",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Done", fontSize = 13.sp)
                }
            }
        }
    }
}

private fun formatPrefillWeight(weight: Double): String =
    if (weight == weight.toLong().toDouble()) weight.toLong().toString()
    else "%.1f".format(weight)
