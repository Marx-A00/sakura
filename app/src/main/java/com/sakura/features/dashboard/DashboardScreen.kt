package com.sakura.features.dashboard

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.ui.theme.SakuraTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home/Dashboard screen — Phase 4 plan 01.
 *
 * Layout:
 * - Header: time-based greeting + today's date + SyncStatusBadge
 * - Food card: HorizontalPager (page 0 = FoodProgressCard, page 1 = chart placeholder)
 * - Workout card: HorizontalPager (page 0 = WorkoutSummaryCard, page 1 = chart placeholder)
 * - Dot indicators for pager pages
 */
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val state by viewModel.today.collectAsStateWithLifecycle()
    val weeklyState by viewModel.weekly.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()



    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = SakuraTheme.colors.accent)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header: greeting + date + sync badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = timeBasedGreeting(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = LocalDate.now().format(
                        DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!state.isLocalMode) {
                SyncStatusBadge(
                    syncStatus = state.syncStatus,
                    onTap = { message ->
                        scope.launch {
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                )
            }
        }

        // Food progress card with HorizontalPager
        DashboardPagerCard(
            label = "Nutrition",
            pageCount = 2,
            cardHeight = 320
        ) { page ->
            when (page) {
                0 -> FoodProgressCard(state = state)
                else -> FoodWeeklyCard(
                    weeklyState = weeklyState,
                    onWeeksChanged = { viewModel.loadWeekly(it) }
                )
            }
        }

        // Workout summary card with HorizontalPager
        DashboardPagerCard(
            label = "Workout",
            pageCount = 2,
            cardHeight = 280
        ) { page ->
            when (page) {
                0 -> WorkoutSummaryCard(state = state)
                else -> WorkoutVolumeCard(
                    weeklyState = weeklyState,
                    onWeeksChanged = { viewModel.loadWeekly(it) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Reusable ElevatedCard with HorizontalPager and dot indicators.
 *
 * @param label      Card title (not shown — used for accessibility)
 * @param pageCount  Number of pager pages
 * @param cardHeight Fixed height in dp
 * @param content    Page content composable by page index
 */
@Composable
private fun DashboardPagerCard(
    label: String,
    pageCount: Int,
    cardHeight: Int,
    content: @Composable (page: Int) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { pageCount })

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight.dp)
            ) { page ->
                content(page)
            }

            // Dot indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) { index ->
                    val isSelected = pagerState.currentPage == index
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (isSelected) 8.dp else 6.dp),
                        shape = CircleShape,
                        color = if (isSelected) SakuraTheme.colors.accent
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    ) {}
                }
            }
        }
    }
}

/** Returns a greeting based on the current hour of day. */
private fun timeBasedGreeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}
