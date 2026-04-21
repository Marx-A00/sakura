package com.sakura.features.workoutlog

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Barbell
import com.adamglin.phosphoricons.regular.Barbell
import com.adamglin.phosphoricons.regular.CaretDown
import com.adamglin.phosphoricons.regular.CaretLeft
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.CaretUp
import com.adamglin.phosphoricons.regular.ClockCounterClockwise
import com.sakura.ui.theme.MediumGray
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SplitCalendar(
    days: List<CalendarDay>,
    selectedDate: LocalDate,
    displayedMonth: YearMonth,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChanged: (YearMonth) -> Unit,
    onTodayClick: () -> Unit,
    onDateLabelClick: () -> Unit,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val weeks = days.chunked(7)
    val isCurrentMonth = displayedMonth == YearMonth.now()
    val isOnToday = selectedDate == LocalDate.now()

    // Find week containing selected date for collapsed mode
    val selectedWeekIndex = weeks.indexOfFirst { week -> week.any { it.date == selectedDate } }
        .takeIf { it >= 0 } ?: 0

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
            // Header: month nav + today button + history + expand/collapse
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Month navigation
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = { onMonthChanged(displayedMonth.minusMonths(1)) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            PhosphorIcons.Regular.CaretLeft,
                            contentDescription = "Previous month",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = displayedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onDateLabelClick() }
                    )

                    IconButton(
                        onClick = { onMonthChanged(displayedMonth.plusMonths(1)) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            PhosphorIcons.Regular.CaretRight,
                            contentDescription = "Next month",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Today button — visible when not on today
                if (!isOnToday || !isCurrentMonth) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.clickable { onTodayClick() }
                    ) {
                        Text(
                            "Today",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // History button
                IconButton(
                    onClick = onHistoryClick,
                    modifier = Modifier.padding(start = 8.dp).size(32.dp)
                ) {
                    Icon(
                        imageVector = PhosphorIcons.Regular.ClockCounterClockwise,
                        contentDescription = "History",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Expand/collapse toggle
                IconButton(
                    onClick = { onExpandedChange(!expanded) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) PhosphorIcons.Regular.CaretUp
                                     else PhosphorIcons.Regular.CaretDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Day-of-week header row
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
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

            // Week rows with swipe detection
            val visibleWeeks = if (expanded) weeks else listOf(weeks.getOrElse(selectedWeekIndex) { weeks.firstOrNull() ?: emptyList() })

            var dragAccumulator by remember { mutableFloatStateOf(0f) }

            Column(
                modifier = Modifier.pointerInput(displayedMonth) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccumulator = 0f },
                        onHorizontalDrag = { _, dragAmount -> dragAccumulator += dragAmount },
                        onDragEnd = {
                            if (dragAccumulator > 100f) {
                                onMonthChanged(displayedMonth.minusMonths(1))
                            } else if (dragAccumulator < -100f) {
                                onMonthChanged(displayedMonth.plusMonths(1))
                            }
                        }
                    )
                }
            ) {
                visibleWeeks.forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        week.forEach { day ->
                            CalendarCell(
                                day = day,
                                isSelected = day.date == selectedDate,
                                isCurrentMonth = YearMonth.from(day.date) == displayedMonth,
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
}

@Composable
private fun CalendarCell(
    day: CalendarDay,
    isSelected: Boolean,
    isCurrentMonth: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimmed = !isCurrentMonth || (!day.isPast && !day.isToday)
    val alpha = if (dimmed) 0.3f else 1f

    Column(
        modifier = modifier
            .alpha(alpha)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = when {
                day.isToday -> Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                isSelected && isCurrentMonth -> Modifier
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
                    isSelected && isCurrentMonth -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center
            )
        }

        if (day.hasWorkout || day.isScheduled) {
            Icon(
                imageVector = if (day.isComplete) PhosphorIcons.Fill.Barbell
                              else PhosphorIcons.Regular.Barbell,
                contentDescription = if (day.isComplete) "Completed" else "Scheduled",
                modifier = Modifier.size(12.dp),
                tint = if (day.isComplete) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }
    }
}
