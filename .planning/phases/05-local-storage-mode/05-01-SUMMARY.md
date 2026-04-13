---
phase: 05-local-storage-mode
plan: "01"
subsystem: infra
tags: [android, kotlin, syncbackend, datastore, internal-storage, atomicfile, dependency-injection]

# Dependency graph
requires:
  - phase: 04-dashboard-and-polish
    provides: DashboardViewModel + SyncStatusBadge + SyncStatus data class in SyncBackend.kt
  - phase: 01-foundation
    provides: AppContainer, SakuraApplication, AppPreferencesRepository, SyncBackend interface
provides:
  - LocalStorageBackend: SyncBackend over Context.filesDir, no permissions needed
  - StorageMode enum (LOCAL/SYNCTHING) persisted via DataStore in AppPreferencesRepository
  - Mode-aware AppContainer constructor: when(storageMode) backend selection
  - Cold-start guard in MainActivity: awaits DataStore before creating AppContainer
  - DashboardTodayState.isLocalMode flag driving conditional sync badge visibility
affects:
  - 05-local-storage-mode plan 02 (onboarding flow needs setStorageMode)
  - 05-local-storage-mode plan 03 (settings storage section calls setStorageMode)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "StorageMode enum for backend selection — when branch in AppContainer"
    - "SakuraApplication holds prefsRepo only; AppContainer constructed in Composable with remember(resolvedMode)"
    - "LocalStorageBackend: AtomicFile writes to Context.filesDir, no permission checks"
    - "storageMode Flow emits null when not yet set (first-launch sentinel)"

key-files:
  created:
    - app/src/main/java/com/sakura/sync/LocalStorageBackend.kt
  modified:
    - app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt
    - app/src/main/java/com/sakura/di/AppContainer.kt
    - app/src/main/java/com/sakura/SakuraApplication.kt
    - app/src/main/java/com/sakura/MainActivity.kt
    - app/src/main/java/com/sakura/features/dashboard/DashboardUiState.kt
    - app/src/main/java/com/sakura/features/dashboard/DashboardViewModel.kt
    - app/src/main/java/com/sakura/features/dashboard/DashboardScreen.kt

key-decisions:
  - "StorageMode enum co-located in AppPreferencesRepository.kt (same file as repo)"
  - "storageMode Flow returns null (not a default) when unset — MainActivity falls back to LOCAL at runtime without persisting"
  - "AppContainer constructed via remember(resolvedMode) in Composable — recreates when mode changes post-migration"
  - "SakuraApplication no longer owns AppContainer — avoids async DataStore read in Application.onCreate()"

patterns-established:
  - "Backend selection via when(storageMode) in AppContainer — add new modes here only"
  - "Null-sentinel pattern for unset DataStore preferences (storageMode, onboardingComplete)"
  - "isLocalMode boolean flag on UiState — ViewModel reads storageMode.first(), converts to boolean for UI"

# Metrics
duration: 3min
completed: 2026-04-13
---

# Phase 5 Plan 01: Local Storage Backend Summary

**LocalStorageBackend over Context.filesDir with StorageMode DataStore preference driving conditional AppContainer wiring and dashboard sync badge suppression**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-04-13T00:52:47Z
- **Completed:** 2026-04-13T00:54:59Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- LocalStorageBackend implements the full SyncBackend contract using internal app storage — no permissions needed, AtomicFile writes for crash safety
- StorageMode enum + Flow + setter added to AppPreferencesRepository; null sentinel used for "not yet chosen" first-launch state
- AppContainer refactored to accept StorageMode and wire correct backend via when branch
- SakuraApplication simplified to hold only prefsRepo; AppContainer now created in Composable with remember(resolvedMode) for lifecycle-correct reconstruction
- DashboardScreen conditionally hides SyncStatusBadge when isLocalMode is true

## Task Commits

Each task was committed atomically:

1. **Task 1: LocalStorageBackend + StorageMode preference + AppContainer wiring** - `3ac27fe` (feat)
2. **Task 2: Hide SyncStatusBadge in local mode on Dashboard** - `fb345b5` (feat)

**Plan metadata:** (docs commit — see below)

## Files Created/Modified

- `app/src/main/java/com/sakura/sync/LocalStorageBackend.kt` — New: SyncBackend impl using Context.filesDir
- `app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt` — Added StorageMode enum, storageMode Flow, setStorageMode()
- `app/src/main/java/com/sakura/di/AppContainer.kt` — Added StorageMode param, when branch backend wiring
- `app/src/main/java/com/sakura/SakuraApplication.kt` — Replaced container with prefsRepo
- `app/src/main/java/com/sakura/MainActivity.kt` — Reads storageMode, creates AppContainer with resolved mode
- `app/src/main/java/com/sakura/features/dashboard/DashboardUiState.kt` — Added isLocalMode: Boolean = false
- `app/src/main/java/com/sakura/features/dashboard/DashboardViewModel.kt` — Sets isLocalMode from storageMode.first()
- `app/src/main/java/com/sakura/features/dashboard/DashboardScreen.kt` — Guards SyncStatusBadge with if (!state.isLocalMode)

## Decisions Made

- `StorageMode` enum placed at file level in `AppPreferencesRepository.kt` (same file, outside class) — keeps it adjacent to its persistence logic without creating a dedicated file
- `storageMode` Flow returns `null` when no value stored — this is the "not yet selected" sentinel; MainActivity uses `?: StorageMode.LOCAL` as a runtime fallback without persisting to DataStore
- `AppContainer` constructed inside a `remember(resolvedMode)` Composable — ensures the container is re-created if mode changes (e.g., after Syncthing migration) without requiring an Activity restart
- `SakuraApplication` simplified to only hold `prefsRepo` — avoids the chicken-and-egg problem where `AppContainer` needs `StorageMode` but `Application.onCreate()` can't await DataStore

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- LocalStorageBackend and StorageMode are ready for plan 02 (onboarding flow) to call `setStorageMode(StorageMode.LOCAL)` or `setStorageMode(StorageMode.SYNCTHING)` after user selection
- AppContainer wiring is complete — plan 03 (settings storage section) can toggle mode and the container reconstructs via remember(resolvedMode)
- All existing tests pass; the Syncthing flow is unaffected

---
*Phase: 05-local-storage-mode*
*Completed: 2026-04-13*
