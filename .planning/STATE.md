# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-09)

**Core value:** One cohesive system for food and workout tracking that the user fully controls, with data living in org files that flow into their existing Emacs workflow.
**Current focus:** All phases complete — milestone ready for audit

## Current Position

Phase: 5 of 5 (Local Storage Mode) — COMPLETE
Plan: 2 of 2 in current phase — COMPLETE
Status: All 5 phases complete, milestone ready for audit
Last activity: 2026-04-13 — Completed Phase 5 (local storage mode)

Progress: [██████████] 100% (12/12 plans complete)

## Performance Metrics

**Velocity:**
- Total plans completed: 12
- Average duration: ~10 min
- Total execution time: ~130 min

**By Phase:**
- Phase 1: 3 of 3 plans done, ~39 min total — COMPLETE
- Phase 2: 2 of 2 plans done — COMPLETE
- Phase 3: 3 of 3 plans done, ~33 min total — COMPLETE
- Phase 4: 2 of 2 plans done, ~17 min total — COMPLETE
- Phase 5: 2 of 2 plans done, ~11 min total — COMPLETE

**Recent Trend:**
- Last 3 plans: 04-02 (~9 min), 05-01 (~3 min), 05-02 (~8 min)
- Trend: stable

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Setup]: MANAGE_EXTERNAL_STORAGE (not SAF) for v1 — sideload-only removes Play Store policy concerns; isolate in SyncBackend for future migration
- [Setup]: No Room database — org files are the data store; dual sources of truth would defeat the purpose
- [Setup]: Custom OrgEngine (no JVM org library exists that is maintained)
- [Setup]: Navigation Compose 2 (not Nav 3) — three-screen personal app does not justify Nav 3 API surface
- [01-01]: kotlin-compose plugin (org.jetbrains.kotlin.plugin.compose 2.1.20) required with AGP 9.1.0 / Kotlin 2.x — apply alongside android.application in app/build.gradle.kts
- [01-01]: JDK 17 installed to ~/.local/jdk/jdk-17.0.18+8/; all gradlew invocations need JAVA_HOME set explicitly
- [01-01]: Android SDK bootstrapped to ~/Library/Android/sdk/; local.properties is gitignored; must be set manually on new machines
- [01-02]: Property drawers chosen as the canonical org format for all structured data
- [02-01]: OrgLibraryEntry separate from OrgFoodEntry — library items use UUID String ids; log entries use epoch millis Long ids
- [02-01]: applyTemplate acquires fileMutex once for the full batch and calls addEntryInternal()
- [02-02]: combine() over same-value reassignment for reload — _reloadTrigger counter pattern
- [02-02]: ViewModel.factory() companion with CreationExtras.createSavedStateHandle()
- [02-02]: invokeOnCompletion dismiss pattern for ModalBottomSheet
- [03-01]: OrgExerciseLog + OrgSetEntry at org levels 3+4 (not flattened)
- [03-01]: findPersonalBest returns null when no prior history
- [03-02]: Incremental per-set write via fileMutex.withLock read-modify-write — mirrors OrgFoodRepository.addEntry
- [03-02]: WorkoutSession.splitDay nullable — freestyle days are first-class
- [03-02]: User exercise persistence via DataStore JSON in AppPreferencesRepository
- [03-03]: NavigationBar replaces PrimaryTabRow — consistent 4-tab bottom nav (FOOD/WORKOUT/HOME/SETTINGS)
- [03-03]: setInputExerciseId cleared immediately on log — prevents reload race
- [03-03]: PR check runs before addSet so isPr=true is persisted to org file
- [04-01]: compileSdk bumped to 36 (required by vico 3.1.0); targetSdk stays 35 — no runtime behavior change
- [04-01]: gradle.properties created with Xmx2048m — default 512MiB OOMs during vico dex merge
- [04-01]: MainScaffold pattern: single Scaffold at AppNavHost level with shared NavigationBar; isTabDestination() extension controls visibility
- [04-01]: SyncStatus data class co-located in SyncBackend.kt with interface
- [04-01]: Vico org.jetbrains.compose.material3 excluded in build.gradle.kts to prevent DatePicker NoSuchMethodError
- [04-02]: Vico 3.1.0 API: Fill(SolidColor(color)) required, LineCartesianLayer.Line direct constructor, MergeMode.Grouped() instantiation
- [04-02]: SplitCalendar uses Column/Row (not LazyVerticalGrid) to avoid nested scrollable containers
- [05-01]: StorageMode enum co-located in AppPreferencesRepository.kt (file level, outside class)
- [05-01]: storageMode Flow returns null for "not yet selected" — MainActivity falls back to LOCAL at runtime without persisting
- [05-01]: AppContainer constructed in Composable via remember(resolvedMode) — SakuraApplication holds only prefsRepo
- [05-01]: isLocalMode boolean on DashboardTodayState — ViewModel reads storageMode.first(), converts to bool for UI
- [05-02]: DisposableEffect for permission checking guarded behind Syncthing-path states only
- [05-02]: clearOnboardingComplete() added for Local→Syncthing migration path
- [05-02]: Migration uses ComponentActivity.recreate() for full container reconstruction

### Pending Todos

- Document Samsung One UI Auto Blocker disable step in device setup notes
- WORK-07 (rest timer) not implemented — deferred, not critical for v1
- UI polish items noted by user during Phase 4 verification — to address after all phases

### Blockers/Concerns

- [Phase 1]: Samsung One UI Auto Blocker must be disabled before first ADB install (Settings > Security and privacy > Auto Blocker > Off)
- [General]: Developer verification requirement takes effect August 2026 — register for free Android Developer Console limited distribution account before then
- [Build]: gradlew requires JAVA_HOME=/Users/marcosandrade/.local/jdk/jdk-17.0.18+8/Contents/Home — JDK 17 not on system PATH

## Session Continuity

Last session: 2026-04-13 UTC
Stopped at: Phase 5 complete — all local storage mode features implemented and verified
Resume file: None
Next action: Milestone audit — all 5 phases complete
