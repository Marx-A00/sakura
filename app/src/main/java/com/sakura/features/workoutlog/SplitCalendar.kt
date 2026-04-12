package com.sakura.features.workoutlog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakura.data.workout.SplitDay
import com.sakura.ui.theme.CherryBlossomPink
import com.sakura.ui.theme.DeepRose
import com.sakura.ui.theme.ForestGreen
import com.sakura.ui.theme.MediumGray
import com.sakura.ui.theme.WarmBrown

/**
 * 4-week rolling training calendar grid.
 *
 * Renders exactly 4 rows × 7 columns (28 days) with:
 * - Day-of-week header row (M T W T F S S)
 * - Date number in each cell
 * - Split label with color coding by split type
 * - Completion checkmark for completed days
 * - Circle highlight for today
 * - Faded alpha for future days
 *
 * Layout uses Column + Row (not LazyVerticalGrid) for predictable sizing.
 */
@Composable
fun SplitCalendar(
    days: List<CalendarDay>,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Training Calendar",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Day-of-week header row: M T W T F S S
            Row(modifier = Modifier.fillMaxWidth()) {
                val dayHeaders = listOf("M", "T", "W", "T", "F", "S", "S")
                dayHeaders.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        color = MediumGray
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // 4 week rows — 7 cells each
            val chunked = days.chunked(7)
            chunked.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    week.forEach { day ->
                        CalendarCell(
                            day = day,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun CalendarCell(
    day: CalendarDay,
    modifier: Modifier = Modifier
) {
    val alpha = if (!day.isPast && !day.isToday) 0.4f else 1f

    Column(
        modifier = modifier
            .alpha(alpha)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // Date number — circle background for today
        Box(
            modifier = if (day.isToday) {
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            } else {
                Modifier.size(22.dp)
            },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                fontSize = 11.sp,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (day.isToday) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }

        // Split label (only for past/today days with a session)
        if (day.splitLabel != null && day.isPast) {
            Text(
                text = splitShortLabel(day.splitLabel),
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                color = splitColor(day.splitDay),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            // Empty space to keep row height consistent
            Spacer(Modifier.height(12.dp))
        }

        // Completion checkmark
        if (day.isComplete) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Complete",
                modifier = Modifier.size(10.dp),
                tint = ForestGreen
            )
        } else {
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** Returns a short abbreviated label for display in the tiny calendar cell. */
private fun splitShortLabel(label: String): String {
    return when {
        label.contains("Lift", ignoreCase = true) -> "Lift"
        label.contains("Cali", ignoreCase = true) -> "Cali"
        label.contains("Push", ignoreCase = true) -> "Push"
        label.contains("Pull", ignoreCase = true) -> "Pull"
        label.contains("Leg", ignoreCase = true) -> "Legs"
        label.length > 4 -> label.take(4)
        else -> label
    }
}

/** Color coding by split type. */
private fun splitColor(splitDay: SplitDay?): Color {
    return when {
        splitDay == null -> MediumGray
        splitDay.label.contains("lift") -> CherryBlossomPink
        splitDay.label.contains("calisthenics") -> ForestGreen
        splitDay.label.contains("push") -> CherryBlossomPink
        splitDay.label.contains("pull") -> ForestGreen
        splitDay.label.contains("leg") -> WarmBrown
        else -> DeepRose
    }
}
