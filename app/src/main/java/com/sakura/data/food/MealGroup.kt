package com.sakura.data.food

/**
 * Domain model for a meal grouping within a day.
 * Aggregates food entries under a named label (e.g., "Breakfast").
 */
data class MealGroup(
    val label: String,              // "Breakfast", "Lunch", "Dinner", "Snacks"
    val entries: List<FoodEntry>
) {
    val totalCalories: Int get() = entries.sumOf { it.calories }
    val totalProtein: Int get() = entries.sumOf { it.protein }
    val totalCarbs: Int get() = entries.sumOf { it.carbs }
    val totalFat: Int get() = entries.sumOf { it.fat }
}
