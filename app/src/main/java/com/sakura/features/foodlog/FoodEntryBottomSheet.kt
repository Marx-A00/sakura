package com.sakura.features.foodlog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakura.data.food.FoodEntry
import com.sakura.ui.theme.CherryBlossomPink
import com.sakura.ui.theme.PaleSakura
import kotlinx.coroutines.launch

private val MEAL_LABELS = listOf("Breakfast", "Lunch", "Dinner", "Snacks")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEntryBottomSheet(
    sheetState: SheetState,
    editingEntry: FoodEntry?,
    viewModel: FoodLogViewModel,
    onSave: (FoodEntry) -> Unit,
    onDismiss: () -> Unit,
    onOpenLibrary: () -> Unit,
    saveToLibrary: Boolean,
    onSaveToLibraryChanged: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()

    // Draft state from SavedStateHandle (only used for new entries)
    val draftName by viewModel.draftName.collectAsStateWithLifecycle()
    val draftProtein by viewModel.draftProtein.collectAsStateWithLifecycle()
    val draftCarbs by viewModel.draftCarbs.collectAsStateWithLifecycle()
    val draftFat by viewModel.draftFat.collectAsStateWithLifecycle()
    val draftCalories by viewModel.draftCalories.collectAsStateWithLifecycle()
    val draftServingSize by viewModel.draftServingSize.collectAsStateWithLifecycle()
    val draftServingUnit by viewModel.draftServingUnit.collectAsStateWithLifecycle()
    val draftNotes by viewModel.draftNotes.collectAsStateWithLifecycle()
    val draftMealLabel by viewModel.draftMealLabel.collectAsStateWithLifecycle()

    // Local state for editing mode (pre-filled from editingEntry, not from draft)
    var editName by remember { mutableStateOf(editingEntry?.name ?: "") }
    var editProtein by remember { mutableStateOf(editingEntry?.protein?.toString() ?: "") }
    var editCarbs by remember { mutableStateOf(editingEntry?.carbs?.toString() ?: "") }
    var editFat by remember { mutableStateOf(editingEntry?.fat?.toString() ?: "") }
    var editCalories by remember { mutableStateOf(editingEntry?.calories?.toString() ?: "") }
    var editServingSize by remember { mutableStateOf(editingEntry?.servingSize ?: "") }
    var editServingUnit by remember { mutableStateOf(editingEntry?.servingUnit ?: "") }
    var editNotes by remember { mutableStateOf(editingEntry?.notes ?: "") }
    var editMealLabel by remember { mutableStateOf(viewModel.defaultMealLabel()) }
    var editCaloriesOverridden by remember { mutableStateOf(false) }

    // Choose which values to display based on editing vs new
    val isEditing = editingEntry != null
    val name = if (isEditing) editName else draftName
    val protein = if (isEditing) editProtein else draftProtein
    val carbs = if (isEditing) editCarbs else draftCarbs
    val fat = if (isEditing) editFat else draftFat
    val calories = if (isEditing) editCalories else draftCalories
    val servingSize = if (isEditing) editServingSize else draftServingSize
    val servingUnit = if (isEditing) editServingUnit else draftServingUnit
    val notes = if (isEditing) editNotes else draftNotes
    val mealLabel = if (isEditing) editMealLabel else draftMealLabel

    // Helper to update protein/carbs/fat and recalc calories
    fun updateProtein(v: String) {
        if (isEditing) {
            editProtein = v
            if (!editCaloriesOverridden) {
                val p = v.toIntOrNull() ?: 0
                val c = editCarbs.toIntOrNull() ?: 0
                val f = editFat.toIntOrNull() ?: 0
                editCalories = (p * 4 + c * 4 + f * 9).let { if (it > 0) it.toString() else "" }
            }
        } else {
            viewModel.updateDraftProtein(v)
        }
    }

    fun updateCarbs(v: String) {
        if (isEditing) {
            editCarbs = v
            if (!editCaloriesOverridden) {
                val p = editProtein.toIntOrNull() ?: 0
                val c = v.toIntOrNull() ?: 0
                val f = editFat.toIntOrNull() ?: 0
                editCalories = (p * 4 + c * 4 + f * 9).let { if (it > 0) it.toString() else "" }
            }
        } else {
            viewModel.updateDraftCarbs(v)
        }
    }

    fun updateFat(v: String) {
        if (isEditing) {
            editFat = v
            if (!editCaloriesOverridden) {
                val p = editProtein.toIntOrNull() ?: 0
                val c = editCarbs.toIntOrNull() ?: 0
                val f = v.toIntOrNull() ?: 0
                editCalories = (p * 4 + c * 4 + f * 9).let { if (it > 0) it.toString() else "" }
            }
        } else {
            viewModel.updateDraftFat(v)
        }
    }

    fun updateCalories(v: String) {
        if (isEditing) {
            editCalories = v
            val p = editProtein.toIntOrNull() ?: 0
            val c = editCarbs.toIntOrNull() ?: 0
            val f = editFat.toIntOrNull() ?: 0
            val autoCalc = p * 4 + c * 4 + f * 9
            editCaloriesOverridden = v.toIntOrNull() != autoCalc && v.isNotBlank()
        } else {
            viewModel.updateDraftCalories(v)
        }
    }

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { dismiss() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Title
            Text(
                text = if (isEditing) "Edit Entry" else "Log Food",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Meal group picker
            Text(text = "Meal", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MEAL_LABELS.forEach { label ->
                    FilterChip(
                        selected = mealLabel == label,
                        onClick = {
                            if (isEditing) editMealLabel = label
                            else viewModel.updateDraftMealLabel(label)
                        },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CherryBlossomPink,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // From Library button
            TextButton(
                onClick = onOpenLibrary,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("From Library", color = CherryBlossomPink)
            }

            // Name field
            OutlinedTextField(
                value = name,
                onValueChange = { if (isEditing) editName = it else viewModel.updateDraftName(it) },
                label = { Text("Name (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Serving size row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = servingSize,
                    onValueChange = { if (isEditing) editServingSize = it else viewModel.updateDraftServingSize(it) },
                    label = { Text("Serving size") },
                    modifier = Modifier.weight(1.5f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = servingUnit,
                    onValueChange = { if (isEditing) editServingUnit = it else viewModel.updateDraftServingUnit(it) },
                    label = { Text("Unit") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Protein
            OutlinedTextField(
                value = protein,
                onValueChange = { updateProtein(it) },
                label = { Text("Protein (g)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Carbs
            OutlinedTextField(
                value = carbs,
                onValueChange = { updateCarbs(it) },
                label = { Text("Carbs (g)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Fat
            OutlinedTextField(
                value = fat,
                onValueChange = { updateFat(it) },
                label = { Text("Fat (g)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Calories — auto-calculated, pale sakura background when auto
            val caloriesOverridden = if (isEditing) editCaloriesOverridden
            else (viewModel.draftCaloriesOverridden.value)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (!caloriesOverridden) PaleSakura else Color.Transparent,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    )
            ) {
                OutlinedTextField(
                    value = calories,
                    onValueChange = { updateCalories(it) },
                    label = { Text("Calories (kcal)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = if (!caloriesOverridden) {
                        { Text("Auto-calculated", color = com.sakura.ui.theme.DeepRose, fontSize = 11.sp) }
                    } else null
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { if (isEditing) editNotes = it else viewModel.updateDraftNotes(it) },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Save to library toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Save to Library", fontSize = 14.sp)
                Switch(
                    checked = saveToLibrary,
                    onCheckedChange = onSaveToLibraryChanged,
                    colors = SwitchDefaults.colors(checkedThumbColor = CherryBlossomPink)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save button
            Button(
                onClick = {
                    val entryId = editingEntry?.id ?: System.currentTimeMillis()
                    val finalEntry = FoodEntry(
                        id = entryId,
                        name = name.trim(),
                        protein = protein.toIntOrNull() ?: 0,
                        carbs = carbs.toIntOrNull() ?: 0,
                        fat = fat.toIntOrNull() ?: 0,
                        calories = calories.toIntOrNull() ?: 0,
                        servingSize = servingSize.trim().ifBlank { null },
                        servingUnit = servingUnit.trim().ifBlank { null },
                        notes = notes.trim().ifBlank { null }
                    )
                    onSave(finalEntry)
                    if (!isEditing) viewModel.clearDraft()
                    dismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CherryBlossomPink)
            ) {
                Text(
                    text = if (isEditing) "Update" else "Save",
                    fontSize = 16.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
