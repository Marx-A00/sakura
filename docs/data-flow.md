# How Data Flows Through the App

This doc traces data from user interaction all the way to disk and back. Three paths: reading data, writing data, and preferences.

## The Read Path (Disk → Screen)

When you open the Food Log tab, here's what happens step by step:

```
User taps "Food" tab
    │
    ▼
FoodLogViewModel.init { loadCalendar() }
    │ selectedDate = LocalDate.now()
    │ combine(selectedDate, reloadTrigger)
    │
    ▼
flatMapLatest { date ->
    emit(Loading)
    foodRepo.loadDay(date)        ──────►  OrgFoodRepository
}                                              │
    │                                          ▼
    │                                  syncBackend.readFile("food-log.org")
    │                                          │
    │                                          ▼
    │                                  OrgParser.parse(content, FOOD)
    │                                          │
    │                                          ▼
    │                                  OrgFile { sections: [OrgDateSection] }
    │                                          │
    │                                  section.meals.map { MealGroup }
    │                                          │
    ◄──────────────────────────────────────────┘
    │
    ▼
emit(Success(meals, targets))
    │
    ▼
uiState: StateFlow<FoodLogUiState>
    │
    ▼
FoodLogScreen collects uiState
    │  val state by viewModel.uiState.collectAsStateWithLifecycle()
    │
    ▼
Compose renders MealGroup cards
```

### Code trace

**1. ViewModel kicks off the reactive chain** — [FoodLogViewModel.kt#L85](../app/src/main/java/com/sakura/features/foodlog/FoodLogViewModel.kt#L85):

```kotlin
val uiState: StateFlow<FoodLogUiState> = combine(_selectedDate, _reloadTrigger) { date, _ -> date }
    .flatMapLatest { date ->
        flow {
            emit(FoodLogUiState.Loading)
            val meals = foodRepo.loadDay(date)
            prefsRepo.macroTargets.collect { targets ->
                emit(FoodLogUiState.Success(meals = meals, targets = targets))
            }
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodLogUiState.Loading)
```

**React analogy**:
```javascript
const [uiState, setUiState] = useState({ type: 'loading' });

useEffect(() => {
  setUiState({ type: 'loading' });
  const meals = await foodRepo.loadDay(selectedDate);
  setUiState({ type: 'success', meals, targets: macroTargets });
}, [selectedDate, reloadTrigger]);
```

**2. Repository reads the file and parses it** — [OrgFoodRepository.kt#L50](../app/src/main/java/com/sakura/data/food/OrgFoodRepository.kt#L50):

```kotlin
override suspend fun loadDay(date: LocalDate): List<MealGroup> {
    val content = syncBackend.readFile(FOOD_LOG_FILE)       // read raw text
    val orgFile = OrgParser.parse(content, ParseMode.FOOD)  // parse into AST
    val section = orgFile.sections.find { it.date == date }  // find today's section
    return section.meals.map { meal ->                       // convert to domain models
        MealGroup(label = meal.label, entries = meal.entries.map { it.toFoodEntry() })
    }
}
```

**3. SyncBackend reads from disk** — [LocalStorageBackend.kt#L30](../app/src/main/java/com/sakura/sync/LocalStorageBackend.kt#L30):

```kotlin
override suspend fun readFile(filename: String): String = withContext(ioDispatcher) {
    val file = resolveFile(filename)
    if (!file.exists()) return@withContext ""
    file.readText(Charsets.UTF_8)
}
```

**4. Screen collects the StateFlow** (in the composable):

```kotlin
val state by viewModel.uiState.collectAsStateWithLifecycle()
when (state) {
    is FoodLogUiState.Loading -> LoadingIndicator()
    is FoodLogUiState.Success -> MealList(state.meals, state.targets)
    is FoodLogUiState.Error -> ErrorScreen(...)
}
```

## The Write Path (Screen → Disk)

When you add a food entry:

```
User fills form, taps "Add"
    │
    ▼
FoodLogViewModel.addEntry(mealLabel, entry)
    │ viewModelScope.launch {
    │     foodRepo.addEntry(date, label, entry)
    │     reloadDay()   // bumps _reloadTrigger
    │ }
    │
    ▼
OrgFoodRepository.addEntry(date, mealLabel, entry)
    │ fileMutex.withLock {              // serialize concurrent writes
    │     content = syncBackend.readFile("food-log.org")
    │     orgFile = OrgParser.parse(content)
    │     updatedFile = upsertEntry(orgFile, date, mealLabel, orgEntry)
    │     syncBackend.writeFile("food-log.org", OrgWriter.write(updatedFile))
    │ }
    │
    ▼
File on disk is updated
    │
    ▼
reloadDay() bumps _reloadTrigger
    │
    ▼
flatMapLatest re-fires → loadDay() reads updated file
    │
    ▼
UI recomposes with new entry
```

### Code trace

**1. ViewModel launches the write** — [FoodLogViewModel.kt#L262](../app/src/main/java/com/sakura/features/foodlog/FoodLogViewModel.kt#L262):

```kotlin
fun addEntry(mealLabel: String, entry: FoodEntry) {
    viewModelScope.launch {
        foodRepo.addEntry(_selectedDate.value, mealLabel, entry)
        _lastAddedEntry.value = Pair(entry.id, mealLabel)  // for undo
        reloadDay()
    }
}
```

**2. Repository does read-modify-write** — [OrgFoodRepository.kt#L88](../app/src/main/java/com/sakura/data/food/OrgFoodRepository.kt#L88):

```kotlin
private suspend fun addEntryInternal(date: LocalDate, mealLabel: String, entry: FoodEntry) {
    val content = syncBackend.readFile(FOOD_LOG_FILE)
    val orgFile = if (content.isBlank()) OrgFile(sections = emptyList())
                  else OrgParser.parse(content, OrgParser.ParseMode.FOOD)
    val orgEntry = entry.toOrgFoodEntry()
    val updatedSections = upsertEntry(orgFile.sections, date, mealLabel, orgEntry)
    val updatedFile = orgFile.copy(sections = updatedSections)
    syncBackend.writeFile(FOOD_LOG_FILE, OrgWriter.write(updatedFile))
}
```

This is a **read-modify-write** cycle: read the whole file, parse it, modify the AST, serialize back to text, write the whole file. The `fileMutex` prevents two concurrent writes from stomping on each other.

**React analogy**: Like doing an optimistic update followed by a full refetch:
```javascript
const addEntry = async (mealLabel, entry) => {
  await api.post('/food', { date, mealLabel, entry });
  // refetch to get the updated list
  const meals = await api.get(`/food/${date}`);
  setMeals(meals);
};
```

**3. Reload triggers recomposition** — `reloadDay()` just increments a counter:

```kotlin
private fun reloadDay() {
    _reloadTrigger.value++
    loadCalendar()
}
```

The `combine(_selectedDate, _reloadTrigger)` in `uiState` picks up the change and `flatMapLatest` re-fires the whole read path.

## The Preferences Path

User settings (theme, macro targets, timer duration) use a separate, simpler path through Jetpack DataStore.

```
SettingsScreen / MacroTargetsScreen
    │ onSave click
    │
    ▼
prefsRepo.setMacroTargets(targets)     ← suspend fun, writes to DataStore
    │
    ▼
DataStore persists to sakura_prefs.xml (internal storage)
    │
    ▼
prefsRepo.macroTargets: Flow<MacroTargets>  ← emits new value
    │
    ▼
Any ViewModel collecting this Flow gets the update automatically
    │
    ▼
UI recomposes
```

### Code trace

**Writing** — [AppPreferencesRepository.kt](../app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt):

```kotlin
suspend fun setMacroTargets(targets: MacroTargets) {
    context.appDataStore.edit { prefs ->
        prefs[MACRO_TARGET_CALORIES] = targets.calories
        prefs[MACRO_TARGET_PROTEIN] = targets.protein
        prefs[MACRO_TARGET_CARBS] = targets.carbs
        prefs[MACRO_TARGET_FAT] = targets.fat
    }
}
```

**Reading** — same file:

```kotlin
val macroTargets: Flow<MacroTargets> = context.appDataStore.data
    .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    .map { prefs ->
        MacroTargets(
            calories = prefs[MACRO_TARGET_CALORIES] ?: 2000,
            protein = prefs[MACRO_TARGET_PROTEIN] ?: 150,
            carbs = prefs[MACRO_TARGET_CARBS] ?: 250,
            fat = prefs[MACRO_TARGET_FAT] ?: 65
        )
    }
```

**React analogy**: DataStore is like a typed localStorage that emits change events. The `Flow<MacroTargets>` is like an event listener that fires whenever the value changes — any screen collecting it will auto-update.

## Thread Safety

Concurrent write protection uses Kotlin's `Mutex` (like a lock):

```kotlin
class OrgFoodRepository(...) {
    private val fileMutex = Mutex()

    override suspend fun addEntry(...): Result<Unit> {
        return fileMutex.withLock {  // only one coroutine can be here at a time
            withContext(ioDispatcher) {
                addEntryInternal(date, mealLabel, entry)
                Result.success(Unit)
            }
        }
    }
}
```

This prevents the scenario where two rapid taps both read the file, both modify it, and the second write stomps the first. The mutex serializes all mutations.

**JS analogy**: There's no direct equivalent since JS is single-threaded, but it's similar to queuing promises:
```javascript
let writeQueue = Promise.resolve();
function addEntry(data) {
  writeQueue = writeQueue.then(() => doTheWrite(data));
}
```
