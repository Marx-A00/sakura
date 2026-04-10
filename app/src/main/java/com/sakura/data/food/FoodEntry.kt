package com.sakura.data.food

/**
 * Domain model for a single food log entry.
 * Decoupled from the org-file representation (OrgFoodEntry).
 *
 * id: epoch millis assigned at creation time — used as a stable identifier
 *     for update/delete operations without requiring a database.
 */
data class FoodEntry(
    val id: Long,               // epoch millis assigned at creation time
    val name: String,           // may be blank for unnamed/quick entries
    val protein: Int,           // grams
    val carbs: Int,             // grams
    val fat: Int,               // grams
    val calories: Int,          // kcal
    val servingSize: String?,   // e.g., "200" — reference info only
    val servingUnit: String?,   // e.g., "g", "ml", "oz"
    val notes: String?          // optional free text
)
