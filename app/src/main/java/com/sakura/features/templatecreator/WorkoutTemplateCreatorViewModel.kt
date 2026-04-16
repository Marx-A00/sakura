package com.sakura.features.templatecreator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.sakura.data.workout.LibraryExercise
import com.sakura.data.workout.TemplateExercise
import com.sakura.data.workout.UserWorkoutTemplate
import com.sakura.data.workout.WorkoutRepository
import com.sakura.data.workout.toTemplateExercise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutTemplateCreatorViewModel(
    private val workoutRepo: WorkoutRepository,
    private val templateId: String?
) : ViewModel() {

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _exercises = MutableStateFlow<List<TemplateExercise>>(emptyList())
    val exercises: StateFlow<List<TemplateExercise>> = _exercises.asStateFlow()

    private val _isLoading = MutableStateFlow(templateId != null)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val isEditing: Boolean = templateId != null

    val canSave: StateFlow<Boolean> = combine(_name, _exercises) { name, exercises ->
        name.isNotBlank() && exercises.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        if (templateId != null) {
            viewModelScope.launch {
                val template = workoutRepo.loadWorkoutTemplates().find { it.id == templateId }
                if (template != null) {
                    _name.value = template.name
                    _exercises.value = template.exercises
                }
                _isLoading.value = false
            }
        }
    }

    fun updateName(name: String) {
        _name.value = name
    }

    fun addExercise(libraryExercise: LibraryExercise) {
        _exercises.value = _exercises.value + libraryExercise.toTemplateExercise()
    }

    fun removeExercise(index: Int) {
        _exercises.value = _exercises.value.toMutableList().apply { removeAt(index) }
    }

    fun reorderExercises(fromIndex: Int, toIndex: Int) {
        _exercises.value = _exercises.value.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
    }

    fun setExerciseOrder(reordered: List<TemplateExercise>) {
        _exercises.value = reordered
    }

    fun updateTargets(index: Int, sets: Int, reps: Int, holdSecs: Int) {
        _exercises.value = _exercises.value.toMutableList().apply {
            this[index] = this[index].copy(
                targetSets = sets,
                targetReps = reps,
                targetHoldSecs = holdSecs
            )
        }
    }

    fun save(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val template = UserWorkoutTemplate(
                id = templateId ?: java.util.UUID.randomUUID().toString(),
                name = _name.value.trim(),
                exercises = _exercises.value
            )
            val result = workoutRepo.saveWorkoutTemplate(template)
            onComplete(result.isSuccess)
        }
    }

    companion object {
        fun factory(workoutRepo: WorkoutRepository, templateId: String?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return WorkoutTemplateCreatorViewModel(workoutRepo, templateId) as T
                }
            }
    }
}
