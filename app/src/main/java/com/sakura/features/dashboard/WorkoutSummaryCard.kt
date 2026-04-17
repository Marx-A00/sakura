package com.sakura.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakura.ui.theme.SakuraTheme
import java.time.format.DateTimeFormatter

/** Amber color reused for "In progress" badge. */
private val InProgressAmber = Color(0xFFF59E0B)

/**
 * Page 0 of the workout HorizontalPager card.
 * Shows today's workout summary: template, exercises, and recent 3 workout days.
 */
@Composable
fun WorkoutSummaryCard(state: DashboardTodayState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header: dumbbell icon + "Workout" + status badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = SakuraTheme.colors.brand
                )
                Text(
                    text = "Workout",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state.hasWorkout) {
                WorkoutStatusBadge(isComplete = state.isWorkoutComplete)
            }
        }

        if (!state.hasWorkout) {
            // Rest day / no workout
            RestDayContent()
        } else {
            // Template name
            if (state.templateName != null) {
                Text(
                    text = state.templateName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Exercise list (up to 4)
            if (state.exercises.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.exercises.take(4).forEach { exercise ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = exercise.name,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            val setCount = exercise.sets.size
                            Text(
                                text = if (setCount > 0) "$setCount set${if (setCount != 1) "s" else ""}" else "No sets",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (state.exercises.size > 4) {
                        Text(
                            text = "+${state.exercises.size - 4} more",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Recent 3 workout days
        if (state.recentWorkoutDays.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.recentWorkoutDays.forEach { day ->
                    val dayFmt = DateTimeFormatter.ofPattern("EEE")
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = day.date.format(dayFmt),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (day.isComplete) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Complete",
                                modifier = Modifier.size(14.dp),
                                tint = SakuraTheme.colors.accent
                            )
                        } else {
                            Text(
                                text = day.splitName?.take(3) ?: "-",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutStatusBadge(isComplete: Boolean) {
    val (color, label) = if (isComplete) {
        SakuraTheme.colors.accent to "Complete"
    } else {
        InProgressAmber to "In progress"
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun RestDayContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
        Text(
            text = "Rest day",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Open Workout to log exercises",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
