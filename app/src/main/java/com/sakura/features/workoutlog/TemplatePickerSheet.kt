package com.sakura.features.workoutlog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakura.data.workout.SplitDay
import com.sakura.data.workout.UserWorkoutTemplate
import com.sakura.data.workout.WorkoutTemplates

/**
 * Bottom sheet that lets the user pick a workout template — either a built-in
 * SplitDay template or a user-created [UserWorkoutTemplate].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePickerSheet(
    sheetState: SheetState,
    userTemplates: List<UserWorkoutTemplate>,
    onSelectBuiltIn: (SplitDay) -> Unit,
    onSelectUserTemplate: (UserWorkoutTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Section: Built-in templates
            item {
                Text(
                    text = "Built-in",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(SplitDay.entries.toList()) { splitDay ->
                val template = WorkoutTemplates.forDay(splitDay)
                TemplateRow(
                    name = splitDay.displayName,
                    exerciseCount = template.exercises.size,
                    onClick = { onSelectBuiltIn(splitDay) }
                )
            }

            // Section: User templates
            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Text(
                    text = "My Workouts",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (userTemplates.isEmpty()) {
                item {
                    Text(
                        text = "No saved workouts yet — create one from the Exercise Library",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            } else {
                items(userTemplates, key = { it.id }) { template ->
                    TemplateRow(
                        name = template.name,
                        exerciseCount = template.exercises.size,
                        onClick = { onSelectUserTemplate(template) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(
    name: String,
    exerciseCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Text(
                text = "$exerciseCount exercises",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
