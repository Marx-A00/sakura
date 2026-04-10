package com.sakura.features.foodlog

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.data.food.FoodEntry
import com.sakura.data.food.MealTemplate
import com.sakura.ui.theme.CherryBlossomPink
import com.sakura.ui.theme.DeepRose
import com.sakura.ui.theme.ForestGreen
import com.sakura.ui.theme.WarmBrown
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodLogScreen(
    viewModel: FoodLogViewModel,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val expandedMeals by viewModel.expandedMeals.collectAsStateWithLifecycle()
    val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
    val lastAddedEntry by viewModel.lastAddedEntry.collectAsStateWithLifecycle()
    val recentItems by viewModel.recentItems.collectAsStateWithLifecycle()
    val libraryItems by viewModel.libraryItems.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Bottom sheet states
    var showEntrySheet by rememberSaveable { mutableStateOf(false) }
    var showLibrarySheet by rememberSaveable { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<FoodEntry?>(null) }
    var saveToLibraryToggle by rememberSaveable { mutableStateOf(false) }

    val entrySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val librarySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Date picker
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    // Template dialogs
    var templateDialogMeal by remember { mutableStateOf<String?>(null) }
    var applyTemplateDialogMeal by remember { mutableStateOf<String?>(null) }
    var templateNameInput by remember { mutableStateOf("") }

    val isToday = selectedDate == LocalDate.now()
    val isPast = selectedDate.isBefore(LocalDate.now())
    val canEdit = isToday || isEditMode

    // Undo snackbar
    LaunchedEffect(lastAddedEntry) {
        if (lastAddedEntry != null) {
            val result = snackbarHostState.showSnackbar(
                message = "Entry added",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoLastAdd()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { viewModel.navigatePrevDay() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous day")
                        }
                        Text(
                            text = formatDate(selectedDate),
                            modifier = Modifier.clickable { showDatePicker = true },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        IconButton(onClick = { viewModel.navigateNextDay() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next day")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(
                    onClick = {
                        editingEntry = null
                        saveToLibraryToggle = false
                        showEntrySheet = true
                    },
                    containerColor = CherryBlossomPink
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add food entry", tint = Color.White)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is FoodLogUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = CherryBlossomPink
                    )
                }

                is FoodLogUiState.Error.FolderUnavailable -> {
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

                is FoodLogUiState.Error.Generic -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Error loading food log",
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

                is FoodLogUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            bottom = 88.dp
                        )
                    ) {
                        // Macro progress section
                        item {
                            MacroProgressSection(state = state)
                        }

                        // Past day controls
                        if (isPast) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { viewModel.toggleEditMode() }) {
                                        Text(
                                            text = if (isEditMode) "Done Editing" else "Edit Day",
                                            color = CherryBlossomPink
                                        )
                                    }
                                }
                            }
                        }

                        // Meal sections
                        val allMealLabels = listOf("Breakfast", "Lunch", "Dinner", "Snacks")
                        allMealLabels.forEach { mealLabel ->
                            val meal = state.meals.find { it.label == mealLabel }
                            val isExpanded = expandedMeals.contains(mealLabel)
                            val mealCalories = meal?.totalCalories ?: 0

                            // Sticky meal header
                            stickyHeader(key = "header_$mealLabel") {
                                MealSectionHeader(
                                    label = mealLabel,
                                    totalCalories = mealCalories,
                                    isExpanded = isExpanded,
                                    templates = templates,
                                    canEdit = canEdit,
                                    onToggle = { viewModel.toggleMealExpanded(mealLabel) },
                                    onSaveTemplate = { templateDialogMeal = mealLabel },
                                    onApplyTemplate = { applyTemplateDialogMeal = mealLabel }
                                )
                            }

                            // Entries
                            if (isExpanded) {
                                if (meal == null || meal.entries.isEmpty()) {
                                    item(key = "empty_$mealLabel") {
                                        Text(
                                            text = "No food logged",
                                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp
                                        )
                                    }
                                } else {
                                    items(
                                        items = meal.entries,
                                        key = { it.id }
                                    ) { entry ->
                                        FoodEntryRow(
                                            entry = entry,
                                            canEdit = canEdit,
                                            onEdit = {
                                                editingEntry = entry
                                                saveToLibraryToggle = false
                                                showEntrySheet = true
                                            },
                                            onDelete = { viewModel.deleteEntry(mealLabel, entry.id) },
                                            onSaveToLibrary = { viewModel.saveToLibrary(entry) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Date picker dialog
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

    // Entry bottom sheet
    if (showEntrySheet) {
        FoodEntryBottomSheet(
            sheetState = entrySheetState,
            editingEntry = editingEntry,
            viewModel = viewModel,
            onSave = { entry ->
                val currentEditingEntry = editingEntry
                if (currentEditingEntry != null) {
                    // Find which meal this entry belongs to
                    val successState = uiState as? FoodLogUiState.Success
                    val mealLabel = successState?.meals
                        ?.find { meal -> meal.entries.any { it.id == currentEditingEntry.id } }
                        ?.label ?: viewModel.draftMealLabel.value
                    viewModel.updateEntry(mealLabel, currentEditingEntry.id, entry)
                } else {
                    viewModel.addEntry(viewModel.draftMealLabel.value, entry)
                    if (saveToLibraryToggle) {
                        viewModel.saveToLibrary(entry)
                    }
                }
            },
            onDismiss = {
                showEntrySheet = false
                editingEntry = null
            },
            onOpenLibrary = {
                showEntrySheet = false
                showLibrarySheet = true
            },
            saveToLibrary = saveToLibraryToggle,
            onSaveToLibraryChanged = { saveToLibraryToggle = it }
        )
    }

    // Library bottom sheet
    if (showLibrarySheet) {
        FoodLibraryBottomSheet(
            sheetState = librarySheetState,
            recentItems = recentItems,
            libraryItems = libraryItems,
            onSelect = { item ->
                viewModel.updateDraftName(item.name)
                viewModel.updateDraftProtein(item.protein.toString())
                viewModel.updateDraftCarbs(item.carbs.toString())
                viewModel.updateDraftFat(item.fat.toString())
                viewModel.updateDraftCalories(item.calories.toString())
                viewModel.updateDraftServingSize(item.servingSize ?: "")
                viewModel.updateDraftServingUnit(item.servingUnit ?: "")
                showLibrarySheet = false
                showEntrySheet = true
            },
            onDismiss = {
                showLibrarySheet = false
                showEntrySheet = true
            }
        )
    }

    // Save template dialog
    templateDialogMeal?.let { mealLabel ->
        AlertDialog(
            onDismissRequest = { templateDialogMeal = null; templateNameInput = "" },
            title = { Text("Save as Template") },
            text = {
                OutlinedTextField(
                    value = templateNameInput,
                    onValueChange = { templateNameInput = it },
                    label = { Text("Template name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (templateNameInput.isNotBlank()) {
                            viewModel.saveMealAsTemplate(mealLabel, templateNameInput)
                        }
                        templateDialogMeal = null
                        templateNameInput = ""
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { templateDialogMeal = null; templateNameInput = "" }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Apply template dialog
    applyTemplateDialogMeal?.let { mealLabel ->
        AlertDialog(
            onDismissRequest = { applyTemplateDialogMeal = null },
            title = { Text("Apply Template") },
            text = {
                if (templates.isEmpty()) {
                    Text("No templates saved yet.")
                } else {
                    Column {
                        templates.forEach { template ->
                            TextButton(
                                onClick = {
                                    viewModel.applyTemplate(mealLabel, template)
                                    applyTemplateDialogMeal = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(template.name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { applyTemplateDialogMeal = null }) { Text("Cancel") }
            }
        )
    }
}

// -------------------------------------------------------------------------
// Macro Progress Section
// -------------------------------------------------------------------------

@Composable
private fun MacroProgressSection(state: FoodLogUiState.Success) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MacroBar(
                label = "Calories",
                logged = state.totalCalories,
                target = state.targets.calories,
                color = CherryBlossomPink,
                unit = "kcal"
            )
            MacroBar(
                label = "Protein",
                logged = state.totalProtein,
                target = state.targets.protein,
                color = ForestGreen,
                unit = "g"
            )
            MacroBar(
                label = "Carbs",
                logged = state.totalCarbs,
                target = state.targets.carbs,
                color = WarmBrown,
                unit = "g"
            )
            MacroBar(
                label = "Fat",
                logged = state.totalFat,
                target = state.targets.fat,
                color = DeepRose,
                unit = "g"
            )
        }
    }
}

@Composable
private fun MacroBar(
    label: String,
    logged: Int,
    target: Int,
    color: Color,
    unit: String
) {
    val progress = if (target > 0) (logged.toFloat() / target).coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("$logged / $target $unit", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}

// -------------------------------------------------------------------------
// Meal Section Header
// -------------------------------------------------------------------------

@Composable
private fun MealSectionHeader(
    label: String,
    totalCalories: Int,
    isExpanded: Boolean,
    templates: List<MealTemplate>,
    canEdit: Boolean,
    onToggle: () -> Unit,
    onSaveTemplate: () -> Unit,
    onApplyTemplate: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                if (totalCalories > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$totalCalories kcal",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canEdit) {
                    var showTemplateMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { showTemplateMenu = true }) {
                            Text("Template", fontSize = 11.sp, color = CherryBlossomPink)
                        }
                        DropdownMenu(
                            expanded = showTemplateMenu,
                            onDismissRequest = { showTemplateMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save as template") },
                                onClick = { showTemplateMenu = false; onSaveTemplate() }
                            )
                            DropdownMenuItem(
                                text = { Text("Apply template") },
                                onClick = { showTemplateMenu = false; onApplyTemplate() }
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// Food Entry Row
// -------------------------------------------------------------------------

@Composable
private fun FoodEntryRow(
    entry: FoodEntry,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSaveToLibrary: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name.ifBlank { "Unnamed" },
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Text(
                text = buildMacroSummary(entry),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (canEdit) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Entry options",
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { showMenu = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { showMenu = false; onDelete() }
                    )
                    DropdownMenuItem(
                        text = { Text("Save to Library") },
                        onClick = { showMenu = false; onSaveToLibrary() }
                    )
                }
            }
        }
    }
}

private fun buildMacroSummary(entry: FoodEntry): String {
    val parts = mutableListOf<String>()
    if (entry.calories > 0) parts.add("${entry.calories} kcal")
    if (entry.protein > 0) parts.add("P: ${entry.protein}g")
    if (entry.carbs > 0) parts.add("C: ${entry.carbs}g")
    if (entry.fat > 0) parts.add("F: ${entry.fat}g")
    return parts.joinToString("  ")
}

private fun formatDate(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
}
