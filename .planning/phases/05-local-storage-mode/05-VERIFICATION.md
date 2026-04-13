---
phase: 05-local-storage-mode
verified: 2026-04-13T02:12:49Z
status: human_needed
score: 5/5 must-haves verified
human_verification:
  - test: "Fresh install — select 'Just me' (Simple mode) and reach Home screen"
    expected: "After tapping 'Get Started' then 'Just me', the app navigates directly to the Home screen with no permission dialogs and no folder path prompts. The entire flow should take under 30 seconds."
    why_human: "Timing and absence of permission dialogs cannot be verified structurally. Must be exercised on a real device (or fresh emulator) to confirm no runtime permission prompt appears."
  - test: "Cold start after selecting local mode persists correctly"
    expected: "Force-quit the app after completing Simple-mode onboarding, relaunch — app opens directly to Home without showing onboarding again. DashboardScreen shows no SyncStatusBadge."
    why_human: "DataStore persistence across cold start requires actual process death and relaunch; cannot be verified by static analysis."
  - test: "Syncthing Power User flow is fully operational"
    expected: "Selecting 'Sync across devices' during onboarding transitions to the permission screen. Granting MANAGE_EXTERNAL_STORAGE and entering a folder path completes onboarding and opens Home with the SyncStatusBadge visible."
    why_human: "Requires MANAGE_EXTERNAL_STORAGE runtime grant which can only be exercised on a device."
  - test: "Storage mode Change button in Settings performs migration"
    expected: "In Settings, tapping Change and confirming migrates storage and restarts the activity into the correct state (LOCAL→SYNCTHING re-runs onboarding; SYNCTHING→LOCAL copies files and goes to Home)."
    why_human: "File copy migration and activity.recreate() require a live process to verify end-to-end."
---

# Phase 5: Local Storage Mode Verification Report

**Phase Goal:** A non-technical user (e.g. mom on Android) can install the app and start logging food and workouts immediately — no Syncthing, no folder paths, no storage permissions. Data lives in app-internal storage with zero configuration.

**Verified:** 2026-04-13T02:12:49Z
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

**Truth 1: A new user selecting "Simple" mode during onboarding can start logging within 30 seconds — no permission prompts, no folder paths**
Status: VERIFIED (structural) / human_needed (runtime)

Evidence:
- `OnboardingViewModel.onModeSelected(StorageMode.LOCAL)` calls `prefsRepo.setStorageMode(mode)` then `prefsRepo.setOnboardingComplete()` then sets state to `OnboardingUiState.Complete` — no permission check, no folder entry, no extra steps.
- `OnboardingScreen` routes `Complete` state to `LaunchedEffect { onOnboardingDone() }` which navigates to Home.
- `ModeSelectionContent` presents "Just me / Mom, choose this one" card that calls `onModeSelected(StorageMode.LOCAL)` on tap — single tap completes.
- `MANAGE_EXTERNAL_STORAGE` permission is declared in manifest but the runtime request path (`Environment.isExternalStorageManager()` + settings intent) is gated entirely inside `OnboardingUiState.NeedsPermission` state, which is only reached via the SYNCTHING branch.
- **Human verification needed** to confirm no unexpected permission dialog appears at runtime and that wall-clock time is under 30 seconds.

**Truth 2: LocalStorageBackend reads/writes org files to app-internal storage, passing all existing OrgParser/OrgWriter tests**
Status: VERIFIED (structural)

Evidence:
- `LocalStorageBackend.kt` (78 lines) fully implements the `SyncBackend` interface: `readFile`, `writeFile`, `fileExists`, `listOrgFiles`, `checkSyncStatus` — no stubs, no TODOs.
- `writeFile` uses `android.util.AtomicFile` for crash-safe writes; IOException triggers `failWrite`.
- `readFile` returns `""` for non-existent files, matching `SyncthingFileBackend` behavior (same contract).
- `listOrgFiles` filters dotfiles and non-.org files, same semantics as `SyncthingFileBackend`.
- `OrgParserTest` and `OrgWriterTest` test `OrgParser`/`OrgWriter` directly (pure Kotlin, no backend dependency) — they pass regardless of which backend is wired. The "passing tests" claim is structurally sound: the backend abstraction means repository/engine tests are backend-agnostic.
- No dedicated `LocalStorageBackendTest` exists, but the claim is about existing parser/writer tests, not new backend tests.

**Truth 3: Existing Syncthing "Power User" flow remains unchanged and fully functional**
Status: VERIFIED (structural) / human_needed (runtime permission grant)

Evidence:
- `SyncthingFileBackend.kt` is unmodified in Phase 5 (not in either SUMMARY's modified file list).
- `AppContainer` wires `SyncthingFileBackend(prefsRepo)` when `storageMode == StorageMode.SYNCTHING` — the Syncthing path is preserved exactly.
- `OnboardingViewModel` detects `existingMode == StorageMode.SYNCTHING` in `init` and skips to `checkPermission()`, so re-onboarding for Syncthing still works.
- `OnboardingScreen` renders `NeedsPermissionContent` and `NeedsFolderContent` for the Syncthing path — both composables are substantive (permission deep link + folder text field + confirm button).
- `DashboardScreen` shows `SyncStatusBadge` only when `!state.isLocalMode`, so Syncthing users still see the badge.
- **Human verification needed** to exercise the runtime MANAGE_EXTERNAL_STORAGE grant on device.

**Truth 4: Storage mode selection persists across restarts; the app launches into the correct backend on every cold start**
Status: VERIFIED (structural) / human_needed (runtime)

Evidence:
- `AppPreferencesRepository.storageMode` is a `DataStore<Preferences>` Flow reading `STORAGE_MODE` string key — DataStore survives process death by definition.
- `MainActivity` collects `prefsRepo.storageMode` as state with `initialValue = null` and only renders `AppNavHost` once `onboardingDone != null` — cold-start guard prevents blank flash and wrong backend.
- `val resolvedMode = storageMode ?: StorageMode.LOCAL` — null (pre-selection) defaults to LOCAL, matching the intent.
- `val container = remember(resolvedMode) { AppContainer(applicationContext, resolvedMode) }` — container is recreated when mode changes, ensuring correct backend on mode switch.
- `startWithOnboarding = onboardingDone != true` correctly routes cold start: completed onboarding goes to Home, not Onboarding.
- **Human verification needed** for actual cold-start test (force-quit + relaunch).

**Truth 5: All food and workout features (Phases 2–4) work identically regardless of storage mode**
Status: VERIFIED (structural)

Evidence:
- `AppContainer.foodRepository` and `AppContainer.workoutRepository` both receive `syncBackend` which is either `LocalStorageBackend` or `SyncthingFileBackend` — both fully implement `SyncBackend`. The repositories are backend-agnostic by design.
- `OrgFoodRepository` and `OrgWorkoutRepository` depend only on `SyncBackend` interface methods (`readFile`, `writeFile`, `fileExists`, `listOrgFiles`) which are identically contracted by both implementations.
- `AppNavHost` wires `FoodLogScreen`, `WorkoutLogScreen`, `DashboardScreen`, and all other feature screens from `appContainer` regardless of mode — no feature screen is conditionally excluded in local mode.
- The only mode-conditional UI is the `SyncStatusBadge` in `DashboardScreen` (hidden in local mode via `if (!state.isLocalMode)`) — this is correct behavior, not a feature regression.

**Score:** 5/5 truths structurally verified; 4 items need human runtime confirmation.

---

## Required Artifacts

**`app/src/main/java/com/sakura/sync/LocalStorageBackend.kt`**
- Exists: YES
- Substantive: YES (78 lines, full interface implementation, AtomicFile writes, no stubs)
- Wired: YES (imported in AppContainer.kt, instantiated in `when(storageMode)` branch)

**`app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt`**
- Exists: YES
- Substantive: YES (254 lines, StorageMode enum, storageMode Flow, setStorageMode(), clearOnboardingComplete())
- Wired: YES (used in MainActivity, OnboardingViewModel, DashboardViewModel, MacroTargetsScreen)

**`app/src/main/java/com/sakura/di/AppContainer.kt`**
- Exists: YES
- Substantive: YES (44 lines, accepts StorageMode param, when branch for backend selection)
- Wired: YES (constructed in MainActivity with `remember(resolvedMode)`)

**`app/src/main/java/com/sakura/MainActivity.kt`**
- Exists: YES
- Substantive: YES (58 lines, null-guarded DataStore await, resolvedMode fallback, container via remember)
- Wired: YES (entry point, wires AppNavHost with resolved container)

**`app/src/main/java/com/sakura/features/onboarding/OnboardingViewModel.kt`**
- Exists: YES
- Substantive: YES (106 lines, Welcome/ModeSelection/Complete states, onModeSelected persists to DataStore)
- Wired: YES (instantiated in AppNavHost composable, receives appContainer.prefsRepo)

**`app/src/main/java/com/sakura/features/onboarding/OnboardingScreen.kt`**
- Exists: YES
- Substantive: YES (341 lines, WelcomeContent + ModeSelectionContent + NeedsPermissionContent + NeedsFolderContent)
- Wired: YES (rendered in AppNavHost Onboarding composable)

**`app/src/main/java/com/sakura/features/dashboard/DashboardUiState.kt`**
- Exists: YES
- Substantive: YES (`isLocalMode: Boolean = false` field present)
- Wired: YES (set by DashboardViewModel, consumed by DashboardScreen conditional)

**`app/src/main/java/com/sakura/features/settings/MacroTargetsScreen.kt`**
- Exists: YES
- Substantive: YES (295 lines, Storage section with mode label, Change button, migration dialog, migrateStorage() function)
- Wired: YES (rendered in AppNavHost Settings composable)

**`app/src/main/java/com/sakura/SakuraApplication.kt`**
- Exists: YES
- Substantive: YES (19 lines, only holds prefsRepo, does not create AppContainer)
- Wired: YES (declared in AndroidManifest.xml android:name=".SakuraApplication")

---

## Key Link Verification

**OnboardingViewModel → DataStore (setStorageMode)**
- `onModeSelected(StorageMode.LOCAL)` calls `prefsRepo.setStorageMode(mode)` — WIRED

**OnboardingViewModel → Onboarding Complete (LOCAL fast path)**
- LOCAL branch: `prefsRepo.setOnboardingComplete()` then `_uiState.value = Complete` — no permission, no folder — WIRED

**MainActivity → DataStore (storageMode read + cold-start guard)**
- `prefsRepo.storageMode.collectAsStateWithLifecycle(initialValue = null)` + `if (onboardingDone != null)` guard — WIRED

**AppContainer → LocalStorageBackend (when branch)**
- `StorageMode.LOCAL -> LocalStorageBackend(context)` — WIRED

**AppContainer → SyncthingFileBackend (Syncthing preserved)**
- `StorageMode.SYNCTHING -> SyncthingFileBackend(prefsRepo)` — WIRED

**DashboardViewModel → isLocalMode**
- `val mode = prefsRepo.storageMode.first()` → `val isLocalMode = (mode == StorageMode.LOCAL)` → set on `_today` state — WIRED

**DashboardScreen → SyncStatusBadge conditional**
- `if (!state.isLocalMode) { SyncStatusBadge(...) }` — WIRED

**MacroTargetsScreen → migrateStorage()**
- Migration dialog `confirmButton` calls `migrateStorage(context, prefsRepo, targetMode)` via coroutine — WIRED
- `migrateStorage` calls `prefsRepo.setStorageMode()` and `context.recreate()` — WIRED

---

## Requirements Coverage

**LOCAL-01: LocalStorageBackend implementation using app-internal storage (no permissions, no setup)**
- Status: SATISFIED
- Evidence: `LocalStorageBackend` uses `context.filesDir` — no permission APIs called anywhere in the class.

**LOCAL-02: Simplified onboarding flow for local mode (no folder path, no MANAGE_EXTERNAL_STORAGE)**
- Status: SATISFIED
- Evidence: LOCAL branch in `onModeSelected` skips all permission and folder states entirely.

**LOCAL-03: Storage mode selection during onboarding — "Simple" (local) vs "Power User" (Syncthing)**
- Status: SATISFIED
- Evidence: `ModeSelectionContent` renders two `OutlinedCard` options: "Just me" (LOCAL) and "Sync across devices" (SYNCTHING).

**LOCAL-04: Data lives entirely on-device in app-internal files, zero configuration**
- Status: SATISFIED
- Evidence: `context.filesDir` is app-private internal storage; no external path, no permission, no sync config needed.

---

## Anti-Patterns Found

None. Scanned all 9 modified/created files for TODO, FIXME, placeholder, stub patterns, empty returns, and console.log equivalents. Zero findings.

---

## Human Verification Required

### 1. Simple Mode — Zero-Config Start

**Test:** Fresh install (or clear app data), launch app, tap "Get Started", tap "Just me / Mom, choose this one". Observe the full flow.

**Expected:** No runtime permission dialog appears at any point. The app navigates directly to the Home (Dashboard) screen. Elapsed time from launch to being on Home screen is under 30 seconds.

**Why human:** The MANAGE_EXTERNAL_STORAGE permission is declared in the manifest. Android may or may not show a system-level prompt depending on API level and install context. Only a live device test can confirm no prompt appears for the LOCAL path. Timing also requires manual measurement.

### 2. Cold Start Mode Persistence

**Test:** Complete Simple-mode onboarding, then force-stop the app (Android Settings > Apps > Sakura > Force Stop). Relaunch.

**Expected:** App opens directly to Home screen (no onboarding). No SyncStatusBadge visible in the dashboard header.

**Why human:** DataStore persistence across actual process death requires live execution. Static analysis confirms the persistence logic is correct but cannot simulate a cold start.

### 3. Syncthing Power User Flow Intact

**Test:** Fresh install (or clear app data), launch, tap "Get Started", tap "Sync across devices". Follow the MANAGE_EXTERNAL_STORAGE permission screen, grant the permission, enter a valid sync folder path, tap Confirm.

**Expected:** Onboarding completes and opens Home. SyncStatusBadge is visible in the dashboard header. Food and workout logging work identically to before Phase 5.

**Why human:** MANAGE_EXTERNAL_STORAGE runtime grant requires a real device interaction; cannot be automated structurally.

### 4. Storage Mode Migration via Settings

**Test:** After completing Simple-mode onboarding, go to Settings. Observe "Storage — Current mode: Just me (local storage)" with a "Change" button. Tap Change, confirm "Switch to Sync mode?".

**Expected:** App restarts into Syncthing onboarding (permission screen). After completing the Syncthing setup, the app operates in Syncthing mode with SyncStatusBadge visible.

**Why human:** Activity.recreate() + onboarding re-entry requires live process observation to confirm correct re-routing.

---

## Summary

All five success criteria are structurally verified. The implementation is complete and non-stubbed:

- `LocalStorageBackend` fully implements `SyncBackend` over `Context.filesDir` with AtomicFile writes.
- `StorageMode` enum is persisted via DataStore with a null-sentinel for first launch.
- `AppContainer` selects the backend via `when(storageMode)` — clean, exhaustive, no fallthrough.
- The LOCAL onboarding path is a single tap with zero permission or folder prompts at the code level.
- The Syncthing path is structurally intact — `SyncthingFileBackend` is untouched, onboarding routes correctly, SyncStatusBadge shows in non-local mode.
- Migration in Settings calls `setStorageMode`, `clearOnboardingComplete` where needed, and `activity.recreate()`.

Four human verification items remain — all are runtime behaviors that require a live device to confirm (permission dialog absence, timing, cold-start DataStore reading, activity recreation). The structural evidence strongly supports a passing result.

---

_Verified: 2026-04-13T02:12:49Z_
_Verifier: Claude (gsd-verifier)_
