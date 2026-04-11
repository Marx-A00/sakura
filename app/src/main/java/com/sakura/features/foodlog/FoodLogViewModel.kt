package com.sakura.features.foodlog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.sakura.data.food.FoodEntry
import com.sakura.data.food.FoodLibraryItem
import com.sakura.data.food.FoodRepository
import com.sakura.data.food.MealTemplate
import com.sakura.preferences.AppPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class FoodLogViewModel(
    private val foodRepo: FoodRepository,
    private val prefsRepo: AppPreferencesRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // -------------------------------------------------------------------------
    // Date navigation
    // -------------------------------------------------------------------------

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _reloadTrigger = MutableStateFlow(0)

    // -------------------------------------------------------------------------
    // UI state — combines selected date + macro targets into Success/Error
    // -------------------------------------------------------------------------

    private val _meals = MutableStateFlow<com.sakura.features.foodlog.FoodLogUiState>(FoodLogUiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<FoodLogUiState> = combine(_selectedDate, _reloadTrigger) { date, _ -> date }
        .flatMapLatest { date ->
            flow {
                emit(FoodLogUiState.Loading)
                try {
                    val meals = foodRepo.loadDay(date)
                    prefsRepo.macroTargets.collect { targets ->
                        emit(FoodLogUiState.Success(meals = meals, targets = targets))
                    }
                } catch (e: Exception) {
                    if (e.message?.contains("folder", ignoreCase = true) == true ||
                        e.message?.contains("unavailable", ignoreCase = true) == true
                    ) {
                        emit(FoodLogUiState.Error.FolderUnavailable)
                    } else {
                        emit(FoodLogUiState.Error.Generic(e.message ?: "Unknown error"))
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FoodLogUiState.Loading
        )

    // -------------------------------------------------------------------------
    // Expand/collapse meal sections
    // -------------------------------------------------------------------------

    private val _expandedMeals = MutableStateFlow<Set<String>>(
        setOf("Breakfast", "Lunch", "Dinner", "Snacks")
    )
    val expandedMeals: StateFlow<Set<String>> = _expandedMeals.asStateFlow()

    fun toggleMealExpanded(mealLabel: String) {
        _expandedMeals.value = if (_expandedMeals.value.contains(mealLabel)) {
            _expandedMeals.value - mealLabel
        } else {
            _expandedMeals.value + mealLabel
        }
    }

    // -------------------------------------------------------------------------
    // Edit mode for past days
    // -------------------------------------------------------------------------

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    fun toggleEditMode() {
        _isEditMode.value = !_isEditMode.value
    }

    // -------------------------------------------------------------------------
    // Undo last add
    // -------------------------------------------------------------------------

    private val _lastAddedEntry = MutableStateFlow<Pair<Long, String>?>(null)
    val lastAddedEntry: StateFlow<Pair<Long, String>?> = _lastAddedEntry.asStateFlow()

    // -------------------------------------------------------------------------
    // Library / template data
    // -------------------------------------------------------------------------

    private val _recentItems = MutableStateFlow<List<FoodLibraryItem>>(emptyList())
    val recentItems: StateFlow<List<FoodLibraryItem>> = _recentItems.asStateFlow()

    private val _libraryItems = MutableStateFlow<List<FoodLibraryItem>>(emptyList())
    val libraryItems: StateFlow<List<FoodLibraryItem>> = _libraryItems.asStateFlow()

    private val _templates = MutableStateFlow<List<MealTemplate>>(emptyList())
    val templates: StateFlow<List<MealTemplate>> = _templates.asStateFlow()

    // -------------------------------------------------------------------------
    // Draft fields backed by SavedStateHandle (SYNC-05 draft persistence)
    // -------------------------------------------------------------------------

    val draftName = savedStateHandle.getStateFlow("draft_name", "")
    val draftProtein = savedStateHandle.getStateFlow("draft_protein", "")
    val draftCarbs = savedStateHandle.getStateFlow("draft_carbs", "")
    val draftFat = savedStateHandle.getStateFlow("draft_fat", "")
    val draftCalories = savedStateHandle.getStateFlow("draft_calories", "")
    val draftServingSize = savedStateHandle.getStateFlow("draft_serving_size", "")
    val draftServingUnit = savedStateHandle.getStateFlow("draft_serving_unit", "")
    val draftNotes = savedStateHandle.getStateFlow("draft_notes", "")
    val draftMealLabel = savedStateHandle.getStateFlow("draft_meal_label", defaultMealLabel())
    val draftCaloriesOverridden = savedStateHandle.getStateFlow("draft_calories_overridden", false)

    fun updateDraftName(value: String) { savedStateHandle["draft_name"] = value }
    fun updateDraftProtein(value: String) {
        savedStateHandle["draft_protein"] = value
        if (!(savedStateHandle.get<Boolean>("draft_calories_overridden") ?: false)) {
            recalcCalories()
        }
    }
    fun updateDraftCarbs(value: String) {
        savedStateHandle["draft_carbs"] = value
        if (!(savedStateHandle.get<Boolean>("draft_calories_overridden") ?: false)) {
            recalcCalories()
        }
    }
    fun updateDraftFat(value: String) {
        savedStateHandle["draft_fat"] = value
        if (!(savedStateHandle.get<Boolean>("draft_calories_overridden") ?: false)) {
            recalcCalories()
        }
    }
    fun updateDraftCalories(value: String) {
        savedStateHandle["draft_calories"] = value
        val protein = savedStateHandle.get<String>("draft_protein")?.toIntOrNull() ?: 0
        val carbs = savedStateHandle.get<String>("draft_carbs")?.toIntOrNull() ?: 0
        val fat = savedStateHandle.get<String>("draft_fat")?.toIntOrNull() ?: 0
        val autoCalc = protein * 4 + carbs * 4 + fat * 9
        val typed = value.toIntOrNull() ?: 0
        savedStateHandle["draft_calories_overridden"] = (typed != autoCalc && value.isNotBlank())
    }
    fun updateDraftServingSize(value: String) { savedStateHandle["draft_serving_size"] = value }
    fun updateDraftServingUnit(value: String) { savedStateHandle["draft_serving_unit"] = value }
    fun updateDraftNotes(value: String) { savedStateHandle["draft_notes"] = value }
    fun updateDraftMealLabel(value: String) { savedStateHandle["draft_meal_label"] = value }

    private fun recalcCalories() {
        val protein = savedStateHandle.get<String>("draft_protein")?.toIntOrNull() ?: 0
        val carbs = savedStateHandle.get<String>("draft_carbs")?.toIntOrNull() ?: 0
        val fat = savedStateHandle.get<String>("draft_fat")?.toIntOrNull() ?: 0
        val autoCalc = protein * 4 + carbs * 4 + fat * 9
        savedStateHandle["draft_calories"] = if (autoCalc > 0) autoCalc.toString() else ""
        savedStateHandle["draft_calories_overridden"] = false
    }

    fun clearDraft() {
        savedStateHandle["draft_name"] = ""
        savedStateHandle["draft_protein"] = ""
        savedStateHandle["draft_carbs"] = ""
        savedStateHandle["draft_fat"] = ""
        savedStateHandle["draft_calories"] = ""
        savedStateHandle["draft_serving_size"] = ""
        savedStateHandle["draft_serving_unit"] = ""
        savedStateHandle["draft_notes"] = ""
        savedStateHandle["draft_meal_label"] = defaultMealLabel()
        savedStateHandle["draft_calories_overridden"] = false
    }

    fun defaultMealLabel(): String {
        val hour = LocalTime.now().hour
        return when {
            hour < 11 -> "Breakfast"
            hour < 14 -> "Lunch"
            hour < 20 -> "Dinner"
            else -> "Snacks"
        }
    }

    // -------------------------------------------------------------------------
    // Date navigation
    // -------------------------------------------------------------------------

    fun navigateToDate(date: LocalDate) {
        _selectedDate.value = date
        _isEditMode.value = false
    }

    fun navigatePrevDay() = navigateToDate(_selectedDate.value.minusDays(1))
    fun navigateNextDay() = navigateToDate(_selectedDate.value.plusDays(1))

    // -------------------------------------------------------------------------
    // Reload helper
    // -------------------------------------------------------------------------

    private fun reloadDay() {
        _reloadTrigger.value++
    }

    // -------------------------------------------------------------------------
    // CRUD operations
    // -------------------------------------------------------------------------

    fun addEntry(mealLabel: String, entry: FoodEntry) {
        viewModelScope.launch {
            foodRepo.addEntry(_selectedDate.value, mealLabel, entry)
            _lastAddedEntry.value = Pair(entry.id, mealLabel)
            reloadDay()
        }
    }

    fun updateEntry(mealLabel: String, entryId: Long, updated: FoodEntry) {
        viewModelScope.launch {
            foodRepo.updateEntry(_selectedDate.value, mealLabel, entryId, updated)
            reloadDay()
        }
    }

    fun deleteEntry(mealLabel: String, entryId: Long) {
        viewModelScope.launch {
            foodRepo.deleteEntry(_selectedDate.value, mealLabel, entryId)
            reloadDay()
        }
    }

    fun undoLastAdd() {
        val last = _lastAddedEntry.value ?: return
        _lastAddedEntry.value = null
        deleteEntry(last.second, last.first)
    }

    // -------------------------------------------------------------------------
    // Library operations
    // -------------------------------------------------------------------------

    fun saveToLibrary(entry: FoodEntry) {
        viewModelScope.launch {
            val item = FoodLibraryItem(
                id = java.util.UUID.randomUUID().toString(),
                name = entry.name,
                protein = entry.protein,
                carbs = entry.carbs,
                fat = entry.fat,
                calories = entry.calories,
                servingSize = entry.servingSize,
                servingUnit = entry.servingUnit
            )
            foodRepo.saveToLibrary(item)
            loadLibraryData()
        }
    }

    fun loadLibraryData() {
        viewModelScope.launch {
            _recentItems.value = foodRepo.loadRecentItems()
            _libraryItems.value = foodRepo.loadLibrary()
            _templates.value = foodRepo.loadTemplates()
        }
    }

    // -------------------------------------------------------------------------
    // Meal template operations
    // -------------------------------------------------------------------------

    fun saveMealAsTemplate(mealLabel: String, templateName: String) {
        viewModelScope.launch {
            val currentState = uiState.value
            if (currentState !is FoodLogUiState.Success) return@launch
            val meal = currentState.meals.find { it.label == mealLabel } ?: return@launch
            val items = meal.entries.map { entry ->
                FoodLibraryItem(
                    id = java.util.UUID.randomUUID().toString(),
                    name = entry.name,
                    protein = entry.protein,
                    carbs = entry.carbs,
                    fat = entry.fat,
                    calories = entry.calories,
                    servingSize = entry.servingSize,
                    servingUnit = entry.servingUnit
                )
            }
            val template = MealTemplate(
                id = java.util.UUID.randomUUID().toString(),
                name = templateName,
                entries = items
            )
            foodRepo.saveTemplate(template)
            loadLibraryData()
        }
    }

    fun applyTemplate(mealLabel: String, template: MealTemplate) {
        viewModelScope.launch {
            foodRepo.applyTemplate(_selectedDate.value, mealLabel, template)
            reloadDay()
        }
    }

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    init {
        loadLibraryData()
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    companion object {
        fun factory(
            foodRepo: FoodRepository,
            prefsRepo: AppPreferencesRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    @Suppress("UNCHECKED_CAST")
                    return FoodLogViewModel(foodRepo, prefsRepo, savedStateHandle) as T
                }
            }
    }
}
