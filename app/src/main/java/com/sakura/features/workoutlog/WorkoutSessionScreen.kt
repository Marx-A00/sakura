package com.sakura.features.workoutlog

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.data.workout.ExerciseType
import com.sakura.data.workout.SetLog
import com.sakura.ui.theme.CherryBlossomPink
import com.sakura.ui.theme.DeepRose

/**
 * Active workout session screen.
 *
 * Layout:
 * - TopAppBar with split day name and "End Workout" confirm button
 * - LazyColumn of exercise sections with per-set data, previous session reference
 * - Bottom rest timer bar (Scaffold bottomBar) with countdown and Skip button
 * - PR detection AlertDialog
 * - Exercise alternative picker via ExercisePickerSheet
 * - Set input via SetInputSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSessionScreen(
    viewModel: WorkoutLogViewModel,
    onSessionFinished: () -> Unit
) {
    val sessionUiState by viewModel.sessionUiState.collectAsStateWithLifecycle()
    val restSecondsRemaining by viewModel.restSecondsRemaining.collectAsStateWithLifecycle()
    val prDetected by viewModel.prDetected.collectAsStateWithLifecycle()

    val activeState = sessionUiState as? SessionUiState.Active

    // Confirm end-workout dialog
    var showEndConfirm by rememberSaveable { mutableStateOf(false) }

    // Track which exercise sheet is open (index into exercises list, or -1)
    var setSheetExerciseIndex by rememberSaveable { mutableIntStateOf(-1) }
    var pickerSheetExerciseIndex by rememberSaveable { mutableIntStateOf(-1) }

    val setSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(activeState?.splitDay?.displayName ?: "Workout Session")
                },
                actions = {
                    TextButton(
                        onClick = { showEndConfirm = true }
                    ) {
                        Text("End Workout", color = DeepRose)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Rest timer bar — only visible when timer is active
            if (restSecondsRemaining > 0) {
                RestTimerBar(
                    secondsRemaining = restSecondsRemaining,
                    onSkip = { viewModel.cancelRestTimer() }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (activeState == null) {
                // No active session — shouldn't happen in normal flow
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "No active session",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(activeState.exercises) { index, exercise ->
                        ExerciseSection(
                            exercise = exercise,
                            exerciseIndex = index,
                            onAddSet = { setSheetExerciseIndex = index },
                            onSwapExercise = {
                                if (exercise.definition.alternatives.isNotEmpty()) {
                                    pickerSheetExerciseIndex = index
                                }
                            }
                        )
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    // --- End workout confirmation dialog ---
    if (showEndConfirm) {
        AlertDialog(
            onDismissRequest = { showEndConfirm = false },
            title = { Text("End Workout") },
            text = { Text("Finish the session? This will save all logged sets to your workout log.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEndConfirm = false
                        viewModel.finishSession()
                        onSessionFinished()
                    }
                ) {
                    Text("Finish", color = CherryBlossomPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- PR detection dialog ---
    prDetected?.let { pr ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPrNotification() },
            title = { Text("New Personal Record!") },
            text = { Text("You just set a new ${pr.prType} PR on ${pr.exerciseName}!") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPrNotification() }) {
                    Text("OK", color = CherryBlossomPink)
                }
            }
        )
    }

    // --- Set input sheet ---
    if (setSheetExerciseIndex >= 0) {
        val exercises = activeState?.exercises
        val exIndex = setSheetExerciseIndex
        if (exercises != null && exIndex < exercises.size) {
            val exercise = exercises[exIndex]
            val setNumber = exercise.loggedSets.size + 1
            val lastLoggedSet = exercise.loggedSets.lastOrNull()
            val prefill = lastLoggedSet
                ?: exercise.previousSets.firstOrNull()

            SetInputSheet(
                sheetState = setSheetState,
                definition = exercise.definition,
                setNumber = setNumber,
                prefillSet = prefill,
                onLogSet = { set ->
                    viewModel.logSet(exIndex, set)
                },
                onDismiss = {
                    setSheetExerciseIndex = -1
                }
            )
        }
    }

    // --- Exercise picker sheet ---
    if (pickerSheetExerciseIndex >= 0) {
        val exercises = activeState?.exercises
        val exIndex = pickerSheetExerciseIndex
        if (exercises != null && exIndex < exercises.size) {
            val exercise = exercises[exIndex]
            ExercisePickerSheet(
                sheetState = pickerSheetState,
                definition = exercise.definition,
                selectedAlternative = exercise.selectedAlternative,
                onSelect = { altName ->
                    if (altName == null) {
                        // Select primary — clear alternative
                        viewModel.selectAlternative(exIndex, exercise.definition.name)
                    } else {
                        viewModel.selectAlternative(exIndex, altName)
                    }
                },
                onDismiss = {
                    pickerSheetExerciseIndex = -1
                }
            )
        }
    }
}

@Composable
private fun ExerciseSection(
    exercise: SessionExercise,
    exerciseIndex: Int,
    onAddSet: () -> Unit,
    onSwapExercise: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Exercise header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exercise.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    val targetText = buildTargetText(exercise)
                    if (targetText.isNotBlank()) {
                        Text(
                            text = targetText,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Exercise type badge
                    ExerciseTypeBadge(exerciseType = exercise.definition.exerciseType)

                    // Swap button if alternatives exist
                    if (exercise.definition.alternatives.isNotEmpty()) {
                        TextButton(
                            onClick = onSwapExercise,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 8.dp, vertical = 4.dp
                            )
                        ) {
                            Text(
                                text = "Swap",
                                fontSize = 12.sp,
                                color = CherryBlossomPink
                            )
                        }
                    }
                }
            }

            // Previous session reference row
            if (exercise.previousSets.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Last: ${formatPreviousSets(exercise)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // Logged sets
            if (exercise.loggedSets.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                exercise.loggedSets.forEach { set ->
                    SetRow(set = set, exerciseType = exercise.definition.exerciseType)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Add set button
            Button(
                onClick = onAddSet,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CherryBlossomPink)
            ) {
                Text("+ Add Set")
            }
        }
    }
}

@Composable
private fun SetRow(set: SetLog, exerciseType: ExerciseType) {
    val label = when {
        exerciseType == ExerciseType.TIMED -> "Set ${set.setNumber}: ${set.holdSecs} sec hold"
        set.unit == "bw" -> "Set ${set.setNumber}: ${set.reps} reps (bw)"
        else -> "Set ${set.setNumber}: ${formatWeight(set.weight)}${set.unit} × ${set.reps} reps"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (set.isPr) {
            Text(
                text = "PR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CherryBlossomPink
            )
        }
        set.rpe?.let { rpe ->
            Text(
                text = "RPE $rpe",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExerciseTypeBadge(exerciseType: ExerciseType) {
    val label = when (exerciseType) {
        ExerciseType.BARBELL -> "BB"
        ExerciseType.DUMBBELL -> "DB"
        ExerciseType.MACHINE -> "MC"
        ExerciseType.CALISTHENICS -> "BW"
        ExerciseType.TIMED -> "TM"
    }
    Text(
        text = label,
        fontSize = 10.sp,
        color = CherryBlossomPink,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(end = 4.dp)
    )
}

@Composable
private fun RestTimerBar(
    secondsRemaining: Int,
    onSkip: () -> Unit
) {
    // Animate background color: pink when fresh, neutral as it approaches 0
    val maxSecs = 90f
    val fraction = (secondsRemaining / maxSecs).coerceIn(0f, 1f)
    val barColor by animateColorAsState(
        targetValue = CherryBlossomPink.copy(alpha = 0.1f + fraction * 0.3f),
        animationSpec = tween(500),
        label = "rest_timer_color"
    )

    val minutes = secondsRemaining / 60
    val seconds = secondsRemaining % 60
    val formatted = if (minutes > 0) "%d:%02d".format(minutes, seconds) else "%ds".format(seconds)

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = barColor
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rest: $formatted",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = DeepRose
                )
                TextButton(onClick = onSkip) {
                    Text("Skip", color = DeepRose)
                }
            }
        }
    }
}

private fun buildTargetText(exercise: SessionExercise): String {
    val def = exercise.definition
    val setsStr = "${def.targetSets} sets"
    val repsStr = when {
        def.exerciseType == ExerciseType.TIMED -> "${def.targetHoldSecs}s hold"
        def.targetReps == -1 -> "max reps"
        else -> "${def.targetReps} reps"
    }
    val sideStr = if (def.perSide) "/side" else ""
    return "$setsStr × $repsStr$sideStr"
}

private fun formatPreviousSets(exercise: SessionExercise): String {
    return exercise.previousSets.take(3).joinToString(", ") { set ->
        when {
            exercise.definition.exerciseType == ExerciseType.TIMED -> "${set.holdSecs}s"
            set.unit == "bw" -> "${set.reps}reps"
            else -> "${formatWeight(set.weight)}${set.unit}×${set.reps}"
        }
    }
}

private fun formatWeight(weight: Double): String =
    if (weight == weight.toLong().toDouble()) weight.toLong().toString()
    else "%.1f".format(weight)
