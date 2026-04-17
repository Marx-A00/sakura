package com.sakura.data.food

import java.time.LocalDate

/**
 * Interface for all food data operations.
 * Implementations read/write the org-file data store via SyncBackend.
 *
 * All methods are suspend — callers must be in a coroutine scope.
 * Read methods return data directly; mutation methods return Result<Unit>
 * so callers can handle I/O errors gracefully.
 */
interface FoodRepository {

    // -------------------------------------------------------------------------
    // Daily log
    // -------------------------------------------------------------------------

    /**
     * Load all meal groups for a given date.
     * Returns an empty list if no entries exist for that date.
     */
    suspend fun loadDay(date: LocalDate): List<MealGroup>

    /**
     * Add a food entry to the specified meal on a given date.
     * Creates the date section and meal group if they don't exist yet.
     */
    suspend fun addEntry(date: LocalDate, mealLabel: String, entry: FoodEntry): Result<Unit>

    /**
     * Replace an existing entry identified by entryId (epoch millis).
     * Returns failure if the entry is not found.
     */
    suspend fun updateEntry(date: LocalDate, mealLabel: String, entryId: Long, updated: FoodEntry): Result<Unit>

    /**
     * Remove an entry identified by entryId from the specified meal.
     * Removes the meal group if it becomes empty.
     * Removes the date section if it becomes empty.
     */
    suspend fun deleteEntry(date: LocalDate, mealLabel: String, entryId: Long): Result<Unit>

    // -------------------------------------------------------------------------
    // Food library
    // -------------------------------------------------------------------------

    /** Load all items from the user's food library. */
    suspend fun loadLibrary(): List<FoodLibraryItem>

    /** Save a new item to the food library (or replace if id matches). */
    suspend fun saveToLibrary(item: FoodLibraryItem): Result<Unit>

    /** Remove an item from the food library by id. */
    suspend fun deleteFromLibrary(itemId: String): Result<Unit>

    // -------------------------------------------------------------------------
    // Meal templates
    // -------------------------------------------------------------------------

    /** Load all saved meal templates. */
    suspend fun loadTemplates(): List<MealTemplate>

    /** Save a new template (or replace if id matches). */
    suspend fun saveTemplate(template: MealTemplate): Result<Unit>

    /**
     * Apply a template by logging all its items to the specified meal on a date.
     * Each item gets a new unique id (System.currentTimeMillis() + index offset).
     */
    suspend fun applyTemplate(date: LocalDate, mealLabel: String, template: MealTemplate): Result<Unit>

    /** Remove a meal template by id. */
    suspend fun deleteTemplate(templateId: String): Result<Unit>

    // -------------------------------------------------------------------------
    // Day templates (full day of eating)
    // -------------------------------------------------------------------------

    /** Load all saved day templates. */
    suspend fun loadDayTemplates(): List<DayTemplate>

    /** Save a new day template (or replace if id matches). */
    suspend fun saveDayTemplate(template: DayTemplate): Result<Unit>

    /**
     * Apply a day template — logs all foods from every meal in the template
     * to the specified date. Each item gets a new unique id.
     */
    suspend fun applyDayTemplate(date: LocalDate, template: DayTemplate): Result<Unit>

    /** Remove a day template by id. */
    suspend fun deleteDayTemplate(templateId: String): Result<Unit>

    // -------------------------------------------------------------------------
    // Calendar helpers
    // -------------------------------------------------------------------------

    /**
     * Return the set of dates that have at least one food entry logged.
     * Used by the food calendar to show a "logged" indicator dot.
     */
    suspend fun loadLoggedDates(): Set<LocalDate>

    // -------------------------------------------------------------------------
    // Recent items
    // -------------------------------------------------------------------------

    /**
     * Load the most recently used distinct food items across all log dates.
     * Useful for quick-add suggestions.
     */
    suspend fun loadRecentItems(limit: Int = 20): List<FoodLibraryItem>
}
