package com.sakura.features.workoutlog

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.data.workout.ExerciseCategory
import com.sakura.data.workout.SetLog
import com.sakura.data.workout.SplitDay
import com.sakura.ui.theme.CherryBlossomPink
import com.sakura.ui.theme.DeepRose
import com.sakura.ui.theme.ForestGreen
import com.sakura.ui.theme.WarmCream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    onNavigateToHistory: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val prDetected by viewModel.prDetected.collectAsStateWithLifecycle()
    val calendarDays by viewModel.calendarDays.collectAsStateWithLifecycle()

    // Date picker
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    // Template picker dialog
    var showTemplatePicker by rememberSaveable { mutableStateOf(false) }

    // Exercise picker sheet
    var showExercisePicker by rememberSaveable { mutableStateOf(false) }
    val exercisePickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Set input sheet — tracks which exercise is being logged
    var setInputExerciseId by rememberSaveable { mutableStateOf<Long?>(null) }
    val setInputSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { viewModel.navigateDate(-1) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous day"
                            )
                        }
                        Text(
                            text = formatWorkoutDate(selectedDate),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showDatePicker = true },
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        IconButton(onClick = { viewModel.navigateDate(1) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next day"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Filled.DateRange, contentDescription = "History")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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
                        // EMPTY STATE — matches 03-workout-empty-state.png
                        EmptyDayContent(
                            calendarDays = calendarDays,
                            onStartFromTemplate = { showTemplatePicker = true },
                            onAddExercise = { showExercisePicker = true }
                        )
                    } else {
                        // ACTIVE STATE — matches 03-workout-active.png
                        ActiveDayContent(
                            state = state,
                            calendarDays = calendarDays,
                            onToggleComplete = { viewModel.toggleComplete() },
                            onAddSet = { exerciseId -> setInputExerciseId = exerciseId },
                            onAddExercise = { showExercisePicker = true },
                            onRemoveExercise = { exerciseId -> viewModel.removeExercise(exerciseId) }
                        )
                    }
                }
            }
        }
    }

    // Template picker dialog
    if (showTemplatePicker) {
        TemplatePickerDialog(
            onSelect = { splitDay ->
                showTemplatePicker = false
                viewModel.loadTemplate(splitDay)
            },
            onDismiss = { showTemplatePicker = false }
        )
    }

    // Exercise picker bottom sheet
    if (showExercisePicker) {
        ExercisePickerSheet(
            sheetState = exercisePickerSheetState,
            onAddExercise = { libraryExercise ->
                viewModel.addExercise(libraryExercise)
            },
            onCreateExercise = { name, category ->
                viewModel.createAndAddExercise(name, category)
            },
            onDismiss = { showExercisePicker = false }
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
                SplitCalendar(days = calendarDays)
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
                    "Pick a template to get started, or add exercises one at a time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))

                // Start from Template — green filled button (matches mockup)
                Button(
                    onClick = onStartFromTemplate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) {
                    Text("Start from Template", color = Color.White)
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
    onToggleComplete: () -> Unit,
    onAddSet: (Long) -> Unit,
    onAddExercise: () -> Unit,
    onRemoveExercise: (Long) -> Unit
) {
    LazyColumn(
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
                SplitCalendar(days = calendarDays)
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
                    Text(
                        text = state.templateName ?: "Freestyle",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Volume summary — WORK-09 (only shown when volume > 0)
                    if (state.totalVolume > 0) {
                        Text(
                            text = "Vol: ${formatVolume(state.totalVolume)} kg",
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

        // Exercise cards
        items(state.exercises, key = { it.exerciseLog.id }) { dayExercise ->
            ExerciseCard(
                dayExercise = dayExercise,
                onAddSet = { onAddSet(dayExercise.exerciseLog.id) },
                onRemoveExercise = { onRemoveExercise(dayExercise.exerciseLog.id) }
            )
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
    onAddSet: () -> Unit,
    onRemoveExercise: () -> Unit
) {
    val exerciseLog = dayExercise.exerciseLog

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: name + category badge + target sets×reps (matches mockup)
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
                        overflow = TextOverflow.Ellipsis
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
            }

            // Logged sets (with PR badge) — shown after divider
            if (exerciseLog.sets.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                exerciseLog.sets.forEach { set ->
                    SetRow(set = set, category = dayExercise.category)
                }
            }

            Spacer(Modifier.height(10.dp))

            // "+ Add Set" button (green text, centered — matches mockup)
            TextButton(
                onClick = onAddSet,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = ForestGreen)
            ) {
                Text(
                    "+ Add Set",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            // Previous session reference ("Last: 80kgx5 · 80kgx5 · ...") — matches mockup
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
private fun SetRow(set: SetLog, category: ExerciseCategory) {
    val label = when (category) {
        ExerciseCategory.TIMED ->
            "Set ${set.setNumber}    ${set.holdSecs}s hold"
        ExerciseCategory.CARDIO ->
            "Set ${set.setNumber}    ${set.durationMin ?: 0}min" +
                (set.distanceKm?.let { " · ${it}km" } ?: "")
        ExerciseCategory.STRETCH ->
            "Set ${set.setNumber}    ${set.durationMin ?: 0}min"
        ExerciseCategory.BODYWEIGHT ->
            "Set ${set.setNumber}    ${set.reps} reps"
        ExerciseCategory.WEIGHTED ->
            "Set ${set.setNumber}    ${formatWeight(set.weight)}${set.unit} × ${set.reps}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
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
// Template picker dialog — lists all 4 split days
// ---------------------------------------------------------------------------

@Composable
private fun TemplatePickerDialog(
    onSelect: (SplitDay) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start from Template") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SplitDay.entries.forEach { splitDay ->
                    TextButton(
                        onClick = { onSelect(splitDay) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = splitDay.displayName,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ---------------------------------------------------------------------------
// Helper functions
// ---------------------------------------------------------------------------

private fun formatWorkoutDate(date: LocalDate): String {
    val today = LocalDate.now()
    return when {
        date == today -> "Today, ${date.format(DateTimeFormatter.ofPattern("MMM d"))}"
        date == today.minusDays(1) -> "Yesterday, ${date.format(DateTimeFormatter.ofPattern("MMM d"))}"
        date == today.plusDays(1) -> "Tomorrow, ${date.format(DateTimeFormatter.ofPattern("MMM d"))}"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
}

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

private fun formatWeight(weight: Double): String =
    if (weight == weight.toLong().toDouble()) weight.toLong().toString()
    else "%.1f".format(weight)
