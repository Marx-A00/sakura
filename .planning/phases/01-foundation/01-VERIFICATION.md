---
phase: 01-foundation
verified: 2026-04-09T00:00:00Z
status: gaps_found
score: 2/5 must-haves verified
gaps:
  - truth: "A food entry written by the app appears in food-log.org under the correct date heading and passes M-x org-lint in Emacs with zero errors"
    status: partial
    reason: "OrgWriter produces correct org format for food entries (proven by 7 JVM tests and org-lint validated food-log-sample.org), but there is no code path in Phase 1 that writes a food entry to food-log.org via user action. No FoodRepository, no food logging screen, and no call site for OrgWriter.appendSection() or SyncBackend.writeFile() exists yet."
    artifacts:
      - path: "app/src/main/java/com/sakura/orgengine/OrgWriter.kt"
        issue: "Substantive and correct, but never called from any Phase 1 feature. OrgWriter is only referenced in AppContainer (DI wiring) and its own tests."
      - path: "app/src/main/java/com/sakura/sync/SyncthingFileBackend.kt"
        issue: "writeFile() is implemented and uses AtomicFile, but no Phase 1 feature calls it. syncBackend in AppContainer is never accessed by any screen or ViewModel."
    missing:
      - "A mechanism (even a debug screen or ADB-triggered path) that actually invokes OrgWriter.appendSection() + SyncBackend.writeFile() to produce food-log.org"
      - "This is correctly deferred to Phase 2 (FoodRepository), but means SC3 is not satisfiable in Phase 1"
  - truth: "A workout entry written by the app appears in workout-log.org under the correct date heading and passes M-x org-lint in Emacs with zero errors"
    status: partial
    reason: "OrgWriter produces correct org format for workout entries (proven by 7 JVM tests and org-lint validated committed workout-log-sample.org). Same gap as SC3: no call path exists in Phase 1 to write a workout entry to workout-log.org."
    artifacts:
      - path: "app/src/main/java/com/sakura/orgengine/OrgWriter.kt"
        issue: "Substantive and correct for workout format, but same orphaned call site issue as SC3."
      - path: "workout-log-sample.org"
        issue: "Working tree copy is manually corrupted (invalid date month 00, missing heading space, non-numeric property values). Committed version at 4799b48 is correct and was org-lint validated. This is an uncommitted local modification, not a code defect."
    missing:
      - "A mechanism to write a workout entry to workout-log.org (deferred to Phase 3)"
  - truth: "When the Syncthing folder is unavailable, the app shows a graceful error instead of crashing; .sync-conflict files are never opened as the active log"
    status: failed
    reason: "SyncBackendError sealed class (FolderUnavailable, PermissionDenied, ConflictDetected) is defined and SyncthingFileBackend throws these correctly. However, no Phase 1 screen or ViewModel catches or surfaces these errors to the user. The OnboardingViewModel, OnboardingScreen, SetupCompleteScreen, and AppNavHost contain zero references to SyncBackendError or any error UI state. The conflict filter in listOrgFiles() exists but is never exercised. The PLAN specified testing graceful degradation at device deploy (steps 12-13), but the 01-03-SUMMARY only reports 7 onboarding-flow steps as verified — the file I/O and graceful degradation verification steps are absent from the summary."
    artifacts:
      - path: "app/src/main/java/com/sakura/sync/SyncBackendError.kt"
        issue: "Defined correctly but never caught or handled at the UI layer."
      - path: "app/src/main/java/com/sakura/sync/SyncthingFileBackend.kt"
        issue: "Throws FolderUnavailable and PermissionDenied correctly, but no consumer exists in Phase 1 to catch these."
    missing:
      - "Any UI error state (in OnboardingViewModel, AppNavHost, or a new screen) that catches SyncBackendError.FolderUnavailable and displays a message instead of crashing"
      - "A call site that exercises SyncBackend before Phase 2 so SC5 can be verified in Phase 1"
      - "Confirmation that device-side graceful degradation test (plan step 12-13) was actually run"
human_verification:
  - test: "Install app-debug.apk on Galaxy S21 FE via ADB, complete onboarding, then revoke All Files Access from Settings while the app is on the Setup Complete screen"
    expected: "App should show an error message or state (not crash and not silently fail) when the Syncthing folder becomes inaccessible"
    why_human: "No automated test covers runtime permission revocation; requires physical device with active session"
  - test: "After onboarding, push food-log-sample.org to the configured Syncthing folder via ADB (adb push food-log-sample.org /storage/emulated/0/Syncthing/roaming/health/food-log.org). Open the file in Emacs and run M-x org-lint."
    expected: "Zero lint errors"
    why_human: "Requires Emacs on the host machine and ADB file transfer; the committed food-log-sample.org matches this format exactly but end-to-end validation needs a human to confirm"
---

# Phase 1: Foundation Verification Report

**Phase Goal:** The app builds, deploys to the Galaxy S21 FE, can read and write valid org files in the Syncthing folder, and onboards the user through permissions and folder selection.
**Verified:** 2026-04-09
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

**Truth 1 — App installs via ADB on Galaxy S21 FE and launches without crashing**
Status: VERIFIED
Evidence: app-debug.apk exists at app/build/outputs/apk/debug/app-debug.apk. AndroidManifest.xml correctly declares MainActivity as MAIN/LAUNCHER. SakuraApplication.onCreate() initializes AppContainer (DataStore + SyncthingFileBackend + OrgParser + OrgWriter). MainActivity uses collectAsStateWithLifecycle(initialValue = null) guard to prevent NavHost flash before DataStore emits. Device deploy reported in 01-03-SUMMARY: 7 of 7 onboarding verification steps passed on physical Galaxy S21 FE.

**Truth 2 — Onboarding flow requests MANAGE_EXTERNAL_STORAGE permission and lets user select the Syncthing folder path; selected path persists across restarts**
Status: VERIFIED
Evidence:
- AndroidManifest.xml declares MANAGE_EXTERNAL_STORAGE permission with tools:ignore="ScopedStorage"
- OnboardingViewModel: 4-state sealed interface (CheckingPermission, NeedsPermission, NeedsFolder, Complete); checkPermission() calls Environment.isExternalStorageManager()
- OnboardingScreen: DisposableEffect with LifecycleEventObserver fires checkPermission() on every ON_RESUME; NeedsPermissionContent launches ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION intent; NeedsFolderContent writes path via onFolderSelected()
- OnboardingViewModel.onFolderSelected() calls prefsRepo.setSyncFolderPath() and prefsRepo.setOnboardingComplete() — both persist to DataStore
- AppNavHost uses onboardingComplete DataStore flow with conditional startDestination; popUpTo<Onboarding> { inclusive = true } clears back stack
- MainActivity guard: onboardingDone == null means DataStore still loading, prevents early NavHost render
- Device deploy verified: restart skips onboarding, back button does not return to onboarding

**Truth 3 — A food entry written by the app appears in food-log.org under the correct date heading and passes M-x org-lint in Emacs with zero errors**
Status: PARTIAL
Evidence: OrgWriter.writeSection() produces property-drawer format food entries; 7 JVM tests pass including round-trip test. food-log-sample.org (committed version) passes org-lint per 01-02 SUMMARY. Format uses Locale.ENGLISH day-of-week to prevent locale-dependent heading corruption. BUT: OrgWriter.appendSection() and SyncBackend.writeFile() have no call site in Phase 1. No food logging screen exists. No end-to-end path from user action to food-log.org write exists.

**Truth 4 — A workout entry written by the app appears in workout-log.org under the correct date heading and passes M-x org-lint in Emacs with zero errors**
Status: PARTIAL
Evidence: Same as Truth 3 for workout format. OrgWriter produces correct "** Workout" / "*** Exercise" / property drawer structure. 7 JVM tests pass. Committed workout-log-sample.org is org-lint clean (note: working tree copy has been manually corrupted but was never committed — committed version at 4799b48 is correct). No end-to-end path from user action to workout-log.org write exists.

**Truth 5 — When the Syncthing folder is unavailable, the app shows a graceful error instead of crashing; .sync-conflict files are never opened as the active log**
Status: FAILED
Evidence: SyncBackendError sealed class is correctly defined with FolderUnavailable, PermissionDenied, ConflictDetected cases. SyncthingFileBackend throws FolderUnavailable when path is null and PermissionDenied when isExternalStorageManager() is false. listOrgFiles() filters files containing ".sync-conflict" from results. However: no Phase 1 screen, ViewModel, or composable references SyncBackendError or contains catch clauses for it. The OnboardingViewModel and SetupCompleteScreen perform zero file I/O. The 01-03-SUMMARY reports only 7 onboarding steps verified; plan steps 12-13 (revoke permission, check for error vs crash) are absent from the summary.

**Score: 2/5 truths verified**

### Required Artifacts

**SyncBackend.kt**
- Exists: yes (13 lines)
- Substantive: yes — interface with 4 method signatures (readFile, writeFile, fileExists, listOrgFiles)
- Wired: yes — instantiated as SyncthingFileBackend in AppContainer

**SyncBackendError.kt**
- Exists: yes (16 lines)
- Substantive: yes — sealed class with 3 meaningful subclasses
- Wired: partial — thrown by SyncthingFileBackend but never caught at UI layer

**SyncthingFileBackend.kt**
- Exists: yes (86 lines)
- Substantive: yes — all 4 interface methods implemented with AtomicFile, permission guards, conflict filter
- Wired: yes — instantiated in AppContainer; orphaned at runtime (no caller yet)

**AppPreferencesRepository.kt**
- Exists: yes (62 lines)
- Substantive: yes — two DataStore flows, two suspend write functions, proper IOException catch
- Wired: yes — used by OnboardingViewModel and SetupCompleteScreen

**OnboardingViewModel.kt**
- Exists: yes (65 lines)
- Substantive: yes — 4-state sealed interface, checkPermission(), onFolderSelected() with DataStore writes
- Wired: yes — used by OnboardingScreen

**OnboardingScreen.kt**
- Exists: yes (193 lines)
- Substantive: yes — full Compose UI with DisposableEffect lifecycle observer, permission intent, folder input
- Wired: yes — registered in AppNavHost under Onboarding route

**AppNavHost.kt**
- Exists: yes (105 lines)
- Substantive: yes — conditional startDestination, OnboardingScreen, SetupCompleteScreen, popUpTo inclusive
- Wired: yes — called from MainActivity

**OrgWriter.kt**
- Exists: yes (77 lines)
- Substantive: yes — write(), writeSection(), appendSection() with correct separation and format
- Wired: partial — in AppContainer DI but no Phase 1 feature calls its methods

**OrgParser.kt**
- Exists: yes (166 lines)
- Substantive: yes — state machine parser, FOOD/WORKOUT modes, property drawer handling, graceful malformed line skip
- Wired: partial — in AppContainer DI but no Phase 1 feature calls its methods

**OrgModels.kt**
- Exists: yes (53 lines)
- Substantive: yes — OrgFile, OrgDateSection, OrgMealGroup, OrgFoodEntry, OrgExerciseEntry data classes
- Wired: yes — used by OrgWriter, OrgParser, and all tests

**OrgSchema.kt**
- Exists: yes (172 lines)
- Substantive: yes — date formatter with Locale.ENGLISH, all format functions, all parsing regexes
- Wired: yes — used by OrgWriter and OrgParser exclusively (no format duplication)

### Key Link Verification

**OnboardingViewModel -> AppPreferencesRepository (folder path persistence)**
Status: WIRED — onFolderSelected() calls prefsRepo.setSyncFolderPath() and prefsRepo.setOnboardingComplete(); both persist to DataStore.

**OnboardingScreen -> OnboardingViewModel (lifecycle re-check)**
Status: WIRED — DisposableEffect with LifecycleEventObserver calls viewModel.checkPermission() on ON_RESUME.

**MainActivity -> DataStore (null-guard before NavHost)**
Status: WIRED — collectAsStateWithLifecycle(initialValue = null) prevents NavHost from rendering until DataStore emits; startWithOnboarding computed correctly from the emitted value.

**AppNavHost -> OnboardingScreen -> SetupCompleteScreen (back stack)**
Status: WIRED — popUpTo<Onboarding> { inclusive = true } clears onboarding from back stack on completion.

**OrgWriter -> OrgSchema (format contract)**
Status: WIRED — OrgWriter calls OrgSchema.formatDateHeading(), formatMealHeading(), formatFoodEntry(), formatExerciseEntry() exclusively; no format strings duplicated.

**OrgParser -> OrgSchema (regex contract)**
Status: WIRED — OrgParser uses OrgSchema.DATE_HEADING_REGEX, MEAL_HEADING_REGEX, ITEM_HEADING_REGEX, PROPERTY_REGEX, PROPERTIES_START, PROPERTIES_END.

**SyncBackend -> any caller (file I/O)**
Status: NOT WIRED — SyncthingFileBackend is instantiated in AppContainer but syncBackend is never accessed by any Phase 1 ViewModel or composable. No call to readFile(), writeFile(), fileExists(), or listOrgFiles() exists in Phase 1 feature code.

**SyncBackendError -> any UI error handler**
Status: NOT WIRED — SyncBackendError is thrown by SyncthingFileBackend but never caught or mapped to UI state anywhere in Phase 1.

### Requirements Coverage

**FOUND-01** — Custom org-mode parser reads org files into Kotlin models
Status: SATISFIED — OrgParser.parse() with FOOD/WORKOUT modes exists, 8 JVM tests pass, round-trip symmetry proven.

**FOUND-02** — Org writer produces valid org syntax verified by Emacs org-lint
Status: SATISFIED — OrgWriter exists, 7 JVM tests pass, food-log-sample.org validated by org-lint per 01-02-SUMMARY. (workout-log-sample.org committed version also validated; working tree copy is locally corrupted but uncommitted.)

**FOUND-03** — Org file schema defined for food and workout entries
Status: SATISFIED — OrgSchema.kt defines all format constants, formatters, regexes, and serialization functions for both food and workout domains.

**FOUND-04** — SyncBackend interface abstracts sync (Syncthing now, server later)
Status: SATISFIED — SyncBackend interface with readFile/writeFile/fileExists/listOrgFiles defined; SyncthingFileBackend implements it; AppContainer wires the concrete implementation.

**FOUND-05** — SyncthingFileBackend reads/writes via direct filesystem access
Status: SATISFIED — AtomicFile-backed writeFile, UTF-8 readFile, fileExists, listOrgFiles all implemented with Environment.isExternalStorageManager() guard on every operation.

**FOUND-06** — Onboarding flow for storage permission + Syncthing folder selection
Status: SATISFIED — Full onboarding flow with permission request, lifecycle re-check on resume, folder path input, DataStore persistence, and navigation shell with back-stack management.

**SYNC-01** — Write org files to Syncthing-managed folder
Status: BLOCKED — SyncthingFileBackend.writeFile() exists and is correct, but no feature code calls it. Capability is present; end-to-end write is not.

**SYNC-02** — Read org files from Syncthing-managed folder (two-way)
Status: BLOCKED — Same as SYNC-01. SyncthingFileBackend.readFile() exists but has no caller.

**SYNC-03** — Handle Syncthing .sync-conflict files gracefully
Status: PARTIAL — listOrgFiles() filters .sync-conflict files from the listing. readFile() accepts a filename parameter so a conflict file can only be accessed if explicitly named (a caller would have to request "food-log.sync-conflict-xxx.org" by name). However, no runtime code exercises this path in Phase 1, and there is no UI notification when conflicts exist.

**SYNC-06** — Graceful degradation when sync folder unavailable
Status: BLOCKED — SyncBackendError.FolderUnavailable is thrown by SyncthingFileBackend but no UI handler catches it. The app does not crash (no file I/O is attempted), but it also does not show any error. This satisfies "no crash" but not "shows a graceful error."

### Anti-Patterns Found

**AppNavHost.kt line 56** — Comment: "Phase 1 placeholder main screen — shows setup confirmation."
Severity: Info — SetupCompleteScreen is explicitly scoped as a Phase 1 placeholder; this is intentional and documented. It does not block any Phase 1 goal. Phase 2 will replace it with the Today screen.

No TODO/FIXME/XXX comments in feature source files. No empty return {} or return null in feature code. No console.log equivalents (no Timber.log-only handlers).

### Human Verification Required

**1. Graceful degradation on permission revoke**
Test: With the app on the Setup Complete screen, go to Settings > Apps > Sakura > Permissions and revoke All Files Access. Return to the app.
Expected: App remains on Setup Complete screen without crashing. If the onboarding re-check fires (DisposableEffect ON_RESUME in OnboardingScreen is no longer active at this point), the app may silently remain on Setup Complete.
Why human: Requires physical device; the absence of file I/O in Phase 1 means there may be no observable crash but also no error message — a human is needed to confirm the actual behavior matches SC5's intent.

**2. Food entry format org-lint validation end-to-end**
Test: Push food-log-sample.org to the device Syncthing folder (adb push food-log-sample.org /storage/emulated/0/Syncthing/roaming/health/food-log.org). Open in Emacs and run M-x org-lint.
Expected: Zero lint errors.
Why human: Requires Emacs on host and ADB access to device; confirms the committed format is what the device would actually serve.

### Gaps Summary

Two of the three gaps (SC3 and SC4) are structural: Phase 1 intentionally does not include food or workout logging screens. The org engine produces provably correct format (15 JVM tests, org-lint validated sample), but writing a food or workout entry to disk via user action requires Phase 2 and Phase 3 features respectively. These criteria as stated in the ROADMAP cannot be fully satisfied until those phases complete.

The third gap (SC5) is a wiring gap within Phase 1's own scope. SyncBackendError infrastructure exists and is correct. What is missing is any UI pathway that catches these errors when the Syncthing folder is unavailable. Since Phase 1 triggers no file I/O at runtime, the app neither crashes nor shows an error — it simply never attempts to access the folder. The PLAN's graceful degradation test (steps 12-13) does not appear in the 01-03-SUMMARY's reported results.

The workout-log-sample.org working tree corruption is informational: the committed version is correct, the local modification was never staged or committed, and it has no impact on the built APK.

---
*Verified: 2026-04-09*
*Verifier: Claude (gsd-verifier)*
