package com.sakura.features.workoutlog

import android.content.Context
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
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
import com.sakura.data.workout.UserWorkoutTemplate
import com.sakura.data.workout.WorkoutRepository
import com.sakura.data.workout.WorkoutSchedule
import com.sakura.data.workout.WorkoutSession
import com.sakura.data.workout.WorkoutTemplates
import com.sakura.preferences.AppPreferencesRepository
import com.sakura.sync.SyncBackendError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import com.sakura.features.workoutlog.RestTimerBridge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutLogViewModel(
    private val workoutRepo: WorkoutRepository,
    private val prefsRepo: AppPreferencesRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // -------------------------------------------------------------------------
    // User templates + schedule — loaded once for the template picker
    // -------------------------------------------------------------------------

    private val _userTemplates = MutableStateFlow<List<UserWorkoutTemplate>>(emptyList())
    val userTemplates: StateFlow<List<UserWorkoutTemplate>> = _userTemplates.asStateFlow()

    private val _schedule = MutableStateFlow(WorkoutSchedule())
    val schedule: StateFlow<WorkoutSchedule> = _schedule.asStateFlow()

    /** The template scheduled for the currently selected date, or null (rest day). */
    private val _scheduledWorkout = MutableStateFlow<UserWorkoutTemplate?>(null)
    val scheduledWorkout: StateFlow<UserWorkoutTemplate?> = _scheduledWorkout.asStateFlow()

    init {
        viewModelScope.launch {
            workoutRepo.templateVersion.collectLatest {
                _userTemplates.value = workoutRepo.loadWorkoutTemplates()
                updateScheduledWorkout()
            }
        }
        viewModelScope.launch {
            prefsRepo.workoutScheduleJson.collectLatest { json ->
                _schedule.value = WorkoutSchedule.fromJson(json)
                updateScheduledWorkout()
                loadCalendar()
            }
        }
    }

    private fun updateScheduledWorkout() {
        val dayOfWeek = _selectedDate.value.dayOfWeek
        val templateId = _schedule.value.templateIdFor(dayOfWeek)
        _scheduledWorkout.value = if (templateId != null) {
            _userTemplates.value.find { it.id == templateId }
        } else null
    }

    // -------------------------------------------------------------------------
    // Date navigation — mirrors FoodLogViewModel pattern
    // -------------------------------------------------------------------------

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // -------------------------------------------------------------------------
    // PR notification — emitted after addSet detects a personal record
    // -------------------------------------------------------------------------

    private val _prDetected = MutableStateFlow<PrNotification?>(null)
    val prDetected: StateFlow<PrNotification?> = _prDetected.asStateFlow()

    // -------------------------------------------------------------------------
    // Rest timer — drift-corrected countdown (Phase 7 / WORK-07)
    // -------------------------------------------------------------------------

    private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private val _activeTimerExerciseId = MutableStateFlow<Long?>(null)
    val activeTimerExerciseId: StateFlow<Long?> = _activeTimerExerciseId.asStateFlow()

    private var timerJob: Job? = null

    /** Background notification enabled — drives foreground service start/stop. */
    val bgNotificationEnabled: StateFlow<Boolean> = prefsRepo.timerBgNotification
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Signal for the UI to start a timer (auto-start after addSet). */
    data class PendingTimerStart(val durationSecs: Int, val exerciseId: Long)
    private val _pendingTimerStart = MutableStateFlow<PendingTimerStart?>(null)
    val pendingTimerStart: StateFlow<PendingTimerStart?> = _pendingTimerStart.asStateFlow()

    fun consumePendingTimerStart() {
        _pendingTimerStart.value = null
    }

    // -------------------------------------------------------------------------
    // Main UI state — derived from selectedDate + reloadTrigger
    // -------------------------------------------------------------------------

    private var _lastLoadedDate: LocalDate? = null

    val uiState: StateFlow<WorkoutLogUiState> = combine(
        _selectedDate, workoutRepo.logVersion
    ) { date, _ -> date }
        .flatMapLatest { date ->
            flow {
                // Only show loading spinner on date change, not on same-day reloads
                if (date != _lastLoadedDate) {
                    emit(WorkoutLogUiState.Loading)
                }
                try {
                    val session = workoutRepo.loadSession(date)
                    val exercises = session?.exercises ?: emptyList()

                    // Load previous sets for each exercise for auto-fill reference
                    val previousSetsMap = mutableMapOf<String, List<SetLog>>()
                    exercises.forEach { ex ->
                        val prev = workoutRepo.loadPreviousSetsForExercise(ex.name)
                        if (prev.isNotEmpty()) previousSetsMap[ex.name] = prev
                    }

                    // Look up template definitions for target sets/reps.
                    // Prefer hardcoded split-day templates; fall back to user template targets.
                    val templateDefs = session?.splitDay?.let { day ->
                        WorkoutTemplates.forDay(day).exercises.associateBy { it.name }
                    } ?: emptyMap()
                    val userTemplateDefs = if (templateDefs.isEmpty()) {
                        _scheduledWorkout.value?.exercises?.associateBy { it.name } ?: emptyMap()
                    } else emptyMap()

                    _lastLoadedDate = date
                    emit(
                        WorkoutLogUiState.DayLoaded(
                            date = date,
                            isToday = date == LocalDate.now(),
                            templateName = session?.templateName ?: _scheduledWorkout.value?.name,
                            exercises = exercises.map { ex ->
                                val def = templateDefs[ex.name]
                                val userDef = userTemplateDefs[ex.name]
                                DayExercise(
                                    exerciseLog = ex,
                                    targetSets = def?.targetSets ?: userDef?.targetSets?.takeIf { it > 0 },
                                    targetReps = def?.targetReps ?: userDef?.targetReps?.takeIf { it != 0 },
                                    targetHoldSecs = def?.targetHoldSecs ?: userDef?.targetHoldSecs?.takeIf { it > 0 },
                                    previousSets = previousSetsMap[ex.name] ?: emptyList(),
                                    category = ex.category
                                )
                            },
                            isComplete = session?.isComplete ?: false,
                            totalVolume = session?.totalVolume ?: 0.0,
                            previousSetsMap = previousSetsMap
                        )
                    )
                } catch (e: SyncBackendError.FolderUnavailable) {
                    emit(WorkoutLogUiState.Error.FolderUnavailable)
                } catch (e: SyncBackendError.PermissionDenied) {
                    emit(WorkoutLogUiState.Error.FolderUnavailable)
                } catch (e: Exception) {
                    emit(WorkoutLogUiState.Error.Generic(e.message ?: "Unknown error"))
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
        updateScheduledWorkout()
    }

    fun navigateToDate(date: LocalDate) {
        _selectedDate.value = date
        updateScheduledWorkout()
        val month = YearMonth.from(date)
        if (month != _displayedMonth.value) {
            setDisplayedMonth(month)
        }
    }

    fun goToToday() {
        val today = LocalDate.now()
        _selectedDate.value = today
        updateScheduledWorkout()
        val month = YearMonth.from(today)
        if (month != _displayedMonth.value) {
            setDisplayedMonth(month)
        }
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
                workoutRepo.addExercise(date, exerciseLog, splitDay = splitDay.label)
                // Small delay to ensure unique IDs when iterating rapidly
                kotlinx.coroutines.delay(2)
            }

        }
    }

    fun loadUserTemplate(template: UserWorkoutTemplate) {
        viewModelScope.launch {
            val date = _selectedDate.value
            template.exercises.forEachIndexed { idx, templateEx ->
                val exerciseType = ExerciseType.fromLabel(templateEx.category.label)
                val exerciseLog = ExerciseLog(
                    id = System.currentTimeMillis() + idx,
                    name = templateEx.name,
                    exerciseType = exerciseType,
                    category = templateEx.category,
                    sets = emptyList()
                )
                workoutRepo.addExercise(date, exerciseLog)
                delay(2)
            }

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
        }
    }

    // -------------------------------------------------------------------------
    // Add a set to an exercise (immediate write — no draft/finish lifecycle)
    // -------------------------------------------------------------------------

    fun addSet(exerciseId: Long, set: SetLog) {
        viewModelScope.launch {
            val date = _selectedDate.value
            val exerciseName = (uiState.value as? WorkoutLogUiState.DayLoaded)
                ?.exercises?.find { it.exerciseLog.id == exerciseId }
                ?.exerciseLog?.name

            // Check for PR BEFORE writing so isPr is stored on the set
            var setToWrite = set
            if (exerciseName != null) {
                val pb = workoutRepo.findPersonalBest(exerciseName)
                if (pb != null) {
                    val prType = when {
                        set.unit != "bw" && set.weight > pb.weight -> "Weight"
                        set.unit == "bw" && set.holdSecs == 0 && set.reps > pb.reps -> "Reps"
                        set.holdSecs > 0 && set.holdSecs > pb.holdSecs -> "Hold"
                        else -> null
                    }
                    if (prType != null) {
                        setToWrite = set.copy(isPr = true)
                        _prDetected.value = PrNotification(exerciseName, prType)
                    }
                }
            }

            workoutRepo.addSet(date, exerciseId, setToWrite)


            // Auto-start rest timer after logging a set (Phase 7)
            val timerEnabled = try { prefsRepo.timerEnabled.first() } catch (_: Exception) { true }
            val autoStart = try { prefsRepo.timerAutoStart.first() } catch (_: Exception) { true }
            if (timerEnabled && autoStart) {
                // Determine rest duration: per-exercise override > global default
                val exercise = (uiState.value as? WorkoutLogUiState.DayLoaded)
                    ?.exercises?.find { it.exerciseLog.id == exerciseId }
                val libraryExercise = exercise?.let {
                    ExerciseLibrary.allExercises().find { lib ->
                        lib.name.equals(it.exerciseLog.name, ignoreCase = true)
                    }
                }
                val perExerciseRest = libraryExercise?.restSecs
                val globalDefault = try { prefsRepo.defaultRestTimerSecs.first() } catch (_: Exception) { 90 }
                val restDuration = perExerciseRest ?: globalDefault
                // Signal UI to start timer (ViewModel has no Context for vibration)
                _pendingTimerStart.value = PendingTimerStart(restDuration, exerciseId)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Remove exercise from day
    // -------------------------------------------------------------------------

    fun removeExercise(exerciseId: Long) {
        viewModelScope.launch {
            workoutRepo.removeExercise(_selectedDate.value, exerciseId)

        }
    }

    // -------------------------------------------------------------------------
    // Replace exercise (change exercise)
    // -------------------------------------------------------------------------

    fun replaceExercise(oldExerciseId: Long, libraryExercise: LibraryExercise) {
        viewModelScope.launch {
            val date = _selectedDate.value
            val exerciseType = ExerciseType.fromLabel(libraryExercise.category.label)
            val newExerciseLog = ExerciseLog(
                id = System.currentTimeMillis(),
                name = libraryExercise.name,
                exerciseType = exerciseType,
                category = libraryExercise.category,
                sets = emptyList()
            )
            workoutRepo.replaceExercise(date, oldExerciseId, newExerciseLog)

        }
    }

    // -------------------------------------------------------------------------
    // Reorder exercises (drag-to-reorder)
    // -------------------------------------------------------------------------

    fun reorderExercises(orderedIds: List<Long>) {
        viewModelScope.launch {
            workoutRepo.reorderExercises(_selectedDate.value, orderedIds)

        }
    }

    // -------------------------------------------------------------------------
    // Remove a set from an exercise
    // -------------------------------------------------------------------------

    fun removeSet(exerciseId: Long, setNumber: Int) {
        viewModelScope.launch {
            workoutRepo.removeSet(_selectedDate.value, exerciseId, setNumber)

        }
    }

    /** Replace a set by removing the old one and adding the updated one. */
    fun updateSet(exerciseId: Long, oldSetNumber: Int, newSet: SetLog) {
        viewModelScope.launch {
            val date = _selectedDate.value
            workoutRepo.removeSet(date, exerciseId, oldSetNumber)
            workoutRepo.addSet(date, exerciseId, newSet)

        }
    }

    // -------------------------------------------------------------------------
    // Mark day complete (soft flag)
    // -------------------------------------------------------------------------

    fun toggleComplete() {
        viewModelScope.launch {
            val current = (uiState.value as? WorkoutLogUiState.DayLoaded)?.isComplete ?: false
            workoutRepo.markComplete(_selectedDate.value, !current)
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
            addExercise(newExercise)
        }
    }

    fun createAndReplaceExercise(oldExerciseId: Long, name: String, category: ExerciseCategory) {
        val newExercise = LibraryExercise(name = name, category = category, isBuiltIn = false)
        ExerciseLibrary.addUserExercise(newExercise)
        viewModelScope.launch {
            workoutRepo.saveUserExercises(ExerciseLibrary.userExercises())
            replaceExercise(oldExerciseId, newExercise)
        }
    }

    // -------------------------------------------------------------------------
    // PR detection
    // -------------------------------------------------------------------------

    fun dismissPrNotification() {
        _prDetected.value = null
    }

    // -------------------------------------------------------------------------
    // Rest timer controls (Phase 7 / WORK-07)
    // -------------------------------------------------------------------------

    /**
     * Start a drift-corrected countdown timer.
     * Uses wall-clock time (System.currentTimeMillis) to avoid accumulating delay() drift.
     * Always cancels any existing timer first (one global timer at a time).
     *
     * @param durationSecs countdown duration in seconds
     * @param exerciseId the exercise card that should display the countdown
     * @param context Android context for vibration/sound on completion
     */
    fun startTimer(durationSecs: Int, exerciseId: Long, context: Context) {
        timerJob?.cancel()
        _activeTimerExerciseId.value = exerciseId
        timerJob = viewModelScope.launch {
            val endMs = System.currentTimeMillis() + durationSecs * 1_000L
            while (true) {
                val remainingMs = endMs - System.currentTimeMillis()
                if (remainingMs <= 0) break
                val remainingSecs = ((remainingMs + 999) / 1000).toInt()
                _timerState.value = TimerState.Running(remainingSecs, durationSecs)
                RestTimerBridge.update(TimerState.Running(remainingSecs, durationSecs))
                delay(minOf(remainingMs, 200L))
            }
            _timerState.value = TimerState.Done
            RestTimerBridge.update(TimerState.Done)
            triggerCompletionFeedback(context)
            delay(2_500L)
            _timerState.value = TimerState.Idle
            RestTimerBridge.update(TimerState.Idle)
            _activeTimerExerciseId.value = null
        }
    }

    /** Dismiss/skip the running timer immediately. */
    fun dismissTimer() {
        timerJob?.cancel()
        timerJob = null
        _timerState.value = TimerState.Idle
        RestTimerBridge.update(TimerState.Idle)
        _activeTimerExerciseId.value = null
    }

    /**
     * Adjust the running timer by a delta (positive = add time, negative = subtract).
     * No-op if no timer is running.
     * This creates a new timer with adjusted remaining time, preserving wall-clock accuracy.
     */
    fun adjustTimer(deltaSecs: Int, context: Context) {
        val current = _timerState.value
        if (current !is TimerState.Running) return
        val newRemaining = (current.remainingSecs + deltaSecs).coerceAtLeast(5)
        val exerciseId = _activeTimerExerciseId.value ?: return
        startTimer(newRemaining, exerciseId, context)
    }

    /**
     * Trigger vibration and/or sound based on user's notification preference.
     * Reads the preference synchronously via .first() (runs on IO already via viewModelScope).
     */
    private suspend fun triggerCompletionFeedback(context: Context) {
        val notifType = try { prefsRepo.timerNotificationType.first() } catch (_: Exception) { "VIBRATION" }
        withContext(Dispatchers.Main) {
            when (notifType) {
                "VIBRATION" -> vibrateDevice(context)
                "SOUND" -> playNotificationSound(context)
                "BOTH" -> {
                    vibrateDevice(context)
                    playNotificationSound(context)
                }
                // "NONE" — do nothing
            }
        }
    }

    private fun vibrateDevice(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            vibrator.vibrate(VibrationEffect.createOneShot(400L, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) { /* Best-effort */ }
    }

    private fun playNotificationSound(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.play()
        } catch (_: Exception) { /* Best-effort */ }
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
    // Calendar — month-based grid
    // -------------------------------------------------------------------------

    private val _displayedMonth = MutableStateFlow(YearMonth.now())
    val displayedMonth: StateFlow<YearMonth> = _displayedMonth.asStateFlow()

    private val _calendarExpanded = MutableStateFlow(false)
    val calendarExpanded: StateFlow<Boolean> = _calendarExpanded.asStateFlow()

    fun setCalendarExpanded(expanded: Boolean) { _calendarExpanded.value = expanded }

    private val _calendarDays = MutableStateFlow<List<CalendarDay>>(emptyList())
    val calendarDays: StateFlow<List<CalendarDay>> = _calendarDays.asStateFlow()

    fun setDisplayedMonth(yearMonth: YearMonth) {
        _displayedMonth.value = yearMonth
        loadCalendar(yearMonth)
    }

    fun loadCalendar(yearMonth: YearMonth = _displayedMonth.value) {
        viewModelScope.launch(Dispatchers.IO) {
            val today = LocalDate.now()
            val firstOfMonth = yearMonth.atDay(1)
            val lastOfMonth = yearMonth.atEndOfMonth()
            val gridStart = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val gridEnd = lastOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

            // Load all history and build a date -> session map
            val allSessions = try { workoutRepo.loadHistory() } catch (e: Exception) { emptyList() }
            val sessionByDate = allSessions.associateBy { it.date }
            val schedule = _schedule.value

            val days = buildList {
                var current = gridStart
                while (!current.isAfter(gridEnd)) {
                    val session = sessionByDate[current]
                    val isToday = current == today
                    val scheduled = schedule.templateIdFor(current.dayOfWeek) != null

                    add(
                        CalendarDay(
                            date = current,
                            splitDay = session?.splitDay,
                            splitLabel = session?.templateName
                                ?: session?.splitDay?.displayName?.substringAfterLast("— ")?.trim(),
                            hasWorkout = session != null,
                            isScheduled = scheduled,
                            isComplete = session?.isComplete ?: false,
                            isPast = !current.isAfter(today),
                            isToday = isToday
                        )
                    )
                    current = current.plusDays(1)
                }
            }

            _calendarDays.value = days
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
        viewModelScope.launch { workoutRepo.logVersion.collectLatest { loadCalendar() } }
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
