---
phase: 03-workout-logging
verified: 2026-04-12T16:08:52Z
status: gaps_found
score: 5/7 must-haves verified
gaps:
  - truth: "The app detects a new personal record and surfaces it with a PR badge"
    status: partial
    reason: "PR dialog fires correctly after addSet, but the isPr flag is never set to true on the SetLog before writing to org. The badge (rendered when set.isPr == true) will never display for newly-logged sets."
    artifacts:
      - path: "app/src/main/java/com/sakura/features/workoutlog/WorkoutLogViewModel.kt"
        issue: "checkForPR() updates _prDetected state for the dialog but never mutates the SetLog.isPr field or re-calls addSet with isPr=true"
      - path: "app/src/main/java/com/sakura/features/workoutlog/SetInputSheet.kt"
        issue: "buildSet() always creates SetLog with isPr=false (default); no mechanism to pass PR status into the set before it is logged"
    missing:
      - "After PR is detected, re-write the set with isPr=true (or pass the flag into addSet before the write)"
      - "Alternatively: mark isPr=true in the addSet call path when checkForPR returns a match"
  - truth: "User can view workout history showing past days with exercises, sets, and metrics"
    status: partial
    reason: "WorkoutHistoryScreen exists and is fully implemented, but there is no UI entry point to navigate to it from WorkoutLogScreen. The onNavigateToHistory callback is declared but never called."
    artifacts:
      - path: "app/src/main/java/com/sakura/features/workoutlog/WorkoutLogScreen.kt"
        issue: "onNavigateToHistory parameter accepted but never invoked — no button, icon, or gesture triggers it within the screen"
    missing:
      - "A navigation entry point (TopAppBar action icon, NavigationBar item, or gesture) in WorkoutLogScreen that calls onNavigateToHistory()"
---

# Phase 3: Workout Logging Verification Report

**Phase Goal:** User can log workouts using a day-based model (mirroring food logging) with template-seeded exercises, incremental per-set writes, extensible exercise categories, a persistent exercise library, PR detection, and all data written to workout-log.org.
**Verified:** 2026-04-12T16:08:52Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

**Truth 1: User can start a workout by selecting a template (split day) and have the exercise list pre-populated**
- Status: VERIFIED
- Evidence: `WorkoutLogScreen` shows `TemplatePickerDialog` when empty state "Start from Template" button is pressed. `WorkoutLogViewModel.loadTemplate(splitDay)` calls `WorkoutTemplates.forDay(splitDay)` and iterates exercises calling `workoutRepo.addExercise()` for each. All 4 split days encoded in `WorkoutTemplates.kt` with full exercise definitions, muscle groups, target sets/reps/hold times.

**Truth 2: When logging sets, weight/reps from the previous session are auto-filled as starting values**
- Status: VERIFIED
- Evidence: `WorkoutLogViewModel` calls `workoutRepo.loadPreviousSetsForExercise(ex.name)` for each exercise after loading the day. `DayExercise.previousSets` is populated and passed to `SetInputSheet` as `prefillSet`. `SetInputSheet` initializes all input field states from `prefillSet` values per category. Pre-fill info row is rendered showing "Pre-filled from last session: X".

**Truth 3: User can log sets per exercise with fields adapting by category (weighted, bodyweight, timed, cardio, stretch)**
- Status: VERIFIED
- Evidence: `SetInputSheet` has exhaustive `when (category)` branches for all 5 `ExerciseCategory` values. WEIGHTED shows weight + unit toggle + reps. BODYWEIGHT shows reps + RPE. TIMED shows hold duration. CARDIO shows duration + optional distance. STRETCH shows duration. `buildSet()` constructs the correct `SetLog` variant per category. `workoutRepo.addSet()` writes immediately.

**Truth 4: The app detects a new personal record and surfaces it with a PR badge**
- Status: PARTIAL — FAILED
- Evidence: `WorkoutLogViewModel.checkForPR()` calls `workoutRepo.findPersonalBest(exerciseName)` and sets `_prDetected` which triggers the PR AlertDialog in `WorkoutLogScreen`. The dialog shows correctly. However, `SetLog.isPr` is always `false` at write time because `buildSet()` in `SetInputSheet` never sets `isPr = true`, and `checkForPR()` does not update the stored set. The `PrBadge` composable in `SetRow` renders only when `set.isPr == true` — which will never be true for freshly logged sets. Historic badges from hand-edited org files would work, but the logging flow never produces them.

**Truth 5: User can view workout history showing past days with exercises, sets, and metrics**
- Status: PARTIAL — FAILED
- Evidence: `WorkoutHistoryScreen` is fully implemented (expandable cards, date, split day, volume, exercise list with sets and set labels per category, PR text). It is registered as a composable at the `WorkoutHistory` route in `AppNavHost`. However, `WorkoutLogScreen` accepts `onNavigateToHistory: () -> Unit` as a parameter but never calls it anywhere in the file — there is no button, TopAppBar action, or gesture that triggers navigation to history. The screen exists but is unreachable from the workout tab.

**Truth 6: User can add exercises from a searchable library and create new custom exercises**
- Status: VERIFIED
- Evidence: `ExercisePickerSheet` shows a search field wired to `ExerciseLibrary.search(query, category)`. Category filter chips cover all 5 categories. `ExerciseListItem` renders name + category + muscle groups with a green add button. `CreateExerciseDialog` collects name + category via dropdown and calls `onCreateExercise`. `WorkoutLogViewModel.createAndAddExercise()` calls `ExerciseLibrary.addUserExercise()`, persists to DataStore via `workoutRepo.saveUserExercises()`, and immediately adds the exercise to the day.

**Truth 7: Each set is written immediately to workout-log.org (incremental, like food logging)**
- Status: VERIFIED
- Evidence: `WorkoutLogViewModel.addSet()` calls `workoutRepo.addSet(date, exerciseId, set)` which resolves to `OrgWorkoutRepository.addSet()`. Implementation uses `fileMutex.withLock { withContext(ioDispatcher) { read → parse → appendSet → writeFile } }` — identical pattern to `OrgFoodRepository.addEntry`. `syncBackend.writeFile(WORKOUT_LOG_FILE, ...)` is called inside the lock on every set logged. No draft/finish lifecycle.

**Score:** 5/7 truths verified

### Required Artifacts

All required files exist and are substantive:

- `data/workout/ExerciseType.kt` — EXISTS, substantive (CARDIO + STRETCH added, toCategory() extension)
- `data/workout/ExerciseCategory.kt` — EXISTS, substantive (5-entry enum with label/displayName)
- `data/workout/ExerciseDefinition.kt` — EXISTS, substantive
- `data/workout/ExerciseLibrary.kt` — EXISTS, substantive (34 built-ins, search(), @Synchronized mutation)
- `data/workout/ExerciseLog.kt` — EXISTS, substantive
- `data/workout/WorkoutRepository.kt` — EXISTS, substantive (97 lines, full interface with incremental methods)
- `data/workout/OrgWorkoutRepository.kt` — EXISTS, substantive (577 lines, full incremental implementation)
- `data/workout/WorkoutSession.kt` — EXISTS, substantive
- `data/workout/WorkoutTemplates.kt` — EXISTS, substantive (4 split days encoded)
- `data/workout/SetLog.kt` — EXISTS, substantive
- `data/workout/PersonalBest.kt` — EXISTS
- `data/workout/SplitDay.kt` — EXISTS
- `features/workoutlog/WorkoutLogUiState.kt` — EXISTS, substantive (DayLoaded, DayExercise, PrNotification)
- `features/workoutlog/WorkoutLogViewModel.kt` — EXISTS, substantive (309 lines, all incremental methods)
- `features/workoutlog/WorkoutLogScreen.kt` — EXISTS, substantive (769 lines, empty+active states, cards, sheets, dialogs)
- `features/workoutlog/SetInputSheet.kt` — EXISTS, substantive (468 lines, all 5 categories)
- `features/workoutlog/ExercisePickerSheet.kt` — EXISTS, substantive (335 lines, search + create)
- `features/workoutlog/WorkoutHistoryScreen.kt` — EXISTS, substantive (259 lines) — ORPHANED (no nav trigger)
- `di/AppContainer.kt` — workoutRepository wired as OrgWorkoutRepository

### Key Link Verification

- WorkoutLogScreen → WorkoutLogViewModel: WIRED (viewModel passed as parameter, all state collected)
- WorkoutLogViewModel → WorkoutRepository: WIRED (addSet, addExercise, removeExercise, removeSet, markComplete all call repo methods)
- WorkoutRepository → workout-log.org: WIRED (fileMutex.withLock { syncBackend.writeFile(WORKOUT_LOG_FILE, ...) })
- loadTemplate → WorkoutTemplates: WIRED (WorkoutTemplates.forDay(splitDay) called, exercises iterated and added)
- ExercisePickerSheet → ExerciseLibrary: WIRED (ExerciseLibrary.search() called in filteredExercises remember block)
- SetInputSheet → addSet: WIRED (onLogSet callback calls viewModel.addSet(), which calls workoutRepo.addSet())
- checkForPR → PrNotification dialog: WIRED (_prDetected set, AlertDialog rendered when non-null)
- checkForPR → SetLog.isPr: NOT WIRED (isPr flag never set to true when PR detected, badge never shows for new sets)
- onNavigateToHistory → WorkoutHistoryScreen: WIRED in AppNavHost, NOT WIRED in WorkoutLogScreen (callback never called)
- AppContainer → OrgWorkoutRepository: WIRED (workoutRepository property set to OrgWorkoutRepository instance)

### Anti-Patterns Found

No stubs, TODOs, or placeholder implementations in any workout feature files. All UI text field `placeholder` parameters are legitimate Android Compose input hint text, not code stubs.

One notable pattern in WorkoutLogScreen line 706: `onClick = { /* Phase 4 */ }` for the HOME navigation bar item. This is expected (Phase 4 placeholder) and does not affect workout logging functionality.

### Human Verification Required

**1. Template loading populates exercises on device**
- Test: Navigate to Workout tab on an empty day, tap "Start from Template", select a split day
- Expected: Exercise cards appear immediately for all exercises in that template
- Why human: Cannot verify live UI rendering or async state flow programmatically

**2. Auto-fill values appear in set sheet from previous session**
- Test: Log a workout with sets, navigate to a later day, tap "+ Add Set" on the same exercise
- Expected: Weight/reps fields are pre-populated with values from the previous session
- Why human: Requires end-to-end org file read-write across two dates

**3. PR dialog fires when a record is broken**
- Test: Log a set heavier than any prior set for that exercise
- Expected: "New Personal Record!" dialog appears after tapping "Log Set"
- Why human: Requires prior history state in workout-log.org to compare against

**4. workout-log.org receives incremental writes**
- Test: Log a set, immediately check the Syncthing folder for workout-log.org
- Expected: The org file contains the set entry at levels 3/4 without needing to "finish" the workout
- Why human: Requires file system inspection of Syncthing folder

## Gaps Summary

Two gaps prevent full goal achievement:

**Gap 1 — PR badge never appears (Truth 4, partial):** The PR notification dialog works correctly. However, the `SetLog.isPr` field is always `false` at write time. The `checkForPR()` function fires after `addSet()` and correctly detects a new personal record, but it only updates `_prDetected` for the transient dialog. It never marks the set itself as a PR before (or after) writing to the org file. As a result, the `PrBadge` composable in exercise cards (`SetRow`) will never render for any set logged via the UI flow, since it requires `set.isPr == true`.

**Gap 2 — History screen unreachable (Truth 5, partial):** `WorkoutHistoryScreen` is complete and correctly implemented. The composable is registered in `AppNavHost`. However, within `WorkoutLogScreen`, the `onNavigateToHistory: () -> Unit` parameter is never invoked. There is no button, icon, or gesture in the workout screen that triggers history navigation. The screen exists but cannot be reached by the user.

These two gaps are independent and can be fixed without touching each other. Gap 1 requires about 10 lines of change (update the set copy with `isPr=true` before or after writing). Gap 2 requires adding a navigation trigger (typically a TopAppBar icon or a NavigationBar item) to call `onNavigateToHistory()`.

---

_Verified: 2026-04-12T16:08:52Z_
_Verifier: Claude (gsd-verifier)_
