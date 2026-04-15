package com.sakura.data.food

import com.sakura.orgengine.OrgFoodEntry
import com.sakura.orgengine.OrgFile
import com.sakura.orgengine.OrgDateSection
import com.sakura.orgengine.OrgLibraryEntry
import com.sakura.orgengine.OrgLibraryFile
import com.sakura.orgengine.OrgMealGroup
import com.sakura.orgengine.OrgMealTemplate
import com.sakura.orgengine.OrgParser
import com.sakura.orgengine.OrgTemplateFile
import com.sakura.orgengine.OrgWriter
import com.sakura.sync.SyncBackend
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Org-file-backed implementation of FoodRepository.
 *
 * Manages three org files via SyncBackend:
 *   - food-log.org    (daily food entries, date + meal hierarchy)
 *   - food-library.org (saved food items for reuse)
 *   - meal-templates.org (saved meal templates)
 *
 * Thread safety: all file mutations are serialized through [fileMutex] to prevent
 * corruption when rapid add/add or add/undo operations fire concurrently.
 * Read operations do not acquire the mutex.
 */
class OrgFoodRepository(
    private val syncBackend: SyncBackend,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : FoodRepository {

    private val fileMutex = Mutex()

    companion object {
        const val FOOD_LOG_FILE = "food-log.org"
        const val LIBRARY_FILE = "food-library.org"
        const val TEMPLATES_FILE = "meal-templates.org"
    }

    // =========================================================================
    // Daily log — read
    // =========================================================================

    override suspend fun loadDay(date: LocalDate): List<MealGroup> {
        return try {
            val content = syncBackend.readFile(FOOD_LOG_FILE)
            if (content.isBlank()) return emptyList()
            val orgFile = OrgParser.parse(content, OrgParser.ParseMode.FOOD)
            val section = orgFile.sections.find { it.date == date } ?: return emptyList()
            section.meals.map { meal ->
                MealGroup(
                    label = meal.label,
                    entries = meal.entries.map { it.toFoodEntry() }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // =========================================================================
    // Daily log — mutations
    // =========================================================================

    override suspend fun addEntry(date: LocalDate, mealLabel: String, entry: FoodEntry): Result<Unit> {
        return fileMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    addEntryInternal(date, mealLabel, entry)
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Internal add that assumes the mutex is already held.
     * Used by [applyTemplate] to batch-add without re-acquiring the mutex per entry.
     */
    private suspend fun addEntryInternal(date: LocalDate, mealLabel: String, entry: FoodEntry) {
        val content = syncBackend.readFile(FOOD_LOG_FILE)
        val orgFile = if (content.isBlank()) OrgFile(sections = emptyList())
                      else OrgParser.parse(content, OrgParser.ParseMode.FOOD)

        val orgEntry = entry.toOrgFoodEntry()
        val updatedSections = upsertEntry(orgFile.sections, date, mealLabel, orgEntry)
        val updatedFile = orgFile.copy(sections = updatedSections)
        syncBackend.writeFile(FOOD_LOG_FILE, OrgWriter.write(updatedFile))
    }

    override suspend fun updateEntry(
        date: LocalDate,
        mealLabel: String,
        entryId: Long,
        updated: FoodEntry
    ): Result<Unit> {
        return fileMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    val content = syncBackend.readFile(FOOD_LOG_FILE)
                    if (content.isBlank()) return@withContext Result.failure(
                        NoSuchElementException("Entry $entryId not found — food-log.org is empty")
                    )
                    val orgFile = OrgParser.parse(content, OrgParser.ParseMode.FOOD)
                    val updatedSections = replaceEntry(orgFile.sections, date, mealLabel, entryId, updated.toOrgFoodEntry())
                    val updatedFile = orgFile.copy(sections = updatedSections)
                    syncBackend.writeFile(FOOD_LOG_FILE, OrgWriter.write(updatedFile))
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    override suspend fun deleteEntry(date: LocalDate, mealLabel: String, entryId: Long): Result<Unit> {
        return fileMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    val content = syncBackend.readFile(FOOD_LOG_FILE)
                    if (content.isBlank()) return@withContext Result.failure(
                        NoSuchElementException("Entry $entryId not found — food-log.org is empty")
                    )
                    val orgFile = OrgParser.parse(content, OrgParser.ParseMode.FOOD)
                    val updatedSections = removeEntry(orgFile.sections, date, mealLabel, entryId)
                    val updatedFile = orgFile.copy(sections = updatedSections)
                    syncBackend.writeFile(FOOD_LOG_FILE, OrgWriter.write(updatedFile))
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    // =========================================================================
    // Food library
    // =========================================================================

    override suspend fun loadLibrary(): List<FoodLibraryItem> {
        return try {
            val content = syncBackend.readFile(LIBRARY_FILE)
            if (content.isBlank()) return emptyList()
            OrgParser.parseLibrary(content).items.map { it.toFoodLibraryItem() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun saveToLibrary(item: FoodLibraryItem): Result<Unit> {
        return fileMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    val content = syncBackend.readFile(LIBRARY_FILE)
                    val libraryFile = if (content.isBlank()) OrgLibraryFile(items = emptyList())
                                     else OrgParser.parseLibrary(content)

                    // Replace existing item if id matches, otherwise append
                    val newEntry = item.toOrgLibraryEntry()
                    val existingIndex = libraryFile.items.indexOfFirst { it.id == item.id }
                    val updatedItems = if (existingIndex >= 0) {
                        libraryFile.items.toMutableList().also { it[existingIndex] = newEntry }
                    } else {
                        libraryFile.items + newEntry
                    }
                    val updatedLibrary = libraryFile.copy(items = updatedItems)
                    syncBackend.writeFile(LIBRARY_FILE, OrgWriter.writeLibrary(updatedLibrary))
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    override suspend fun deleteFromLibrary(itemId: String): Result<Unit> {
        return fileMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    val content = syncBackend.readFile(LIBRARY_FILE)
                    if (content.isBlank()) return@withContext Result.success(Unit)
                    val libraryFile = OrgParser.parseLibrary(content)
                    val updatedItems = libraryFile.items.filter { it.id != itemId }
                    val updatedLibrary = libraryFile.copy(items = updatedItems)
                    syncBackend.writeFile(LIBRARY_FILE, OrgWriter.writeLibrary(updatedLibrary))
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    // =========================================================================
    // Meal templates
    // =========================================================================

    override suspend fun loadTemplates(): List<MealTemplate> {
        return try {
            val content = syncBackend.readFile(TEMPLATES_FILE)
            if (content.isBlank()) return emptyList()
            OrgParser.parseTemplates(content).templates.map { it.toMealTemplate() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun saveTemplate(template: MealTemplate): Result<Unit> {
        return fileMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    val content = syncBackend.readFile(TEMPLATES_FILE)
                    val templateFile = if (content.isBlank()) OrgTemplateFile(templates = emptyList())
                                      else OrgParser.parseTemplates(content)

                    val newOrgTemplate = template.toOrgMealTemplate()
                    val existingIndex = templateFile.templates.indexOfFirst { it.id == template.id }
                    val updatedTemplates = if (existingIndex >= 0) {
                        templateFile.templates.toMutableList().also { it[existingIndex] = newOrgTemplate }
                    } else {
                        templateFile.templates + newOrgTemplate
                    }
                    val updatedFile = templateFile.copy(templates = updatedTemplates)
                    syncBackend.writeFile(TEMPLATES_FILE, OrgWriter.writeTemplates(updatedFile))
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    override suspend fun applyTemplate(
        date: LocalDate,
        mealLabel: String,
        template: MealTemplate
    ): Result<Unit> {
        // Acquire the mutex ONCE for the entire batch to avoid read-modify-write races
        return fileMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    val baseTime = System.currentTimeMillis()
                    template.entries.forEachIndexed { index, libraryItem ->
                        val entry = FoodEntry(
                            id = baseTime + index,  // unique id per item via index offset
                            name = libraryItem.name,
                            protein = libraryItem.protein,
                            carbs = libraryItem.carbs,
                            fat = libraryItem.fat,
                            calories = libraryItem.calories,
                            servingSize = libraryItem.servingSize,
                            servingUnit = libraryItem.servingUnit,
                            notes = null
                        )
                        // Call internal add (mutex already held)
                        addEntryInternal(date, mealLabel, entry)
                    }
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    override suspend fun deleteTemplate(templateId: String): Result<Unit> {
        return fileMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    val content = syncBackend.readFile(TEMPLATES_FILE)
                    if (content.isBlank()) return@withContext Result.success(Unit)
                    val templateFile = OrgParser.parseTemplates(content)
                    val updatedTemplates = templateFile.templates.filter { it.id != templateId }
                    val updatedFile = templateFile.copy(templates = updatedTemplates)
                    syncBackend.writeFile(TEMPLATES_FILE, OrgWriter.writeTemplates(updatedFile))
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    // =========================================================================
    // Calendar helpers
    // =========================================================================

    override suspend fun loadLoggedDates(): Set<LocalDate> {
        return try {
            val content = syncBackend.readFile(FOOD_LOG_FILE)
            if (content.isBlank()) return emptySet()
            val orgFile = OrgParser.parse(content, OrgParser.ParseMode.FOOD)
            orgFile.sections.filter { it.meals.isNotEmpty() }.map { it.date }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    // =========================================================================
    // Recent items
    // =========================================================================

    override suspend fun loadRecentItems(limit: Int): List<FoodLibraryItem> {
        return try {
            val content = syncBackend.readFile(FOOD_LOG_FILE)
            if (content.isBlank()) return emptyList()
            val orgFile = OrgParser.parse(content, OrgParser.ParseMode.FOOD)

            // Walk all entries across all dates (newest sections first), collect distinct names
            val seen = LinkedHashSet<String>()
            val results = mutableListOf<FoodLibraryItem>()

            for (section in orgFile.sections) {
                for (meal in section.meals) {
                    for (entry in meal.entries) {
                        if (results.size >= limit) break
                        if (entry.name !in seen) {
                            seen.add(entry.name)
                            results.add(
                                FoodLibraryItem(
                                    id = entry.id.toString(),
                                    name = entry.name,
                                    protein = entry.protein,
                                    carbs = entry.carbs,
                                    fat = entry.fat,
                                    calories = entry.calories,
                                    servingSize = entry.servingSize,
                                    servingUnit = entry.servingUnit
                                )
                            )
                        }
                    }
                    if (results.size >= limit) break
                }
                if (results.size >= limit) break
            }

            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    // =========================================================================
    // org file manipulation helpers
    // =========================================================================

    /**
     * Find-or-create the date section and meal group, then append the entry.
     * Sections remain sorted by date descending (newest first).
     */
    private fun upsertEntry(
        sections: List<OrgDateSection>,
        date: LocalDate,
        mealLabel: String,
        entry: OrgFoodEntry
    ): List<OrgDateSection> {
        val mutableSections = sections.toMutableList()
        val sectionIndex = mutableSections.indexOfFirst { it.date == date }

        if (sectionIndex < 0) {
            // No section for this date — create one and insert in sorted position
            val newMealGroup = OrgMealGroup(label = mealLabel, entries = listOf(entry))
            val newSection = OrgDateSection(date = date, meals = listOf(newMealGroup), exercises = emptyList())
            val insertAt = mutableSections.indexOfFirst { it.date < date }.let {
                if (it < 0) mutableSections.size else it
            }
            mutableSections.add(insertAt, newSection)
        } else {
            val section = mutableSections[sectionIndex]
            val mealIndex = section.meals.indexOfFirst { it.label == mealLabel }
            val updatedMeals = if (mealIndex < 0) {
                // No meal group for this label — append one
                section.meals + OrgMealGroup(label = mealLabel, entries = listOf(entry))
            } else {
                section.meals.toMutableList().also { meals ->
                    val meal = meals[mealIndex]
                    meals[mealIndex] = meal.copy(entries = meal.entries + entry)
                }
            }
            mutableSections[sectionIndex] = section.copy(meals = updatedMeals)
        }

        return mutableSections
    }

    /**
     * Find entry by id and replace it. Throws if not found.
     */
    private fun replaceEntry(
        sections: List<OrgDateSection>,
        date: LocalDate,
        mealLabel: String,
        entryId: Long,
        updated: OrgFoodEntry
    ): List<OrgDateSection> {
        return sections.map { section ->
            if (section.date != date) return@map section
            section.copy(meals = section.meals.map { meal ->
                if (meal.label != mealLabel) return@map meal
                val entryIndex = meal.entries.indexOfFirst { it.id == entryId }
                if (entryIndex < 0) throw NoSuchElementException("Entry $entryId not found in $mealLabel on $date")
                meal.copy(entries = meal.entries.toMutableList().also { it[entryIndex] = updated })
            })
        }
    }

    /**
     * Remove entry by id from the specified meal.
     * Removes the meal group if it becomes empty.
     * Removes the date section if it has no meals left.
     */
    private fun removeEntry(
        sections: List<OrgDateSection>,
        date: LocalDate,
        mealLabel: String,
        entryId: Long
    ): List<OrgDateSection> {
        return sections.mapNotNull { section ->
            if (section.date != date) return@mapNotNull section
            val updatedMeals = section.meals.mapNotNull { meal ->
                if (meal.label != mealLabel) return@mapNotNull meal
                val updatedEntries = meal.entries.filter { it.id != entryId }
                if (updatedEntries.isEmpty()) null else meal.copy(entries = updatedEntries)
            }
            // Remove section entirely if no meals remain
            if (updatedMeals.isEmpty() && section.exercises.isEmpty()) null
            else section.copy(meals = updatedMeals)
        }
    }
}

// =============================================================================
// Conversion extension functions
// =============================================================================

/** OrgFoodEntry -> domain FoodEntry */
private fun OrgFoodEntry.toFoodEntry(): FoodEntry = FoodEntry(
    id = this.id,
    name = this.name,
    protein = this.protein,
    carbs = this.carbs,
    fat = this.fat,
    calories = this.calories,
    servingSize = this.servingSize,
    servingUnit = this.servingUnit,
    notes = this.notes
)

/** Domain FoodEntry -> OrgFoodEntry (substitutes "Unnamed" for blank names). */
private fun FoodEntry.toOrgFoodEntry(): OrgFoodEntry = OrgFoodEntry(
    name = if (this.name.isBlank()) "Unnamed" else this.name,
    protein = this.protein,
    carbs = this.carbs,
    fat = this.fat,
    calories = this.calories,
    id = this.id,
    servingSize = this.servingSize,
    servingUnit = this.servingUnit,
    notes = this.notes
)

/** OrgLibraryEntry -> domain FoodLibraryItem */
private fun OrgLibraryEntry.toFoodLibraryItem(): FoodLibraryItem = FoodLibraryItem(
    id = this.id,
    name = this.name,
    protein = this.protein,
    carbs = this.carbs,
    fat = this.fat,
    calories = this.calories,
    servingSize = this.servingSize,
    servingUnit = this.servingUnit
)

/** Domain FoodLibraryItem -> OrgLibraryEntry */
private fun FoodLibraryItem.toOrgLibraryEntry(): OrgLibraryEntry = OrgLibraryEntry(
    id = this.id,
    name = this.name,
    protein = this.protein,
    carbs = this.carbs,
    fat = this.fat,
    calories = this.calories,
    servingSize = this.servingSize,
    servingUnit = this.servingUnit
)

/** OrgMealTemplate -> domain MealTemplate */
private fun OrgMealTemplate.toMealTemplate(): MealTemplate = MealTemplate(
    id = this.id,
    name = this.name,
    entries = this.items.map { it.toFoodLibraryItem() }
)

/** Domain MealTemplate -> OrgMealTemplate */
private fun MealTemplate.toOrgMealTemplate(): OrgMealTemplate = OrgMealTemplate(
    id = this.id,
    name = this.name,
    items = this.entries.map { it.toOrgLibraryEntry() }
)
