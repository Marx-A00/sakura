---
phase: 06-fix-data-bugs
plan: 02
subsystem: storage
tags: [kotlin, coroutines, file-io, onboarding, migration, syncthing]

# Dependency graph
requires:
  - phase: 05-local-storage-mode
    provides: AppPreferencesRepository with clearOnboardingComplete(), StorageMode enum, LOCAL/SYNCTHING modes
provides:
  - copyLocalOrgFilesToFolder() helper in AppPreferencesRepository for file migration
  - LOCAL->SYNCTHING migration copies existing org files to sync folder before onboarding completes
affects:
  - 06-fix-data-bugs (other plans in phase building on migration correctness)
  - 07 (next phase — migration integrity prerequisite)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "File migration via File.copyTo(overwrite = true) — same pattern as MacroTargetsScreen SYNCTHING->LOCAL direction"
    - "Dispatchers.IO for file I/O in suspend functions"
    - "Migration call sandwiched between setSyncFolderPath and setOnboardingComplete in onFolderSelected()"

key-files:
  created: []
  modified:
    - app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt
    - app/src/main/java/com/sakura/features/onboarding/OnboardingViewModel.kt

key-decisions:
  - "Migration is unconditional — fresh installs are safe because listFiles returns empty array (no-op)"
  - "overwrite = true ensures idempotency if migration is triggered multiple times"
  - "copyLocalOrgFilesToFolder placed after setSyncFolderPath but before setOnboardingComplete — ensures files are present when app switches to reading from sync folder"

patterns-established:
  - "File migration helpers grouped in a dedicated commented section at bottom of AppPreferencesRepository"

# Metrics
duration: 1min
completed: 2026-04-13
---

# Phase 6 Plan 02: Fix LOCAL->SYNCTHING Migration Data Loss Summary

**copyLocalOrgFilesToFolder() helper in AppPreferencesRepository copies all .org files from filesDir to the selected sync folder during LOCAL->SYNCTHING re-onboarding**

## Performance

- **Duration:** ~1 min
- **Started:** 2026-04-13T17:08:41Z
- **Completed:** 2026-04-13T17:09:57Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Added `copyLocalOrgFilesToFolder(destFolderPath: String)` suspend function to `AppPreferencesRepository` using `Dispatchers.IO`
- Wired migration call into `OnboardingViewModel.onFolderSelected()` at the correct position (after path save, before onboarding complete)
- Users switching from LOCAL to Syncthing mode no longer lose their food-log.org and workout-log.org data

## Task Commits

Each task was committed atomically:

1. **Task 1: Add copyLocalOrgFilesToFolder() to AppPreferencesRepository** - `3f32c9a` (feat)
2. **Task 2: Call copy in OnboardingViewModel.onFolderSelected()** - `656c498` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt` - Added `copyLocalOrgFilesToFolder()` method and `Dispatchers`/`withContext` imports
- `app/src/main/java/com/sakura/features/onboarding/OnboardingViewModel.kt` - Added migration call in `onFolderSelected()` between `setSyncFolderPath` and `setOnboardingComplete`

## Decisions Made

- Migration is unconditional (no storageMode check needed) — `listFiles` returns an empty array for fresh installs so `forEach` is a no-op
- `overwrite = true` ensures the copy is idempotent if somehow triggered more than once
- `MacroTargetsScreen.migrateStorage()` left unchanged — it handles the reverse direction (SYNCTHING->LOCAL) correctly and independently

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- LOCAL->SYNCTHING migration is now data-safe
- Both migration directions (LOCAL->SYNCTHING via re-onboarding and SYNCTHING->LOCAL via MacroTargetsScreen) are correct
- Ready for Phase 7

---
*Phase: 06-fix-data-bugs*
*Completed: 2026-04-13*
