# Phase 2: Food Logging - Research

**Researched:** 2026-04-10
**Domain:** Jetpack Compose UI patterns — ModalBottomSheet, LazyColumn sections, TabRow/HorizontalPager, DatePickerDialog, LinearProgressIndicator, SnackbarHost — plus Repository/ViewModel architecture for org-file-backed data with SavedStateHandle draft save
**Confidence:** HIGH (all UI APIs verified against official Android developer docs updated March 2026; architecture patterns verified against official guides; SavedStateHandle behavior verified against official docs)

---

## Summary

Phase 2 builds the complete food logging UI on top of the org-file data layer established in Phase 1. Three areas require careful research: (1) the Compose Material3 component APIs — specifically ModalBottomSheet, DatePickerDialog (experimental), TabRow, and LinearProgressIndicator; (2) the FoodRepository/ViewModel architecture without Room; and (3) draft save/restore via SavedStateHandle for SYNC-05. All three are well-understood.

The standard approach for this phase is: `FoodRepository` interface backed by `OrgFoodRepository` (reads/writes food-log.org via the existing `SyncBackend` + `OrgParser`/`OrgWriter`), one `FoodLogViewModel` per screen with `StateFlow<UiState>` exposed to composables via `collectAsStateWithLifecycle()`, `ModalBottomSheet` for all entry forms, `LazyColumn` with expand/collapse state in the ViewModel for meal sections, and `SavedStateHandle` (not DataStore) for form draft persistence across process death.

The one area requiring upfront design before implementation: the food library and meal template data must be persisted somewhere. Since there is no Room and templates/library items are not date-scoped entries in food-log.org, a separate `food-library.org` and `meal-templates.org` file (in the same Syncthing folder) is the natural extension of the established org-file-as-datastore pattern. This is a design decision not yet locked in CONTEXT.md — the planner should address it.

**Primary recommendation:** Follow the org-file-as-datastore pattern already established. Introduce `food-library.org` and `meal-templates.org` as sibling files to `food-log.org`, parsed with the same OrgEngine. Use `SavedStateHandle` for form draft state, not DataStore.

---

## Standard Stack

### Core (Phase 2 additions — nothing new in libs.versions.toml needed unless noted)

All Phase 2 UI components are in `androidx.compose.material3` already in the BOM (`composeBom = "2025.05.00"`). No new Gradle dependencies are required for the core UI work.

**ModalBottomSheet — Material3 Compose:**
- In `androidx.compose.material3` (already in BOM). No new dependency.
- API: `ModalBottomSheet(onDismissRequest, sheetState, content)`. State via `rememberModalBottomSheetState()`.
- Confidence: HIGH — verified against official Android docs (updated 2026-03-30).

**DatePickerDialog — Material3 Compose (EXPERIMENTAL):**
- In `androidx.compose.material3` (already in BOM). No new dependency.
- Annotated `@ExperimentalMaterial3Api`. API may change across BOM updates.
- Key type: `rememberDatePickerState()` → `datePickerState.selectedDateMillis: Long?`
- Confidence: HIGH — verified against official Android docs (updated 2026-03-30).

**LinearProgressIndicator — Material3 Compose:**
- In `androidx.compose.material3` (already in BOM). No new dependency.
- API: `LinearProgressIndicator(progress = { float }, color, trackColor, modifier)`. Progress is a lambda returning Float 0.0–1.0.
- Confidence: HIGH — verified against official docs.

**TabRow / Tab — Material3 Compose:**
- In `androidx.compose.material3` (already in BOM). No new dependency.
- Current API: `PrimaryTabRow(selectedTabIndex)` + `Tab(selected, onClick, text, icon)`. `PrimaryTabRow` is the current M3 name (not just `TabRow`).
- For swipeable tab content: use `HorizontalPager` from `androidx.compose.foundation` (already in BOM via `ui`).
- Confidence: HIGH — verified against official docs (updated 2026-03-30).

**SnackbarHost — Material3 Compose:**
- In `androidx.compose.material3` (already in BOM). No new dependency.
- API: `SnackbarHostState.showSnackbar(message, actionLabel, duration)` returns `SnackbarResult`. Handle in `scope.launch {}`.
- Confidence: HIGH — verified against official docs (updated 2026-03-30).

**SavedStateHandle:**
- In `androidx.lifecycle:lifecycle-viewmodel` (already provided by `lifecycle-viewmodel-compose`). No new dependency.
- Use `savedStateHandle.getStateFlow(key, initialValue)` for reactive state. Write with `savedStateHandle[key] = value`.
- Confidence: HIGH — verified against official docs.

### Supporting

**HorizontalPager (for Recent/Library tabs in bottom sheet):**
- `androidx.compose.foundation` — already in BOM. No new dependency.
- Use `rememberPagerState()` + `HorizontalPager(state)`. Synchronize with `PrimaryTabRow` via `pagerState.currentPage` and `scope.launch { pagerState.animateScrollToPage(index) }`.

**AnimatedVisibility (for meal section expand/collapse):**
- `androidx.compose.animation` — already in BOM. No new dependency.
- Use `AnimatedVisibility(visible = isExpanded)` wrapping the items list. Pair with `animateFloatAsState` for the chevron rotation.

### Alternatives Considered

- DataStore for form draft state vs. SavedStateHandle: DataStore is for long-term app preferences; SavedStateHandle is the correct tool for transient UI state that must survive process death. Use SavedStateHandle. (Source: official state-saving docs, verified.)
- Room for food library vs. org files: Room was explicitly rejected as a design decision. The food library must live in an org file (`food-library.org`). Parsed with the existing OrgEngine.
- BottomSheetScaffold vs. ModalBottomSheet: `BottomSheetScaffold` is persistent (always on screen). `ModalBottomSheet` is modal (appears over content, has scrim). The design calls for modal — use `ModalBottomSheet`.
- `TabRow` vs. `PrimaryTabRow`: `PrimaryTabRow` is the M3-correct component for primary navigation tabs. `TabRow` still works but `PrimaryTabRow` is the current recommended API for top-level content destinations.

### Installation

No new Gradle dependencies required. All Material3 components are in the existing Compose BOM. All lifecycle components are in the existing `lifecycle-viewmodel-compose`.

---

## Architecture Patterns

### Recommended Project Structure (Phase 2 scope)

```
app/src/main/java/com/sakura/
├── di/
│   └── AppContainer.kt              # Add FoodRepository wiring
│
├── features/
│   ├── foodlog/
│   │   ├── FoodLogScreen.kt         # LazyColumn with meal sections + FAB
│   │   ├── FoodLogViewModel.kt      # StateFlow<FoodLogUiState>, date nav, expand/collapse
│   │   ├── FoodEntryBottomSheet.kt  # Entry form — new and edit modes
│   │   ├── FoodLibraryBottomSheet.kt # Recent + Library tabs (PrimaryTabRow + HorizontalPager)
│   │   └── FoodLogUiState.kt        # Sealed/data class for screen state
│   │
│   └── settings/
│       └── MacroTargetsScreen.kt    # Static macro targets form → DataStore
│
├── data/
│   ├── food/
│   │   ├── FoodRepository.kt        # Interface: loadDay, addEntry, updateEntry, deleteEntry, library ops
│   │   ├── OrgFoodRepository.kt     # Impl: reads/writes food-log.org, food-library.org, meal-templates.org via SyncBackend
│   │   ├── FoodEntry.kt             # Domain model (decoupled from OrgFoodEntry)
│   │   ├── MealGroup.kt             # Domain model
│   │   ├── FoodLibraryItem.kt       # Domain model for saved foods
│   │   └── MealTemplate.kt          # Domain model for saved meal templates
│   └── (no changes to sync/ or orgengine/)
│
└── navigation/
    └── Routes.kt                    # Add FoodLog, Settings routes
```

### Pattern 1: FoodRepository Interface

**What:** Interface abstracting all food data operations. `OrgFoodRepository` implements it by reading/writing org files via `SyncBackend`. ViewModels depend on the interface only.

```kotlin
// Source: Android architecture guide — data layer abstraction
interface FoodRepository {
    // Day log operations
    suspend fun loadDay(date: LocalDate): List<MealGroup>
    suspend fun addEntry(date: LocalDate, mealLabel: String, entry: FoodEntry): Result<Unit>
    suspend fun updateEntry(date: LocalDate, mealLabel: String, entryIndex: Int, updated: FoodEntry): Result<Unit>
    suspend fun deleteEntry(date: LocalDate, mealLabel: String, entryIndex: Int): Result<Unit>

    // Food library operations
    suspend fun loadLibrary(): List<FoodLibraryItem>
    suspend fun saveToLibrary(item: FoodLibraryItem): Result<Unit>
    suspend fun deleteFromLibrary(itemId: String): Result<Unit>

    // Meal template operations
    suspend fun loadTemplates(): List<MealTemplate>
    suspend fun saveTemplate(template: MealTemplate): Result<Unit>
    suspend fun applyTemplate(date: LocalDate, mealLabel: String, template: MealTemplate): Result<Unit>
    suspend fun deleteTemplate(templateId: String): Result<Unit>

    // Recent entries (last N distinct food names logged)
    suspend fun loadRecentItems(limit: Int = 20): List<FoodLibraryItem>
}
```

**Key design notes:**
- `loadDay` returns the org file parsed for a specific date. If the date has no section, returns empty list.
- `addEntry` reads the full file, merges the new entry into the correct date section and meal group, writes back via `SyncBackend.writeFile()` (AtomicFile-backed).
- `updateEntry` and `deleteEntry` read-modify-write the full file.
- Library and templates live in separate org files (`food-library.org`, `meal-templates.org`). Parsed with the same `OrgParser` — may need new OrgSchema constants for library/template-specific property keys.

### Pattern 2: ViewModel with StateFlow UiState

**What:** One ViewModel per screen. Exposes `StateFlow<UiState>` consumed by Compose via `collectAsStateWithLifecycle()`. Events flow up as function calls on the ViewModel.

```kotlin
// Source: official Compose architecture guide
class FoodLogViewModel(
    private val foodRepo: FoodRepository,
    private val prefsRepo: AppPreferencesRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow<FoodLogUiState>(FoodLogUiState.Loading)
    val uiState: StateFlow<FoodLogUiState> = _uiState.asStateFlow()

    // Separate states for lightweight concerns
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _expandedMeals = MutableStateFlow(setOf<String>()) // meal labels
    val expandedMeals: StateFlow<Set<String>> = _expandedMeals.asStateFlow()

    fun loadDay(date: LocalDate) {
        viewModelScope.launch {
            _uiState.value = FoodLogUiState.Loading
            try {
                val meals = foodRepo.loadDay(date)
                val targets = prefsRepo.macroTargets.first()
                _uiState.value = FoodLogUiState.Success(meals, targets)
            } catch (e: SyncBackendError.FolderUnavailable) {
                _uiState.value = FoodLogUiState.Error.FolderUnavailable
            }
        }
    }

    fun toggleMealExpanded(mealLabel: String) {
        _expandedMeals.update { current ->
            if (mealLabel in current) current - mealLabel else current + mealLabel
        }
    }
}
```

**Anti-pattern:** Do NOT create a single giant UiState with everything (meals, expanded state, date, sheet open state, snackbar pending, etc.). Use separate StateFlows for lightweight orthogonal concerns (expanded sections, selected date). Reserve UiState for the primary screen data with loading/error states.

### Pattern 3: ModalBottomSheet for Food Entry Form

**What:** Entry form in a `ModalBottomSheet` controlled by a `showBottomSheet: Boolean` state hoisted to the ViewModel or screen-level state holder.

```kotlin
// Source: official Android docs (updated 2026-03-30)
var showEntrySheet by remember { mutableStateOf(false) }
val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
val scope = rememberCoroutineScope()

if (showEntrySheet) {
    ModalBottomSheet(
        onDismissRequest = { showEntrySheet = false },
        sheetState = sheetState
    ) {
        FoodEntryForm(
            onSave = { entry ->
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) showEntrySheet = false
                }
                viewModel.addEntry(entry)
            },
            onDismiss = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) showEntrySheet = false
                }
            }
        )
    }
}
```

**Critical:** Always remove `ModalBottomSheet` from composition after hiding (the `invokeOnCompletion` pattern above). If the sheet is left in composition while hidden, it can cause animation and state bugs.

**For the entry form used both for new entries and editing:** Accept an optional `editingEntry: FoodEntry?` parameter. If non-null, pre-fill fields and the save action calls `viewModel.updateEntry(...)` instead of `addEntry(...)`.

### Pattern 4: Collapsible Meal Sections in LazyColumn

**What:** Meal section headers are always visible; entries are shown/hidden via `AnimatedVisibility`. Expand/collapse state is a `Set<String>` of expanded meal labels in the ViewModel.

```kotlin
// Source: official LazyColumn docs + community pattern (multiple sources agree)
LazyColumn {
    for (mealGroup in meals) {
        // Sticky meal header
        stickyHeader(key = "header-${mealGroup.label}") {
            MealSectionHeader(
                label = mealGroup.label,
                totalCalories = mealGroup.totalCalories,
                isExpanded = expandedMeals.contains(mealGroup.label),
                onToggle = { viewModel.toggleMealExpanded(mealGroup.label) }
            )
        }

        // Collapsible entries
        if (mealGroup.label in expandedMeals) {
            items(
                items = mealGroup.entries,
                key = { entry -> "${mealGroup.label}-${entry.name}-${entry.hashCode()}" }
            ) { entry ->
                FoodEntryRow(
                    entry = entry,
                    onEdit = { viewModel.startEdit(entry) },
                    onDelete = { viewModel.deleteEntry(mealGroup.label, entry) }
                )
            }
        }
    }
}
```

**Note on stickyHeader for collapsible sections:** `stickyHeader` is appropriate here because section headers should remain visible while scrolling through entries. When collapsed, there are no items beneath — the next header appears immediately.

**AnimatedVisibility alternative:** For smooth collapse/expand animation, wrap the items block in `AnimatedVisibility(visible = isExpanded)` rather than an `if` check. This adds enter/exit transitions but requires the items block outside of `LazyColumn.items()` (wrap a `Column` inside the lazy scope).

### Pattern 5: Tabs in Bottom Sheet (Recent + Library)

**What:** `PrimaryTabRow` + `HorizontalPager` inside the `ModalBottomSheet` for library/template browsing.

```kotlin
// Source: official tabs docs (updated 2026-03-30)
val pagerState = rememberPagerState(pageCount = { 2 })
val scope = rememberCoroutineScope()

Column {
    PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
        Tab(
            selected = pagerState.currentPage == 0,
            onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
            text = { Text("Recent") }
        )
        Tab(
            selected = pagerState.currentPage == 1,
            onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
            text = { Text("Library") }
        )
    }
    HorizontalPager(state = pagerState) { page ->
        when (page) {
            0 -> RecentItemsList(...)
            1 -> LibraryItemsList(...)
        }
    }
}
```

### Pattern 6: Draft Save/Restore via SavedStateHandle (SYNC-05)

**What:** In-progress form field values survive process death via `SavedStateHandle`. Not DataStore — DataStore is for long-lived app preferences, not transient UI state.

```kotlin
// Source: official state-saving docs — SavedStateHandle is the correct mechanism
class FoodEntryViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Each form field backed by SavedStateHandle
    var draftName: StateFlow<String> = savedStateHandle.getStateFlow("draft_name", "")
    var draftProtein: StateFlow<String> = savedStateHandle.getStateFlow("draft_protein", "")
    var draftCarbs: StateFlow<String> = savedStateHandle.getStateFlow("draft_carbs", "")
    var draftFat: StateFlow<String> = savedStateHandle.getStateFlow("draft_fat", "")
    var draftCalories: StateFlow<String> = savedStateHandle.getStateFlow("draft_calories", "")
    var draftNotes: StateFlow<String> = savedStateHandle.getStateFlow("draft_notes", "")
    var draftMealLabel: StateFlow<String> = savedStateHandle.getStateFlow("draft_meal_label", "Breakfast")

    fun updateDraftName(value: String) { savedStateHandle["draft_name"] = value }
    fun updateDraftProtein(value: String) { savedStateHandle["draft_protein"] = value }
    // ... etc for each field

    fun clearDraft() {
        savedStateHandle["draft_name"] = ""
        savedStateHandle["draft_protein"] = ""
        // ... etc
    }
}
```

**Supported types in SavedStateHandle:** String, Int, Long, Float, Double, Boolean — all form field values map naturally to these. No Parcelable needed.

**Limitation to understand:** SavedStateHandle data is only persisted when the Activity is stopped. If the process is killed while the app is in the foreground (unusual), data may not be saved. This is acceptable for a draft — the official docs acknowledge this edge case.

### Pattern 7: Undo Snackbar After Log Entry

**What:** After saving a food entry, show a snackbar with "Undo" action. If the user taps Undo, call `deleteEntry` for the just-added item.

```kotlin
// Source: official Snackbar docs (updated 2026-03-30)
scope.launch {
    val result = snackbarHostState.showSnackbar(
        message = "Logged ${entry.name.ifBlank { "entry" }}",
        actionLabel = "Undo",
        duration = SnackbarDuration.Short
    )
    if (result == SnackbarResult.ActionPerformed) {
        viewModel.undoLastAdd()
    }
}
```

**SnackbarHost placement:** Must be in the `Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) })`. The `SnackbarHostState` must be passed down to wherever the entry save action fires.

### Pattern 8: DatePickerDialog for Past Day Navigation

**What:** Tap the date text label → show `DatePickerDialog`. Experimental API — must opt-in with `@OptIn(ExperimentalMaterial3Api::class)`.

```kotlin
// Source: official DatePicker docs (updated 2026-03-30) — EXPERIMENTAL
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateNavigation(currentDate: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = currentDate.toEpochDay() * 86_400_000L
    )

    // Left/right arrows + tappable date label
    Row {
        IconButton(onClick = { onDateSelected(currentDate.minusDays(1)) }) { /* prev */ }
        Text(currentDate.toString(), modifier = Modifier.clickable { showPicker = true })
        IconButton(onClick = { onDateSelected(currentDate.plusDays(1)) }) { /* next */ }
    }

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selected = LocalDate.ofEpochDay(millis / 86_400_000L)
                        onDateSelected(selected)
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
```

**Note on millisToLocalDate conversion:** `datePickerState.selectedDateMillis` returns UTC midnight epoch millis. Convert with `LocalDate.ofEpochDay(millis / 86_400_000L)`. Do not use `Instant.ofEpochMilli` with a timezone — this can shift the date by one day depending on locale.

### Pattern 9: MacroTargets in DataStore (FOOD-03)

**What:** Daily macro targets are user-configurable static values. Stored in `AppPreferencesRepository` via DataStore — they are app preferences, not daily log data.

Add keys to `AppPreferencesRepository`:
```kotlin
companion object {
    // existing keys...
    val MACRO_TARGET_CALORIES = intPreferencesKey("macro_target_calories")
    val MACRO_TARGET_PROTEIN = intPreferencesKey("macro_target_protein")
    val MACRO_TARGET_CARBS = intPreferencesKey("macro_target_carbs")
    val MACRO_TARGET_FAT = intPreferencesKey("macro_target_fat")
}

val macroTargetCalories: Flow<Int> = context.appDataStore.data.map { it[MACRO_TARGET_CALORIES] ?: 2000 }
// etc for each macro
```

### Pattern 10: OrgFoodRepository — Read-Modify-Write Pattern

**What:** Because the org file is the data store (no Room), every mutation (add/edit/delete) requires: (1) read the full file, (2) parse it, (3) mutate the in-memory model, (4) serialize back to string, (5) write atomically. This is the correct pattern — it matches how the existing OrgWriter works.

```kotlin
// In OrgFoodRepository
override suspend fun addEntry(date: LocalDate, mealLabel: String, entry: FoodEntry) = withContext(ioDispatcher) {
    val content = syncBackend.readFile("food-log.org")
    val orgFile = orgParser.parse(content, OrgParser.ParseMode.FOOD)

    // Mutate in-memory: find or create date section, find or create meal group, add entry
    val updated = orgFile.addEntry(date, mealLabel, entry.toOrgFoodEntry())

    syncBackend.writeFile("food-log.org", orgWriter.write(updated))
    Result.success(Unit)
}
```

**Extension functions on OrgFile:** The mutation operations (addEntry, updateEntry, deleteEntry) are cleanly expressed as extension functions on `OrgFile` or `OrgDateSection`. They return new immutable instances (functional style). This keeps `OrgFoodRepository` readable.

### Anti-Patterns to Avoid

- **Giant single UiState data class for all screen state:** Causes excessive recomposition. Split orthogonal state (expanded sections, date, sheet visibility) into separate StateFlows.
- **Showing ModalBottomSheet without removing from composition on hide:** Causes animation and state bugs. Always use `invokeOnCompletion { if (!sheetState.isVisible) showSheet = false }`.
- **Using DataStore for form draft state:** DataStore is correct for macro targets (long-lived preferences) but wrong for in-progress form fields (transient UI state). Use `SavedStateHandle` for drafts.
- **Calling food file ops on main thread:** All `SyncBackend` calls must be inside `withContext(Dispatchers.IO)` (or the injected `ioDispatcher`). The ViewModel wraps these in `viewModelScope.launch`.
- **Using `TabRow` instead of `PrimaryTabRow`:** `PrimaryTabRow` is the current Material3 API for top-level content tabs. `TabRow` still works but is less semantically correct under M3.
- **Converting epoch millis to LocalDate with timezone:** `datePickerState.selectedDateMillis` is UTC. Use `LocalDate.ofEpochDay(millis / 86_400_000L)` — not `Instant.ofEpochMilli(millis).atZone(systemDefault())`, which shifts by timezone offset.
- **Not providing unique keys in LazyColumn items:** Without `key`, Compose cannot maintain item state or animate correctly across insertions/deletions. Always key food entry items by a stable identifier.

---

## Don't Hand-Roll

- **Bottom sheet from scratch:** `ModalBottomSheet` in Material3 handles scrim, drag-to-dismiss, and keyboard avoidance. Do not build a custom overlay.
- **Progress bar with color customization:** `LinearProgressIndicator` takes `color` and `trackColor` parameters. Apply the Sakura palette directly.
- **Date picker dialog:** `DatePickerDialog` with `DatePicker` handles calendar display, month navigation, and date selection. Do not build a custom calendar.
- **Tab-pager synchronization:** `PrimaryTabRow` + `HorizontalPager` sharing a `PagerState` gives bidirectional sync (swipe updates tab, tap updates pager). Do not hand-sync.
- **Undo action toast:** `SnackbarHostState.showSnackbar()` with `actionLabel` and awaiting `SnackbarResult` is the complete undo pattern. No custom overlay needed.
- **File-write atomicity:** `SyncBackend.writeFile()` uses `AtomicFile` (established in Phase 1). `OrgFoodRepository` must call `syncBackend.writeFile()`, never `File.writeText()` directly.

---

## Common Pitfalls

### Pitfall 1: ModalBottomSheet Left in Composition After Hide

**What goes wrong:** The sheet flickers back, animations misbehave, or state resets unexpectedly.
**Why it happens:** Calling `sheetState.hide()` collapses the sheet visually but does not remove it from composition. The boolean flag controlling composition must also be set to false.
**How to avoid:** Always use `.invokeOnCompletion { if (!sheetState.isVisible) showSheet = false }` after calling `sheetState.hide()`.
**Warning signs:** Sheet reappears briefly or keyboard dismissal causes sheet to flash.

### Pitfall 2: DatePickerDialog Epoch Millis Timezone Shift

**What goes wrong:** User selects April 10 in the calendar, app receives April 9 or April 11.
**Why it happens:** `selectedDateMillis` is UTC midnight. Converting via `Instant.atZone(systemDefault())` applies the device timezone offset, potentially crossing a day boundary.
**How to avoid:** `LocalDate.ofEpochDay(millis / 86_400_000L)` — pure integer arithmetic, no timezone.
**Warning signs:** Date off by one day for users in UTC+/- timezones.

### Pitfall 3: Read-Modify-Write Concurrency on Org Files

**What goes wrong:** Two rapid operations (e.g., add entry while a previous add is still writing) produce a corrupted org file.
**Why it happens:** Both coroutines read the same stale content, mutate independently, and write conflicting results.
**How to avoid:** Serialize file mutations through a `Mutex` in `OrgFoodRepository`:
```kotlin
private val fileMutex = Mutex()

override suspend fun addEntry(...) = fileMutex.withLock {
    // read-modify-write inside mutex
}
```
This is a real risk with the undo toast — the user could tap "Add" again while the undo snackbar is showing.
**Warning signs:** Org file missing entries or having duplicate date sections after rapid operations.

### Pitfall 4: SavedStateHandle Draft State Not Cleared After Save

**What goes wrong:** User logs an entry, navigates away, returns to the form, and sees the previous entry's values pre-filled.
**Why it happens:** `SavedStateHandle` persists keys until explicitly cleared. If the draft is not cleared after a successful save, the old values survive.
**How to avoid:** Call `clearDraft()` in the ViewModel immediately after a successful `addEntry` or `updateEntry`. Don't clear on dismiss without save — that's exactly when restoration is valuable.
**Warning signs:** Form pre-fills with stale data on second open.

### Pitfall 5: OrgFoodEntry Name Cannot Be Blank in OrgSchema

**What goes wrong:** An unnamed food entry (blank name field) produces `*** ` (three asterisks and a space with no text), which fails org-lint.
**Why it happens:** `OrgSchema.formatFoodEntry()` uses `entry.name` directly in the heading. An empty string produces an invalid heading.
**How to avoid:** In `OrgFoodRepository.addEntry()`, substitute a placeholder when name is blank:
```kotlin
val effectiveName = entry.name.ifBlank { "Unnamed" }
```
Or update `OrgSchema.formatFoodEntry()` to handle blank names defensively. Either way, a blank org heading must never reach the file.
**Warning signs:** org-lint reports "Invalid heading" for entries with no name.

### Pitfall 6: Library and Template Org File Schema Must Be Designed Before Implementation

**What goes wrong:** Implementation starts without a defined org format for `food-library.org` and `meal-templates.org`, resulting in a format that does not pass org-lint or is not queryable from Emacs.
**Why it happens:** The existing `OrgSchema` only covers `food-log.org` and `workout-log.org`. Library items and templates have different property sets (e.g., library items need an ID; templates need a list of entries under a heading).
**How to avoid:** Design the org format for both files before starting implementation. Hand-author 3-5 entries in Emacs and run `M-x org-lint` before coding the parser. Add new constants to `OrgSchema` for the new property keys.
**Warning signs:** Library items parsed incorrectly, or template application produces malformed date sections.

### Pitfall 7: Macro Progress Bar Exceeds 100%

**What goes wrong:** `LinearProgressIndicator(progress = { logged / target })` crashes or renders incorrectly if `logged > target` (progress > 1.0f) or `target == 0`.
**Why it happens:** The component clamps internally but a division-by-zero when target is 0 causes a NaN that renders as 0%.
**How to avoid:**
```kotlin
val progress = if (target > 0) (logged.toFloat() / target).coerceIn(0f, 1f) else 0f
```
Always coerce the value to 0..1 before passing to the component.
**Warning signs:** Progress bar shows nothing (NaN → 0) or throws ArithmeticException when targets are unset.

---

## Code Examples

Verified patterns from official sources:

### ModalBottomSheet Show/Hide with State Guard

```kotlin
// Source: https://developer.android.com/develop/ui/compose/components/bottom-sheets (2026-03-30)
var showBottomSheet by remember { mutableStateOf(false) }
val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
val scope = rememberCoroutineScope()

FloatingActionButton(onClick = { showBottomSheet = true }) { /* add icon */ }

if (showBottomSheet) {
    ModalBottomSheet(
        onDismissRequest = { showBottomSheet = false },
        sheetState = sheetState
    ) {
        Button(onClick = {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) showBottomSheet = false
            }
        }) { Text("Save") }
    }
}
```

### LinearProgressIndicator with Sakura Colors

```kotlin
// Source: https://developer.android.com/develop/ui/compose/components/progress
val caloriesProgress = if (targetCalories > 0)
    (loggedCalories.toFloat() / targetCalories).coerceIn(0f, 1f)
else 0f

LinearProgressIndicator(
    progress = { caloriesProgress },
    modifier = Modifier.fillMaxWidth(),
    color = Color(0xFFC9758B),        // Cherry blossom pink
    trackColor = Color(0xFFF5DDE5)    // Pale sakura
)
```

### PrimaryTabRow + HorizontalPager (Bidirectional Sync)

```kotlin
// Source: https://developer.android.com/develop/ui/compose/components/tabs
val pagerState = rememberPagerState(pageCount = { 2 })
val scope = rememberCoroutineScope()

Column {
    PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
        listOf("Recent", "Library").forEachIndexed { index, title ->
            Tab(
                selected = pagerState.currentPage == index,
                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                text = { Text(title) }
            )
        }
    }
    HorizontalPager(state = pagerState) { page ->
        when (page) {
            0 -> RecentList(items = recentItems, onSelect = onSelect)
            1 -> LibraryList(items = libraryItems, onSelect = onSelect)
            else -> {}
        }
    }
}
```

### SnackbarHostState with Undo Action

```kotlin
// Source: https://developer.android.com/develop/ui/compose/components/snackbar
val snackbarHostState = remember { SnackbarHostState() }
val scope = rememberCoroutineScope()

// After successful entry save:
scope.launch {
    val result = snackbarHostState.showSnackbar(
        message = "Logged ${entry.name.ifBlank { "entry" }}",
        actionLabel = "Undo",
        duration = SnackbarDuration.Short
    )
    if (result == SnackbarResult.ActionPerformed) {
        viewModel.undoLastAdd()
    }
}
```

### Coroutines Mutex for Org File Mutations

```kotlin
// Source: kotlinx.coroutines documentation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OrgFoodRepository(
    private val syncBackend: SyncBackend,
    private val ioDispatcher: CoroutineDispatcher
) : FoodRepository {
    private val fileMutex = Mutex()

    override suspend fun addEntry(date: LocalDate, mealLabel: String, entry: FoodEntry) =
        fileMutex.withLock {
            withContext(ioDispatcher) {
                val content = syncBackend.readFile("food-log.org")
                val orgFile = OrgParser.parse(content, OrgParser.ParseMode.FOOD)
                val updated = orgFile.withEntryAdded(date, mealLabel, entry.toOrg())
                syncBackend.writeFile("food-log.org", OrgWriter.write(updated))
                Result.success(Unit)
            }
        }
}
```

---

## Open Questions

1. **Food library and meal template org file schema**
   - What we know: The existing OrgSchema covers food-log.org (date sections → meal groups → food entries with protein/carbs/fat/calories drawers). Library items and templates need additional property keys (e.g., `:id:`, `:serving_size:`, `:serving_unit:`).
   - What's unclear: The exact format for `meal-templates.org` — a template is a named group of multiple food entries. The org hierarchy (template name as level-1 or level-2? entries as level-2 or level-3?) must be designed and org-lint validated before implementation.
   - Recommendation: Hand-author 3-5 sample library items and 1-2 templates in Emacs. Run org-lint. Use that as the OrgSchema spec for Plan 02-01 or 02-02 setup tasks.

2. **Serving size field in OrgFoodEntry**
   - What we know: The CONTEXT.md entry form includes "Serving size + unit" as a field. The current `OrgFoodEntry` model does NOT have serving size or unit fields.
   - What's unclear: Should serving size be stored in the org property drawer (`:serving_size: 200 :serving_unit: g`)? Or is it only used for library items (where it acts as a template for re-use), not in the daily log?
   - Recommendation: For the daily log, serving size is reference info (how you got to those macro numbers). Store it as optional properties in the log entry's drawer. Requires adding `servingSize: String?` and `servingUnit: String?` to `OrgFoodEntry` and updating `OrgSchema.formatFoodEntry()`.

3. **Entry IDs for stable LazyColumn keys and undo**
   - What we know: `OrgFoodEntry` has no ID field. LazyColumn needs stable keys for correct item animation. The undo action needs to identify which entry to delete.
   - What's unclear: Whether IDs should be assigned at write time (stored as `:id:` in the property drawer) or generated ephemerally at read time (e.g., hash of name+meal+date).
   - Recommendation: Assign a timestamp-based ID at write time (`:id: 1712752800000`). This is stable, simple, and lets undo say "delete entry with id X from date Y meal Z." Requires adding `id: Long` to `OrgFoodEntry` and updating `OrgSchema`.

4. **Edit/Delete interaction pattern (Claude's Discretion)**
   - What we know: CONTEXT.md leaves the interaction pattern (tap, swipe, long-press, menu) to Claude's discretion.
   - Recommendation: **Swipe-to-reveal** (trailing swipe shows Edit and Delete buttons) is the standard Android pattern for list item actions, familiar to users, and does not require long-press discovery. Implement using `SwipeToDismissBox` from `androidx.compose.material3` (available in the BOM). Alternatively, a trailing three-dot menu icon on each row is simpler to implement and avoids the swipe state management — better choice for a first pass.
   - Simpler first choice: **Trailing row action menu (three-dot icon)** — lower complexity, fewer edge cases with scroll conflicts.

---

## State of the Art

- **`TabRow` → `PrimaryTabRow` / `SecondaryTabRow`:** Material3 introduced semantic tab row variants. `PrimaryTabRow` for top-level navigation, `SecondaryTabRow` for filtering/categorization within a screen. `TabRow` still compiles but the M3 semantic names are current. Changed: M3 stable (2024).
- **`ModalBottomSheetLayout` (Material2) → `ModalBottomSheet` (Material3):** The old `ModalBottomSheetLayout` from Material2 is gone. The new API in Material3 is `ModalBottomSheet`. The project already uses Material3 exclusively. No migration needed.
- **`collectAsState()` → `collectAsStateWithLifecycle()`:** `collectAsState()` does not stop collection when backgrounded. `collectAsStateWithLifecycle()` is lifecycle-aware. Phase 1 already uses `collectAsStateWithLifecycle()` — continue this pattern.
- **`rememberSaveable` vs. `SavedStateHandle`:** Both survive process death. `rememberSaveable` is for composable-local UI state (e.g., a toggle). `SavedStateHandle` is for business-logic state in ViewModels (e.g., form fields that the ViewModel needs to act on). Use `SavedStateHandle` for draft form state.

---

## Sources

### Primary (HIGH confidence)

- https://developer.android.com/develop/ui/compose/components/bottom-sheets — ModalBottomSheet API, sheetState, invokeOnCompletion pattern; last updated 2026-03-30
- https://developer.android.com/develop/ui/compose/components/bottom-sheets-partial — skipPartiallyExpanded parameter; last updated 2026-03-30
- https://developer.android.com/develop/ui/compose/components/datepickers — DatePickerDialog, DatePickerState, selectedDateMillis; experimental status confirmed; last updated 2026-03-30
- https://developer.android.com/develop/ui/compose/components/progress — LinearProgressIndicator, progress lambda, color/trackColor parameters; last updated 2026-03-30
- https://developer.android.com/develop/ui/compose/components/tabs — PrimaryTabRow, Tab, HorizontalPager synchronization; last updated 2026-03-30
- https://developer.android.com/develop/ui/compose/components/snackbar — SnackbarHostState, showSnackbar, SnackbarResult; last updated 2026-03-30
- https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate — SavedStateHandle: getStateFlow, supported types, write pattern; last updated 2026-03-30
- https://developer.android.com/develop/ui/compose/state-saving — rememberSaveable vs SavedStateHandle decision guide; last updated 2026-03-30
- https://developer.android.com/topic/libraries/architecture/saving-states — Three-tier state strategy (ViewModel + SavedStateHandle + local persistence); last updated 2026-03-30
- https://developer.android.com/develop/ui/compose/architecture — UDF pattern, StateFlow, collectAsStateWithLifecycle; last updated 2026-03-30
- https://developer.android.com/develop/ui/compose/lists — LazyColumn, stickyHeader, item keys, contentType; last updated 2026-03-30

### Secondary (MEDIUM confidence)

- Official Android docs confirm: DatePickerDialog is `@ExperimentalMaterial3Api` as of 2026-03-30
- Multiple community sources confirm: `PrimaryTabRow` is the current M3 API (not `TabRow`); consistent with official docs
- kotlinx.coroutines `Mutex` for shared-resource serialization — standard concurrency pattern, no new dependency needed (coroutines already in Kotlin stdlib for Compose)

---

## Metadata

**Confidence breakdown:**

- ModalBottomSheet / LinearProgressIndicator / Snackbar: HIGH — verified against official docs updated 2026-03-30
- DatePickerDialog: HIGH for the API; MEDIUM for stability — officially marked `@ExperimentalMaterial3Api`, API may change
- PrimaryTabRow / HorizontalPager: HIGH — verified against official tabs docs
- SavedStateHandle for draft state: HIGH — verified against official state-saving guide; DataStore vs. SavedStateHandle distinction explicitly stated in docs
- OrgFoodRepository read-modify-write: HIGH — direct application of established Phase 1 patterns
- Mutex for concurrent writes: HIGH — standard kotlinx.coroutines pattern
- Food library / meal template org file schema: LOW — no source; this is a design gap that must be resolved before implementation

**Research date:** 2026-04-10
**Valid until:** 2026-06-01 (stable Compose BOM; DatePickerDialog experimental status could change; verify against official docs before planning implementation)
