# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-09)

**Core value:** One cohesive system for food and workout tracking that the user fully controls, with data living in org files that flow into their existing Emacs workflow.
**Current focus:** Phase 4 - Dashboard and Polish

## Current Position

Phase: 4 of 5 (Dashboard and Polish) — In progress
Plan: 1 of 3 in current phase — COMPLETE
Status: In progress — plan 04-01 complete, paused at checkpoint for human verification
Last activity: 2026-04-12 — Completed 04-01 (home screen + navigation restructure)

Progress: [█████████░] 90% (9/10 plans complete)

## Performance Metrics

**Velocity:**
- Total plans completed: 9
- Average duration: ~11 min
- Total execution time: ~93 min

**By Phase:**
- Phase 1: 3 of 3 plans done, ~39 min total — COMPLETE
- Phase 2: 2 of 2 plans done — COMPLETE
- Phase 3: 3 of 3 plans done, ~33 min total — COMPLETE
- Phase 4: 1 of 3 plans done, ~8 min so far — In progress

**Recent Trend:**
- Last 3 plans: 03-02 (~8 min), 03-03 (~20 min), 04-01 (~8 min)
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
- [04-01]: FoodLogScreen/WorkoutLogScreen onNavigateToSettings removed — settings now accessed via shared nav bar only

### Pending Todos

- Document Samsung One UI Auto Blocker disable step in device setup notes
- WORK-07 (rest timer) not implemented — deferred, not critical for v1
- WORK-08 (today's workout on home screen) — COMPLETE in 04-01

### Blockers/Concerns

- [Phase 1]: Samsung One UI Auto Blocker must be disabled before first ADB install (Settings > Security and privacy > Auto Blocker > Off)
- [General]: Developer verification requirement takes effect August 2026 — register for free Android Developer Console limited distribution account before then
- [Build]: gradlew requires JAVA_HOME=/Users/marcosandrade/.local/jdk/jdk-17.0.18+8/Contents/Home — JDK 17 not on system PATH

## Session Continuity

Last session: 2026-04-12T17:35:31Z
Stopped at: 04-01 complete — paused at checkpoint:human-verify (tasks 1-3 done)
Resume file: None
Next: Continue 04-01 checkpoint (approve or report issues), then proceed to 04-02 and 04-03
