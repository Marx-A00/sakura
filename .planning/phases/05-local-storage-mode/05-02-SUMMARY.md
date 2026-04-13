---
phase: 05-local-storage-mode
plan: "02"
subsystem: ui
tags: [android, kotlin, jetpack-compose, onboarding, settings, migration, material3]

# Dependency graph
requires:
  - phase: 05-local-storage-mode
    provides: LocalStorageBackend, StorageMode enum, setStorageMode(), mode-aware AppContainer
  - phase: 01-foundation
    provides: OnboardingViewModel, OnboardingScreen, AppPreferencesRepository
provides:
  - Welcome + ModeSelection onboarding screens with playful copy
  - Local mode instant-complete path (no permissions, no folder selection)
  - Syncthing mode path preserved (permission + folder flow)
  - Settings Storage section showing current mode with Change button
  - Bidirectional migration (Syncthing→Local copies files; Local→Syncthing re-onboards)
  - clearOnboardingComplete() method on AppPreferencesRepository
affects:
  - Any future onboarding changes (new screens insert after ModeSelection)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "OnboardingViewModel init checks pre-set StorageMode to skip Welcome/ModeSelection on re-onboarding"
    - "Migration via ComponentActivity.recreate() — forces full AppContainer reconstruction"
    - "OutlinedCard tap actions for mode selection — one-tap, no toggle state"

key-files:
  created: []
  modified:
    - app/src/main/java/com/sakura/features/onboarding/OnboardingViewModel.kt
    - app/src/main/java/com/sakura/features/onboarding/OnboardingScreen.kt
    - app/src/main/java/com/sakura/features/settings/MacroTargetsScreen.kt
    - app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt

key-decisions:
  - "DisposableEffect for permission checking guarded behind Syncthing path states only"
  - "clearOnboardingComplete() added to AppPreferencesRepository for Local→Syncthing migration"
  - "Migration uses ComponentActivity.recreate() — simplest path to full container reconstruction"

patterns-established:
  - "OnboardingViewModel init block detects pre-set mode for re-onboarding skip"
  - "Settings migration pattern: change prefs, recreate activity, let onboarding/container handle the rest"

# Metrics
duration: 8min
completed: 2026-04-13
---

# Phase 5 Plan 02: Onboarding + Settings Storage Summary

**Welcome/ModeSelection onboarding screens with playful copy, Settings storage section with bidirectional migration, device-verified on Galaxy S21 FE**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-04-13T01:00:00Z
- **Completed:** 2026-04-13T01:08:00Z
- **Tasks:** 3 (2 auto + 1 checkpoint)
- **Files modified:** 4

## Accomplishments

- Welcome screen with "Sakura" title and cherry blossom warmth, ModeSelection with "Just me" / "Sync across devices" cards with playful subtitles
- Local mode path completes onboarding instantly — no permissions, no folder paths, straight to Home
- Settings shows current storage mode with "Change" button and confirmation dialog for bidirectional migration
- Device-verified: fresh install local mode, Syncthing flow start, cold start persistence all confirmed working

## Task Commits

Each task was committed atomically:

1. **Task 1: Expand onboarding flow with Welcome + ModeSelection screens** - `0d07e26` (feat)
2. **Task 2: Settings storage section with mode display and migration** - `f557beb` (feat)
3. **Task 3: Device verification checkpoint** - Human-verified, approved

**Plan metadata:** docs commit below

## Files Created/Modified

- `app/src/main/java/com/sakura/features/onboarding/OnboardingViewModel.kt` — Added Welcome/ModeSelection states, onGetStarted(), onModeSelected(), init block for re-onboarding detection
- `app/src/main/java/com/sakura/features/onboarding/OnboardingScreen.kt` — WelcomeContent + ModeSelectionContent composables, guarded DisposableEffect
- `app/src/main/java/com/sakura/features/settings/MacroTargetsScreen.kt` — Storage section with mode display, Change button, migration dialog and logic
- `app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt` — Added clearOnboardingComplete() for migration

## Decisions Made

- DisposableEffect for permission checking moved inside Syncthing-path states only — prevents overwriting Welcome/ModeSelection on app resume
- clearOnboardingComplete() added to support Local→Syncthing migration path (clears flag, recreates activity, onboarding detects SYNCTHING mode)
- Smart-cast fix: nullable storageMode captured into local val before when() for exhaustive matching

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Unreachable null branch in when(currentStorageMode)**
- **Found during:** Task 2 (Settings storage section)
- **Issue:** Compiler warned about unreachable null branches inside a null-guarded block
- **Fix:** Captured nullable into local `val resolvedMode` before `when`, enabling proper smart-cast exhaustive matching
- **Verification:** Build clean with no warnings

**2. [Rule 2 - Missing Critical] clearOnboardingComplete() not present**
- **Found during:** Task 2 (Settings storage section)
- **Issue:** Local→Syncthing migration requires clearing the onboarding flag, but the method didn't exist
- **Fix:** Added clearOnboardingComplete() to AppPreferencesRepository (removes ONBOARDING_COMPLETE key from DataStore)
- **Verification:** Build compiles, method available for migration logic

---

**Total deviations:** 2 auto-fixed (1 bug, 1 missing critical)
**Impact on plan:** Both necessary for correctness. No scope creep.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- Phase 5 complete — all local storage mode features implemented and device-verified
- This is the final phase of the milestone

---
*Phase: 05-local-storage-mode*
*Completed: 2026-04-13*
