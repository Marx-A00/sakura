package com.sakura.features.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.sakura.data.food.FoodRepository
import com.sakura.data.workout.WorkoutRepository
import com.sakura.preferences.AppPreferencesRepository
import com.sakura.preferences.MacroTargets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class ProgressViewModel(
    private val foodRepo: FoodRepository,
    private val workoutRepo: WorkoutRepository,
    private val prefsRepo: AppPreferencesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(isLoading = true)

        viewModelScope.launch(Dispatchers.IO) {
            val today = LocalDate.now()
            val sevenDaysAgo = today.minusDays(6)

            // 1. Load macro targets
            val targets = try {
                prefsRepo.macroTargets.first()
            } catch (e: Exception) {
                MacroTargets(2000, 150, 250, 65)
            }

            // 2. Load all food-logged dates
            val loggedDates: Set<LocalDate> = try {
                foodRepo.loadLoggedDates()
            } catch (e: Exception) {
                emptySet()
            }

            // 3. Compute streak — walk backwards from today (or yesterday if today not logged)
            val streak = computeStreak(today, loggedDates)

            // 4. Last 7 days logged (index 0 = 6 days ago, index 6 = today)
            val last7 = (0L..6L).map { offset ->
                sevenDaysAgo.plusDays(offset) in loggedDates
            }
            val foodCount = last7.count { it }

            // 5. Workout days in last 7
            val workoutDates: Set<LocalDate> = try {
                workoutRepo.loadHistory()
                    .filter { !it.date.isBefore(sevenDaysAgo) && !it.date.isAfter(today) }
                    .map { it.date }
                    .toSet()
            } catch (e: Exception) {
                emptySet()
            }

            // 6. Macros hit days — only check dates that have food logged
            var macrosHitCount = 0
            for (offset in 0L..6L) {
                val date = sevenDaysAgo.plusDays(offset)
                if (date !in loggedDates) continue

                val meals = try { foodRepo.loadDay(date) } catch (e: Exception) { emptyList() }
                val totalCal = meals.sumOf { it.totalCalories }
                val totalPro = meals.sumOf { it.totalProtein }
                val totalCarbs = meals.sumOf { it.totalCarbs }
                val totalFat = meals.sumOf { it.totalFat }

                val hitCal = targets.calories <= 0 || totalCal >= (targets.calories * 0.9)
                val hitPro = targets.protein <= 0 || totalPro >= (targets.protein * 0.9)
                val hitCarbs = targets.carbs <= 0 || totalCarbs >= (targets.carbs * 0.9)
                val hitFat = targets.fat <= 0 || totalFat >= (targets.fat * 0.9)

                if (hitCal && hitPro && hitCarbs && hitFat) macrosHitCount++
            }

            _state.value = ProgressUiState(
                isLoading = false,
                streakDays = streak,
                last7DaysLogged = last7,
                foodDaysCount = foodCount,
                workoutDaysCount = workoutDates.size,
                macrosHitDaysCount = macrosHitCount
            )
        }
    }

    private fun computeStreak(today: LocalDate, loggedDates: Set<LocalDate>): Int {
        // If today is logged, start counting from today. Otherwise start from yesterday
        // so the streak doesn't reset to 0 before the user logs today's food.
        val start = if (today in loggedDates) today else today.minusDays(1)
        var count = 0
        var date = start
        while (date in loggedDates) {
            count++
            date = date.minusDays(1)
        }
        return count
    }

    companion object {
        fun factory(
            foodRepo: FoodRepository,
            workoutRepo: WorkoutRepository,
            prefsRepo: AppPreferencesRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return ProgressViewModel(foodRepo, workoutRepo, prefsRepo) as T
                }
            }
    }
}
