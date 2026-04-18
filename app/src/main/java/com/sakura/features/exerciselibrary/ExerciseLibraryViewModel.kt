package com.sakura.features.exerciselibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.sakura.data.workout.ExerciseCategory
import com.sakura.data.workout.ExerciseLibrary
import com.sakura.data.workout.LibraryExercise
import com.sakura.data.workout.UserWorkoutTemplate
import com.sakura.data.workout.WorkoutRepository
import com.sakura.data.workout.WorkoutSchedule
import com.sakura.preferences.AppPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek

class ExerciseLibraryViewModel(
    private val workoutRepo: WorkoutRepository,
    private val prefsRepo: AppPreferencesRepository
) : ViewModel() {

    private val _allExercises = MutableStateFlow<List<LibraryExercise>>(emptyList())
    val allExercises: StateFlow<List<LibraryExercise>> = _allExercises.asStateFlow()
    private val _allTemplates = MutableStateFlow<List<UserWorkoutTemplate>>(emptyList())
    val allTemplates: StateFlow<List<UserWorkoutTemplate>> = _allTemplates.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _selectedCategory = MutableStateFlow<ExerciseCategory?>(null)
    val selectedCategory: StateFlow<ExerciseCategory?> = _selectedCategory.asStateFlow()

    val filteredExercises: StateFlow<List<LibraryExercise>> = combine(
        _allExercises, _searchQuery, _selectedCategory
    ) { exercises, query, category ->
        exercises.filter { ex ->
            (category == null || ex.category == category) &&
                (query.isBlank() || ex.name.contains(query, ignoreCase = true))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredTemplates: StateFlow<List<UserWorkoutTemplate>> = combine(
        _allTemplates, _searchQuery
    ) { templates, query ->
        if (query.isBlank()) templates
        else templates.filter { t ->
            t.name.contains(query, ignoreCase = true) ||
                t.exercises.any { it.name.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isLoading = MutableStateFlow(true)

    // Schedule state
    private val _schedule = MutableStateFlow(WorkoutSchedule())
    val schedule: StateFlow<WorkoutSchedule> = _schedule.asStateFlow()

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun updateCategory(category: ExerciseCategory?) {
        _selectedCategory.value = category
    }

    fun saveExercise(exercise: LibraryExercise) {
        viewModelScope.launch {
            ExerciseLibrary.addUserExercise(exercise)
            workoutRepo.saveUserExercises(ExerciseLibrary.userExercises())
        }
    }

    fun updateExercise(oldName: String, updated: LibraryExercise) {
        viewModelScope.launch {
            ExerciseLibrary.updateUserExercise(oldName, updated)
            workoutRepo.saveUserExercises(ExerciseLibrary.userExercises())
        }
    }

    fun deleteExercise(name: String) {
        viewModelScope.launch {
            ExerciseLibrary.deleteUserExercise(name)
            workoutRepo.saveUserExercises(ExerciseLibrary.userExercises())
        }
    }

    fun saveTemplate(template: UserWorkoutTemplate) {
        viewModelScope.launch { workoutRepo.saveWorkoutTemplate(template) }
    }

    fun deleteTemplate(templateId: String) {
        viewModelScope.launch {
            // Also remove from schedule if assigned
            val currentSchedule = _schedule.value
            val updated = currentSchedule.assignments.filterValues { it != templateId }
            if (updated.size != currentSchedule.assignments.size) {
                val newSchedule = WorkoutSchedule(updated)
                _schedule.value = newSchedule
                prefsRepo.saveWorkoutScheduleJson(newSchedule.toJson())
            }
            workoutRepo.deleteWorkoutTemplate(templateId)
        }
    }

    // -------------------------------------------------------------------------
    // Schedule operations
    // -------------------------------------------------------------------------

    fun assignTemplateToDay(day: DayOfWeek, templateId: String?) {
        viewModelScope.launch {
            val updated = _schedule.value.withDay(day, templateId)
            _schedule.value = updated
            prefsRepo.saveWorkoutScheduleJson(updated.toJson())
        }
    }

    init {
        // Seed built-in templates if version is behind (runs once on first load)
        viewModelScope.launch {
            val currentVersion = try { prefsRepo.seedTemplatesVersion.first() } catch (_: Exception) { 0 }
            if (currentVersion < com.sakura.data.workout.OrgWorkoutRepository.SEED_VERSION) {
                workoutRepo.seedBuiltinTemplates()
                prefsRepo.setSeedTemplatesVersion(com.sakura.data.workout.OrgWorkoutRepository.SEED_VERSION)
            }
        }
        viewModelScope.launch {
            workoutRepo.templateVersion.collectLatest {
                _allExercises.value = ExerciseLibrary.allExercises()
                _allTemplates.value = workoutRepo.loadWorkoutTemplates()
                isLoading.value = false
            }
        }
        viewModelScope.launch {
            prefsRepo.workoutScheduleJson.collectLatest { json ->
                _schedule.value = WorkoutSchedule.fromJson(json)
            }
        }
    }

    companion object {
        fun factory(
            workoutRepo: WorkoutRepository,
            prefsRepo: AppPreferencesRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return ExerciseLibraryViewModel(workoutRepo, prefsRepo) as T
                }
            }
    }
}
