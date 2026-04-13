package com.sakura.features.workoutlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom sheet for adjusting a running rest timer.
 *
 * Provides:
 * - Current remaining time display (M:SS)
 * - Quick-adjust buttons (-30s, -15s, +15s, +30s)
 * - Free-form seconds input with Set button
 * - Dismiss Timer (destructive) and OK (close without change) actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerAdjustSheet(
    sheetState: SheetState,
    currentRemainingSecs: Int,
    onAdjust: (deltaSecs: Int) -> Unit,
    onSetExact: (secs: Int) -> Unit,
    onDismissTimer: () -> Unit,
    onDismissSheet: () -> Unit
) {
    var customSecs by rememberSaveable { mutableStateOf("") }

    val mins = currentRemainingSecs / 60
    val secs = currentRemainingSecs % 60
    val timeText = "%d:%02d".format(mins, secs)

    ModalBottomSheet(
        onDismissRequest = onDismissSheet,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title + current time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Adjust Rest Timer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = timeText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100)
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Quick-adjust buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onAdjust(-30) },
                    modifier = Modifier.weight(1f)
                ) { Text("-30s") }
                OutlinedButton(
                    onClick = { onAdjust(-15) },
                    modifier = Modifier.weight(1f)
                ) { Text("-15s") }
                OutlinedButton(
                    onClick = { onAdjust(15) },
                    modifier = Modifier.weight(1f)
                ) { Text("+15s") }
                OutlinedButton(
                    onClick = { onAdjust(30) },
                    modifier = Modifier.weight(1f)
                ) { Text("+30s") }
            }

            Spacer(Modifier.height(16.dp))

            // Free-form seconds input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customSecs,
                    onValueChange = { customSecs = it.filter { c -> c.isDigit() } },
                    label = { Text("Seconds") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        val parsed = customSecs.toIntOrNull()
                        if (parsed != null) {
                            val clamped = parsed.coerceIn(5, 600)
                            onSetExact(clamped)
                            customSecs = ""
                        }
                    },
                    modifier = Modifier.padding(top = 4.dp)
                ) { Text("Set") }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Action row: Dismiss Timer (destructive) + OK
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismissTimer) {
                    Text(
                        "Dismiss Timer",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismissSheet) {
                    Text("OK")
                }
            }
        }
    }
}
