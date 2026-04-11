package com.sakura.features.workoutlog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sakura.data.workout.ExerciseDefinition
import com.sakura.ui.theme.CherryBlossomPink
import kotlinx.coroutines.launch

/**
 * ModalBottomSheet listing primary exercise name and all alternatives.
 * Shows checkmark on the currently selected option.
 * Uses invokeOnCompletion dismiss pattern from 02-02 decision.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    sheetState: SheetState,
    definition: ExerciseDefinition,
    selectedAlternative: String?,     // null = primary exercise selected
    onSelect: (String?) -> Unit,      // null = primary, non-null = alternative name
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

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
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                "Choose exercise",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Primary exercise option
            val isPrimarySelected = selectedAlternative == null
            ExerciseOptionRow(
                name = definition.name,
                isSelected = isPrimarySelected,
                onClick = {
                    onSelect(null)  // null = select primary
                    dismiss()
                }
            )

            // Alternative options
            definition.alternatives.forEach { alt ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                val isAltSelected = selectedAlternative == alt
                ExerciseOptionRow(
                    name = alt,
                    isSelected = isAltSelected,
                    onClick = {
                        onSelect(alt)
                        dismiss()
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ExerciseOptionRow(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) CherryBlossomPink else MaterialTheme.colorScheme.onSurface
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = CherryBlossomPink,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
