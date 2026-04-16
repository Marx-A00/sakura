package com.sakura.features.workoutlog

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.data.workout.ExerciseCategory
import com.sakura.data.workout.ExerciseLog
import com.sakura.data.workout.SetLog
import com.sakura.ui.theme.CherryBlossomPink
import com.sakura.ui.theme.DeepRose
import com.sakura.ui.theme.ForestGreen
import com.sakura.ui.theme.WarmCream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.sakura.features.workoutlog.RestTimerService
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Day-based workout log screen.
 *
 * Layout:
 * - TopAppBar with date navigation (left arrow / date label / right arrow)
 * - EMPTY state: dumbbell icon, "No workout logged", template/add buttons
 * - ACTIVE state: template name + "Complete" button header, exercise cards
 * - Each exercise card shows: name, category badge, target sets×reps, inline sets,
 *   PR badge, previous session reference, "+ Add Set" button
 * - Bottom: "+ Add Exercise" outlined button
 * - NavigationBar: FOOD | WORKOUT | HOME | SETTINGS
 *
 * Matches mockups: 03-workout-empty-state.png, 03-workout-active.png
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLogScreen(
    viewModel: WorkoutLogViewModel,
    onNavigateToHistory: () -> Unit,
    fromTemplateTrigger: Int = 0
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val prDetected by viewModel.prDetected.collectAsStateWithLifecycle()
    val calendarDays by viewModel.calendarDays.collectAsStateWithLifecycle()
    val timerState by viewModel.timerState.collectAsStateWithLifecycle()
    val activeTimerExerciseId by viewModel.activeTimerExerciseId.collectAsStateWithLifecycle()
    val pendingTimer by viewModel.pendingTimerStart.collectAsStateWithLifecycle()
    val bgNotifEnabled by viewModel.bgNotificationEnabled.collectAsStateWithLifecycle()
    val userTemplates by viewModel.userTemplates.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Date picker
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    // Template picker sheet
    var showTemplatePicker by rememberSaveable { mutableStateOf(false) }
    val templatePickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Open template picker when radial menu triggers it
    LaunchedEffect(fromTemplateTrigger) {
        if (fromTemplateTrigger > 0) showTemplatePicker = true
    }

    // Exercise picker sheet
    var showExercisePicker by rememberSaveable { mutableStateOf(false) }
    val exercisePickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Change exercise — tracks which exercise is being replaced
    var changeExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Set input sheet — tracks which exercise is being logged
    var setInputExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }
    val setInputSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Edit set state — when non-null, SetInputSheet is in edit mode for this set
    var editingSet by remember { mutableStateOf<Pair<Long, SetLog>?>(null) }

    // Timer adjust sheet
    var showTimerAdjust by rememberSaveable { mutableStateOf(false) }
    val timerAdjustSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Handle auto-start timer signal from addSet
    LaunchedEffect(pendingTimer) {
        pendingTimer?.let { pending ->
            viewModel.startTimer(pending.durationSecs, pending.exerciseId, context)
            viewModel.consumePendingTimerStart()
        }
    }

    // Start/stop foreground service based on timer state + bg notification preference
    LaunchedEffect(timerState, bgNotifEnabled) {
        if (bgNotifEnabled && timerState is TimerState.Running) {
            RestTimerService.startService(context)
        } else if (timerState is TimerState.Idle) {
            RestTimerService.stopService(context)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is WorkoutLogUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = CherryBlossomPink)
                }
            }

            is WorkoutLogUiState.Error.FolderUnavailable -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Sync folder unavailable",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Check your Syncthing folder in Settings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is WorkoutLogUiState.Error.Generic -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Error loading workout",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is WorkoutLogUiState.DayLoaded -> {
                if (state.exercises.isEmpty()) {
                    EmptyDayContent(
                        calendarDays = calendarDays,
                        selectedDate = selectedDate,
                        onDateSelected = { viewModel.navigateToDate(it) },
                        onDateLabelClick = { showDatePicker = true },
                        onNavigateToHistory = onNavigateToHistory,
                        onStartFromTemplate = { showTemplatePicker = true },
                        onAddExercise = { showExercisePicker = true }
                    )
                } else {
                    ActiveDayContent(
                        state = state,
                        calendarDays = calendarDays,
                        selectedDate = selectedDate,
                        onDateSelected = { viewModel.navigateToDate(it) },
                        onDateLabelClick = { showDatePicker = true },
                        onNavigateToHistory = onNavigateToHistory,
                        timerState = timerState,
                        activeTimerExerciseId = activeTimerExerciseId,
                        onToggleComplete = { viewModel.toggleComplete() },
                        onAddSet = { exerciseId -> setInputExerciseId = exerciseId },
                        onEditSet = { exerciseId, set -> editingSet = Pair(exerciseId, set) },
                        onRemoveSet = { exerciseId, setNum -> viewModel.removeSet(exerciseId, setNum) },
                        onAddExercise = { showExercisePicker = true },
                        onRemoveExercise = { exerciseId -> viewModel.removeExercise(exerciseId) },
                        onChangeExercise = { exerciseId -> changeExerciseId = exerciseId },
                        onReorderExercises = { orderedIds -> viewModel.reorderExercises(orderedIds) },
                        onTimerTap = { showTimerAdjust = true }
                    )
                }
            }
        }
    }

    // Template picker sheet
    if (showTemplatePicker) {
        TemplatePickerSheet(
            sheetState = templatePickerSheetState,
            userTemplates = userTemplates,
            onSelectBuiltIn = { splitDay ->
                showTemplatePicker = false
                viewModel.loadTemplate(splitDay)
            },
            onSelectUserTemplate = { template ->
                showTemplatePicker = false
                viewModel.loadUserTemplate(template)
            },
            onDismiss = { showTemplatePicker = false }
        )
    }

    // Exercise picker bottom sheet (shared for add + change exercise)
    if (showExercisePicker || changeExerciseId != null) {
        val replacingId = changeExerciseId
        ExercisePickerSheet(
            sheetState = exercisePickerSheetState,
            onAddExercise = { libraryExercise ->
                if (replacingId != null) {
                    viewModel.replaceExercise(replacingId, libraryExercise)
                    changeExerciseId = null
                } else {
                    viewModel.addExercise(libraryExercise)
                }
            },
            onCreateExercise = { name, category ->
                if (replacingId != null) {
                    viewModel.createAndReplaceExercise(replacingId, name, category)
                    changeExerciseId = null
                } else {
                    viewModel.createAndAddExercise(name, category)
                }
            },
            onDismiss = {
                showExercisePicker = false
                changeExerciseId = null
            }
        )
    }

    // Set input bottom sheet
    val exerciseId = setInputExerciseId
    if (exerciseId != null) {
        val dayState = uiState as? WorkoutLogUiState.DayLoaded
        val dayExercise = dayState?.exercises?.find { it.exerciseLog.id == exerciseId }
        if (dayExercise != null) {
            val setNumber = dayExercise.exerciseLog.sets.size + 1
            // Pre-fill: last logged set from this session, or first previous set
            val prefill = dayExercise.exerciseLog.sets.lastOrNull()
                ?: dayExercise.previousSets.firstOrNull()

            SetInputSheet(
                sheetState = setInputSheetState,
                exerciseName = dayExercise.exerciseLog.name,
                category = dayExercise.category,
                setNumber = setNumber,
                prefillSet = prefill,
                previousSets = dayExercise.previousSets,
                onLogSet = { set ->
                    viewModel.addSet(exerciseId, set)
                    setInputExerciseId = null
                },
                onDismiss = { setInputExerciseId = null }
            )
        }
    }

    // Edit set sheet — re-opens SetInputSheet pre-filled with the existing set
    val editData = editingSet
    if (editData != null) {
        val (editExerciseId, editSet) = editData
        val dayState = uiState as? WorkoutLogUiState.DayLoaded
        val dayExercise = dayState?.exercises?.find { it.exerciseLog.id == editExerciseId }
        if (dayExercise != null) {
            val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            SetInputSheet(
                sheetState = editSheetState,
                exerciseName = dayExercise.exerciseLog.name,
                category = dayExercise.category,
                setNumber = editSet.setNumber,
                prefillSet = editSet,
                previousSets = dayExercise.previousSets,
                onLogSet = { updatedSet ->
                    viewModel.updateSet(editExerciseId, editSet.setNumber, updatedSet)
                    editingSet = null
                },
                onDismiss = { editingSet = null }
            )
        }
    }

    // PR notification dialog
    prDetected?.let { pr ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPrNotification() },
            title = { Text("New Personal Record!") },
            text = { Text("You just set a new ${pr.prType} PR on ${pr.exerciseName}!") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPrNotification() }) {
                    Text("Awesome!", color = CherryBlossomPink)
                }
            }
        )
    }

    // Timer adjust bottom sheet
    if (showTimerAdjust) {
        val currentRemaining = (timerState as? TimerState.Running)?.remainingSecs ?: 0
        TimerAdjustSheet(
            sheetState = timerAdjustSheetState,
            currentRemainingSecs = currentRemaining,
            onAdjust = { delta -> viewModel.adjustTimer(delta, context) },
            onSetExact = { secs ->
                val exerciseId = activeTimerExerciseId
                if (exerciseId != null) {
                    viewModel.startTimer(secs, exerciseId, context)
                }
                showTimerAdjust = false
            },
            onDismissTimer = {
                viewModel.dismissTimer()
                showTimerAdjust = false
            },
            onDismissSheet = { showTimerAdjust = false }
        )
    }

    // Date picker dialog — tapping date title opens this
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        viewModel.navigateToDate(date)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

@Composable
private fun EmptyDayContent(
    calendarDays: List<CalendarDay>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDateLabelClick: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onStartFromTemplate: () -> Unit,
    onAddExercise: () -> Unit
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Calendar at top
        if (calendarDays.isNotEmpty()) {
            item {
                SplitCalendar(
                    days = calendarDays,
                    selectedDate = selectedDate,
                    onDateSelected = onDateSelected,
                    onDateLabelClick = onDateLabelClick,
                    onHistoryClick = onNavigateToHistory
                )
            }
        }

        // Empty state body
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No workout logged",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pick a saved workout to get started, or add exercises one at a time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))

                // Start from Saved Workouts — green filled button (matches mockup)
                Button(
                    onClick = onStartFromTemplate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) {
                    Text("Start from Saved Workouts", color = Color.White)
                }
                Spacer(Modifier.height(12.dp))

                // Add Exercise — outlined button (matches mockup)
                OutlinedButton(
                    onClick = onAddExercise,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Add Exercise")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Active state
// ---------------------------------------------------------------------------

@Composable
private fun ActiveDayContent(
    state: WorkoutLogUiState.DayLoaded,
    calendarDays: List<CalendarDay>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDateLabelClick: () -> Unit,
    onNavigateToHistory: () -> Unit,
    timerState: TimerState,
    activeTimerExerciseId: Long?,
    onToggleComplete: () -> Unit,
    onAddSet: (Long) -> Unit,
    onEditSet: (Long, SetLog) -> Unit,
    onRemoveSet: (Long, Int) -> Unit,
    onAddExercise: () -> Unit,
    onRemoveExercise: (Long) -> Unit,
    onChangeExercise: (Long) -> Unit,
    onReorderExercises: (List<Long>) -> Unit,
    onTimerTap: () -> Unit
) {
    val context = LocalContext.current

    // Local mutable exercise list for drag reorder — synced from state
    val exercises = remember { mutableStateListOf<DayExercise>() }
    LaunchedEffect(state.exercises) {
        if (exercises.map { it.exerciseLog.id } != state.exercises.map { it.exerciseLog.id }) {
            exercises.clear()
            exercises.addAll(state.exercises)
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromId = from.key as? Long ?: return@rememberReorderableLazyListState
        val toId = to.key as? Long ?: return@rememberReorderableLazyListState
        val fromIdx = exercises.indexOfFirst { it.exerciseLog.id == fromId }
        val toIdx = exercises.indexOfFirst { it.exerciseLog.id == toId }
        if (fromIdx >= 0 && toIdx >= 0) {
            exercises.apply { add(toIdx, removeAt(fromIdx)) }
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Training calendar at the top
        if (calendarDays.isNotEmpty()) {
            item {
                SplitCalendar(
                    days = calendarDays,
                    selectedDate = selectedDate,
                    onDateSelected = onDateSelected,
                    onDateLabelClick = onDateLabelClick,
                    onHistoryClick = onNavigateToHistory
                )
            }
        }

        // Header: template name + Complete button (matches 03-workout-active.png)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (state.templateName != null) {
                        Text(
                            text = state.templateName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Volume summary — WORK-09 (only shown when volume > 0)
                    if (state.totalVolume > 0) {
                        Text(
                            text = "Vol: ${formatVolume(state.totalVolume)} ${state.volumeUnit}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Complete button (outlined, green checkmark when done)
                OutlinedButton(
                    onClick = onToggleComplete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (state.isComplete) ForestGreen else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    if (state.isComplete) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                    }
                    Text("Complete")
                }
            }
        }

        // Exercise cards — drag-to-reorder via long press
        items(exercises, key = { it.exerciseLog.id }) { dayExercise ->
            ReorderableItem(reorderableLazyListState, key = dayExercise.exerciseLog.id) { isDragging ->
                val elevation by animateDpAsState(
                    if (isDragging) 8.dp else 1.dp,
                    label = "dragElevation"
                )
                val isTimerActiveForThis = dayExercise.exerciseLog.id == activeTimerExerciseId &&
                    timerState !is TimerState.Idle
                ExerciseCard(
                    modifier = Modifier.longPressDraggableHandle(
                        onDragStarted = {
                            val vibrator = context.getSystemService(Vibrator::class.java)
                            vibrator?.vibrate(
                                VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE)
                            )
                        },
                        onDragStopped = {
                            onReorderExercises(exercises.map { it.exerciseLog.id })
                        }
                    ),
                    elevation = elevation,
                    dayExercise = dayExercise,
                    timerState = timerState,
                    isTimerActiveForThis = isTimerActiveForThis,
                    onAddSet = { onAddSet(dayExercise.exerciseLog.id) },
                    onEditSet = { set -> onEditSet(dayExercise.exerciseLog.id, set) },
                    onRemoveSet = { setNum -> onRemoveSet(dayExercise.exerciseLog.id, setNum) },
                    onRemoveExercise = { onRemoveExercise(dayExercise.exerciseLog.id) },
                    onChangeExercise = { onChangeExercise(dayExercise.exerciseLog.id) },
                    onTimerTap = onTimerTap
                )
            }
        }

        // "+ Add Exercise" at bottom of list
        item {
            OutlinedButton(
                onClick = onAddExercise,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("+ Add Exercise")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Exercise card
// ---------------------------------------------------------------------------

@Composable
private fun ExerciseCard(
    dayExercise: DayExercise,
    timerState: TimerState,
    isTimerActiveForThis: Boolean,
    onAddSet: () -> Unit,
    onEditSet: (SetLog) -> Unit,
    onRemoveSet: (Int) -> Unit,
    onRemoveExercise: () -> Unit,
    onChangeExercise: () -> Unit,
    onTimerTap: () -> Unit,
    modifier: Modifier = Modifier,
    elevation: Dp = 1.dp
) {
    val exerciseLog = dayExercise.exerciseLog
    var showMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: name + category badge + target + overflow menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = exerciseLog.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    CategoryBadge(category = dayExercise.category)
                }

                // Target sets × reps (if from template)
                val targetText = buildTargetText(dayExercise)
                if (targetText != null) {
                    Text(
                        text = targetText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Countdown display when timer is active for this exercise
                if (isTimerActiveForThis) {
                    val timerText = when (timerState) {
                        is TimerState.Running -> {
                            val mins = timerState.remainingSecs / 60
                            val secs = timerState.remainingSecs % 60
                            "%d:%02d".format(mins, secs)
                        }
                        is TimerState.Done -> "Done"
                        else -> null
                    }
                    if (timerText != null) {
                        Text(
                            text = timerText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100),
                            modifier = Modifier
                                .clickable { onTimerTap() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // 3-dot overflow menu
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (exerciseLog.sets.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text(if (editing) "Done editing" else "Edit sets") },
                                onClick = {
                                    showMenu = false
                                    editing = !editing
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Change exercise") },
                            onClick = {
                                showMenu = false
                                onChangeExercise()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove exercise") },
                            onClick = {
                                showMenu = false
                                onRemoveExercise()
                            }
                        )
                    }
                }
            }

            // Sets section — logged sets + remaining target placeholders
            val loggedCount = exerciseLog.sets.size
            val targetCount = dayExercise.targetSets ?: 0
            val hasContent = loggedCount > 0 || targetCount > 0

            if (hasContent) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Logged sets (with PR badge, tappable + deletable in edit mode)
                exerciseLog.sets.forEach { set ->
                    SetRow(
                        set = set,
                        category = dayExercise.category,
                        editing = editing,
                        onTap = { onEditSet(set) },
                        onDelete = { onRemoveSet(set.setNumber) }
                    )
                }

                // Remaining target placeholders
                val remaining = (targetCount - loggedCount).coerceAtLeast(0)
                for (i in 1..remaining) {
                    val setNum = loggedCount + i
                    TargetSetRow(
                        setNumber = setNum,
                        targetReps = dayExercise.targetReps,
                        targetHoldSecs = dayExercise.targetHoldSecs,
                        category = dayExercise.category
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (editing) {
                // Edit mode action bar
                FilledTonalButton(
                    onClick = { editing = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Done Editing", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            } else {
                // "+ Add Set" button (green text, centered)
                TextButton(
                    onClick = onAddSet,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = ForestGreen)
                ) {
                    Text(
                        "+ Log Set",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            // Exercise volume summary (only when sets are logged)
            if (exerciseLog.sets.isNotEmpty()) {
                val volumeText = formatExerciseVolume(exerciseLog, dayExercise.category)
                if (volumeText != null) {
                    Text(
                        text = volumeText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Previous session reference ("Last: 80kgx5 · 80kgx5 · ...")
            if (dayExercise.previousSets.isNotEmpty()) {
                Text(
                    text = "Last:  ${formatPreviousSets(dayExercise)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Set row (inline set display with PR badge)
// ---------------------------------------------------------------------------

@Composable
private fun SetRow(
    set: SetLog,
    category: ExerciseCategory,
    editing: Boolean = false,
    onTap: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (editing) Modifier.clickable { onTap() } else Modifier)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Delete icon in edit mode
        if (editing) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Delete set",
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onDelete() },
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.size(4.dp))
        }

        Text(
            text = "Set ${set.setNumber}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.3f)
        )
        Row(
            modifier = Modifier.weight(0.7f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val valueText = when (category) {
                ExerciseCategory.TIMED -> "${set.holdSecs}s hold"
                ExerciseCategory.CARDIO -> "${set.durationMin ?: 0}min" + (set.distanceKm?.let { " · ${it}km" } ?: "")
                ExerciseCategory.STRETCH -> "${set.durationMin ?: 0}min"
                ExerciseCategory.BODYWEIGHT -> "${set.reps} reps"
                ExerciseCategory.WEIGHTED -> "${formatWeight(set.weight)}${set.unit} × ${set.reps}"
            }
            Text(
                text = valueText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (set.isPr) {
                Spacer(Modifier.size(6.dp))
                PrBadge()
            }
        }
    }
}

/**
 * Placeholder row for a target set that hasn't been logged yet.
 * Shown faded to distinguish from actual logged sets.
 */
@Composable
private fun TargetSetRow(
    setNumber: Int,
    targetReps: Int?,
    targetHoldSecs: Int?,
    category: ExerciseCategory
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .alpha(0.4f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Set $setNumber",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.3f)
        )
        Text(
            text = when {
                targetHoldSecs != null && targetHoldSecs > 0 -> "${targetHoldSecs}s hold"
                targetReps == -1 -> "max reps"
                targetReps != null -> "$targetReps reps"
                else -> "—"
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun PrBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFB5860D))  // amber/gold for PR — matches mockup
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = "PR",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

// ---------------------------------------------------------------------------
// Category badge (chip-style, gray background — matches mockup)
// ---------------------------------------------------------------------------

@Composable
private fun CategoryBadge(category: ExerciseCategory) {
    val label = when (category) {
        ExerciseCategory.WEIGHTED -> "weighted"
        ExerciseCategory.BODYWEIGHT -> "bodyweight"
        ExerciseCategory.TIMED -> "timed"
        ExerciseCategory.CARDIO -> "cardio"
        ExerciseCategory.STRETCH -> "stretch"
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------
// Helper functions
// ---------------------------------------------------------------------------

private fun formatVolume(volume: Double): String {
    val rounded = volume.toInt()
    return if (rounded >= 1000) {
        "%,d".format(rounded)
    } else {
        rounded.toString()
    }
}

private fun buildTargetText(dayExercise: DayExercise): String? {
    val sets = dayExercise.targetSets ?: return null
    val reps = dayExercise.targetReps
    val holdSecs = dayExercise.targetHoldSecs

    return when {
        holdSecs != null && holdSecs > 0 -> "$sets × ${holdSecs}s"
        reps != null && reps == -1 -> "$sets × max"
        reps != null -> "$sets × $reps"
        else -> "$sets sets"
    }
}

private fun formatPreviousSets(dayExercise: DayExercise): String {
    return dayExercise.previousSets.take(4).joinToString(" · ") { set ->
        when (dayExercise.category) {
            ExerciseCategory.TIMED -> "${set.holdSecs}s"
            ExerciseCategory.CARDIO -> "${set.durationMin ?: 0}min"
            ExerciseCategory.STRETCH -> "${set.durationMin ?: 0}min"
            ExerciseCategory.BODYWEIGHT -> "${set.reps}reps"
            ExerciseCategory.WEIGHTED -> "${formatWeight(set.weight)}${set.unit}×${set.reps}"
        }
    }
}

private fun formatExerciseVolume(exerciseLog: ExerciseLog, category: ExerciseCategory): String? {
    return when (category) {
        ExerciseCategory.WEIGHTED -> {
            val vol = exerciseLog.volume
            if (vol > 0) {
                val unit = exerciseLog.sets.firstOrNull { it.unit != "bw" }?.unit ?: "lbs"
                "Vol: ${formatVolume(vol)} $unit"
            } else null
        }
        ExerciseCategory.BODYWEIGHT -> {
            val reps = exerciseLog.totalReps
            if (reps > 0) "Total: $reps reps" else null
        }
        ExerciseCategory.TIMED -> {
            val secs = exerciseLog.totalHoldSecs
            if (secs > 0) "Total: ${secs}s" else null
        }
        ExerciseCategory.CARDIO -> {
            val mins = exerciseLog.totalDurationMin
            if (mins > 0) "Total: ${mins}min" else null
        }
        ExerciseCategory.STRETCH -> {
            val mins = exerciseLog.totalDurationMin
            if (mins > 0) "Total: ${mins}min" else null
        }
    }
}

private fun formatWeight(weight: Double): String =
    if (weight == weight.toLong().toDouble()) weight.toLong().toString()
    else "%.1f".format(weight)
