package com.sakura.features.templatecreator

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.adamglin.phosphoricons.regular.DotsSixVertical
import com.adamglin.phosphoricons.regular.X
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.data.workout.ExerciseCategory
import com.sakura.data.workout.ExerciseLibrary
import com.sakura.data.workout.TemplateExercise
import com.sakura.data.workout.toTemplateExercise
import com.sakura.features.workoutlog.ExercisePickerSheet
import com.sakura.ui.theme.SakuraTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutTemplateCreatorScreen(
    viewModel: WorkoutTemplateCreatorViewModel,
    onNavigateBack: () -> Unit
) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val canSave by viewModel.canSave.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var showExercisePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Local mutable list for drag-to-reorder
    val localExercises = remember { mutableStateListOf<TemplateExercise>() }
    androidx.compose.runtime.LaunchedEffect(exercises) {
        if (localExercises.toList() != exercises) {
            localExercises.clear()
            localExercises.addAll(exercises)
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = from.index - 1  // offset by 1 for the name field item
        val toIdx = to.index - 1
        if (fromIdx in localExercises.indices && toIdx in localExercises.indices) {
            localExercises.apply { add(toIdx, removeAt(fromIdx)) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (viewModel.isEditing) "Edit Workout" else "Create Workout")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(PhosphorIcons.Regular.ArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.save { success ->
                                if (success) onNavigateBack()
                            }
                        },
                        enabled = canSave
                    ) {
                        Text(
                            "Save",
                            color = if (canSave) SakuraTheme.colors.accent
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SakuraTheme.colors.accent)
            }
            return@Scaffold
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
        ) {
            // Name field
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { viewModel.updateName(it) },
                    label = { Text("Workout name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Exercise cards with drag-to-reorder
            itemsIndexed(localExercises, key = { idx, ex -> "${ex.name}_$idx" }) { index, exercise ->
                ReorderableItem(reorderableState, key = "${exercise.name}_$index") { isDragging ->
                    val elevation by animateDpAsState(
                        if (isDragging) 8.dp else 1.dp,
                        label = "dragElevation"
                    )
                    TemplateExerciseCard(
                        exercise = exercise,
                        index = index,
                        modifier = Modifier.longPressDraggableHandle(
                            onDragStarted = {
                                val vibrator = context.getSystemService(Vibrator::class.java)
                                vibrator?.vibrate(
                                    VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE)
                                )
                            },
                            onDragStopped = {
                                viewModel.setExerciseOrder(localExercises.toList())
                            }
                        ),
                        elevation = elevation,
                        onUpdateTargets = { sets, reps, holdSecs ->
                            viewModel.updateTargets(index, sets, reps, holdSecs)
                        },
                        onRemove = { viewModel.removeExercise(index) }
                    )
                }
            }

            // + Add Exercise button
            item {
                OutlinedButton(
                    onClick = { showExercisePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Add Exercise", color = SakuraTheme.colors.accent)
                }
            }
        }
    }

    // Exercise picker bottom sheet
    if (showExercisePicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ExercisePickerSheet(
            sheetState = sheetState,
            onAddExercise = { libraryExercise ->
                viewModel.addExercise(libraryExercise)
            },
            onCreateExercise = { name, category ->
                val newExercise = com.sakura.data.workout.LibraryExercise(
                    name = name,
                    category = category,
                    muscleGroups = emptyList(),
                    isBuiltIn = false
                )
                ExerciseLibrary.addUserExercise(newExercise)
                viewModel.addExercise(newExercise)
            },
            onDismiss = { showExercisePicker = false }
        )
    }
}

@Composable
private fun TemplateExerciseCard(
    exercise: TemplateExercise,
    index: Int,
    modifier: Modifier = Modifier,
    elevation: androidx.compose.ui.unit.Dp = 1.dp,
    onUpdateTargets: (sets: Int, reps: Int, holdSecs: Int) -> Unit,
    onRemove: () -> Unit
) {
    var setsText by remember(exercise) { mutableStateOf(if (exercise.targetSets > 0) exercise.targetSets.toString() else "") }
    var repsText by remember(exercise) { mutableStateOf(if (exercise.targetReps > 0) exercise.targetReps.toString() else "") }
    var holdText by remember(exercise) { mutableStateOf(if (exercise.targetHoldSecs > 0) exercise.targetHoldSecs.toString() else "") }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: drag handle + name + category + remove
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    PhosphorIcons.Regular.DotsSixVertical,
                    contentDescription = "Drag to reorder",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exercise.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Category badge
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = exercise.category.displayName.lowercase(),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (exercise.muscleGroups.isNotEmpty()) {
                            Text(
                                text = exercise.muscleGroups.joinToString(", "),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        PhosphorIcons.Regular.X,
                        contentDescription = "Remove",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Target inputs row — adapts by category
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sets input (all categories)
                CompactNumberField(
                    value = setsText,
                    onValueChange = { newVal ->
                        setsText = newVal
                        onUpdateTargets(
                            newVal.toIntOrNull() ?: 0,
                            repsText.toIntOrNull() ?: 0,
                            holdText.toIntOrNull() ?: 0
                        )
                    },
                    label = "Sets",
                    modifier = Modifier.weight(1f)
                )

                Text("×", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                when (exercise.category) {
                    ExerciseCategory.TIMED -> {
                        CompactNumberField(
                            value = holdText,
                            onValueChange = { newVal ->
                                holdText = newVal
                                onUpdateTargets(
                                    setsText.toIntOrNull() ?: 0,
                                    repsText.toIntOrNull() ?: 0,
                                    newVal.toIntOrNull() ?: 0
                                )
                            },
                            label = "Hold (s)",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    else -> {
                        CompactNumberField(
                            value = repsText,
                            onValueChange = { newVal ->
                                repsText = newVal
                                onUpdateTargets(
                                    setsText.toIntOrNull() ?: 0,
                                    newVal.toIntOrNull() ?: 0,
                                    holdText.toIntOrNull() ?: 0
                                )
                            },
                            label = "Reps",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newVal ->
            // Only allow digits
            if (newVal.all { it.isDigit() } || newVal.isEmpty()) {
                onValueChange(newVal)
            }
        },
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(8.dp)
    )
}
