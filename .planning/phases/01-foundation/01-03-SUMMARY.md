---
phase: 01-foundation
plan: 03
subsystem: sync, onboarding, navigation
tags: [syncthing, datastore, manage-external-storage, atomicfile, navigation-compose, onboarding]

# Dependency graph
requires:
  - phase: 01-01
    provides: AppContainer stub, MainActivity shell, SakuraTheme, AndroidManifest
  - phase: 01-02
    provides: OrgWriter and OrgParser objects for AppContainer wiring
provides:
  - sync-backend-interface
  - syncthing-file-backend
  - app-preferences-datastore
  - onboarding-flow
  - navigation-shell
  - device-deploy-verified
affects:
  - 02-01 (FoodRepository will use SyncBackend to read/write food-log.org)
  - 02-02 (Today screen will use navigation shell)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - sync-backend-interface-abstraction
    - atomicfile-for-safe-writes
    - datastore-preferences-for-settings
    - lifecycle-observer-for-permission-recheck
    - conditional-navigation-with-datastore-guard

key-files:
  created:
    - app/src/main/java/com/sakura/sync/SyncBackend.kt
    - app/src/main/java/com/sakura/sync/SyncBackendError.kt
    - app/src/main/java/com/sakura/sync/SyncthingFileBackend.kt
    - app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt
    - app/src/main/java/com/sakura/features/onboarding/OnboardingScreen.kt
    - app/src/main/java/com/sakura/features/onboarding/OnboardingViewModel.kt
    - app/src/main/java/com/sakura/navigation/AppNavHost.kt
    - app/src/main/java/com/sakura/navigation/Routes.kt
  modified:
    - app/src/main/java/com/sakura/di/AppContainer.kt
    - app/src/main/java/com/sakura/MainActivity.kt

key-decisions: []

patterns-established:
  - "SyncBackend interface abstracts file I/O — swap implementations without touching consumers"
  - "Environment.isExternalStorageManager() checked before every file op in SyncthingFileBackend"
  - "DisposableEffect + LifecycleEventObserver for permission re-check on Activity resume"
  - "collectAsStateWithLifecycle(initialValue = null) prevents NavHost flash before DataStore emits"

# Metrics
duration: ~12 min
completed: 2026-04-09
---

# Phase 1 Plan 03: Sync / Onboarding / Deploy Summary

**SyncBackend interface with AtomicFile-backed SyncthingFileBackend, DataStore preferences, onboarding flow with MANAGE_EXTERNAL_STORAGE permission handling, and navigation shell — verified on physical Galaxy S21 FE.**

## Performance

- **Duration:** ~12 min
- **Completed:** 2026-04-09
- **Tasks:** 3 of 3
- **Files modified:** 10 (8 created, 2 modified)

## Accomplishments

- SyncBackend interface and SyncBackendError sealed class define the file I/O contract — SyncthingFileBackend implements atomic writes via AtomicFile and guards every operation with Environment.isExternalStorageManager()
- AppPreferencesRepository wraps DataStore<Preferences> to persist the Syncthing folder path and onboarding-complete flag; wired into AppContainer alongside OrgEngine from 01-02
- Onboarding flow (OnboardingViewModel 4-state sealed interface + OnboardingScreen DisposableEffect lifecycle observer) handles permission request, permission re-check on resume, and folder path entry; navigation shell uses conditional startDestination so cold-launch skips onboarding once setup is complete and back-stack is cleared with popUpTo inclusive
- All 7 functional verification steps passed on physical device (Galaxy S21 FE): launch, onboarding renders, Grant Access opens Settings, permission re-check on resume, folder path input completes setup, back does not return to onboarding, kill-and-relaunch skips onboarding

## Task Commits

Each task was committed atomically:

1. **Task 1: SyncBackend, SyncthingFileBackend, AppPreferencesRepository, AppContainer** - `081d8a2` (feat)
2. **Task 2: Onboarding flow, navigation shell, conditional start** - `bdda1e8` (feat)
3. **Task 3: Device deploy checkpoint** - approved (no code commit; device verification only)

## Files Created/Modified

- `app/src/main/java/com/sakura/sync/SyncBackend.kt` - Interface defining read/write/list contract for org file backends
- `app/src/main/java/com/sakura/sync/SyncBackendError.kt` - Sealed class covering permission denied, IO error, conflict, and not found cases
- `app/src/main/java/com/sakura/sync/SyncthingFileBackend.kt` - AtomicFile-backed implementation; checks isExternalStorageManager() before every operation; filters conflict copies from listings
- `app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt` - DataStore<Preferences> wrapper exposing syncFolderPath and onboardingComplete flows
- `app/src/main/java/com/sakura/features/onboarding/OnboardingViewModel.kt` - 4-state sealed interface (CheckingPermission, NeedsPermission, NeedsFolder, Complete); drives onboarding progression
- `app/src/main/java/com/sakura/features/onboarding/OnboardingScreen.kt` - Composable with DisposableEffect LifecycleEventObserver; fires ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION intent; re-checks permission on ON_RESUME
- `app/src/main/java/com/sakura/navigation/AppNavHost.kt` - NavHost with DataStore-guarded conditional startDestination; SetupCompleteScreen; popUpTo inclusive clears back stack
- `app/src/main/java/com/sakura/navigation/Routes.kt` - @Serializable route objects for type-safe Navigation Compose 2
- `app/src/main/java/com/sakura/di/AppContainer.kt` - Wires DataStore, AppPreferencesRepository, SyncthingFileBackend, and OrgEngine together
- `app/src/main/java/com/sakura/MainActivity.kt` - collectAsStateWithLifecycle(initialValue = null) null guard prevents NavHost flash before DataStore first emission

## Decisions Made

None - followed plan as specified.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 1 is complete. All foundation components are in place and verified on device:

- OrgEngine (OrgWriter + OrgParser with property drawer format) — ready for FoodRepository
- SyncBackend interface + SyncthingFileBackend — ready for Phase 2 file read/write operations
- AppPreferencesRepository — ready for any future preference keys
- Navigation shell — ready for Today and Workout screens

No blockers for Phase 2. The Samsung One UI Auto Blocker note (disable before first ADB install) is already documented in STATE.md and applies to new device setups only — the current device is fully configured.

---
*Phase: 01-foundation*
*Completed: 2026-04-09*
