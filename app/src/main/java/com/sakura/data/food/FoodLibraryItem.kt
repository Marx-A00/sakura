package com.sakura.data.food

/**
 * Domain model for a saved food item in the user's personal library.
 * Library items are reusable across multiple log entries.
 *
 * id: UUID string for stable identity across library mutations.
 */
data class FoodLibraryItem(
    val id: String,             // UUID string for stable identity
    val name: String,
    val protein: Int,           // grams
    val carbs: Int,             // grams
    val fat: Int,               // grams
    val calories: Int,          // kcal
    val servingSize: String?,   // e.g., "100"
    val servingUnit: String?    // e.g., "g", "ml"
)
