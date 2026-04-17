package com.sakura.features.exerciselibrary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.data.workout.ExerciseCategory
import com.sakura.data.workout.ExerciseLibrary
import com.sakura.data.workout.LibraryExercise
import com.sakura.data.workout.UserWorkoutTemplate
import com.sakura.data.workout.WorkoutSchedule
import com.sakura.ui.theme.SakuraTheme
import kotlinx.coroutines.launch
import java.time.DayOfWeek

private val LIBRARY_TABS = listOf("Exercises", "Workouts")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(
    viewModel: ExerciseLibraryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTemplateCreator: (templateId: String?) -> Unit = {}
) {
    val exercises by viewModel.filteredExercises.collectAsStateWithLifecycle()
    val allExercises by viewModel.allExercises.collectAsStateWithLifecycle()
    val templates by viewModel.filteredTemplates.collectAsStateWithLifecycle()
    val allTemplates by viewModel.allTemplates.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0) { LIBRARY_TABS.size }

    var showCreateExerciseDialog by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<LibraryExercise?>(null) }
    var deleteConfirmExercise by remember { mutableStateOf<LibraryExercise?>(null) }
    var deleteConfirmTemplate by remember { mutableStateOf<UserWorkoutTemplate?>(null) }
    var showScheduleDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exercise Library") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showScheduleDialog = true }) {
                        Icon(
                            Icons.Filled.CalendarMonth,
                            contentDescription = "Weekly schedule"
                        )
                    }
                    IconButton(onClick = {
                        if (pagerState.currentPage == 0) showCreateExerciseDialog = true
                        else onNavigateToTemplateCreator(null)
                    }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = if (pagerState.currentPage == 0) "Add exercise" else "Create workout"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearch(it) },
                placeholder = {
                    Text(
                        if (pagerState.currentPage == 0) "Search exercises..."
                        else "Search workouts..."
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearch("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Tabs
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                LIBRARY_TABS.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) }
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SakuraTheme.colors.brand)
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> ExercisesTab(
                            exercises = exercises,
                            searchQuery = searchQuery,
                            selectedCategory = selectedCategory,
                            onCategorySelected = { viewModel.updateCategory(it) },
                            onEdit = { editingExercise = it },
                            onDelete = { deleteConfirmExercise = it }
                        )
                        1 -> WorkoutsTab(
                            templates = templates,
                            searchQuery = searchQuery,
                            onEdit = { onNavigateToTemplateCreator(it.id) },
                            onDelete = { deleteConfirmTemplate = it }
                        )
                    }
                }
            }
        }
    }

    // Create exercise dialog
    if (showCreateExerciseDialog) {
        ExerciseDialog(
            existing = null,
            onSave = { exercise ->
                viewModel.saveExercise(exercise)
                showCreateExerciseDialog = false
            },
            onDismiss = { showCreateExerciseDialog = false }
        )
    }

    // Edit exercise dialog
    editingExercise?.let { exercise ->
        ExerciseDialog(
            existing = exercise,
            onSave = { updated ->
                viewModel.updateExercise(exercise.name, updated)
                editingExercise = null
            },
            onDismiss = { editingExercise = null }
        )
    }

    // Delete exercise confirmation
    deleteConfirmExercise?.let { exercise ->
        AlertDialog(
            onDismissRequest = { deleteConfirmExercise = null },
            title = { Text("Delete ${exercise.name}?") },
            text = { Text("This will remove it from your exercise library.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExercise(exercise.name)
                    deleteConfirmExercise = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmExercise = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete template confirmation
    deleteConfirmTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTemplate = null },
            title = { Text("Delete ${template.name}?") },
            text = { Text("This will remove the saved workout.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTemplate(template.id)
                    deleteConfirmTemplate = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTemplate = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Weekly schedule dialog
    if (showScheduleDialog) {
        WeeklyScheduleDialog(
            schedule = schedule,
            templates = allTemplates,
            onAssign = { day, templateId -> viewModel.assignTemplateToDay(day, templateId) },
            onDismiss = { showScheduleDialog = false }
        )
    }
}

// =============================================================================
// Exercises tab
// =============================================================================

@Composable
private fun ExercisesTab(
    exercises: List<LibraryExercise>,
    searchQuery: String,
    selectedCategory: ExerciseCategory?,
    onCategorySelected: (ExerciseCategory?) -> Unit,
    onEdit: (LibraryExercise) -> Unit,
    onDelete: (LibraryExercise) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Category filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategorySelected(null) },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
            ExerciseCategory.entries.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                    label = { Text(category.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        if (exercises.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (searchQuery.isNotEmpty() || selectedCategory != null)
                            "No matching exercises" else "No exercises",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty() || selectedCategory != null)
                            "Try a different search or filter"
                        else "Add exercises using the + button",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(exercises, key = { it.name }) { exercise ->
                    ExerciseRow(
                        exercise = exercise,
                        onEdit = { onEdit(exercise) },
                        onDelete = { onDelete(exercise) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

// =============================================================================
// Workouts tab
// =============================================================================

@Composable
private fun WorkoutsTab(
    templates: List<UserWorkoutTemplate>,
    searchQuery: String,
    onEdit: (UserWorkoutTemplate) -> Unit,
    onDelete: (UserWorkoutTemplate) -> Unit
) {
    if (templates.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (searchQuery.isNotEmpty()) "No matching workouts" else "No saved workouts",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (searchQuery.isNotEmpty()) "Try a different search"
                    else "Create a workout using the + button",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(templates, key = { it.id }) { template ->
                WorkoutTemplateRow(
                    template = template,
                    onEdit = { onEdit(template) },
                    onDelete = { onDelete(template) }
                )
                HorizontalDivider()
            }
        }
    }
}

// =============================================================================
// Row composables
// =============================================================================

@Composable
private fun ExerciseRow(
    exercise: LibraryExercise,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (!exercise.isBuiltIn) it.clickable(onClick = onEdit) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = exercise.name,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Text(
                text = buildString {
                    append(exercise.category.displayName)
                    if (exercise.muscleGroups.isNotEmpty()) {
                        append("  ·  ")
                        append(exercise.muscleGroups.joinToString(", "))
                    }
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!exercise.isBuiltIn) {
            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutTemplateRow(
    template: UserWorkoutTemplate,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name.ifBlank { "Unnamed workout" },
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    text = "${template.exercises.size} exercises",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit name",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                    else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 16.dp, bottom = 8.dp)
            ) {
                template.exercises.forEach { exercise ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = exercise.name,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = exercise.category.displayName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// Dialogs
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDialog(
    existing: LibraryExercise?,
    onSave: (LibraryExercise) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing = existing != null

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var selectedCategory by remember { mutableStateOf(existing?.category ?: ExerciseCategory.WEIGHTED) }
    var muscleGroups by remember { mutableStateOf(existing?.muscleGroups?.joinToString(", ") ?: "") }
    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Exercise" else "Add Exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        ExerciseCategory.entries.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.displayName) },
                                onClick = {
                                    selectedCategory = category
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = muscleGroups,
                    onValueChange = { muscleGroups = it },
                    label = { Text("Muscle groups") },
                    placeholder = { Text("e.g. Chest, Triceps", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val exercise = LibraryExercise(
                        name = name.trim(),
                        category = selectedCategory,
                        muscleGroups = if (muscleGroups.isBlank()) emptyList()
                            else muscleGroups.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        isBuiltIn = false
                    )
                    onSave(exercise)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save", color = if (name.isNotBlank()) SakuraTheme.colors.brand else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// =============================================================================
// Weekly Schedule Dialog
// =============================================================================

private val DAY_LABELS = listOf(
    DayOfWeek.MONDAY to "Mon",
    DayOfWeek.TUESDAY to "Tue",
    DayOfWeek.WEDNESDAY to "Wed",
    DayOfWeek.THURSDAY to "Thu",
    DayOfWeek.FRIDAY to "Fri",
    DayOfWeek.SATURDAY to "Sat",
    DayOfWeek.SUNDAY to "Sun"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeeklyScheduleDialog(
    schedule: WorkoutSchedule,
    templates: List<UserWorkoutTemplate>,
    onAssign: (DayOfWeek, String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weekly Schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DAY_LABELS.forEach { (day, label) ->
                    ScheduleDayRow(
                        dayLabel = label,
                        assignedTemplateId = schedule.templateIdFor(day),
                        templates = templates,
                        onAssign = { templateId -> onAssign(day, templateId) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDayRow(
    dayLabel: String,
    assignedTemplateId: String?,
    templates: List<UserWorkoutTemplate>,
    onAssign: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val assignedTemplate = templates.find { it.id == assignedTemplateId }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = dayLabel,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.width(40.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = assignedTemplate?.name ?: "Rest",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Rest", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {
                        onAssign(null)
                        expanded = false
                    }
                )
                templates.forEach { template ->
                    DropdownMenuItem(
                        text = { Text(template.name) },
                        onClick = {
                            onAssign(template.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

