package com.sakura.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.sakura.ui.theme.SakuraTheme
import java.time.format.TextStyle
import java.util.Locale

/**
 * Page 2 of the food dashboard card — weekly macro breakdown.
 *
 * Shows a grouped bar chart (protein/carbs/fat per day) for the selected
 * time range (1W / 2W / 4W), an averages row, and a color legend.
 *
 * Loading state: shows a CircularProgressIndicator until data is ready.
 * Empty state: shows "No data for this period" when macroData is empty.
 */
@Composable
fun FoodWeeklyCard(
    weeklyState: WeeklyAnalyticsState,
    onWeeksChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Title
        Text(
            text = "Weekly Macros",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Time range selector (always visible, even during loading)
        TimeRangeTabs(
            selected = weeklyState.selectedWeeks,
            onSelected = onWeeksChanged
        )

        when {
            weeklyState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SakuraTheme.colors.accent)
                }
            }

            weeklyState.macroData.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No data for this period",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                // Color legend
                MacroLegend()

                // Grouped bar chart
                MacroChart(
                    macroData = weeklyState.macroData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                // Averages row
                AveragesRow(
                    avgProtein = weeklyState.avgProtein,
                    avgCarbs = weeklyState.avgCarbs,
                    avgFat = weeklyState.avgFat
                )
            }
        }
    }
}

@Composable
private fun TimeRangeTabs(
    selected: Int,
    onSelected: (Int) -> Unit
) {
    val options = listOf(1 to "1W", 2 to "2W", 4 to "4W")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (weeks, label) ->
            FilterChip(
                selected = selected == weeks,
                onClick = { onSelected(weeks) },
                label = { Text(text = label, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SakuraTheme.colors.accent,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun MacroLegend() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendDot(color = SakuraTheme.colors.proteinBar, label = "Protein")
        LegendDot(color = SakuraTheme.colors.carbsBar, label = "Carbs")
        LegendDot(color = SakuraTheme.colors.fatBar, label = "Fat")
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MacroChart(
    macroData: List<DailyMacros>,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    // Build day label formatter
    val dayLabels = macroData.map { dm ->
        dm.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
    }
    val valueFormatter = CartesianValueFormatter { _, x, _ ->
        dayLabels.getOrElse(x.toInt()) { "" }
    }

    LaunchedEffect(macroData) {
        modelProducer.runTransaction {
            columnSeries { series(macroData.map { it.protein }) }
            columnSeries { series(macroData.map { it.carbs }) }
            columnSeries { series(macroData.map { it.fat }) }
        }
    }

    val proteinColor = SakuraTheme.colors.proteinBar
    val carbsColor = SakuraTheme.colors.carbsBar
    val fatColor = SakuraTheme.colors.fatBar
    val proteinColumn = rememberLineComponent(fill = Fill(SolidColor(proteinColor)))
    val carbsColumn = rememberLineComponent(fill = Fill(SolidColor(carbsColor)))
    val fatColumn = rememberLineComponent(fill = Fill(SolidColor(fatColor)))

    val columnLayer = rememberColumnCartesianLayer(
        columnProvider = ColumnCartesianLayer.ColumnProvider.series(
            proteinColumn,
            carbsColumn,
            fatColumn
        ),
        mergeMode = { ColumnCartesianLayer.MergeMode.Grouped() }
    )

    val chart = rememberCartesianChart(
        columnLayer,
        startAxis = VerticalAxis.rememberStart(),
        bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = valueFormatter)
    )

    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            modifier = modifier
        )
    }
}

@Composable
private fun AveragesRow(
    avgProtein: Int,
    avgCarbs: Int,
    avgFat: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MacroAvgChip(
            label = "Protein",
            value = "${avgProtein}g",
            color = SakuraTheme.colors.proteinBar,
            modifier = Modifier.weight(1f)
        )
        MacroAvgChip(
            label = "Carbs",
            value = "${avgCarbs}g",
            color = SakuraTheme.colors.carbsBar,
            modifier = Modifier.weight(1f)
        )
        MacroAvgChip(
            label = "Fat",
            value = "${avgFat}g",
            color = SakuraTheme.colors.fatBar,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MacroAvgChip(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Avg $label",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}
