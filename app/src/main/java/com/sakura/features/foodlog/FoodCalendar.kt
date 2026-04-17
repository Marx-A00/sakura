package com.sakura.features.foodlog

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakura.ui.theme.MediumGray
import com.sakura.ui.theme.SakuraTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Collapsible food calendar that doubles as the primary date navigator.
 *
 * Header shows the currently selected date (tappable to open DatePicker)
 * plus an expand/collapse chevron.
 *
 * Collapsed (default): current week only.
 * Expanded: full 4-week rolling grid.
 *
 * Tapping a day cell navigates to that date. Days with food logged show a dot.
 */
@Composable
fun FoodCalendar(
    days: List<FoodCalendarDay>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDateLabelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val weeks = days.chunked(7)

    // Current week = the one containing today (last chunk in the 4-week window)
    val currentWeekIndex = weeks.indexOfFirst { week -> week.any { it.isToday } }
        .takeIf { it >= 0 } ?: (weeks.size - 1)

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
        ) {
            // Header row: selected date label + expand/collapse chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Selected date — tappable to open DatePickerDialog
                Text(
                    text = formatCalendarDate(selectedDate),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onDateLabelClick() }
                )

                // Expand/collapse toggle
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                                     else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

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

            // Week rows — show only current week when collapsed, all 4 when expanded
            val visibleWeeks = if (expanded) weeks else listOf(weeks.getOrElse(currentWeekIndex) { weeks.last() })

            visibleWeeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    week.forEach { day ->
                        FoodCalendarCell(
                            day = day,
                            isSelected = day.date == selectedDate,
                            onClick = { onDateSelected(day.date) },
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
private fun FoodCalendarCell(
    day: FoodCalendarDay,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha = if (!day.isPast && !day.isToday) 0.4f else 1f

    Column(
        modifier = modifier
            .alpha(alpha)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // Date number — filled circle for today, outline ring for selected (non-today)
        Box(
            modifier = when {
                day.isToday -> Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                isSelected -> Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                else -> Modifier.size(22.dp)
            },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                fontSize = 11.sp,
                fontWeight = if (day.isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    day.isToday -> MaterialTheme.colorScheme.onPrimary
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center
            )
        }

        // Small dot if food was logged on this day
        if (day.hasEntries && (day.isPast || day.isToday)) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(SakuraTheme.colors.brand)
            )
        } else {
            Spacer(Modifier.height(5.dp))
        }
    }
}

/** Format the selected date for the calendar header. */
private fun formatCalendarDate(date: LocalDate): String {
    val today = LocalDate.now()
    return when {
        date == today -> "Today, ${date.format(DateTimeFormatter.ofPattern("MMM d"))}"
        date == today.minusDays(1) -> "Yesterday, ${date.format(DateTimeFormatter.ofPattern("MMM d"))}"
        date == today.plusDays(1) -> "Tomorrow, ${date.format(DateTimeFormatter.ofPattern("MMM d"))}"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
}
