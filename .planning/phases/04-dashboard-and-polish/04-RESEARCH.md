# Phase 4: Dashboard and Polish - Research

**Researched:** 2026-04-12
**Domain:** Jetpack Compose UI — HorizontalPager, Vico charts, Material3 DatePicker, grid calendar, navigation restructure, sync status
**Confidence:** HIGH (core stack), MEDIUM (Vico chart API details)

## Summary

Phase 4 adds a Home screen, restructures navigation to a 4-tab bottom nav, and surfaces analytics (charts, calendar, sync status). All data already exists on disk via org files; this phase is purely read-aggregation and UI. No new data write paths are required.

The critical Compose primitive for the card-pager design is `HorizontalPager` from `androidx.compose.foundation.pager`, which is now stable (part of Compose BOM). Page-indicator dots are hand-rolled from a `Row` of `Box` composables — no library needed.

Chart rendering should use **Vico 3.1.0** (`com.patrykandpatrick.vico:compose-m3:3.1.0`). It provides `CartesianChartHost`, `ColumnCartesianLayer` (grouped bars, with `MergeMode.Grouped`), and `LineCartesianLayer` (trend line). These can be stacked in the same `rememberCartesianChart()` call to produce the combo bar+line volume chart. Data is pushed via `CartesianChartModelProducer.runTransaction {}`. Vico's `compose-m3` module automatically inherits `MaterialTheme.colorScheme` for axis colors; custom per-series colors are set on `rememberColumnCartesianLayer(columns = listOf(...))`.

The training split calendar is a **custom composable** built from nested `Row`/`Column` — no external library. Material3 `DatePicker` / `DatePickerDialog` handles the date-jump picker (still annotated `@ExperimentalMaterial3Api` in some BOM versions, safe to `@OptIn`). Navigation restructure requires adding a `Home` route, wiring a shared `Scaffold` with `NavigationBar` across all four tabs, and creating a `DashboardViewModel` that aggregates data from both existing repositories.

**Primary recommendation:** Add Vico `compose-m3:3.1.0` to dependencies; use `HorizontalPager` (foundation, already on BOM) for cards; keep all other UI as pure Compose Canvas or standard M3 composables. No additional libraries needed.

## Standard Stack

### Core (already in project)

**androidx.compose.foundation pager** — version included in Compose BOM 2025.05.00
- `HorizontalPager`, `PagerState`, `rememberPagerState`
- Already on classpath via `androidx-compose-ui`
- Import: `androidx.compose.foundation.pager.*`

**androidx.compose.material3** — version from Compose BOM 2025.05.00
- `NavigationBar`, `NavigationBarItem`
- `DatePicker`, `DatePickerDialog`, `rememberDatePickerState`
- Already on classpath

**androidx.lifecycle.viewmodel.compose** — 2.9.0 (already in project)
- `viewModel()` factory pattern identical to Phases 2-3

### New Dependency: Vico charts

**com.patrykandpatrick.vico:compose-m3** — version 3.1.0 (latest stable as of April 2026)

The `compose-m3` artifact includes:
- `CartesianChartHost` — the Compose entry point for all chart types
- `rememberCartesianChart()` — assembles layers + axes
- `rememberColumnCartesianLayer()` — bar series
- `rememberLineCartesianLayer()` — line series
- `VerticalAxis.rememberStart()`, `HorizontalAxis.rememberBottom()` — axes
- `CartesianChartModelProducer` — data push via `runTransaction {}`
- M3 theme integration via `VicoTheme` derived from `MaterialTheme.colorScheme`

Add to `libs.versions.toml`:
```toml
[versions]
vico = "3.1.0"

[libraries]
vico-compose-m3 = { group = "com.patrykandpatrick.vico", name = "compose-m3", version.ref = "vico" }
```

Add to `app/build.gradle.kts`:
```kotlin
implementation(libs.vico.compose.m3)
```

### Alternatives Considered

- **Compose Canvas custom charts** — viable for single-series bars but the grouped 3-bar-per-day macro chart requires significant math for bar positioning, hit testing, and animation. Vico provides this out of the box. Use Canvas only if Vico's API proves too constrained.
- **ComposeCharts (ehsannarmani)** — lighter, but less documentation and not M3-aware.
- **Material3 CalendarDateRangePicker** — overkill; single date selection with `DatePickerDialog` is sufficient.

## Architecture Patterns

### Recommended Project Structure

```
features/
├── dashboard/
│   ├── DashboardScreen.kt          # Home tab: two HorizontalPager cards
│   ├── DashboardViewModel.kt       # Aggregates food + workout data for today + week
│   ├── DashboardUiState.kt         # Sealed class with today + weekly analytics
│   ├── FoodProgressCard.kt         # Page 1 of food pager
│   ├── FoodWeeklyCard.kt           # Page 2 of food pager (Vico grouped bar)
│   ├── WorkoutSummaryCard.kt       # Page 1 of workout pager
│   ├── WorkoutVolumeCard.kt        # Page 2 of workout pager (Vico combo bar+line)
│   └── SyncStatusBadge.kt          # Green pill composable
├── workoutlog/
│   └── SplitCalendar.kt            # 4-week rolling calendar (in workout tab)
navigation/
├── AppNavHost.kt                   # Reworked: add Home route, wire 4-tab Scaffold
├── Routes.kt                       # Add: object Home
```

### Pattern 1: Shared Scaffold with NavigationBar

The 4-tab bottom nav requires a **single Scaffold** wrapping all tab screens. The current `AppNavHost` uses flat composable routes with no shared shell. Phase 4 introduces a `MainScaffold` composable that holds `NavigationBar` and delegates content to the selected tab.

```kotlin
// Source: https://developer.android.com/develop/ui/compose/components/navigation-bar
@Composable
fun MainScaffold(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(FoodLog, WorkoutLog, Home, Settings).forEachIndexed { _, route ->
                    NavigationBarItem(
                        selected = currentRoute == route::class.qualifiedName,
                        onClick = {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { /* icon per tab */ },
                        label = { Text("...") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Home, modifier = Modifier.padding(innerPadding)) {
            // all tab routes here
        }
    }
}
```

**Key nav flags:** `launchSingleTop = true` + `restoreState = true` + `saveState = true` prevent back-stack accumulation on tab switches. This is the canonical M3 bottom nav pattern.

### Pattern 2: HorizontalPager Card with Dot Indicators

Each card (food progress, workout summary) contains a `HorizontalPager` with 2 pages. The pager state drives dot indicators rendered in a `Row` overlaid at the bottom of the card.

```kotlin
// Source: https://developer.android.com/develop/ui/compose/layouts/pager
val pagerState = rememberPagerState(pageCount = { 2 })

Box {
    HorizontalPager(state = pagerState) { page ->
        when (page) {
            0 -> TodayContent(...)
            1 -> WeeklyAnalyticsContent(...)
        }
    }
    // Dot indicators
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(2) { i ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (pagerState.currentPage == i) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}
```

### Pattern 3: Vico Grouped Bar Chart (Macro Chart)

Three series (protein, carbs, fat) grouped per day. One `CartesianChartModelProducer` per chart. Data pushed in a `LaunchedEffect` whenever the weekly data changes.

```kotlin
// Source: https://guide.vico.patrykandpatrick.com/compose/cartesian-charts/starter-examples
val modelProducer = remember { CartesianChartModelProducer() }

LaunchedEffect(weeklyData) {
    modelProducer.runTransaction {
        // Three columnSeries calls = three grouped series
        columnSeries { series(*weeklyData.map { it.protein.toFloat() }.toTypedArray()) }
        columnSeries { series(*weeklyData.map { it.carbs.toFloat() }.toTypedArray()) }
        columnSeries { series(*weeklyData.map { it.fat.toFloat() }.toTypedArray()) }
    }
}

CartesianChartHost(
    chart = rememberCartesianChart(
        rememberColumnCartesianLayer(
            columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                rememberLineComponent(color = ProteinGreen, thickness = 12.dp),
                rememberLineComponent(color = CarbsCoral, thickness = 12.dp),
                rememberLineComponent(color = FatBrown, thickness = 12.dp),
            ),
            mergeMode = { ColumnCartesianLayer.MergeMode.Grouped }
        ),
        startAxis = VerticalAxis.rememberStart(),
        bottomAxis = HorizontalAxis.rememberBottom(),
    ),
    modelProducer = modelProducer,
)
```

### Pattern 4: Vico Combo Chart (Volume + Trend)

One `columnSeries` (daily volume, pink bars) plus one `lineSeries` (trend, green line) in the same chart.

```kotlin
val modelProducer = remember { CartesianChartModelProducer() }

LaunchedEffect(volumeData) {
    modelProducer.runTransaction {
        columnSeries { series(*volumeData.map { it.volume.toFloat() }.toTypedArray()) }
        lineSeries { series(*volumeData.map { it.trendValue.toFloat() }.toTypedArray()) }
    }
}

CartesianChartHost(
    chart = rememberCartesianChart(
        rememberColumnCartesianLayer(),  // pink bars (custom color via ColumnProvider)
        rememberLineCartesianLayer(),    // green trend line
        startAxis = VerticalAxis.rememberStart(),
        bottomAxis = HorizontalAxis.rememberBottom(),
    ),
    modelProducer = modelProducer,
)
```

### Pattern 5: DashboardViewModel — Multi-Repository Aggregation

The `DashboardViewModel` reads from both `FoodRepository` and `WorkoutRepository`. It loads today's data on init and computes weekly aggregates separately. Use the same `viewModelScope + SharingStarted.WhileSubscribed(5_000)` pattern established in Phases 2-3.

```kotlin
class DashboardViewModel(
    private val foodRepo: FoodRepository,
    private val workoutRepo: WorkoutRepository,
    private val prefsRepo: AppPreferencesRepository,
    private val syncBackend: SyncBackend
) : ViewModel() {

    private val _today = MutableStateFlow<DashboardTodayState>(DashboardTodayState.Loading)
    val today: StateFlow<DashboardTodayState> = _today.asStateFlow()

    private val _weekly = MutableStateFlow<WeeklyAnalyticsState>(WeeklyAnalyticsState.Empty)
    val weekly: StateFlow<WeeklyAnalyticsState> = _weekly.asStateFlow()

    val syncStatus: StateFlow<SyncStatus> = ...

    fun loadWeekly(weeks: Int) { /* load N weeks of history, aggregate */ }
}
```

Weekly aggregation is pure Kotlin: load all sessions in range with `workoutRepo.loadHistory()` and all food days via multiple `foodRepo.loadDay()` calls (one per date). No new repository methods needed beyond what Phases 2-3 already provide, except possibly a convenience `loadDaysRange(startDate, endDate)` on `FoodRepository`.

### Pattern 6: Training Split Calendar

A 4-week rolling grid. No external library — pure Compose with `Column` of 4 `Row` weeks, each `Row` containing 7 day cells. Compute the 28-day range on the ViewModel side (2 weeks past, current week, 1 week future based on today's date).

```kotlin
@Composable
fun SplitCalendar(
    days: List<CalendarDay>,   // 28 items: date + splitDay? + isComplete + isPast
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        // 7 weekday headers
        Row { DayOfWeek.entries.forEach { Header(it.getDisplayName(...)) } }
        // 4 weeks
        (0 until 4).forEach { week ->
            Row {
                (0 until 7).forEach { dow ->
                    val day = days[week * 7 + dow]
                    CalendarCell(day)
                }
            }
        }
    }
}
```

### Pattern 7: Sync Status Detection

Sync status = filesystem check at startup and on-demand. The `SyncBackend` interface already has `listOrgFiles()` which filters `.sync-conflict` files. Add a method to `SyncBackend` or detect conflicts by calling `listFiles()` on the folder and checking for `.sync-conflict` in filenames. Last-synced timestamp = `File(path, "food-log.org").lastModified()`.

```kotlin
data class SyncStatus(
    val lastSyncedAt: Long?,         // millis, from file.lastModified()
    val hasConflicts: Boolean,       // true if any .sync-conflict file found
    val folderAccessible: Boolean
)
```

The `SyncthingFileBackend` can expose a `checkSyncStatus()` suspend function that calls `listFiles()` on the folder, examines filenames, and reads `lastModified()` of the primary org files.

### Anti-Patterns to Avoid

- **Recreating `CartesianChartModelProducer` on recomposition** — wrap in `remember {}`. Recreating it causes chart flicker and memory churn.
- **Calling `foodRepo.loadDay()` for every date in a loop on the main thread** — use `withContext(Dispatchers.IO)` and batch load all dates in a single coroutine scope on the ViewModel.
- **Placing HorizontalPager inside a vertically scrolling LazyColumn cell** — nested scrollable containers in the same axis cause gesture conflicts. Keep pager at a fixed height with `fillMaxWidth().height(260.dp)`.
- **Using `LazyVerticalGrid` for the split calendar** — lazy grids cannot be embedded inside scrollable parents. Use repeated `Row`s instead (4 rows max, no laziness needed).
- **Tab switches accumulating back stack** — always use `popUpTo(graph.startDestination) { saveState = true }` + `launchSingleTop = true` for tab navigation.

## Don't Hand-Roll

**Grouped bar chart with animation and axis labels**
- Don't build: Custom Canvas chart with `drawRect()` per bar, manual axis rendering
- Use instead: `Vico ColumnCartesianLayer` with `MergeMode.Grouped`
- Why: Bar positioning math across N groups, label collision, animation on data change, accessibility are all handled

**Combo bar+line chart**
- Don't build: Two separate Canvas draws composited
- Use instead: Vico `rememberCartesianChart(rememberColumnCartesianLayer(), rememberLineCartesianLayer())`
- Why: Shared axis scaling between bars and line requires Vico's internal model — impossible to align manually without replicating its layout engine

**Swipeable pager with snapping**
- Don't build: Custom gesture detector with `Offset` tracking
- Use instead: `HorizontalPager` from `androidx.compose.foundation.pager`
- Why: Velocity fling, accessibility, RTL support, keyboard nav

**Calendar date picker dialog**
- Don't build: Custom month grid with touch selection
- Use instead: `DatePickerDialog` + `rememberDatePickerState()` from Material3
- Why: Localization, accessibility, keyboard input mode, RTL

**Key insight:** The analytics domain's most complex primitives (charts) have a production-quality library (Vico). Using Canvas for charts in a fitness app is a common beginner mistake — the grouping math alone would consume 2-3 tasks.

## Common Pitfalls

### Pitfall 1: Navigation Back Stack Accumulation on Tab Switches

**What goes wrong:** User taps FOOD → WORKOUT → FOOD → WORKOUT repeatedly; back button walks through all previous tab destinations instead of exiting the app.
**Why it happens:** Default `navigate()` pushes to back stack every call.
**How to avoid:** Use the canonical M3 tab navigation pattern with `popUpTo(startDestination) { saveState = true }` and `restoreState = true` on every tab click.
**Warning signs:** Pressing back from any tab takes you to a previous tab rather than exiting.

### Pitfall 2: Vico `CartesianChartModelProducer` Created Inside Composition

**What goes wrong:** Chart data disappears or flickers on any recomposition.
**Why it happens:** `CartesianChartModelProducer()` is instantiated fresh each recomposition, discarding previously pushed data.
**How to avoid:** Always `val modelProducer = remember { CartesianChartModelProducer() }`.
**Warning signs:** Chart shows empty briefly whenever parent state changes.

### Pitfall 3: Weekly Data Computed on Every Recomposition

**What goes wrong:** App freezes or ANRs when switching 1W/2W/4W tabs.
**Why it happens:** Multiple `loadDay()` file reads (7-28 org file parses) invoked in a composable body or without `Dispatchers.IO`.
**How to avoid:** Compute aggregates in `DashboardViewModel` on `Dispatchers.IO`, expose as `StateFlow`. Trigger reload only when the `weeks` parameter changes via `LaunchedEffect(selectedWeeks)`.
**Warning signs:** UI thread jank when tapping time-range tabs.

### Pitfall 4: HorizontalPager Height Unspecified

**What goes wrong:** `HorizontalPager` collapses to zero height or causes infinite measurement error when placed inside a `Column` with `fillMaxSize()`.
**Why it happens:** Pager needs a fixed height constraint — it cannot intrinsically size itself when parent is unbounded.
**How to avoid:** Give each card a fixed or `wrapContentHeight()` with a known-bounded parent. Use `Modifier.height(N.dp)` or `aspectRatio()` on the card container.
**Warning signs:** Blank area where card should appear; crash with "Pager requires a bounded constraint".

### Pitfall 5: `@ExperimentalMaterial3Api` Not Opted-In for DatePicker

**What goes wrong:** Build fails with "This declaration is experimental."
**Why it happens:** `DatePickerDialog` and `rememberDatePickerState()` are still annotated `@ExperimentalMaterial3Api` in some BOM versions (including 2025.05.00).
**How to avoid:** Annotate any function calling DatePicker with `@OptIn(ExperimentalMaterial3Api::class)`.
**Warning signs:** Compile-time error at call site.

### Pitfall 6: Sync Conflict Detection Using `listOrgFiles()` (which filters conflicts out)

**What goes wrong:** `SyncthingFileBackend.listOrgFiles()` already filters `.sync-conflict` files out — calling it to detect conflicts will always return "no conflicts".
**Why it happens:** The filter was intentionally added in Phase 1 to hide conflict files from the parser.
**How to avoid:** Implement a separate `checkForConflicts(): Boolean` method on `SyncBackend` that calls `folder.listFiles()` directly and checks for `.sync-conflict` in filenames, bypassing the existing filter.
**Warning signs:** Sync status always shows "Synced" even when conflict files are present.

### Pitfall 7: Grouped Bar Colors Not Applied

**What goes wrong:** All three macro bars in the grouped chart render with the same default M3 primary color.
**Why it happens:** `rememberColumnCartesianLayer()` without `columnProvider` uses a single default column style. M3 theme integration doesn't automatically assign distinct series colors.
**How to avoid:** Use `ColumnCartesianLayer.ColumnProvider.series(col1, col2, col3)` with `rememberLineComponent(color = ...)` for each series explicitly.
**Warning signs:** All bars are the same color (usually M3 primary/purple).

## Code Examples

### HorizontalPager with Dot Indicators

```kotlin
// Source: https://developer.android.com/develop/ui/compose/layouts/pager
@Composable
fun SwipeableCard(modifier: Modifier = Modifier, content1: @Composable () -> Unit, content2: @Composable () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    Box(modifier.height(260.dp)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> content1()
                1 -> content2()
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(2) { i ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == i)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
    }
}
```

### NavigationBar (4 tabs) with State Preservation

```kotlin
// Source: https://developer.android.com/develop/ui/compose/components/navigation-bar
sealed class Tab(val route: Any, val label: String) {
    object Food : Tab(FoodLog, "Food")
    object Workout : Tab(WorkoutLog, "Workout")
    object Home : Tab(com.sakura.navigation.Home, "Home")
    object Settings : Tab(com.sakura.navigation.Settings, "Settings")
}

NavigationBar {
    tabs.forEach { tab ->
        NavigationBarItem(
            selected = currentRoute == tab.route::class.qualifiedName,
            onClick = {
                navController.navigate(tab.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(tab.icon, contentDescription = tab.label) },
            label = { Text(tab.label) }
        )
    }
}
```

### Vico Bar Chart — Minimal Working Example

```kotlin
// Source: https://guide.vico.patrykandpatrick.com/compose/cartesian-charts/starter-examples
val modelProducer = remember { CartesianChartModelProducer() }
LaunchedEffect(data) {
    modelProducer.runTransaction {
        columnSeries { series(*data.toTypedArray()) }
    }
}
CartesianChartHost(
    chart = rememberCartesianChart(
        rememberColumnCartesianLayer(),
        startAxis = VerticalAxis.rememberStart(),
        bottomAxis = HorizontalAxis.rememberBottom(),
    ),
    modelProducer = modelProducer,
)
```

### Material3 DatePicker Dialog

```kotlin
// Source: https://developer.android.com/develop/ui/compose/components/datepickers
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateJumpPicker(
    currentDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = currentDate.toEpochDay() * 86_400_000L
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onDateSelected(LocalDate.ofEpochDay(millis / 86_400_000L))
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = state)
    }
}
```

### Sync Status Data Model

```kotlin
// No external library needed — pure filesystem check
data class SyncStatus(
    val lastSyncedAt: Long?,      // millis epoch from File.lastModified()
    val hasConflicts: Boolean,    // true if *.sync-conflict files exist in folder
    val folderAccessible: Boolean
)

// In SyncthingFileBackend (new method):
suspend fun checkSyncStatus(): SyncStatus = withContext(ioDispatcher) {
    val path = prefsRepo.syncFolderPath.first() ?: return@withContext SyncStatus(null, false, false)
    val folder = File(path)
    if (!folder.exists() || !folder.isDirectory) return@withContext SyncStatus(null, false, false)
    val allFiles = folder.listFiles() ?: return@withContext SyncStatus(null, false, true)
    val hasConflicts = allFiles.any { it.name.contains(".sync-conflict") }
    val lastModified = allFiles
        .filter { it.name.endsWith(".org") && !it.name.contains(".sync-conflict") }
        .maxOfOrNull { it.lastModified() }
    SyncStatus(lastSyncedAt = lastModified, hasConflicts = hasConflicts, folderAccessible = true)
}
```

## State of the Art

- **Accompanist Pager** → deprecated, migrated to `androidx.compose.foundation.pager` (stable since Compose 1.4). Project must NOT use accompanist pager.
- **Navigation Compose 2 type-safe routes** (current) — the project already uses `@Serializable` objects as routes. Phase 4 simply adds a `Home` object to `Routes.kt`. Nav 3 is out of scope per prior decisions.
- **Vico 1.x** → replaced by Vico 2.x and then 3.x. The API changed substantially. Use 3.1.0 only — do not follow any pre-3.0 tutorials.
- **PrimaryTabRow** → replaced by `NavigationBar` per Phase 3 decisions. No tabs above content.

## Open Questions

1. **`FoodRepository` multi-day loading**
   - What we know: `loadDay(date: LocalDate)` exists and reads the full org file per call, parsing all sections to find the one matching date.
   - What's unclear: Loading 28 days for the 4-week calendar would call `loadDay()` 28 times, each parsing the entire file. This may be acceptable (small files) or need a `loadDaysRange()` optimization.
   - Recommendation: Start with 28 individual `loadDay()` calls in a single `withContext(Dispatchers.IO)` block. Profile; add `loadDaysRange()` if noticeably slow.

2. **Vico `ColumnCartesianLayer` per-series color API exact signature**
   - What we know: `ColumnCartesianLayer.ColumnProvider.series(...)` accepts `LineComponent` instances with custom color. `MergeMode.Grouped` controls grouped vs stacked layout.
   - What's unclear: The exact import path for `rememberLineComponent` with color parameter — Vico's package structure changed from 2.x to 3.x. Verify at implementation time against the 3.1.0 artifact.
   - Recommendation: Check `com.patrykandpatrick.vico.compose.component` package. If not found there, check `com.patrykandpatrick.vico.core.component`.

3. **SyncBackend interface extension for conflict detection**
   - What we know: Adding `checkSyncStatus()` to `SyncBackend` interface requires updating `SyncthingFileBackend` and any test doubles.
   - What's unclear: Whether to add it to the interface or keep it as a concrete method only on `SyncthingFileBackend` and call it directly.
   - Recommendation: Add to interface to keep `DashboardViewModel` testable. Simple Boolean return is sufficient: `suspend fun listConflictFiles(): List<String>`.

## Sources

### Primary (HIGH confidence)

- `https://developer.android.com/develop/ui/compose/layouts/pager` — HorizontalPager API, PagerState properties, dot indicator pattern, performance guidance
- `https://developer.android.com/develop/ui/compose/components/navigation-bar` — NavigationBar/NavigationBarItem API, 4-tab pattern, state management
- `https://developer.android.com/develop/ui/compose/components/datepickers` — DatePicker/DatePickerDialog API, ExperimentalMaterial3Api requirement, state management
- `https://guide.vico.patrykandpatrick.com/compose/cartesian-charts/starter-examples` — CartesianChartHost, ColumnCartesianLayer, LineCartesianLayer, ModelProducer pattern
- `https://central.sonatype.com/artifact/com.patrykandpatrick.vico/compose-m3` — confirmed 3.1.0 as latest stable
- Existing project source (`app/build.gradle.kts`, `libs.versions.toml`) — confirmed current BOM and library versions, existing patterns

### Secondary (MEDIUM confidence)

- WebSearch + Maven Central cross-reference: Vico 3.1.0 release date (April 2026), `MergeMode.Grouped` API
- WebSearch + official release notes: `@ExperimentalMaterial3Api` removal in Material3 1.4.0+ (project may not be on that version yet — assume OptIn required)

### Tertiary (LOW confidence)

- Vico `ColumnCartesianLayer.ColumnProvider.series()` exact per-series color API — verified the concept exists from GitHub discussions but exact method signature not fetched from authoritative source. Verify at implementation time.

## Metadata

**Confidence breakdown:**
- Standard stack (pager, nav, date picker): HIGH — all verified from official Android Developer docs
- Vico integration (chart types, data model, M3 module): MEDIUM — documented from official Vico guide + Maven Central; per-series color API is LOW for exact signature
- Architecture patterns (ViewModel aggregation, calendar grid): HIGH — follows established Phase 2-3 patterns
- Pitfalls: HIGH for Compose/Nav pitfalls (official docs), MEDIUM for Vico pitfalls (GitHub discussions)
- Sync status detection: HIGH — pure Java File API, deterministic behavior

**Research date:** 2026-04-12
**Valid until:** 2026-05-12 (30 days — Compose BOM and Vico are stable tracks, low churn)
