package com.sakura.features.foodlibrary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.data.food.FoodLibraryItem
import com.sakura.data.food.MealTemplate
import com.sakura.ui.theme.CherryBlossomPink
import kotlinx.coroutines.launch

private val LIBRARY_TABS = listOf("Foods", "Meals")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodLibraryScreen(
    viewModel: FoodLibraryViewModel,
    onNavigateBack: () -> Unit
) {
    val items by viewModel.filteredItems.collectAsStateWithLifecycle()
    val allItems by viewModel.allItems.collectAsStateWithLifecycle()
    val templates by viewModel.filteredTemplates.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0) { LIBRARY_TABS.size }

    var showAddFoodDialog by remember { mutableStateOf(false) }
    var showCreateMealDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<FoodLibraryItem?>(null) }
    var deleteConfirmItem by remember { mutableStateOf<FoodLibraryItem?>(null) }
    var deleteConfirmTemplate by remember { mutableStateOf<MealTemplate?>(null) }
    var editingTemplate by remember { mutableStateOf<MealTemplate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Food Library") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (pagerState.currentPage == 0) showAddFoodDialog = true
                        else showCreateMealDialog = true
                    }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = if (pagerState.currentPage == 0) "Add food" else "Create meal"
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
                        if (pagerState.currentPage == 0) "Search foods..."
                        else "Search meals..."
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
                    CircularProgressIndicator(color = CherryBlossomPink)
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> FoodsTab(
                            items = items,
                            searchQuery = searchQuery,
                            onEdit = { editingItem = it },
                            onDelete = { deleteConfirmItem = it }
                        )
                        1 -> MealsTab(
                            templates = templates,
                            searchQuery = searchQuery,
                            onEdit = { editingTemplate = it },
                            onDelete = { deleteConfirmTemplate = it }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit food dialog
    val dialogItem = editingItem
    if (showAddFoodDialog || dialogItem != null) {
        FoodItemDialog(
            existing = dialogItem,
            onSave = { item ->
                viewModel.saveItem(item)
                showAddFoodDialog = false
                editingItem = null
            },
            onDismiss = {
                showAddFoodDialog = false
                editingItem = null
            }
        )
    }

    // Create meal dialog
    if (showCreateMealDialog) {
        CreateMealDialog(
            availableItems = allItems,
            onSave = { template ->
                viewModel.saveTemplate(template)
                showCreateMealDialog = false
            },
            onAddFood = { newItem -> viewModel.saveItem(newItem) },
            onDismiss = { showCreateMealDialog = false }
        )
    }

    // Edit template name dialog
    editingTemplate?.let { template ->
        TemplateNameDialog(
            currentName = template.name,
            onSave = { newName ->
                viewModel.saveTemplate(template.copy(name = newName))
                editingTemplate = null
            },
            onDismiss = { editingTemplate = null }
        )
    }

    // Delete food confirmation
    deleteConfirmItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteConfirmItem = null },
            title = { Text("Delete ${item.name}?") },
            text = { Text("This will remove it from your food library.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteItem(item.id)
                    deleteConfirmItem = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmItem = null }) {
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
            text = { Text("This will remove the meal template. It won't affect any logged entries.") },
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
}

// =============================================================================
// Foods tab
// =============================================================================

@Composable
private fun FoodsTab(
    items: List<FoodLibraryItem>,
    searchQuery: String,
    onEdit: (FoodLibraryItem) -> Unit,
    onDelete: (FoodLibraryItem) -> Unit
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (searchQuery.isNotEmpty()) "No matching foods" else "No saved foods",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (searchQuery.isNotEmpty()) "Try a different search"
                    else "Save foods from your log or add them here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { item ->
                FoodLibraryRow(
                    item = item,
                    onEdit = { onEdit(item) },
                    onDelete = { onDelete(item) }
                )
                HorizontalDivider()
            }
        }
    }
}

// =============================================================================
// Meals tab
// =============================================================================

@Composable
private fun MealsTab(
    templates: List<MealTemplate>,
    searchQuery: String,
    onEdit: (MealTemplate) -> Unit,
    onDelete: (MealTemplate) -> Unit
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
                    text = if (searchQuery.isNotEmpty()) "No matching meals" else "No saved meals",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (searchQuery.isNotEmpty()) "Try a different search"
                    else "Save a meal from your food log using the menu",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(templates, key = { it.id }) { template ->
                MealTemplateRow(
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
private fun FoodLibraryRow(
    item: FoodLibraryItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name.ifBlank { "Unnamed" },
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Text(
                text = buildString {
                    append("${item.calories} kcal")
                    append("  P: ${item.protein}g")
                    append("  C: ${item.carbs}g")
                    append("  F: ${item.fat}g")
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!item.servingSize.isNullOrBlank()) {
                Text(
                    text = "Serving: ${item.servingSize}${item.servingUnit ?: ""}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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

@Composable
private fun MealTemplateRow(
    template: MealTemplate,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val totalCalories = template.entries.sumOf { it.calories }
    val totalProtein = template.entries.sumOf { it.protein }
    val totalCarbs = template.entries.sumOf { it.carbs }
    val totalFat = template.entries.sumOf { it.fat }

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
                    text = template.name.ifBlank { "Unnamed meal" },
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    text = "${template.entries.size} items  ·  $totalCalories kcal",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "P: ${totalProtein}g  C: ${totalCarbs}g  F: ${totalFat}g",
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
                template.entries.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = entry.name.ifBlank { "Unnamed" },
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${entry.calories} kcal",
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

@Composable
private fun FoodItemDialog(
    existing: FoodLibraryItem?,
    onSave: (FoodLibraryItem) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing = existing != null

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var calories by remember { mutableStateOf(existing?.calories?.toString() ?: "") }
    var protein by remember { mutableStateOf(existing?.protein?.toString() ?: "") }
    var carbs by remember { mutableStateOf(existing?.carbs?.toString() ?: "") }
    var fat by remember { mutableStateOf(existing?.fat?.toString() ?: "") }
    var servingSize by remember { mutableStateOf(existing?.servingSize ?: "") }
    var servingUnit by remember { mutableStateOf(existing?.servingUnit ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Food" else "Add Food") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it },
                    label = { Text("Calories") },
                    suffix = { Text("kcal", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it },
                        label = { Text("Protein", fontSize = 12.sp, maxLines = 1) },
                        suffix = { Text("g", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it },
                        label = { Text("Carbs", fontSize = 12.sp, maxLines = 1) },
                        suffix = { Text("g", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it },
                        label = { Text("Fat", fontSize = 12.sp, maxLines = 1) },
                        suffix = { Text("g", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = servingSize,
                        onValueChange = { servingSize = it },
                        label = { Text("Serving", fontSize = 12.sp, maxLines = 1) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = servingUnit,
                        onValueChange = { servingUnit = it },
                        label = { Text("Unit", fontSize = 12.sp, maxLines = 1) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val item = FoodLibraryItem(
                        id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        calories = calories.toIntOrNull() ?: 0,
                        protein = protein.toIntOrNull() ?: 0,
                        carbs = carbs.toIntOrNull() ?: 0,
                        fat = fat.toIntOrNull() ?: 0,
                        servingSize = servingSize.ifBlank { null },
                        servingUnit = servingUnit.ifBlank { null }
                    )
                    onSave(item)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save", color = if (name.isNotBlank()) CherryBlossomPink else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CreateMealDialog(
    availableItems: List<FoodLibraryItem>,
    onSave: (MealTemplate) -> Unit,
    onAddFood: (FoodLibraryItem) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var foodSearch by remember { mutableStateOf("") }
    val selectedIds: SnapshotStateList<String> = remember {
        emptyList<String>().toMutableStateList()
    }
    var showInlineAddFood by remember { mutableStateOf(false) }
    val canSave = name.isNotBlank() && selectedIds.isNotEmpty()

    val displayItems = remember(availableItems, foodSearch) {
        if (foodSearch.isBlank()) availableItems
        else availableItems.filter { it.name.contains(foodSearch, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Meal") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Meal name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (availableItems.isEmpty()) "No foods in your library yet"
                        else "Select foods to include:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { showInlineAddFood = true }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("Add food", fontSize = 12.sp)
                    }
                }

                if (availableItems.isNotEmpty()) {
                    OutlinedTextField(
                        value = foodSearch,
                        onValueChange = { foodSearch = it },
                        placeholder = { Text("Search foods...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (foodSearch.isNotEmpty()) {
                                IconButton(onClick = { foodSearch = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (displayItems.isEmpty()) {
                            Text(
                                text = "No matching foods",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        displayItems.forEach { item ->
                            val checked = item.id in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (checked) selectedIds.remove(item.id)
                                        else selectedIds.add(item.id)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        if (it) selectedIds.add(item.id)
                                        else selectedIds.remove(item.id)
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name.ifBlank { "Unnamed" },
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${item.calories} kcal  P: ${item.protein}g  C: ${item.carbs}g  F: ${item.fat}g",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = availableItems.filter { it.id in selectedIds }
                    val template = MealTemplate(
                        id = java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        entries = selected
                    )
                    onSave(template)
                },
                enabled = canSave
            ) {
                Text("Save", color = if (canSave) CherryBlossomPink else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showInlineAddFood) {
        FoodItemDialog(
            existing = null,
            onSave = { newItem ->
                onAddFood(newItem)
                selectedIds.add(newItem.id)
                showInlineAddFood = false
            },
            onDismiss = { showInlineAddFood = false }
        )
    }
}

@Composable
private fun TemplateNameDialog(
    currentName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Meal") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("Save", color = if (name.isNotBlank()) CherryBlossomPink else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
