---
phase: 04-dashboard-and-polish
plan: 01
subsystem: ui
tags: [dashboard, navigation, compose-pager, syncthing, viewmodel, datepicker, vico]

# Dependency graph
requires:
  - phase: 03-workout-logging
    provides: WorkoutRepository, WorkoutLogViewModel, WorkoutLogScreen, WorkoutHistoryScreen
  - phase: 02-food-logging
    provides: FoodRepository, FoodLogViewModel, FoodLogScreen
  - phase: 01-foundation
    provides: SyncBackend, AppContainer, AppPreferencesRepository, Routes

provides:
  - DashboardScreen with HorizontalPager food + workout cards and dot indicators
  - DashboardViewModel aggregating FoodRepository + WorkoutRepository + SyncBackend
  - DashboardUiState (DashboardTodayState, WeeklyAnalyticsState stubs)
  - SyncStatusBadge (green/amber/orange pill with conflict detection)
  - FoodProgressCard (calories + macro progress bars + recent 3 days)
  - WorkoutSummaryCard (template name + exercises + recent 3 sessions)
  - MainScaffold with shared NavigationBar across all 4 tabs
  - Home route as startDestination (post-onboarding)
  - Onboarding completion navigates to Home
  - navigateToDate() on WorkoutLogViewModel for DatePicker support

affects:
  - 04-02-weekly-analytics (WeeklyAnalyticsState stub ready, chart placeholders on page 2)
  - 04-03-workout-history (WorkoutHistoryScreen already wired in nav)

# Tech tracking
tech-stack:
  added:
    - vico 3.1.0 (com.patrykandpatrick.vico:compose-m3) — chart library for Plan 02
    - compileSdk bumped to 36 (required by vico 3.1.0)
    - gradle.properties created with Xmx2048m (required for vico dex merge)
  patterns:
    - MainScaffold pattern: single Scaffold with shared bottomBar wrapping all tab NavHost routes
    - HorizontalPager with rememberPagerState for swipeable dashboard cards
    - DashboardViewModel loads all today's data eagerly on init with refresh() method
    - SyncStatus data class in SyncBackend.kt for shared use across features

key-files:
  created:
    - app/src/main/java/com/sakura/features/dashboard/DashboardScreen.kt
    - app/src/main/java/com/sakura/features/dashboard/DashboardViewModel.kt
    - app/src/main/java/com/sakura/features/dashboard/DashboardUiState.kt
    - app/src/main/java/com/sakura/features/dashboard/FoodProgressCard.kt
    - app/src/main/java/com/sakura/features/dashboard/WorkoutSummaryCard.kt
    - app/src/main/java/com/sakura/features/dashboard/SyncStatusBadge.kt
    - gradle.properties
  modified:
    - app/src/main/java/com/sakura/navigation/AppNavHost.kt
    - app/src/main/java/com/sakura/navigation/Routes.kt
    - app/src/main/java/com/sakura/sync/SyncBackend.kt
    - app/src/main/java/com/sakura/sync/SyncthingFileBackend.kt
    - app/src/main/java/com/sakura/features/foodlog/FoodLogScreen.kt
    - app/src/main/java/com/sakura/features/workoutlog/WorkoutLogScreen.kt
    - app/src/main/java/com/sakura/features/workoutlog/WorkoutLogViewModel.kt
    - gradle/libs.versions.toml
    - app/build.gradle.kts

key-decisions:
  - "compileSdk 36 required by vico 3.1.0 — targetSdk stays 35; no runtime behavior change"
  - "Xmx2048m in gradle.properties required to prevent dex merge OOM when vico is included"
  - "SyncStatus data class placed in SyncBackend.kt (same file as interface) — single import for consumers"
  - "Settings tab shown in NavigationBar while on Settings screen — user can see where they are"
  - "FoodLogScreen onNavigateToSettings parameter removed — settings now accessed via shared nav bar"

patterns-established:
  - "MainScaffold: Scaffold at AppNavHost level with showBottomBar derived from current destination"
  - "isTabDestination() extension on NavDestination? — clean nav bar visibility control"
  - "DashboardViewModel.refresh() pattern: one-shot coroutine loading all today's data on Dispatchers.IO"

# Metrics
duration: 8min
completed: 2026-04-12
---

# Phase 4 Plan 1: Home Screen with Navigation Restructure Summary

**Home/Dashboard screen with today's macro + workout summary in swipeable HorizontalPager cards, shared 4-tab NavigationBar via MainScaffold, SyncStatusBadge with conflict detection, and DatePicker on workout date titles**

## Performance

- **Duration:** 8 min
- **Started:** 2026-04-12T17:27:28Z
- **Completed:** 2026-04-12T17:35:31Z
- **Tasks:** 3/3 auto tasks
- **Files modified:** 14

## Accomplishments

- DashboardScreen renders with food progress card and workout summary card in HorizontalPager with dot indicators and chart placeholders on page 2
- Navigation restructured: single MainScaffold with shared NavigationBar eliminates per-screen nav bar duplication; Home is now startDestination
- SyncStatusBadge detects Syncthing conflicts by calling `folder.listFiles()` directly (not `listOrgFiles()` which filters .sync-conflict files)

## Task Commits

1. **Task 1: Data layer and ViewModel foundation** - `5d30ee3` (feat)
2. **Task 2: Navigation restructure and per-screen cleanup** - `9866464` (feat)
3. **Task 3: DashboardScreen UI with pager cards, sync badge, and DatePicker** - `be71576` (feat)

## Files Created/Modified

- `features/dashboard/DashboardScreen.kt` — Home screen with HorizontalPager cards, greeting, sync badge
- `features/dashboard/DashboardViewModel.kt` — Aggregates food + workout + sync for today
- `features/dashboard/DashboardUiState.kt` — DashboardTodayState, WeeklyAnalyticsState stub
- `features/dashboard/FoodProgressCard.kt` — Calorie number, macro progress bars, recent 3 days
- `features/dashboard/WorkoutSummaryCard.kt` — Template name, exercise list, status badge, recent workouts
- `features/dashboard/SyncStatusBadge.kt` — Green/amber/orange pill with conflict detection
- `navigation/AppNavHost.kt` — Rewritten with MainScaffold, shared NavigationBar, Home startDestination
- `navigation/Routes.kt` — Added Home route
- `sync/SyncBackend.kt` — Added SyncStatus data class and checkSyncStatus() interface method
- `sync/SyncthingFileBackend.kt` — Implemented checkSyncStatus() with conflict file detection
- `features/foodlog/FoodLogScreen.kt` — Removed bottomBar and onNavigateToWorkout/onNavigateToSettings params
- `features/workoutlog/WorkoutLogScreen.kt` — Removed WorkoutBottomNav, added DatePickerDialog
- `features/workoutlog/WorkoutLogViewModel.kt` — Added navigateToDate(LocalDate)
- `gradle/libs.versions.toml` + `app/build.gradle.kts` — Added vico 3.1.0, compileSdk 36

## Decisions Made

- **compileSdk 36**: Vico 3.1.0 requires compileSdk 36. Bumped from 35. targetSdk stays 35 so no runtime behavior change.
- **gradle.properties Xmx2048m**: Default 512MiB daemon heap causes OOM during vico dex merge. Created gradle.properties with `-Xmx2048m`.
- **SyncStatus in SyncBackend.kt**: Co-locating the data class with its interface avoids a separate file and makes the import obvious for consumers.
- **onNavigateToSettings removed from FoodLogScreen/WorkoutLogScreen**: These params were only used by the per-screen NavigationBar which is now removed. Settings is accessed via the shared MainScaffold NavigationBar.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Bumped compileSdk to 36 and added gradle.properties**

- **Found during:** Task 1 (Vico dependency)
- **Issue:** Vico 3.1.0 requires compileSdk >= 36. Build failed with `checkDebugAarMetadata`. Then dex merge OOM with default 512MiB gradle heap.
- **Fix:** Bumped `compileSdk = 36` in `app/build.gradle.kts`. Created `gradle.properties` with `org.gradle.jvmargs=-Xmx2048m`.
- **Files modified:** `app/build.gradle.kts`, `gradle.properties` (new)
- **Verification:** `assembleDebug` succeeds after stopping daemon to pick up new heap settings
- **Committed in:** `5d30ee3` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (blocking)
**Impact on plan:** Required to include vico dependency as specified. compileSdk 36 is forward-compatible. No scope creep.

## Issues Encountered

None beyond the vico heap issue documented above.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- WeeklyAnalyticsState stub is ready in DashboardUiState.kt; Plan 02 populates macroData/volumeData
- Chart page placeholders exist on page 2 of each pager; Plan 02 replaces them with Vico charts
- DashboardViewModel.loadWeekly(weeks) stub is ready for Plan 02 to implement
- WorkoutHistoryScreen is already wired in nav; Plan 03 can enhance it

---
*Phase: 04-dashboard-and-polish*
*Completed: 2026-04-12*
