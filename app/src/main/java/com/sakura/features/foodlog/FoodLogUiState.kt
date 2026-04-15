package com.sakura.features.foodlog

import com.sakura.data.food.MealGroup
import com.sakura.preferences.MacroTargets
import java.time.LocalDate

/**
 * UI state for the Food Log screen.
 * Sealed interface pattern ensures exhaustive handling of all states.
 */
sealed interface FoodLogUiState {

    data object Loading : FoodLogUiState

    data class Success(
        val meals: List<MealGroup>,
        val targets: MacroTargets
    ) : FoodLogUiState {
        val totalCalories: Int get() = meals.sumOf { it.totalCalories }
        val totalProtein: Int get() = meals.sumOf { it.totalProtein }
        val totalCarbs: Int get() = meals.sumOf { it.totalCarbs }
        val totalFat: Int get() = meals.sumOf { it.totalFat }
    }

    sealed interface Error : FoodLogUiState {
        data object FolderUnavailable : Error
        data class Generic(val message: String) : Error
    }
}

/**
 * A single day in the 4-week rolling calendar grid for the food log.
 * Simpler than the workout CalendarDay — just tracks whether food was logged.
 */
data class FoodCalendarDay(
    val date: LocalDate,
    val hasEntries: Boolean = false,
    val isPast: Boolean = true,
    val isToday: Boolean = false
)
