---
phase: 07-rest-timer
plan: 01
subsystem: workout
tags: [android, kotlin, coroutines, datastore, vibration, timer, stateflow]

# Dependency graph
requires:
  - phase: 03-workout-log
    provides: WorkoutLogViewModel, AppPreferencesRepository, ExerciseLibrary, OrgWorkoutRepository
  - phase: 06-fix-data-bugs
    provides: clean org parsing, templateName and isPr round-trip fixes

provides:
  - TimerState sealed interface (Idle, Running, Done) in WorkoutLogUiState.kt
  - timerState StateFlow and activeTimerExerciseId StateFlow on WorkoutLogViewModel
  - startTimer() with wall-clock drift correction, dismissTimer(), adjustTimer()
  - LibraryExercise.restSecs: Int? = null per-exercise rest duration field
  - SerializableLibraryExercise.restSecs round-trip through toSerializable()/toLibraryExercise()
  - TIMER_ENABLED, TIMER_AUTO_START, TIMER_NOTIFICATION_TYPE DataStore keys with flows and setters
  - VIBRATE permission in AndroidManifest.xml

affects:
  - 07-02 (rest timer UI — reads timerState, calls startTimer/dismissTimer/adjustTimer)
  - 07-03 (foreground service — extends timer lifecycle to background)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Drift-corrected countdown: endMs = System.currentTimeMillis() + duration; loop with remainingMs = endMs - now()"
    - "Job-cancel-before-reuse: timerJob?.cancel() before launching new coroutine prevents Job leak"
    - "Best-effort system services: try/catch wraps Vibrator and RingtoneManager calls"
    - "Context passed from Composable layer (LocalContext.current) for system services in ViewModel"

key-files:
  created: []
  modified:
    - app/src/main/java/com/sakura/features/workoutlog/WorkoutLogUiState.kt
    - app/src/main/java/com/sakura/features/workoutlog/WorkoutLogViewModel.kt
    - app/src/main/java/com/sakura/data/workout/ExerciseLibrary.kt
    - app/src/main/java/com/sakura/data/workout/OrgWorkoutRepository.kt
    - app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt
    - app/src/main/AndroidManifest.xml

key-decisions:
  - "TimerState hosted in WorkoutLogViewModel (not a separate ViewModel) — timer is tightly scoped to the workout log screen"
  - "Context passed as parameter to startTimer/adjustTimer — avoids storing Application context in ViewModel field"
  - "200ms polling interval in countdown loop — smooth enough for countdown display without excessive CPU"
  - "2.5s Done state dwell before returning to Idle — gives user time to see completion before UI resets"
  - "coerceAtLeast(5) in adjustTimer — prevents timer being reduced to 0 or negative seconds"
  - "VIBRATE is install-time permission — no runtime dialog needed, declared in manifest only"
  - "timerNotificationType defaults to VIBRATION — most useful default for gym environment"

patterns-established:
  - "Timer deviation from plan: auto-start wiring deferred to Plan 02 (alongside UI) — not wired in addSet() yet"

# Metrics
duration: 2min
completed: 2026-04-13
---

# Phase 7 Plan 01: Rest Timer Foundation Summary

**TimerState sealed interface + drift-corrected coroutine countdown in WorkoutLogViewModel with per-exercise restSecs field, DataStore timer preference keys, and VIBRATE manifest permission**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-13T18:17:49Z
- **Completed:** 2026-04-13T18:19:57Z
- **Tasks:** 2/2
- **Files modified:** 6

## Accomplishments

- TimerState sealed interface (Idle/Running/Done) establishes the timer data model that Plan 02 UI will observe
- Wall-clock drift-corrected countdown (endMs - System.currentTimeMillis()) avoids the 1-second-per-minute drift that naive delay(1000) counting produces
- Per-exercise rest duration field (LibraryExercise.restSecs) round-trips through serialization with null default for backward compat
- Three DataStore preference keys for master toggle, auto-start, and notification type — all with sensible defaults

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend data models and preferences for rest timer** - `e83c8d1` (feat)
2. **Task 2: Add TimerState model and drift-corrected countdown to ViewModel** - `29edbea` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `app/src/main/java/com/sakura/features/workoutlog/WorkoutLogUiState.kt` - Added TimerState sealed interface at end of file
- `app/src/main/java/com/sakura/features/workoutlog/WorkoutLogViewModel.kt` - Added timer state fields, startTimer/dismissTimer/adjustTimer/triggerCompletionFeedback methods
- `app/src/main/java/com/sakura/data/workout/ExerciseLibrary.kt` - Added restSecs: Int? = null to LibraryExercise
- `app/src/main/java/com/sakura/data/workout/OrgWorkoutRepository.kt` - Extended SerializableLibraryExercise with restSecs, updated round-trip mappings
- `app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt` - Added TIMER_ENABLED/TIMER_AUTO_START/TIMER_NOTIFICATION_TYPE keys, flows, and setters
- `app/src/main/AndroidManifest.xml` - Added VIBRATE permission

## Decisions Made

- TimerState hosted in WorkoutLogViewModel (not a separate ViewModel) — timer is tightly scoped to workout log screen, no reason to separate
- Context passed as parameter to startTimer/adjustTimer rather than storing Application context as a ViewModel field — cleaner lifecycle
- 200ms polling interval in countdown loop — smooth display without excessive CPU usage
- 2.5s Done state dwell before returning to Idle — gives user time to read "Done" before UI resets
- coerceAtLeast(5) in adjustTimer — prevents timer being reduced to 0 or negative via repeated minus taps
- timerNotificationType defaults to "VIBRATION" — most reliable feedback in noisy gym environment

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All Plan 02 (UI) dependencies are satisfied: timerState StateFlow, activeTimerExerciseId StateFlow, startTimer/dismissTimer/adjustTimer API
- All Plan 03 (foreground service) dependencies are satisfied: VIBRATE permission declared, timerNotificationType preference available
- auto-start wiring (calling startTimer from addSet) is intentionally deferred to Plan 02 per the plan spec

---
*Phase: 07-rest-timer*
*Completed: 2026-04-13*
