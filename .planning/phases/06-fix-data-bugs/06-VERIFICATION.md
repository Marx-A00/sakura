---
phase: 06-fix-data-bugs
verified: 2026-04-13T17:13:33Z
status: passed
score: 7/7 must-haves verified
---

# Phase 6: Fix Data Bugs Verification Report

**Phase Goal:** Fix three data integrity bugs — LOCAL→SYNCTHING migration data loss, PR badge persistence, and templateName cold start restoration.
**Verified:** 2026-04-13T17:13:33Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

**Truth 1: WorkoutSession.templateName is non-null after cold-start parse when splitDay was persisted**
- Status: VERIFIED
- Evidence: `OrgWorkoutRepository.kt` line 546 — `templateName = splitDayParsed?.displayName` added to `WorkoutSession` constructor in `toWorkoutSession()`. `SplitDay.fromLabel(it)` is called at line 542, result assigned to `splitDayParsed`, then `displayName` is derived and passed to the constructor.

**Truth 2: Dashboard and workout screen display the correct template name instead of null/Freestyle after cold start**
- Status: VERIFIED
- Evidence: `WorkoutLogScreen.kt` line 447 uses `state.templateName ?: "Freestyle"` — now that `templateName` is populated, this will show the real name. `DashboardViewModel.kt` lines 88 and 329 pass `session?.templateName` to UI state. The derivation chain is complete: org file → `OrgDateSection.splitDay` → `SplitDay.fromLabel()` → `displayName` → `WorkoutSession.templateName` → ViewModel → Screen.

**Truth 3: SetLog.isPr round-trips through OrgSchema.formatSetEntry → OrgParser correctly (true written, true read back)**
- Status: VERIFIED
- Evidence: Test `test_parse_workout_isPr_round_trip` (OrgParserTest.kt line 848) creates `OrgSetEntry(isPr = true)`, formats via `OrgSchema.formatSetEntry()`, embeds in minimal workout org string, parses with `OrgParser.parse()`, and asserts `assertTrue(set.isPr)`. `OrgSchema.formatSetEntry` writes `:is_pr: ${set.isPr}` (line 418 in OrgSchema.kt). `OrgParser` reads it at line 287 with `drawerProperties[OrgSchema.PROP_IS_PR]?.toBooleanStrictOrNull() ?: false`. `PROPERTY_REGEX = Regex("""^:(\w+):\s+(.+)$""")` captures the trimmed value correctly via `content.lines()` split (no trailing newlines in captures).

**Truth 4: PR badge renders on sets detected as PRs after app restart**
- Status: VERIFIED
- Evidence: `WorkoutLogScreen.kt` line 642 — `if (set.isPr) { PrBadge() }`. `WorkoutHistoryScreen.kt` line 230 also checks `set.isPr`. The write-time PR detection in `WorkoutLogViewModel.addSet()` (lines 194-210) sets `setToWrite = set.copy(isPr = true)` before calling `workoutRepo.addSet()`. This was already fixed in Phase 3 commit `adc4dd6`. Phase 06 adds the round-trip test to confirm the full persistence path.

**Truth 5: LOCAL→SYNCTHING migration copies all .org files from app-internal storage to the user-selected sync folder**
- Status: VERIFIED
- Evidence: `AppPreferencesRepository.kt` lines 255-263 — `copyLocalOrgFilesToFolder(destFolderPath)` uses `context.filesDir.listFiles { file -> file.name.endsWith(".org") }?.forEach { orgFile -> orgFile.copyTo(dest, overwrite = true) }`. This runs on `Dispatchers.IO` via `withContext`. Both `Dispatchers` and `withContext` are imported at lines 12 and 17.

**Truth 6: Files are copied AFTER sync folder path is known but BEFORE onboarding completes**
- Status: VERIFIED
- Evidence: `OnboardingViewModel.kt` lines 99-108 — `onFolderSelected()` calls `setSyncFolderPath(path)` then `copyLocalOrgFilesToFolder(path)` then `setOnboardingComplete()`. The ordering is correct. The copy call is at line 104, sandwiched correctly.

**Truth 7: Fresh Syncthing installs (empty filesDir) work correctly — copy is a no-op**
- Status: VERIFIED
- Evidence: `copyLocalOrgFilesToFolder` uses `?.forEach` on the `listFiles` result — when `filesDir` contains no `.org` files, `listFiles` returns an empty array and `forEach` is never called. The `?.` safe-call also handles the case where `listFiles` returns `null`. No-op behavior is guaranteed.

**Score:** 7/7 truths verified

## Required Artifacts

**`app/src/main/java/com/sakura/data/workout/OrgWorkoutRepository.kt`**
- Provides: templateName derivation from splitDay in toWorkoutSession()
- Status: VERIFIED
- Details: 577 lines, substantive. Line 546 contains `templateName = splitDayParsed?.displayName`. Commit `7ff8f56` added this one-line fix.

**`app/src/test/java/com/sakura/orgengine/OrgParserTest.kt`**
- Provides: isPr round-trip verification test (Test 20)
- Status: VERIFIED
- Details: 948 lines, substantive. Test `test_parse_workout_isPr_round_trip` at line 848 exercises the full `formatSetEntry → parse` path with `isPr = true` and asserts `assertTrue(set.isPr)`. Commit `a9e55e7` added this test.

**`app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt`**
- Provides: `copyLocalOrgFilesToFolder()` helper method
- Status: VERIFIED
- Details: 274 lines, substantive. Method at lines 255-263. Uses `Dispatchers.IO` (imported at line 12), `withContext` (imported at line 17), and `File.copyTo(overwrite = true)`. Commit `3f32c9a`.

**`app/src/main/java/com/sakura/features/onboarding/OnboardingViewModel.kt`**
- Provides: Migration copy call in onFolderSelected()
- Status: VERIFIED
- Details: 109 lines, substantive. `copyLocalOrgFilesToFolder(path)` called at line 104 inside `onFolderSelected()`. Commit `656c498`.

## Key Link Verification

**OrgWorkoutRepository.toWorkoutSession() → WorkoutSession.templateName via splitDayParsed?.displayName**
- Status: WIRED
- Details: Line 542 computes `splitDayParsed`, line 546 assigns `templateName = splitDayParsed?.displayName` to the `WorkoutSession` constructor.

**OrgSchema.formatSetEntry() → OrgParser set-level drawer via :is_pr: property**
- Status: WIRED
- Details: `OrgSchema.formatSetEntry` writes `:is_pr: ${set.isPr}` (OrgSchema.kt line 418). `OrgParser` reads `drawerProperties[OrgSchema.PROP_IS_PR]?.toBooleanStrictOrNull() ?: false` (OrgParser.kt line 287). Round-trip confirmed by Test 20.

**OnboardingViewModel.onFolderSelected() → AppPreferencesRepository.copyLocalOrgFilesToFolder() via suspend call**
- Status: WIRED
- Details: Line 104 in OnboardingViewModel.kt calls `prefsRepo.copyLocalOrgFilesToFolder(path)` within `viewModelScope.launch`. The call is positioned after `setSyncFolderPath(path)` and before `setOnboardingComplete()`.

**OnboardingScreen → onFolderSelected() callback**
- Status: WIRED
- Details: `OnboardingScreen.kt` line 92 calls `viewModel.onFolderSelected(path)` in the `onFolderConfirmed` callback. The new directory browser UI (uncommitted changes to OnboardingScreen.kt) passes `currentDir.absolutePath.trimEnd('/') + "/"` as the path — same contract as before.

## Requirements Coverage

**WORK-05 (partial — isPr persistence)**
- Status: SATISFIED by Phase 06
- Evidence: Round-trip test confirms `isPr = true` persists through the write/read path. Write-time detection was already correct from Phase 3 (`adc4dd6`). The test adds confidence and serves as a regression guard.

**LOCAL-03 (partial — migration completeness)**
- Status: SATISFIED by Phase 06
- Evidence: `copyLocalOrgFilesToFolder()` is added to `AppPreferencesRepository` and called from `OnboardingViewModel.onFolderSelected()`. The copy happens before `setOnboardingComplete()`, ensuring data is present when the app switches to reading from the sync folder.

## Anti-Patterns Found

No blockers or warnings found in the modified files.

The uncommitted changes to `OnboardingScreen.kt` (folder picker UI from text field to directory browser) are outside the scope of Phase 06's plans but do not break the migration fix wiring — the `onFolderConfirmed` callback still calls `viewModel.onFolderSelected(path)` correctly.

## E2E Flow Status (Post-Fix)

**Flow 4: Workout template → sets → PR → history**
- Previous status: PARTIAL (PR dialog fires, badge doesn't persist per audit)
- Current status: COMPLETE — write-time PR detection in `WorkoutLogViewModel.addSet()` correctly sets `isPr = true` before writing (Phase 3 fix `adc4dd6`). Phase 06 round-trip test confirms the persistence path is correct. PR badge renders via `set.isPr` check in `WorkoutLogScreen.kt` and `WorkoutHistoryScreen.kt`.
- Note: First-ever exercise session cannot yield a PR (no prior history to compare against) — this is correct and intentional behavior.

**Flow 5: Cold start persistence**
- Previous status: PARTIAL (templateName cosmetic break)
- Current status: COMPLETE — `WorkoutSession.templateName` is now derived from `splitDayParsed?.displayName` in `toWorkoutSession()`. Dashboard and workout screen show correct template name after cold start.

**Flow 6: Storage mode migration (LOCAL→SYNCTHING)**
- Previous status: BROKEN (org files left behind in filesDir)
- Current status: COMPLETE — `copyLocalOrgFilesToFolder()` is called during `onFolderSelected()`, copying all `.org` files from `context.filesDir` to the selected sync folder before onboarding completes.

## Human Verification Required

The following items require device testing and cannot be verified programmatically:

### 1. Cold Start Template Name Display

**Test:** Log a workout session with a split day (e.g. Monday Lift), force-stop the app, restart it.
**Expected:** Dashboard workout card shows "Monday — Lift (Heavy Compounds)" and workout screen header shows the template name instead of "Freestyle".
**Why human:** Requires runtime; verifying that the org file was written with `:split_day:`, that parsing restores `templateName`, and that the UI renders it.

### 2. PR Badge Persistence After Restart

**Test:** Log a workout with a weight that exceeds a prior personal best. Confirm the PR dialog appears. Force-stop the app and restart. Navigate to workout history for that date.
**Expected:** The set that was a PR shows the gold PR badge in workout history.
**Why human:** Requires an actual prior session in the file for `findPersonalBest` to return a value. The round-trip test confirms the path works but cannot test the full ViewModel → Repository flow at runtime.

### 3. LOCAL→SYNCTHING Migration Data Preservation

**Test:** Use local mode and log some food/workout data. Go to Settings, change storage mode to Syncthing, select a sync folder. Verify the app opens with data intact.
**Expected:** food-log.org and workout-log.org (and any other .org files) appear in the selected Syncthing folder with their original content. Dashboard shows the migrated data.
**Why human:** Requires Android device with Syncthing installed and file system access to verify copy.

---

_Verified: 2026-04-13T17:13:33Z_
_Verifier: Claude (gsd-verifier)_
