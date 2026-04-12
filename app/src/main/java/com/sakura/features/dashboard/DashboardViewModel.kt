package com.sakura.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.sakura.data.food.FoodRepository
import com.sakura.data.workout.WorkoutRepository
import com.sakura.preferences.AppPreferencesRepository
import com.sakura.sync.SyncBackend
import com.sakura.sync.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * ViewModel for the Home/Dashboard screen (Phase 4).
 *
 * Aggregates today's food macro totals, today's workout summary, recent history,
 * and sync status into DashboardTodayState.
 *
 * WeeklyAnalyticsState is populated by loadWeekly() — called on init for 1W
 * and on demand when the user switches time ranges.
 */
class DashboardViewModel(
    private val foodRepo: FoodRepository,
    private val workoutRepo: WorkoutRepository,
    private val prefsRepo: AppPreferencesRepository,
    private val syncBackend: SyncBackend
) : ViewModel() {

    private val _today = MutableStateFlow(DashboardTodayState())
    val today: StateFlow<DashboardTodayState> = _today.asStateFlow()

    private val _weekly = MutableStateFlow(WeeklyAnalyticsState())
    val weekly: StateFlow<WeeklyAnalyticsState> = _weekly.asStateFlow()

    init {
        refresh()
        loadWeekly(1)
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _today.value = _today.value.copy(isLoading = true)

            val today = LocalDate.now()

            // Load food data for today
            val todayMeals = try { foodRepo.loadDay(today) } catch (e: Exception) { emptyList() }
            val totalCalories = todayMeals.sumOf { it.totalCalories }
            val totalProtein = todayMeals.sumOf { it.totalProtein }
            val totalCarbs = todayMeals.sumOf { it.totalCarbs }
            val totalFat = todayMeals.sumOf { it.totalFat }

            // Load macro targets
            val targets = try { prefsRepo.macroTargets.first() } catch (e: Exception) { null }

            // Load recent 3 food days (yesterday, day-2, day-3)
            val recentFoodDays = mutableListOf<RecentDay>()
            for (daysBack in 1..3) {
                val date = today.minusDays(daysBack.toLong())
                val meals = try { foodRepo.loadDay(date) } catch (e: Exception) { emptyList() }
                val cals = meals.sumOf { it.totalCalories }
                recentFoodDays.add(RecentDay(date, cals))
            }

            // Load today's workout
            val todaySession = try { workoutRepo.loadSession(today) } catch (e: Exception) { null }
            val hasWorkout = todaySession != null && todaySession.exercises.isNotEmpty()

            // Load recent 3 workout sessions
            val history = try { workoutRepo.loadHistory() } catch (e: Exception) { emptyList() }
            val recentWorkoutDays = history.take(3).map { session ->
                RecentWorkoutDay(
                    date = session.date,
                    splitName = session.templateName,
                    isComplete = session.isComplete
                )
            }

            // Check sync status
            val syncStatus = try {
                syncBackend.checkSyncStatus()
            } catch (e: Exception) {
                SyncStatus(null, false, false)
            }

            _today.value = DashboardTodayState(
                isLoading = false,
                totalCalories = totalCalories,
                totalProtein = totalProtein,
                totalCarbs = totalCarbs,
                totalFat = totalFat,
                targetCalories = targets?.calories ?: 2000,
                targetProtein = targets?.protein ?: 150,
                targetCarbs = targets?.carbs ?: 250,
                targetFat = targets?.fat ?: 65,
                recentFoodDays = recentFoodDays,
                splitDay = todaySession?.splitDay,
                templateName = todaySession?.templateName,
                exercises = todaySession?.exercises ?: emptyList(),
                isWorkoutComplete = todaySession?.isComplete ?: false,
                hasWorkout = hasWorkout,
                recentWorkoutDays = recentWorkoutDays,
                syncStatus = syncStatus
            )
        }
    }

    /**
     * Load weekly analytics data for the given number of weeks (1, 2, or 4).
     *
     * Sets isLoading = true immediately (before the IO coroutine) so composables
     * can show a spinner right away. Runs food and workout loading concurrently
     * with the today refresh (separate coroutines on Dispatchers.IO).
     */
    fun loadWeekly(weeks: Int) {
        // Set loading state immediately — before launching IO coroutine
        _weekly.value = _weekly.value.copy(isLoading = true, selectedWeeks = weeks)

        viewModelScope.launch(Dispatchers.IO) {
            val today = LocalDate.now()
            val startDate = today.minusDays((weeks * 7 - 1).toLong())
            val dateRange = generateSequence(startDate) { it.plusDays(1) }
                .takeWhile { !it.isAfter(today) }
                .toList()

            // ----------------------------------------------------------------
            // Food: load each day and aggregate macros
            // ----------------------------------------------------------------
            val macroData = mutableListOf<DailyMacros>()
            for (date in dateRange) {
                val meals = try { foodRepo.loadDay(date) } catch (e: Exception) { emptyList() }
                val protein = meals.sumOf { it.totalProtein }
                val carbs = meals.sumOf { it.totalCarbs }
                val fat = meals.sumOf { it.totalFat }
                macroData.add(DailyMacros(date, protein, carbs, fat))
            }

            // Compute averages
            val avgProtein = if (macroData.isNotEmpty())
                macroData.map { it.protein }.average().toInt() else 0
            val avgCarbs = if (macroData.isNotEmpty())
                macroData.map { it.carbs }.average().toInt() else 0
            val avgFat = if (macroData.isNotEmpty())
                macroData.map { it.fat }.average().toInt() else 0

            // ----------------------------------------------------------------
            // Workout: load history once, filter to date range, compute volume
            // ----------------------------------------------------------------
            val allSessions = try { workoutRepo.loadHistory() } catch (e: Exception) { emptyList() }
            val sessionMap = allSessions
                .filter { !it.date.isBefore(startDate) && !it.date.isAfter(today) }
                .associateBy { it.date }

            // Build daily volume list
            val rawVolumes = dateRange.map { date ->
                date to (sessionMap[date]?.totalVolume ?: 0.0)
            }

            // Compute 3-day moving average trend
            val volumeData = rawVolumes.mapIndexed { idx, (date, vol) ->
                // Collect up to 3 days ending at idx (inclusive) that have data
                val windowValues = (maxOf(0, idx - 2)..idx)
                    .map { rawVolumes[it].second }
                    .filter { it > 0.0 }
                val trendValue = if (windowValues.isNotEmpty()) windowValues.average() else 0.0
                DailyVolume(date, vol, trendValue)
            }

            // Carry forward trendValue for zero-volume days after first workout
            val volumeDataFilled = buildList {
                var lastTrend = 0.0
                for (dv in volumeData) {
                    val trend = if (dv.trendValue > 0.0) {
                        lastTrend = dv.trendValue
                        dv.trendValue
                    } else {
                        lastTrend
                    }
                    add(dv.copy(trendValue = trend))
                }
            }

            _weekly.value = WeeklyAnalyticsState(
                isLoading = false,
                macroData = macroData,
                volumeData = volumeDataFilled,
                selectedWeeks = weeks,
                avgProtein = avgProtein,
                avgCarbs = avgCarbs,
                avgFat = avgFat
            )
        }
    }

    // -------------------------------------------------------------------------
    // Factory — same pattern as FoodLogViewModel and WorkoutLogViewModel
    // -------------------------------------------------------------------------

    companion object {
        fun factory(
            foodRepo: FoodRepository,
            workoutRepo: WorkoutRepository,
            prefsRepo: AppPreferencesRepository,
            syncBackend: SyncBackend
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return DashboardViewModel(foodRepo, workoutRepo, prefsRepo, syncBackend) as T
                }
            }
    }
}
