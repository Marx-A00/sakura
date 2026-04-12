---
phase: 04
plan: 02
subsystem: analytics
tags: [vico, charts, dashboard, workout, calendar, weekly-analytics]

dependency-graph:
  requires:
    - "04-01 (DashboardViewModel stub, WeeklyAnalyticsState stub, DashboardScreen pager)"
    - "03-03 (WorkoutLogViewModel, WorkoutLogScreen, WorkoutLogUiState)"
    - "02-01 (FoodRepository.loadDay)"
    - "03-01 (WorkoutRepository.loadHistory)"
  provides:
    - "FoodWeeklyCard: Vico grouped bar chart (protein/carbs/fat per day)"
    - "WorkoutVolumeCard: Vico combo bar+line chart (volume + trend)"
    - "DashboardViewModel.loadWeekly: weekly aggregation with 1W/2W/4W ranges"
    - "SplitCalendar: 4-week rolling grid calendar with split labels and completion"
    - "WorkoutLogViewModel.loadCalendar: 28-day history map"
  affects:
    - "04-03 (settings/polish — no chart changes expected)"

tech-stack:
  added: []
  patterns:
    - "Vico 3.1.0: Fill(SolidColor(color)) for color assignment (constructor is private in Kotlin source)"
    - "Vico 3.1.0: LineCartesianLayer.Line(fill, stroke) direct constructor instead of rememberLine() companion extension"
    - "Vico 3.1.0: ColumnCartesianLayer.MergeMode.Grouped() requires instantiation (not singleton)"
    - "Vico 3.1.0: columnSeries/lineSeries extension on CartesianChartModelProducer.Transaction"
    - "LaunchedEffect(data) + modelProducer.runTransaction for Vico data push"
    - "loadWeekly: isLoading set before coroutine launch for immediate spinner feedback"
    - "SplitCalendar: Column { Row { ... } } grid (not LazyVerticalGrid) for predictable card sizing"

file-tracking:
  created:
    - "app/src/main/java/com/sakura/features/dashboard/FoodWeeklyCard.kt"
    - "app/src/main/java/com/sakura/features/dashboard/WorkoutVolumeCard.kt"
    - "app/src/main/java/com/sakura/features/workoutlog/SplitCalendar.kt"
  modified:
    - "app/src/main/java/com/sakura/features/dashboard/DashboardViewModel.kt"
    - "app/src/main/java/com/sakura/features/dashboard/DashboardUiState.kt"
    - "app/src/main/java/com/sakura/features/dashboard/DashboardScreen.kt"
    - "app/src/main/java/com/sakura/features/workoutlog/WorkoutLogViewModel.kt"
    - "app/src/main/java/com/sakura/features/workoutlog/WorkoutLogUiState.kt"
    - "app/src/main/java/com/sakura/features/workoutlog/WorkoutLogScreen.kt"

decisions:
  - id: "04-02-fill-solidcolor"
    description: "Use Fill(SolidColor(color)) for Vico color assignment — Fill(Color) constructor is internal/private at Kotlin source level despite being public in bytecode; the only public Kotlin-accessible constructor is Fill(Brush)"
  - id: "04-02-line-direct-constructor"
    description: "Use LineCartesianLayer.Line(fill, stroke) direct constructor instead of rememberLine() companion extension — rememberLine is defined as Kotlin extension on Companion but Kotlin 2.x does not resolve it via LineCartesianLayer.rememberLine() syntax"
  - id: "04-02-mergemode-grouped-instance"
    description: "ColumnCartesianLayer.MergeMode.Grouped() must be instantiated — it's a class, not a singleton like Stacked"
  - id: "04-02-calendar-grid-pattern"
    description: "SplitCalendar uses Column+Row grid (not LazyVerticalGrid) — fixed 4×7 layout ensures predictable card height and avoids nested lazy scroll issues"
  - id: "04-02-calendar-in-both-states"
    description: "SplitCalendar appears in both empty and active WorkoutLog states — both convert to LazyColumn with calendar as first item"

metrics:
  duration: "~9 min"
  completed: "2026-04-12"
  tasks-completed: 2
  tasks-total: 2
---

# Phase 4 Plan 02: Weekly Analytics Charts and Split Calendar Summary

**One-liner:** Vico grouped bar chart for weekly macros, combo bar+line chart for workout volume trend, and 4-week training calendar grid — all data aggregated live from org-file repositories.

## What Was Built

### Task 1: Weekly aggregation logic and Vico chart cards

**DashboardViewModel.loadWeekly(weeks: Int)**
- Accepts 1, 2, or 4 (week count). Date range: `today - (weeks*7 - 1)` to `today`.
- Sets `isLoading = true` before launching IO coroutine (immediate spinner feedback).
- Food: `foodRepo.loadDay(date)` per date, summed into `DailyMacros`. Computes `avgProtein/avgCarbs/avgFat` averages.
- Workout: `workoutRepo.loadHistory()` once, filtered to range. 3-day moving average trend with carry-forward for zero-volume days.
- Updates `WeeklyAnalyticsState` with all data + `isLoading = false`.
- Called on `init` with `loadWeekly(1)` (concurrent with `refresh()`).

**DashboardUiState.kt** — added `avgProtein/avgCarbs/avgFat` fields to `WeeklyAnalyticsState`.

**FoodWeeklyCard.kt**
- Loading: `CircularProgressIndicator(color = CherryBlossomPink)`
- Time tabs: `FilterChip` row "1W / 2W / 4W", always visible
- Legend: colored circles + labels (Protein=ForestGreen, Carbs=coral, Fat=WarmBrown)
- Chart: `ColumnCartesianLayer` with 3 series (protein, carbs, fat), `MergeMode.Grouped()`
- Day labels on bottom axis from `dayOfWeek.getDisplayName(NARROW, ...)`
- Averages row: 3 `Surface` chips with "Avg Protein/Carbs/Fat"
- Empty state: "No data for this period"

**WorkoutVolumeCard.kt**
- Same loading/tabs pattern
- Legend: pink square (volume bars) + green line (trend)
- Chart: `ColumnCartesianLayer` (pink bars) + `LineCartesianLayer` (green trend line) in same `CartesianChartHost`
- Empty state: "No workouts this period"

**DashboardScreen.kt** — replaced `ChartPlaceholder` with real chart cards in pager page 2; collects `viewModel.weekly`.

### Task 2: Training split calendar in workout tab

**WorkoutLogUiState.kt** — added `CalendarDay` data class with date/splitDay/splitLabel/isComplete/isPast/isToday.

**WorkoutLogViewModel.loadCalendar()**
- Finds Monday of current week, goes back 3 weeks = 28 days grid.
- Loads all history via `loadHistory()`, builds `date -> WorkoutSession` map.
- Maps to `CalendarDay` list; called on init.

**SplitCalendar.kt**
- `ElevatedCard` wrapper with "Training Calendar" title
- M/T/W/T/F/S/S header row
- 4 rows × 7 columns via `Column { Row { CalendarCell(...) } }`
- `CalendarCell`: date number (circle on today), split label (abbreviated, color-coded), checkmark icon for complete days
- Color coding: lift=CherryBlossomPink, calisthenics=ForestGreen, legs=WarmBrown, other=DeepRose
- Future days at `alpha = 0.4f`

**WorkoutLogScreen.kt** — `SplitCalendar` added as first `LazyColumn` item in both empty and active states.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Vico Fill constructor is internal**

- **Found during:** Task 1 (first build)
- **Issue:** `Fill(Color.value)` — the `Fill(color: Color)` constructor is private at Kotlin source level; only `Fill(Brush)` is public.
- **Fix:** Use `Fill(SolidColor(color))` since `SolidColor` is a `Brush`.
- **Files modified:** FoodWeeklyCard.kt, WorkoutVolumeCard.kt
- **Commit:** e49fa5c

**2. [Rule 1 - Bug] MergeMode.Grouped is a class, not companion singleton**

- **Found during:** Task 1 (first build)
- **Issue:** `{ ColumnCartesianLayer.MergeMode.Grouped }` — Grouped is a class, not an object/companion.
- **Fix:** `{ ColumnCartesianLayer.MergeMode.Grouped() }` — instantiate it.
- **Files modified:** FoodWeeklyCard.kt
- **Commit:** e49fa5c

**3. [Rule 1 - Bug] rememberLine companion extension unresolvable in Kotlin 2.x**

- **Found during:** Task 1 (second build after first fixes)
- **Issue:** `LineCartesianLayer.rememberLine(...)` — Kotlin 2.x does not resolve companion extension functions via this syntax despite `rememberLine` being defined as an extension on `LineCartesianLayer.Companion`.
- **Fix:** Use direct `LineCartesianLayer.Line(fill, stroke)` constructor instead.
- **Files modified:** WorkoutVolumeCard.kt
- **Commit:** e49fa5c

## Decisions Made

- Vico `Fill` creation must use `Fill(SolidColor(color))` — direct color constructor is inaccessible from Kotlin
- `LineCartesianLayer.Line` direct constructor preferred over `rememberLine()` companion extension (Kotlin 2.x resolution issue)
- `ColumnCartesianLayer.MergeMode.Grouped()` must be instantiated
- `SplitCalendar` calendar grid uses `Column { Row }` not `LazyVerticalGrid` to avoid nested scroll + predictable height
- Calendar shown in both empty AND active workout states (both convert `EmptyDayContent` to use `LazyColumn`)

## Next Phase Readiness

- Phase 4 plan 03 (settings + polish) can proceed
- Charts load real data but will show empty state until user has food/workout org files on device
- No blockers
