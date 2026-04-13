---
phase: 07-rest-timer
plan: 02
subsystem: ui
tags: [compose, rest-timer, bottom-sheet, datastore, workout-log, settings]

# Dependency graph
requires:
  - phase: 07-01
    provides: TimerState sealed interface, startTimer/adjustTimer/dismissTimer in ViewModel, DataStore timer prefs, VIBRATE permission

provides:
  - Amber app bar (Color 0xFFE65100) when TimerState.Running or Done
  - Countdown text (M:SS / "Done") on active exercise card header
  - TimerAdjustSheet bottom sheet with quick-adjust buttons and free-form input
  - Auto-start timer after set logging via PendingTimerStart signal pattern
  - Rest Timer settings section in MacroTargetsScreen (toggle, auto-start, duration, notif type)

affects: [07-03, future workout UI work]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - PendingTimerStart signal: ViewModel emits signal, UI LaunchedEffect consumes it with Context — keeps ViewModel Context-free
    - Timer UI state drives TopAppBar color via when(timerState) expression
    - FilterChip for notification type selection (4 options, selected = primaryContainer)

key-files:
  created:
    - app/src/main/java/com/sakura/features/workoutlog/TimerAdjustSheet.kt
  modified:
    - app/src/main/java/com/sakura/features/workoutlog/WorkoutLogScreen.kt
    - app/src/main/java/com/sakura/features/workoutlog/WorkoutLogViewModel.kt
    - app/src/main/java/com/sakura/features/settings/MacroTargetsScreen.kt

key-decisions:
  - "PendingTimerStart signal pattern: ViewModel signals UI via StateFlow<PendingTimerStart?>, UI LaunchedEffect calls startTimer with Context — preserves ViewModel Context-free design"
  - "Countdown clickable on ExerciseCard opens TimerAdjustSheet — tap target colocated with the timer display"
  - "Default rest duration field saves live on change (not only on blur) — simpler UX for a single numeric field"

patterns-established:
  - "PendingTimerStart: when ViewModel needs UI to perform a side-effect requiring Context, emit to a nullable StateFlow and clear after consumption"

# Metrics
duration: 3min
completed: 2026-04-13
---

# Phase 7 Plan 02: Rest Timer UI Summary

**Amber TopAppBar + countdown on exercise card + TimerAdjustSheet bottom sheet + auto-start wiring after set logging + Rest Timer settings section with 4 controls**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-04-13T18:23:35Z
- **Completed:** 2026-04-13T18:26:48Z
- **Tasks:** 2
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments

- TopAppBar turns Color(0xFFE65100) deep amber with white icons/text when timer is Running or Done
- Countdown text ("1:30" / "Done") appears on the active exercise card header; tapping opens TimerAdjustSheet
- TimerAdjustSheet provides four quick-adjust buttons (-30s/-15s/+15s/+30s), free-form seconds input with "Set", "Dismiss Timer" (error color), and "OK" (close without change)
- Auto-start timer after `addSet()` using PendingTimerStart StateFlow signal — ViewModel stays Context-free, UI LaunchedEffect consumes the signal and calls `startTimer()`
- Rest Timer settings section in MacroTargetsScreen: master enable toggle, auto-start toggle, default duration field (5-600s, live save), completion alert FilterChip selector (VIBRATION/SOUND/BOTH/NONE)

## Task Commits

1. **Task 1: Amber app bar, countdown, timer adjust sheet, auto-start wiring** - `e31a782` (feat)
2. **Task 2: Rest Timer settings section in MacroTargetsScreen** - `475e468` (feat)

**Plan metadata:** (docs commit below)

## Files Created/Modified

- `app/src/main/java/com/sakura/features/workoutlog/TimerAdjustSheet.kt` - New ModalBottomSheet for timer adjustment
- `app/src/main/java/com/sakura/features/workoutlog/WorkoutLogScreen.kt` - Amber app bar, countdown in ExerciseCard, TimerAdjustSheet render, LaunchedEffect for auto-start
- `app/src/main/java/com/sakura/features/workoutlog/WorkoutLogViewModel.kt` - PendingTimerStart data class + StateFlow, auto-start logic in addSet()
- `app/src/main/java/com/sakura/features/settings/MacroTargetsScreen.kt` - Rest Timer section with Switch toggles, duration field, FilterChip notif selector

## Decisions Made

- **PendingTimerStart signal pattern:** `addSet()` in ViewModel has no Context for vibration. Instead of threading Context through, ViewModel emits `PendingTimerStart?` state. UI collects it via `LaunchedEffect(pendingTimer)` and calls `startTimer()` with the local Context. Pattern keeps architectural boundary clean.
- **Countdown clickable on ExerciseCard:** The countdown text itself is the tap target for opening TimerAdjustSheet — user naturally looks at the timer and can tap it directly.
- **Live-save for duration field:** Default rest duration saves on every keystroke (not only on unfocus). Simpler than managing focus state; DataStore writes are cheap.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None - build succeeded first try on both tasks.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 07-03 (foreground service / persistent notification) can proceed: all UI wiring is in place
- The timer engine (07-01) and UI (07-02) are both done; 07-03 adds the persistent notification channel
- No blockers

---
*Phase: 07-rest-timer*
*Completed: 2026-04-13*
