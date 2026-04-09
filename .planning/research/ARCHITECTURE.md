# Architecture Research

**Domain:** Personal Android tracking app (food + workout, org-mode file backend, Syncthing sync)
**Researched:** 2026-04-09
**Confidence:** HIGH — Android MVVM + StateFlow patterns verified via official Android Developers docs and Hilt official docs. File access patterns verified via official Android storage documentation. Navigation verified via official Compose navigation docs. Org parser strategy is MEDIUM (no viable maintained Kotlin library; custom parser approach confirmed viable by reviewing available libraries and org-syntax spec).

---

## Recommended Architecture

**Pattern: MVVM + Repository (Interface-backed Sync Layer)**

MVVM is the Google-recommended architecture for Jetpack Compose and fits this app's complexity exactly — single developer, personal tool, no team coordination concerns. ViewModels expose `StateFlow<UiState>` consumed by Composables via `collectAsStateWithLifecycle()`, following Android's unidirectional data flow (UDF) model.

A thin Repository layer sits beneath ViewModels, abstracting the org file backend from the UI. The `SyncBackend` interface wraps filesystem reads and writes, making the Syncthing-managed directory the concrete v1 and a future local server the concrete v2 — a swap requiring no changes to any screen code.

Hilt is the correct DI mechanism on Android. It wires repository interfaces to concrete implementations at compile time, injects them into ViewModels via `@HiltViewModel`, and replaces the manual `AppContainer` approach that would be used in an iOS equivalent.

---

## System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                      UI Layer (Compose)                          │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │  TodayScreen │  │ FoodLogScreen│  │  WorkoutLogScreen    │   │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘   │
│         │                 │                      │               │
├─────────┼─────────────────┼──────────────────────┼──────────────┤
│                      ViewModel Layer                             │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │  TodayVM     │  │  FoodLogVM   │  │  WorkoutLogVM        │   │
│  │ StateFlow<   │  │ StateFlow<   │  │  StateFlow<          │   │
│  │  UiState>    │  │  UiState>    │  │   UiState>           │   │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘   │
│         │                 │                      │               │
├─────────┼─────────────────┼──────────────────────┼──────────────┤
│                      Repository Layer                            │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │  FoodRepository (interface)  WorkoutRepository (interface)│   │
│  │  OrgFoodRepository (impl)    OrgWorkoutRepository (impl)  │   │
│  └────────────────────────────┬──────────────────────────────┘   │
│                               │                                  │
├───────────────────────────────┼──────────────────────────────────┤
│                        Data Layer                                │
│                                                                  │
│  ┌─────────────────────┐    ┌────────────────────────────────┐   │
│  │   OrgParser          │    │   SyncBackend (interface)      │   │
│  │   OrgWriter          │    │   ├─ SyncthingFileBackend      │   │
│  │   (domain models)    │    │   └─ LocalServerBackend (v2)   │   │
│  └─────────────────────┘    └────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────┤
│                     DI Layer (Hilt)                              │
│                                                                  │
│  @HiltAndroidApp Application → @Module bindings →               │
│  @HiltViewModel injection into all ViewModels                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## Component Responsibilities

**TodayScreen / TodayViewModel**
- Responsibility: Drive the home screen — today's food entries, macro totals, today's workout session
- Communicates with: FoodRepository, WorkoutRepository
- State: `StateFlow<TodayUiState>` (sealed class: Loading, Success, Error)
- Owns: today-scoped aggregation, macro summary display state

**FoodLogScreen / FoodLogViewModel**
- Responsibility: Food log history; compose and submit new food entries
- Communicates with: FoodRepository
- State: `StateFlow<FoodLogUiState>`
- Owns: entry form state, food history list, inline editing state

**WorkoutLogScreen / WorkoutLogViewModel**
- Responsibility: Workout history; compose sets/reps/weight for a session
- Communicates with: WorkoutRepository
- State: `StateFlow<WorkoutLogUiState>`
- Owns: active session state (current exercise, set builder), history list

**FoodRepository / WorkoutRepository (interfaces)**
- Responsibility: Single interface for all reads and writes per domain; decouples ViewModels from file format and sync mechanism
- Communicates with: OrgParser (reads), OrgWriter (writes), SyncBackend
- Pattern: Interface with one concrete implementation (`OrgFile[Domain]Repository`); Hilt binds the concrete at compile time

**OrgParser**
- Responsibility: Read an org-mode file string and return typed domain models (FoodEntry, WorkoutSession)
- Communicates with: nothing (pure function — string in, models out)
- Owns: all knowledge of the app's org subset: date headlines, meal/exercise labels, property-like inline text

**OrgWriter**
- Responsibility: Accept a domain model and produce valid org-mode text to append or update
- Communicates with: nothing (pure function — models in, string out)
- Owns: all formatting decisions. What OrgWriter produces is the exact format OrgParser must handle — they are a coupled pair

**SyncBackend (interface)**
- Responsibility: Abstract file I/O — read a file from the Syncthing-managed directory, write back to it
- Protocol surface: `suspend fun readFile(path: String): String`, `suspend fun writeFile(content: String, path: String)`
- Concrete v1: `SyncthingFileBackend` — direct file path access via `java.io.File` / `java.nio.file`, running on `Dispatchers.IO`
- Concrete v2: `LocalServerBackend` — HTTP/socket calls, swapped in via Hilt at app startup
- Note: No Android equivalent of `NSFileCoordinator` is needed. Syncthing is a separate process that does not hold locks on files between sync events — reads and writes from the app do not need coordination primitives beyond dispatching to `Dispatchers.IO`

---

## Recommended Project Structure

```
app/src/main/java/com/origami/
├── OrigamiApplication.kt          # @HiltAndroidApp
│
├── di/
│   ├── RepositoryModule.kt        # @Binds FoodRepository → OrgFoodRepository
│   └── SyncModule.kt              # @Binds SyncBackend → SyncthingFileBackend
│
├── features/
│   ├── today/
│   │   ├── TodayScreen.kt
│   │   └── TodayViewModel.kt
│   ├── food/
│   │   ├── FoodLogScreen.kt
│   │   ├── FoodLogViewModel.kt
│   │   └── FoodEntryForm.kt       # extracted composable for entry form
│   └── workout/
│       ├── WorkoutLogScreen.kt
│       ├── WorkoutLogViewModel.kt
│       └── WorkoutEntryForm.kt
│
├── repositories/
│   ├── FoodRepository.kt          # interface
│   ├── WorkoutRepository.kt       # interface
│   ├── OrgFoodRepository.kt       # concrete: parses food-log.org
│   └── OrgWorkoutRepository.kt    # concrete: parses workout-log.org
│
├── sync/
│   ├── SyncBackend.kt             # interface
│   ├── SyncthingFileBackend.kt    # java.io.File + Dispatchers.IO
│   └── LocalServerBackend.kt      # placeholder for v2
│
├── orgengine/
│   ├── OrgParser.kt               # String → List<domain models>
│   ├── OrgWriter.kt               # domain models → String
│   └── OrgModels.kt               # OrgEntry, OrgDate, internal raw structs
│
├── models/
│   ├── FoodEntry.kt               # protein/carbs/fat/calories, timestamp
│   ├── WorkoutSession.kt          # exercise, sets, reps, weight, date
│   └── MacroSummary.kt            # computed totals for today view
│
└── navigation/
    ├── AppNavHost.kt              # NavHost, route graph
    └── AppDestinations.kt         # @Serializable route data classes
```

### Structure Rationale

- **features/**: Feature-based grouping — screen + ViewModel co-located per feature. Avoids the layer-first ("all ViewModels in one folder") split that makes large apps harder to navigate. Confirmed as current best practice for Compose projects.
- **repositories/**: Interface and concrete in one place. Hilt module (`RepositoryModule`) does the binding. When `LocalServerBackend` is wired in, only `SyncModule` changes — nothing in `features/` changes.
- **sync/**: Isolated from repositories. Repositories do not know they are reading from a Syncthing-managed directory; they call `SyncBackend.readFile()`.
- **orgengine/**: Pure Kotlin, zero Android imports. Can be unit-tested with plain `runTest {}` without needing an emulator or robolectric.
- **di/**: Hilt modules kept in one package to make dependency wiring visible without hunting through feature packages.
- **navigation/**: `NavHost` and route definitions separated from individual screens. Each screen composable receives navigation callbacks as lambdas, not `NavController` directly (per official Compose navigation guidance).

---

## Architectural Patterns

### Pattern 1: Interface-backed Repository with Hilt Binding

**What:** Define `FoodRepository` and `WorkoutRepository` as Kotlin interfaces. Bind concrete implementations via a Hilt `@Module`. Inject into ViewModels via constructor injection.

**When to use:** Always. This is the Android standard and directly enables backend swapping (Syncthing files → local server) and testability (inject a fake repository in tests).

**Trade-offs:** Minimal boilerplate (one `@Binds` line per repository). Pays off immediately when writing unit tests and when the `LocalServerBackend` pivot happens.

**Example:**
```kotlin
// Interface
interface FoodRepository {
    suspend fun fetchAll(): List<FoodEntry>
    suspend fun fetchToday(): List<FoodEntry>
    suspend fun append(entry: FoodEntry)
}

// Concrete implementation
class OrgFoodRepository @Inject constructor(
    private val syncBackend: SyncBackend,
    private val ioDispatcher: CoroutineDispatcher
) : FoodRepository {
    override suspend fun fetchAll(): List<FoodEntry> =
        withContext(ioDispatcher) {
            val content = syncBackend.readFile("food-log.org")
            OrgParser.parse(content).flatMap { it.entries }
        }
}

// Hilt module
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindFoodRepository(impl: OrgFoodRepository): FoodRepository
}
```

---

### Pattern 2: StateFlow UiState in ViewModels

**What:** ViewModels expose `StateFlow<UiState>` using sealed classes. Screens collect with `collectAsStateWithLifecycle()`. Unidirectional data flow: events go up to ViewModel, state flows down to UI.

**When to use:** All ViewModels in this project. This is the current Android standard, replacing the older `LiveData` + `observe` pattern.

**Trade-offs:** Sealed class `UiState` adds one file per feature, but makes Loading/Success/Error states explicit and testable. `collectAsStateWithLifecycle()` stops collection when the app is backgrounded, saving resources.

**Example:**
```kotlin
sealed class TodayUiState {
    object Loading : TodayUiState()
    data class Success(
        val foodEntries: List<FoodEntry>,
        val macros: MacroSummary,
        val workoutSession: WorkoutSession?
    ) : TodayUiState()
    data class Error(val message: String) : TodayUiState()
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val foodRepo: FoodRepository,
    private val workoutRepo: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    fun loadToday() {
        viewModelScope.launch {
            _uiState.value = TodayUiState.Loading
            try {
                val entries = foodRepo.fetchToday()
                val session = workoutRepo.fetchToday()
                _uiState.value = TodayUiState.Success(
                    foodEntries = entries,
                    macros = MacroSummary.from(entries),
                    workoutSession = session
                )
            } catch (e: IOException) {
                _uiState.value = TodayUiState.Error(e.message ?: "Read failed")
            }
        }
    }
}

// In Composable:
@Composable
fun TodayScreen(viewModel: TodayViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // render based on uiState
}
```

---

### Pattern 3: Custom Org Parser (Targeted, Not Full-Featured)

**What:** Write a purpose-built org parser that handles only the format this app produces. Not a general org parser.

**When to use:** This is the only viable approach. Two Kotlin org libraries were evaluated:
- `pmiddend/org-parser` — last commit August 2016, no releases published, effectively abandoned
- `iliayar/kotlin-org-mode` — GPL-3.0 (license conflict risk for personal apps), partial feature implementation, no evidence of Android usage

Neither is appropriate for production use. The custom parser is straightforward because the format is controlled: `OrgWriter` defines what is produced, `OrgParser` handles exactly that subset.

**Org format this app produces:**
```org
* 2026-04-09
** Breakfast
   - Protein: 30g  Carbs: 45g  Fat: 12g  Calories: 412
** Lunch
   - Protein: 22g  Carbs: 60g  Fat: 8g  Calories: 406

* 2026-04-08
...
```

**Parser approach (Kotlin):**
1. Split file content by `\n`
2. Detect level-1 headlines (`^\\* \\d{4}-\\d{2}-\\d{2}`) → date section boundary
3. Detect level-2 headlines (`^\\*\\* `) → meal or exercise label
4. Parse property-like inline text with simple regex for macro values
5. Return `List<DatedSection>` with typed `FoodEntry` / `WorkoutSet` entries

**Trade-offs:** Parser is tightly coupled to the format `OrgWriter` produces — an acceptable constraint for a personal tool. Keeps the parser trivially small and unit-testable without any external dependencies.

---

### Pattern 4: Append-Only Writes with Read-Before-Write

**What:** For every new entry, read the current file content, find-or-create today's date heading in the parsed model, append the new entry, serialize back to a string, write the whole file. Always re-read; never mutate a stale in-memory copy.

**When to use:** Every write. Syncthing may have modified the file since the last app read (e.g., Emacs on desktop added an entry). The file must be read fresh before every write.

**Trade-offs:** Each write is a full file round-trip on `Dispatchers.IO`. Acceptable — plain text org files stay small (a year of 3 meals/day ≈ 110 KB), and this is a single-writer personal app. No file locking or coordination primitives are needed because Syncthing holds no lock on files between sync events.

---

### Pattern 5: Injected Dispatchers for Testability

**What:** Inject `CoroutineDispatcher` instances into repositories and the sync backend via Hilt rather than hardcoding `Dispatchers.IO`. In tests, swap with `UnconfinedTestDispatcher`.

**When to use:** Any class that uses `withContext(...)`. Standard coroutine best practice per official Android guidance.

**Example:**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

// In repository constructor:
class SyncthingFileBackend @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SyncBackend {
    override suspend fun readFile(path: String): String =
        withContext(ioDispatcher) { File(path).readText() }
}
```

---

## Data Flow

### Write Flow (New Entry)

```
User taps "Save Entry"
    ↓
ViewModel.saveEntry(entry)                    ← runs in viewModelScope
    ↓
FoodRepository.append(entry)                  ← suspend fun, Dispatchers.IO
    ↓
SyncBackend.readFile("food-log.org")          ← always read current state first
    ↓
OrgParser.parse(fileContent) → List<DatedSection>
    ↓
Merge: find today section, append new OrgEntry
    ↓
OrgWriter.serialize(List<DatedSection>) → String
    ↓
SyncBackend.writeFile(content, "food-log.org")
    ↓
ViewModel calls loadToday() to refresh uiState
    ↓
StateFlow emits new TodayUiState.Success
    ↓
Compose recomposition
```

### Read Flow (History / Today Dashboard)

```
Screen appears (LaunchedEffect)
    ↓
ViewModel.loadToday()                         ← viewModelScope.launch
    ↓
_uiState.value = TodayUiState.Loading
    ↓
FoodRepository.fetchToday()                   ← Dispatchers.IO
    ↓
SyncBackend.readFile("food-log.org")
    ↓
OrgParser.parse(content) → List<DatedSection>
    ↓
Repository filters to today → List<FoodEntry>
    ↓
_uiState.value = TodayUiState.Success(...)
    ↓
collectAsStateWithLifecycle() triggers recomposition
```

### Backend Swap Flow

```
App startup → OrigamiApplication (@HiltAndroidApp)
    ↓
Hilt reads @Module bindings in SyncModule.kt
    ↓
@Binds SyncthingFileBackend as SyncBackend     ← change this one line to swap
    ↓
All repositories, ViewModels, screens: unchanged
```

---

## File Access: Syncthing Integration

Syncthing runs as a separate Android app (Syncthing-Fork is the current active fork, F-Droid as of March 2026) that manages a shared directory on the device's shared storage. Origami reads and writes org files in that same directory.

**Required permission:**
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
```

**Runtime permission check and request:**
```kotlin
// Check:
if (!Environment.isExternalStorageManager()) {
    // Redirect user to settings:
    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    startActivity(intent)
}
```

**File path resolution:**
Syncthing syncs to a directory the user configures (e.g., `/storage/emulated/0/Sync/origami/`). The app should expose this path as a user-configurable setting (stored in `DataStore<Preferences>`) rather than hardcoding it.

**Key point:** `MANAGE_EXTERNAL_STORAGE` grants direct `java.io.File` access to this path. No Storage Access Framework URI bookmarking is needed. This is a personal sideloaded app — the Google Play policy restriction on this permission does not apply.

**Syncthing-as-sync mechanism:** Origami does not integrate with Syncthing's API at runtime. Syncthing is a background service that keeps the directory in sync with the desktop. Origami simply reads and writes files in that directory. The sync "happens around" the app — no API calls, no awareness needed.

---

## Navigation

**Use `navigation-compose` with type-safe navigation (current stable approach).**

Navigation 3 (`androidx.navigation3:navigation3-*:1.0.1`) released stable in February 2026 but represents a significant API change from `navigation-compose`. For a three-screen app (Today, Food Log, Workout Log) with a bottom navigation bar, `navigation-compose` with type-safe routes is the right choice — lower risk, fully stable, sufficient for this complexity.

```kotlin
// AppDestinations.kt
@Serializable object Today
@Serializable object FoodLog
@Serializable object WorkoutLog

// AppNavHost.kt
@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = Today) {
        composable<Today> { TodayScreen() }
        composable<FoodLog> { FoodLogScreen() }
        composable<WorkoutLog> { WorkoutLogScreen() }
    }
}
```

Bottom nav lives in a `Scaffold(bottomBar = { NavigationBar { ... } })` at the top level, outside the `NavHost`. Each screen composable receives navigation callbacks as lambdas, not the `NavController` directly.

---

## Suggested Build Order

Dependencies between components dictate this sequence:

**1. OrgEngine (parser + writer)**
No dependencies. Pure Kotlin. Build and test with sample org files from the existing Emacs setup. This is the highest-risk unknown — validate that the parser round-trips correctly before building any other layer.

**2. SyncBackend (SyncthingFileBackend)**
Depends only on `java.io.File` and `Dispatchers.IO`. Build and test in isolation: can the app read and write a file in the Syncthing-managed directory? Resolve `MANAGE_EXTERNAL_STORAGE` permission flow and user-configurable path setting here.

**3. Models**
Typed domain structs. No dependencies. Define once OrgEngine internal shapes are understood.

**4. Repositories**
Depends on OrgEngine + SyncBackend + Models. Wire them together. Write a simple integration test: read a real org file → parse → write back → verify round-trip.

**5. Hilt DI modules**
Wire `@Module` bindings for repositories and sync backend. Validate injection at app startup.

**6. Today feature (ViewModel + Screen)**
Highest-value screen. Depends on FoodRepository + WorkoutRepository. Exercises both read and write paths. Build before log history views.

**7. Food Log history screen**
Read path from FoodRepository. Simpler once repository layer is solid.

**8. Workout Log history screen**
Same as Food Log — repository read path, different domain model.

**9. LocalServerBackend (v2, future)**
Implement when the local server pivot is ready. Change one `@Binds` line in `SyncModule.kt`. Nothing else changes.

---

## Anti-Patterns

### Anti-Pattern 1: Caching Org File Content Across Sessions

**What people do:** Load the file once on app start, keep an in-memory model, only re-read on explicit user pull-to-refresh.

**Why it's wrong:** Syncthing may have synced changes from the desktop (Emacs added an entry, edited a workout) while the app was backgrounded or between launches. The in-memory model goes stale. The user opens the app and sees data that does not match what Emacs shows — the core value proposition of a unified system breaks.

**Do this instead:** Always call `SyncBackend.readFile()` when a screen needs fresh data. `LaunchedEffect(Unit)` on screen entry triggers a re-read on every navigation. For a small org file (< 500 KB), this is sub-millisecond on `Dispatchers.IO`.

---

### Anti-Pattern 2: Parsing Org Features You Don't Write

**What people do:** Pull in a general org parser (or write one) that handles all of org-syntax — drawers, clocks, agenda, LaTeX, macros, footnotes.

**Why it's wrong:** No viable maintained Kotlin org parser exists (both evaluated libraries are effectively abandoned). Writing a full org parser is weeks of unnecessary work for features the app will never write.

**Do this instead:** Write a parser that handles exactly what `OrgWriter` produces. Lock the format in the writer; the parser becomes trivial. If `OrgWriter` emits only date headlines and plain text entries, `OrgParser` only needs to handle those.

---

### Anti-Pattern 3: File I/O Directly in ViewModels

**What people do:** Call `File.readText()` or `SyncBackend` directly from a ViewModel.

**Why it's wrong:** Bypasses the repository abstraction. Swapping `SyncthingFileBackend` for `LocalServerBackend` requires touching every ViewModel. Testing ViewModel logic requires real files. This is the single most common architecture mistake on Android.

**Do this instead:** ViewModels call repository methods (suspend functions). Repositories call `SyncBackend`. The ViewModel has no knowledge of how data is stored or where files live.

---

### Anti-Pattern 4: Hardcoding the Sync Directory Path

**What people do:** Hardcode the Syncthing folder path (e.g., `/storage/emulated/0/Sync/origami/`).

**Why it's wrong:** The Syncthing-managed folder location varies per user and per device configuration. Hardcoding breaks every device that uses a different path.

**Do this instead:** Expose the sync directory as a user-configurable setting stored in `DataStore<Preferences>`. Read it at `SyncBackend` construction time. Default to a sensible suggested path but let the user change it.

---

### Anti-Pattern 5: Using `Dispatchers.Main` for File I/O

**What people do:** Read or write files without switching dispatchers, causing ANRs (Application Not Responding) when the file operation blocks the main thread.

**Why it's wrong:** `java.io.File.readText()` is a blocking call. Calling it on `Dispatchers.Main` (the UI thread) blocks the entire UI and triggers an ANR on operations longer than ~5 seconds.

**Do this instead:** All file I/O runs inside `withContext(Dispatchers.IO) { ... }`. Inject the dispatcher to keep it testable. This is non-negotiable.

---

## Integration Points

**Syncthing (sync mechanism)**
- Integration: No runtime API. Syncthing is a background daemon managing a directory. Origami reads/writes files in that directory directly.
- Access: `MANAGE_EXTERNAL_STORAGE` → direct `java.io.File` path access
- User configuration: Sync directory path stored in `DataStore<Preferences>`, settable in app settings
- File conflict note: Syncthing can generate `.sync-conflict` files if both sides write simultaneously. The app should detect and surface these gracefully (show an alert, do not silently overwrite). This is a known Syncthing behavior on Android per community reports.

**Emacs / org-roam (external reader)**
- Integration: None at runtime. Emacs reads the same files from the synced directory on desktop.
- Key requirement: `OrgWriter` must produce valid org-mode syntax. Test output by opening it in Emacs and confirming it parses correctly. This is a file format contract, not a software API.

**DataStore (user settings)**
- Integration: `androidx.datastore:datastore-preferences` — stores sync directory path, any other user preferences
- Replaces `SharedPreferences`; coroutine-native; no Android equivalent of `UserDefaults` needed

**Local Server (v2 sync, future)**
- Integration: HTTP or socket calls wrapped in `LocalServerBackend : SyncBackend`
- Swap point: `SyncModule.kt` `@Binds` binding — one line change
- Nothing in `features/`, `repositories/`, or `orgengine/` changes

---

## Scaling Considerations

This is a single-user personal app on one device. Scaling is not a concern. The relevant "scale" question is org file size over time.

A `food-log.org` with 3 meals/day × 365 days = ~1,095 entries. At roughly 100 bytes per entry, that is ~110 KB after one year. Parsing 110 KB in Kotlin on `Dispatchers.IO` is well under 10 ms. No pagination, lazy loading, caching, or database is needed for this use case.

If org files grow to several MB over multiple years: parse only the last N date sections for history views (scan from file end), cache today's parsed section between writes within the same app session. Both are straightforward optimizations if ever needed — do not build them now.

---

## Sources

- Compose architecture (UDF, StateFlow, collectAsStateWithLifecycle): https://developer.android.com/develop/ui/compose/architecture — HIGH confidence (official Android Developers docs)
- Coroutines best practices (dispatcher injection, viewModelScope, Dispatchers.IO): https://developer.android.com/kotlin/coroutines/coroutines-best-practices — HIGH confidence (official Android Developers docs)
- Hilt dependency injection (@HiltAndroidApp, @HiltViewModel, @Binds): https://developer.android.com/training/dependency-injection/hilt-android — HIGH confidence (official Android Developers docs)
- Navigation with Compose (type-safe routes, NavHost, NavController): https://developer.android.com/develop/ui/compose/navigation — HIGH confidence (official Android Developers docs)
- MANAGE_EXTERNAL_STORAGE (what it grants, how to request, caveats): https://developer.android.com/training/data-storage/manage-all-files — HIGH confidence (official Android Developers docs)
- Navigation 3 stable release (1.0.1, February 2026): https://developer.android.com/jetpack/androidx/releases/navigation3 — HIGH confidence (official release notes)
- kotlin-org-mode library: https://github.com/iliayar/kotlin-org-mode — HIGH confidence (repository inspection; GPL-3.0, partial implementation)
- org-parser library: https://github.com/pmiddend/org-parser — HIGH confidence (repository inspection; last commit August 2016, no releases)
- Syncthing-Fork for Android (active fork, March 2026): https://f-droid.org/packages/com.github.catfriend1.syncthingfork/ — MEDIUM confidence (F-Droid listing, verified active)
- Syncthing conflict file behavior on Android: https://forum.syncthing.net/t/file-conflict-for-every-modification-on-android/19272 — MEDIUM confidence (community forum, corroborated by multiple threads)
- Feature-based folder structure for Compose: https://medium.com/@alborzihoseinali/feature-based-folder-structure-in-jetpack-compose-best-practices-2c3708c8b833 — MEDIUM confidence (community, consistent with official guidance)
- Org-mode syntax specification: https://orgmode.org/worg/org-syntax.html — HIGH confidence (official org-mode project)

---

*Architecture research for: Origami — personal Android food and workout tracking app*
*Researched: 2026-04-09*
