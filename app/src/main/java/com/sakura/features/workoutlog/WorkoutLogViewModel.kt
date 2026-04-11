package com.sakura.features.workoutlog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.sakura.data.workout.ExerciseCategory
import com.sakura.data.workout.ExerciseLibrary
import com.sakura.data.workout.ExerciseLog
import com.sakura.data.workout.ExerciseType
import com.sakura.data.workout.LibraryExercise
import com.sakura.data.workout.SetLog
import com.sakura.data.workout.SplitDay
import com.sakura.data.workout.WorkoutRepository
import com.sakura.data.workout.WorkoutSession
import com.sakura.data.workout.WorkoutTemplates
import com.sakura.preferences.AppPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutLogViewModel(
    private val workoutRepo: WorkoutRepository,
    private val prefsRepo: AppPreferencesRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // -------------------------------------------------------------------------
    // Date navigation — mirrors FoodLogViewModel pattern
    // -------------------------------------------------------------------------

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // -------------------------------------------------------------------------
    // Reload trigger — increment after any mutation to force state refresh
    // combine() pattern from 02-02 decision: avoids StateFlow.equals() short-circuit
    // -------------------------------------------------------------------------

    private val _reloadTrigger = MutableStateFlow(0)

    // -------------------------------------------------------------------------
    // PR notification — emitted after addSet detects a personal record
    // -------------------------------------------------------------------------

    private val _prDetected = MutableStateFlow<PrNotification?>(null)
    val prDetected: StateFlow<PrNotification?> = _prDetected.asStateFlow()

    // -------------------------------------------------------------------------
    // Main UI state — derived from selectedDate + reloadTrigger
    // -------------------------------------------------------------------------

    val uiState: StateFlow<WorkoutLogUiState> = combine(
        _selectedDate, _reloadTrigger
    ) { date, _ -> date }
        .flatMapLatest { date ->
            flow {
                emit(WorkoutLogUiState.Loading)
                try {
                    val session = workoutRepo.loadSession(date)
                    val exercises = session?.exercises ?: emptyList()

                    // Load previous sets for each exercise for auto-fill reference
                    val previousSetsMap = mutableMapOf<String, List<SetLog>>()
                    exercises.forEach { ex ->
                        val prev = workoutRepo.loadPreviousSetsForExercise(ex.name)
                        if (prev.isNotEmpty()) previousSetsMap[ex.name] = prev
                    }

                    emit(
                        WorkoutLogUiState.DayLoaded(
                            date = date,
                            isToday = date == LocalDate.now(),
                            templateName = session?.templateName,
                            exercises = exercises.map { ex ->
                                DayExercise(
                                    exerciseLog = ex,
                                    targetSets = null,   // template targets not persisted per-exercise yet
                                    targetReps = null,
                                    targetHoldSecs = null,
                                    previousSets = previousSetsMap[ex.name] ?: emptyList(),
                                    category = ex.category
                                )
                            },
                            isComplete = session?.isComplete ?: false,
                            totalVolume = session?.totalVolume ?: 0.0,
                            previousSetsMap = previousSetsMap
                        )
                    )
                } catch (e: Exception) {
                    if (e.message?.contains("folder", ignoreCase = true) == true ||
                        e.message?.contains("unavailable", ignoreCase = true) == true
                    ) {
                        emit(WorkoutLogUiState.Error.FolderUnavailable)
                    } else {
                        emit(WorkoutLogUiState.Error.Generic(e.message ?: "Unknown error"))
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WorkoutLogUiState.Loading
        )

    // -------------------------------------------------------------------------
    // Date navigation (mirror FoodLogViewModel pattern)
    // -------------------------------------------------------------------------

    fun navigateDate(delta: Int) {
        _selectedDate.value = _selectedDate.value.plusDays(delta.toLong())
    }

    fun goToToday() {
        _selectedDate.value = LocalDate.now()
    }

    // -------------------------------------------------------------------------
    // Template loading — seeds the day with exercises from the selected split day
    // -------------------------------------------------------------------------

    fun loadTemplate(splitDay: SplitDay) {
        viewModelScope.launch {
            val template = WorkoutTemplates.forDay(splitDay)
            val date = _selectedDate.value
            template.exercises.forEachIndexed { idx, def ->
                val exerciseLog = ExerciseLog(
                    id = System.currentTimeMillis() + idx,
                    name = def.name,
                    exerciseType = def.exerciseType,
                    category = def.category,
                    sets = emptyList()
                )
                workoutRepo.addExercise(date, exerciseLog)
                // Small delay to ensure unique IDs when iterating rapidly
                kotlinx.coroutines.delay(2)
            }
            _reloadTrigger.value++
        }
    }

    // -------------------------------------------------------------------------
    // Add a single exercise to the day (from library picker)
    // -------------------------------------------------------------------------

    fun addExercise(libraryExercise: LibraryExercise) {
        viewModelScope.launch {
            val date = _selectedDate.value
            // Map category label to ExerciseType for org file storage.
            // ExerciseType.fromLabel("cardio") returns CARDIO (added in 03-02).
            val exerciseType = ExerciseType.fromLabel(libraryExercise.category.label)
            val exerciseLog = ExerciseLog(
                id = System.currentTimeMillis(),
                name = libraryExercise.name,
                exerciseType = exerciseType,
                category = libraryExercise.category,
                sets = emptyList()
            )
            workoutRepo.addExercise(date, exerciseLog)
            _reloadTrigger.value++
        }
    }

    // -------------------------------------------------------------------------
    // Add a set to an exercise (immediate write — no draft/finish lifecycle)
    // -------------------------------------------------------------------------

    fun addSet(exerciseId: Long, set: SetLog) {
        viewModelScope.launch {
            val date = _selectedDate.value
            workoutRepo.addSet(date, exerciseId, set)
            // Check for PR after logging the set
            val exerciseName = (uiState.value as? WorkoutLogUiState.DayLoaded)
                ?.exercises?.find { it.exerciseLog.id == exerciseId }
                ?.exerciseLog?.name
            if (exerciseName != null) {
                checkForPR(exerciseName, set)
            }
            _reloadTrigger.value++
        }
    }

    // -------------------------------------------------------------------------
    // Remove exercise from day
    // -------------------------------------------------------------------------

    fun removeExercise(exerciseId: Long) {
        viewModelScope.launch {
            workoutRepo.removeExercise(_selectedDate.value, exerciseId)
            _reloadTrigger.value++
        }
    }

    // -------------------------------------------------------------------------
    // Remove a set from an exercise
    // -------------------------------------------------------------------------

    fun removeSet(exerciseId: Long, setNumber: Int) {
        viewModelScope.launch {
            workoutRepo.removeSet(_selectedDate.value, exerciseId, setNumber)
            _reloadTrigger.value++
        }
    }

    // -------------------------------------------------------------------------
    // Mark day complete (soft flag)
    // -------------------------------------------------------------------------

    fun toggleComplete() {
        viewModelScope.launch {
            val current = (uiState.value as? WorkoutLogUiState.DayLoaded)?.isComplete ?: false
            workoutRepo.markComplete(_selectedDate.value, !current)
            _reloadTrigger.value++
        }
    }

    // -------------------------------------------------------------------------
    // Create new exercise in library and add to day
    // -------------------------------------------------------------------------

    fun createAndAddExercise(name: String, category: ExerciseCategory) {
        val newExercise = LibraryExercise(name = name, category = category, isBuiltIn = false)
        ExerciseLibrary.addUserExercise(newExercise)
        viewModelScope.launch {
            workoutRepo.saveUserExercises(ExerciseLibrary.userExercises())
            // Also add to today's workout immediately
            addExercise(newExercise)
        }
    }

    // -------------------------------------------------------------------------
    // PR detection
    // -------------------------------------------------------------------------

    private fun checkForPR(exerciseName: String, set: SetLog) {
        viewModelScope.launch {
            val pb = workoutRepo.findPersonalBest(exerciseName) ?: return@launch
            // Only flag as PR if there IS prior history (null = first session, no PR)
            val prType = when {
                set.unit != "bw" && set.weight > pb.weight -> "Weight"
                set.unit == "bw" && set.holdSecs == 0 && set.reps > pb.reps -> "Reps"
                set.holdSecs > 0 && set.holdSecs > pb.holdSecs -> "Hold"
                else -> null
            }
            if (prType != null) {
                _prDetected.value = PrNotification(exerciseName, prType)
            }
        }
    }

    fun dismissPrNotification() {
        _prDetected.value = null
    }

    // -------------------------------------------------------------------------
    // History
    // -------------------------------------------------------------------------

    private val _history = MutableStateFlow<List<WorkoutSession>>(emptyList())
    val history: StateFlow<List<WorkoutSession>> = _history.asStateFlow()

    fun loadHistory() {
        viewModelScope.launch {
            _history.value = workoutRepo.loadHistory()
        }
    }

    // -------------------------------------------------------------------------
    // Initialise user exercises from persistent storage on startup
    // -------------------------------------------------------------------------

    init {
        viewModelScope.launch {
            val exercises = workoutRepo.loadUserExercises()
            ExerciseLibrary.loadUserExercises(exercises)
        }
    }

    // -------------------------------------------------------------------------
    // Factory — same pattern as FoodLogViewModel (02-02 decision)
    // -------------------------------------------------------------------------

    companion object {
        fun factory(
            workoutRepo: WorkoutRepository,
            prefsRepo: AppPreferencesRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val savedStateHandle = extras.createSavedStateHandle()
                    return WorkoutLogViewModel(workoutRepo, prefsRepo, savedStateHandle) as T
                }
            }
    }
}
