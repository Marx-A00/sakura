---
phase: 07-rest-timer
plan: 03
subsystem: ui
tags: [android, kotlin, foreground-service, notifications, datastore, coroutines]

# Dependency graph
requires:
  - phase: 07-01
    provides: TimerState sealed interface (Idle/Running/Done), ViewModel timer logic with drift-corrected countdown
  - phase: 07-02
    provides: Timer UI (amber bar, countdown on exercise cards), settings section with timer prefs in MacroTargetsScreen
provides:
  - RestTimerBridge singleton (shared StateFlow between ViewModel and foreground Service)
  - RestTimerService foreground service with shortService type and countdown notification
  - FOREGROUND_SERVICE, FOREGROUND_SERVICE_SHORT_SERVICE, POST_NOTIFICATIONS manifest permissions
  - Background Notification toggle in Settings (off by default) with POST_NOTIFICATIONS runtime permission request
  - ViewModel bridges all timer state transitions to RestTimerBridge
  - WorkoutLogScreen LaunchedEffect manages service start/stop lifecycle
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Shared singleton bridge object (RestTimerBridge) for ViewModel-to-Service state propagation via StateFlow"
    - "shortService foreground type for bounded-duration foreground services (~3 min)"
    - "LaunchedEffect(timerState, bgNotifEnabled) for service lifecycle management in Composable"

key-files:
  created:
    - app/src/main/java/com/sakura/features/workoutlog/RestTimerService.kt
  modified:
    - app/src/main/AndroidManifest.xml
    - app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt
    - app/src/main/java/com/sakura/features/workoutlog/WorkoutLogViewModel.kt
    - app/src/main/java/com/sakura/features/workoutlog/WorkoutLogScreen.kt
    - app/src/main/java/com/sakura/features/settings/MacroTargetsScreen.kt

key-decisions:
  - "RestTimerBridge is a process-level singleton object (not injected) — same process guarantees no null reference"
  - "shortService type chosen over mediaPlayback/camera/etc. — covers all realistic rest durations (up to ~3 min) without requiring ongoing media/sensor permissions"
  - "Service stopped from WorkoutLogScreen (Composable) via LaunchedEffect, not from ViewModel — keeps ViewModel Context-free"
  - "Permission request uses ActivityCompat.requestPermissions (simpler approach) — Activity Result API not needed for this optional opt-in feature"
  - "bgNotifEnabled persisted regardless of permission grant — Android enforces permission at runtime; toggle state can be pre-set by user before granting"

patterns-established:
  - "Shared bridge pattern: process-level singleton with MutableStateFlow for cross-component state sharing without DI"

# Metrics
duration: 3min
completed: 2026-04-13
---

# Phase 7 Plan 03: Rest Timer Foreground Service Summary

**Optional foreground service (RestTimerService) with shortService type shows persistent countdown notification; off-by-default toggle with POST_NOTIFICATIONS permission request on API 33+**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-04-13T18:30:04Z
- **Completed:** 2026-04-13T18:33:00Z
- **Tasks:** 2
- **Files modified:** 5 (+ 1 created)

## Accomplishments

- RestTimerBridge singleton provides shared StateFlow for ViewModel-to-Service communication without DI complexity
- RestTimerService foreground service observes bridge, updates notification each tick, stops itself on Done/Idle/timeout
- Settings "Background Notification" toggle with POST_NOTIFICATIONS permission request on Android 13+, off by default
- ViewModel bridges all timer transitions (Running/Done/Idle) to RestTimerBridge alongside existing _timerState
- WorkoutLogScreen manages service lifecycle via LaunchedEffect keyed on timerState + bgNotifEnabled

## Task Commits

Each task was committed atomically:

1. **Task 1: Create RestTimerService with shared state and manifest registration** - `140f41c` (feat)
2. **Task 2: Wire service start/stop to ViewModel and add settings toggle** - `5291ef9` (feat)

**Plan metadata:** (to be added in docs commit)

## Files Created/Modified

- `app/src/main/java/com/sakura/features/workoutlog/RestTimerService.kt` - RestTimerBridge singleton + RestTimerService foreground service
- `app/src/main/AndroidManifest.xml` - FOREGROUND_SERVICE, FOREGROUND_SERVICE_SHORT_SERVICE, POST_NOTIFICATIONS permissions + service declaration
- `app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt` - TIMER_BG_NOTIFICATION DataStore key, timerBgNotification flow, setTimerBgNotification setter
- `app/src/main/java/com/sakura/features/workoutlog/WorkoutLogViewModel.kt` - RestTimerBridge.update() calls mirroring _timerState, bgNotificationEnabled StateFlow
- `app/src/main/java/com/sakura/features/workoutlog/WorkoutLogScreen.kt` - bgNotifEnabled collection, LaunchedEffect for service start/stop
- `app/src/main/java/com/sakura/features/settings/MacroTargetsScreen.kt` - "Background Notification" toggle with helper text and permission handling

## Decisions Made

- **RestTimerBridge as process-level singleton:** Using an `object` (not injected) is safe because ViewModel and Service run in the same process. Avoids DI boilerplate for a single data path.
- **shortService type:** Covers all realistic rest periods (max ~3 min). Avoids the ongoing-task permissions required by other foreground service types. System calls `onTimeout()` gracefully — service calls `stopSelf()`.
- **Service lifecycle in Composable:** LaunchedEffect in WorkoutLogScreen starts/stops the service. ViewModel has no Context dependency, consistent with Phase 7 decision from Plan 01.
- **Permission approach:** `ActivityCompat.requestPermissions` used over Activity Result API. This is an opt-in feature with minimal permission flow; the simpler approach is appropriate. Android enforces permission at runtime — the toggle persisting before permission grant is intentional UX (user can pre-configure).
- **bgNotifEnabled persisted regardless of permission result:** Service silently fails to show notification if permission is denied (Android system enforces this). User can re-enable notification in system settings without needing to touch the in-app toggle again.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None - build succeeded on first attempt for both tasks.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 7 (Rest Timer / WORK-07) is fully complete:
- Plan 01: Timer foundation (TimerState, ViewModel countdown, vibration/sound)
- Plan 02: Timer UI (amber app bar, countdown on exercise cards, TimerAdjustSheet, auto-start, settings)
- Plan 03: Foreground service (background notification, opt-in toggle, permission handling)

All v1 phases complete. No blockers for final testing and release prep.

---
*Phase: 07-rest-timer*
*Completed: 2026-04-13*
