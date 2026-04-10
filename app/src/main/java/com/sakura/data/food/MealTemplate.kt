package com.sakura.data.food

/**
 * Domain model for a saved meal template.
 * Templates let the user quickly log a recurring meal (e.g., "Weekday Breakfast").
 *
 * id: UUID string for stable identity.
 */
data class MealTemplate(
    val id: String,                         // UUID string
    val name: String,                       // e.g., "Weekday Breakfast"
    val entries: List<FoodLibraryItem>      // items comprising this template
)
