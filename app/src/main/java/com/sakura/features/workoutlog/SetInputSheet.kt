package com.sakura.features.workoutlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sakura.data.workout.ExerciseDefinition
import com.sakura.data.workout.ExerciseType
import com.sakura.data.workout.SetLog
import com.sakura.ui.theme.CherryBlossomPink
import kotlinx.coroutines.launch

/**
 * ModalBottomSheet for logging a single set.
 * Fields adapt based on exercise type (weighted, calisthenics, timed).
 *
 * Uses invokeOnCompletion dismiss pattern from 02-02 decision to prevent
 * race between hide animation and onDismiss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetInputSheet(
    sheetState: SheetState,
    definition: ExerciseDefinition,
    setNumber: Int,
    prefillSet: SetLog?,             // pre-filled values (from previous session or last logged set)
    onLogSet: (SetLog) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val isTimed = definition.exerciseType == ExerciseType.TIMED
    val isBodyweight = definition.exerciseType == ExerciseType.CALISTHENICS
    val isMaxReps = definition.targetReps == -1

    // Field states — pre-fill from previous set if available
    var weightText by remember {
        mutableStateOf(
            if (prefillSet != null && !isBodyweight && !isTimed) {
                prefillSet.weight.let { if (it == 0.0) "" else it.toString() }
            } else ""
        )
    }
    var unit by remember {
        mutableStateOf(prefillSet?.unit?.takeIf { it != "bw" } ?: "kg")
    }
    var repsText by remember {
        mutableStateOf(
            if (!isMaxReps && !isTimed && prefillSet != null) prefillSet.reps.let { if (it == 0) "" else it.toString() }
            else ""
        )
    }
    var holdSecsText by remember {
        mutableStateOf(
            if (isTimed && prefillSet != null) prefillSet.holdSecs.let { if (it == 0) "" else it.toString() }
            else ""
        )
    }
    var selectedRpe by remember { mutableIntStateOf(prefillSet?.rpe ?: 0) }

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
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
            Text(
                text = "Set $setNumber",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
            )

            when {
                isTimed -> {
                    // Timed exercise: only hold duration in seconds
                    OutlinedTextField(
                        value = holdSecsText,
                        onValueChange = { holdSecsText = it.filter { c -> c.isDigit() } },
                        label = { Text("Hold duration (seconds)") },
                        placeholder = { Text("e.g. 25") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                isBodyweight -> {
                    // Calisthenics: reps only (no weight)
                    OutlinedTextField(
                        value = repsText,
                        onValueChange = { repsText = it.filter { c -> c.isDigit() } },
                        label = { Text("Reps") },
                        placeholder = { if (isMaxReps) Text("max") else Text("reps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // RPE (optional)
                    RpeSelector(selectedRpe = selectedRpe, onSelect = { selectedRpe = it })
                }

                else -> {
                    // Weighted exercise: weight + reps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = weightText,
                            onValueChange = { weightText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Weight") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(2f),
                            singleLine = true,
                            trailingIcon = { Text(unit, modifier = Modifier.padding(end = 8.dp)) }
                        )

                        // Unit toggle
                        Column(modifier = Modifier.weight(1f)) {
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

                    OutlinedTextField(
                        value = repsText,
                        onValueChange = { repsText = it.filter { c -> c.isDigit() } },
                        label = { Text("Reps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // RPE (optional)
                    RpeSelector(selectedRpe = selectedRpe, onSelect = { selectedRpe = it })
                }
            }

            Spacer(Modifier.height(4.dp))

            // Log Set button
            Button(
                onClick = {
                    val set = when {
                        isTimed -> SetLog(
                            setNumber = setNumber,
                            reps = 1,
                            weight = 0.0,
                            unit = "bw",
                            holdSecs = holdSecsText.toIntOrNull() ?: 0,
                            rpe = selectedRpe.takeIf { it > 0 }
                        )
                        isBodyweight -> SetLog(
                            setNumber = setNumber,
                            reps = repsText.toIntOrNull() ?: 0,
                            weight = 0.0,
                            unit = "bw",
                            rpe = selectedRpe.takeIf { it > 0 }
                        )
                        else -> SetLog(
                            setNumber = setNumber,
                            reps = repsText.toIntOrNull() ?: 0,
                            weight = weightText.toDoubleOrNull() ?: 0.0,
                            unit = unit,
                            rpe = selectedRpe.takeIf { it > 0 }
                        )
                    }
                    onLogSet(set)
                    dismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CherryBlossomPink)
            ) {
                Text("Log Set")
            }

            TextButton(
                onClick = { dismiss() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RpeSelector(
    selectedRpe: Int,
    onSelect: (Int) -> Unit
) {
    Column {
        Text(
            "RPE (optional)",
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            (6..10).forEach { rpe ->
                FilterChip(
                    selected = selectedRpe == rpe,
                    onClick = {
                        // Toggle: tap same again to deselect
                        onSelect(if (selectedRpe == rpe) 0 else rpe)
                    },
                    label = { Text(rpe.toString()) }
                )
            }
        }
    }
}
