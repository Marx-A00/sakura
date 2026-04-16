package com.sakura.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.sakura.ui.theme.CherryBlossomPink
import com.sakura.ui.theme.ForestGreen
import java.time.format.TextStyle
import java.util.Locale

/**
 * Page 2 of the workout dashboard card — weekly volume trend.
 *
 * Shows a combo bar+line chart: pink bars for daily volume, green trend line
 * for the 3-day moving average. Time range selector: 1W / 2W / 4W.
 *
 * Loading state: shows a CircularProgressIndicator until data is ready.
 * Empty state: shows "No workouts this period" when volumeData is empty.
 */
@Composable
fun WorkoutVolumeCard(
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
            text = "Weekly Volume",
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
                    CircularProgressIndicator(color = CherryBlossomPink)
                }
            }

            weeklyState.volumeData.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No workouts this period",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                // Legend
                VolumeLegend()

                // Combo chart
                VolumeChart(
                    volumeData = weeklyState.volumeData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
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
                    selectedContainerColor = CherryBlossomPink,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun VolumeLegend() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 12.dp, height = 8.dp)
                    .background(CherryBlossomPink, RoundedCornerShape(2.dp))
            )
            Text("Volume (kg)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 16.dp, height = 2.dp)
                    .background(ForestGreen)
            )
            Text("Trend", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VolumeChart(
    volumeData: List<DailyVolume>,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    val dayLabels = volumeData.map { dv ->
        dv.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
    }
    val valueFormatter = CartesianValueFormatter { _, x, _ ->
        dayLabels.getOrElse(x.toInt()) { "" }
    }

    LaunchedEffect(volumeData) {
        modelProducer.runTransaction {
            columnSeries { series(volumeData.map { it.volume }) }
            lineSeries { series(volumeData.map { it.trendValue }) }
        }
    }

    val volumeBar = rememberLineComponent(fill = Fill(SolidColor(CherryBlossomPink)))

    val columnLayer = rememberColumnCartesianLayer(
        columnProvider = ColumnCartesianLayer.ColumnProvider.series(volumeBar)
    )

    val trendLine = LineCartesianLayer.Line(
        fill = LineCartesianLayer.LineFill.single(Fill(SolidColor(ForestGreen))),
        stroke = LineCartesianLayer.LineStroke.Continuous()
    )
    val lineLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(trendLine)
    )

    val chart = rememberCartesianChart(
        columnLayer,
        lineLayer,
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
