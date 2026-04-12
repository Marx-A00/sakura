package com.sakura.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakura.ui.theme.CherryBlossomPink
import com.sakura.ui.theme.DeepRose
import com.sakura.ui.theme.ForestGreen
import com.sakura.ui.theme.WarmBrown
import java.text.NumberFormat
import java.time.format.DateTimeFormatter

/**
 * Page 0 of the food HorizontalPager card.
 * Shows today's calorie + macro progress and a recent-3-days row.
 */
@Composable
fun FoodProgressCard(state: DashboardTodayState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Big calorie number
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val fmt = NumberFormat.getNumberInstance()
            Text(
                text = fmt.format(state.totalCalories),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Column {
                Text(
                    text = "/ ${fmt.format(state.targetCalories)} kcal",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val remaining = state.targetCalories - state.totalCalories
                val remainingColor = if (remaining >= 0) ForestGreen else DeepRose
                Text(
                    text = if (remaining >= 0) "${fmt.format(remaining)} left"
                    else "${fmt.format(-remaining)} over",
                    fontSize = 12.sp,
                    color = remainingColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Full-width calorie progress bar
        val calProgress = if (state.targetCalories > 0)
            (state.totalCalories.toFloat() / state.targetCalories).coerceIn(0f, 1f)
        else 0f
        LinearProgressIndicator(
            progress = { calProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = CherryBlossomPink,
            trackColor = CherryBlossomPink.copy(alpha = 0.2f)
        )

        // Three macro mini-cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MacroMiniCard(
                label = "Protein",
                logged = state.totalProtein,
                target = state.targetProtein,
                unit = "g",
                color = ForestGreen,
                modifier = Modifier.weight(1f)
            )
            MacroMiniCard(
                label = "Carbs",
                logged = state.totalCarbs,
                target = state.targetCarbs,
                unit = "g",
                color = WarmBrown,
                modifier = Modifier.weight(1f)
            )
            MacroMiniCard(
                label = "Fat",
                logged = state.totalFat,
                target = state.targetFat,
                unit = "g",
                color = DeepRose,
                modifier = Modifier.weight(1f)
            )
        }

        // Recent 3 days row
        if (state.recentFoodDays.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.recentFoodDays.forEach { day ->
                    val dayFmt = DateTimeFormatter.ofPattern("EEE")
                    val calFmt = NumberFormat.getNumberInstance()
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = day.date.format(dayFmt),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (day.totalCalories > 0) calFmt.format(day.totalCalories) else "-",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroMiniCard(
    label: String,
    logged: Int,
    target: Int,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val progress = if (target > 0) (logged.toFloat() / target).coerceIn(0f, 1f) else 0f
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
            Text("$logged/$target$unit", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}
