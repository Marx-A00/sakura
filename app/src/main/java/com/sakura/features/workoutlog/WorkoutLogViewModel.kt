package com.sakura.features.workoutlog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.sakura.data.workout.SetLog
import com.sakura.data.workout.SplitDay
import com.sakura.data.workout.WorkoutRepository
import com.sakura.data.workout.WorkoutSession
import com.sakura.data.workout.WorkoutTemplates
import com.sakura.preferences.AppPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class WorkoutLogViewModel(
    private val workoutRepo: WorkoutRepository,
    private val prefsRepo: AppPreferencesRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // -------------------------------------------------------------------------
    // Reload trigger — incremented after session save to force state refresh
    // -------------------------------------------------------------------------

    private val _reloadTrigger = MutableStateFlow(0)

    // -------------------------------------------------------------------------
    // Session draft — null when no active session
    // -------------------------------------------------------------------------

    private val _sessionDraft = MutableStateFlow<SessionDraft?>(null)
    val sessionDraft: StateFlow<SessionDraft?> = _sessionDraft.asStateFlow()

    // -------------------------------------------------------------------------
    // Rest timer
    // -------------------------------------------------------------------------

    private val _restSecondsRemaining = MutableStateFlow(0)
    val restSecondsRemaining: StateFlow<Int> = _restSecondsRemaining.asStateFlow()

    private var restTimerJob: Job? = null

    fun startRestTimer(durationSeconds: Int) {
        restTimerJob?.cancel()
        restTimerJob = viewModelScope.launch {
            var remaining = durationSeconds
            _restSecondsRemaining.value = remaining
            while (remaining > 0) {
                delay(1000)
                remaining--
                _restSecondsRemaining.value = remaining
            }
        }
    }

    fun cancelRestTimer() {
        restTimerJob?.cancel()
        _restSecondsRemaining.value = 0
    }

    // -------------------------------------------------------------------------
    // PR detection
    // -------------------------------------------------------------------------

    private val _prDetected = MutableStateFlow<PrNotification?>(null)
    val prDetected: StateFlow<PrNotification?> = _prDetected.asStateFlow()

    private fun checkForPR(exerciseName: String, set: SetLog) {
        viewModelScope.launch {
            val pb = workoutRepo.findPersonalBest(exerciseName) ?: return@launch
            // Only check PR if there IS prior history (null = first session, don't show PR)
            val prType = when {
                set.unit != "bw" && set.weight > pb.weight -> "Weight"
                set.unit == "bw" && set.holdSecs == 0 && set.reps > pb.reps -> "Reps"
                set.holdSecs > 0 && set.holdSecs > pb.holdSecs -> "Hold"
                else -> null
            }
            if (prType != null) {
                _prDetected.value = PrNotification(exerciseName = exerciseName, prType = prType)
            }
        }
    }

    fun dismissPrNotification() {
        _prDetected.value = null
    }

    // -------------------------------------------------------------------------
    // Session UI state — derived from draft + rest timer + PR
    // -------------------------------------------------------------------------

    val sessionUiState: StateFlow<SessionUiState> = combine(
        _sessionDraft,
        _restSecondsRemaining,
        _prDetected
    ) { draft, restSecs, pr ->
        if (draft == null) {
            SessionUiState.Inactive
        } else {
            SessionUiState.Active(
                splitDay = draft.splitDay,
                exercises = draft.exercises.map { de ->
                    SessionExercise(
                        definition = de.definition,
                        selectedAlternative = de.selectedAlternative,
                        loggedSets = de.loggedSets,
                        previousSets = de.previousSets
                    )
                },
                restSecondsRemaining = restSecs,
                prDetected = pr,
                startTimeMillis = draft.startTimeMillis
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionUiState.Inactive
    )

    // -------------------------------------------------------------------------
    // Log UI state — derives from prefs + reload trigger
    // -------------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    val logUiState: StateFlow<WorkoutLogUiState> =
        combine(prefsRepo.lastWorkoutSplitDay, prefsRepo.lastWorkoutDate, _reloadTrigger) { splitDay, date, _ ->
            splitDay  // only care about splitDay for next-day calculation
        }
        .flatMapLatest { lastSplitDayLabel ->
            flow {
                emit(WorkoutLogUiState.Loading)
                try {
                    val lastSplitDay = lastSplitDayLabel?.let { SplitDay.fromLabel(it) }
                    val nextSplitDay = SplitDay.nextAfter(lastSplitDay)
                    val lastSession: WorkoutSession? = if (lastSplitDay != null) {
                        workoutRepo.loadLastSessionForSplitDay(lastSplitDay)
                    } else {
                        null
                    }
                    emit(
                        WorkoutLogUiState.Ready(
                            nextSplitDay = nextSplitDay,
                            lastSession = lastSession,
                            hasActiveSession = _sessionDraft.value != null
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
    // Session lifecycle
    // -------------------------------------------------------------------------

    fun startSession(splitDay: SplitDay) {
        viewModelScope.launch {
            val template = WorkoutTemplates.forDay(splitDay)
            val lastSession = workoutRepo.loadLastSessionForSplitDay(splitDay)

            val draftExercises = template.exercises.map { def ->
                val previousSets = lastSession?.exercises
                    ?.find { ex ->
                        ex.name.trim().lowercase() == def.name.trim().lowercase() ||
                            def.alternatives.any { alt ->
                                ex.name.trim().lowercase() == alt.trim().lowercase()
                            }
                    }
                    ?.sets?.map { s -> SetLog(s.setNumber, s.reps, s.weight, s.unit, s.holdSecs, s.rpe, false) }
                    ?: emptyList()

                val selectedAlt = lastSession?.exercises
                    ?.find { ex ->
                        def.alternatives.any { alt ->
                            alt.trim().lowercase() == ex.name.trim().lowercase()
                        }
                    }
                    ?.name  // preserve last session's alternative selection

                DraftExercise(
                    definition = def,
                    selectedAlternative = selectedAlt,
                    previousSets = previousSets
                )
            }

            _sessionDraft.value = SessionDraft(
                splitDay = splitDay,
                exercises = draftExercises.toMutableList()
            )
        }
    }

    fun logSet(exerciseIndex: Int, set: SetLog) {
        val draft = _sessionDraft.value ?: return
        val exerciseName = draft.exercises[exerciseIndex].displayName
        _sessionDraft.value = draft.addSet(exerciseIndex, set)

        // Start rest timer automatically after logging a set
        viewModelScope.launch {
            val defaultSecs = prefsRepo.defaultRestTimerSecs.first()
            startRestTimer(defaultSecs)
        }

        // Check for PR asynchronously
        checkForPR(exerciseName, set)
    }

    fun selectAlternative(exerciseIndex: Int, alternativeName: String) {
        val draft = _sessionDraft.value ?: return
        _sessionDraft.value = draft.selectAlternative(exerciseIndex, alternativeName)
    }

    fun finishSession() {
        viewModelScope.launch {
            val draft = _sessionDraft.value ?: return@launch
            val session = draft.toWorkoutSession(LocalDate.now())
            val result = workoutRepo.saveSession(session)
            if (result.isSuccess) {
                // Update last workout info in preferences for split awareness
                prefsRepo.setLastWorkout(
                    splitDay = session.splitDay.label,
                    date = session.date.toString()
                )
                _sessionDraft.value = null
                cancelRestTimer()
                _prDetected.value = null
                // Trigger reload of the log screen state
                _reloadTrigger.value++
            }
        }
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
    // Factory — same pattern as FoodLogViewModel
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
