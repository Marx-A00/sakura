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
import java.time.LocalDate

/**
 * ViewModel for the Home/Dashboard screen (Phase 4 plan 01).
 *
 * Aggregates today's food macro totals, today's workout summary, recent history,
 * and sync status into DashboardTodayState.
 *
 * WeeklyAnalyticsState is a stub — Plan 02 will implement chart data loading.
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
     * Stub — Plan 02 will implement weekly chart data loading.
     * Sets selectedWeeks and marks as loading until Plan 02 populates macroData/volumeData.
     */
    fun loadWeekly(weeks: Int) {
        _weekly.value = WeeklyAnalyticsState(isLoading = true, selectedWeeks = weeks)
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
