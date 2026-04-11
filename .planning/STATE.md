# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-09)

**Core value:** One cohesive system for food and workout tracking that the user fully controls, with data living in org files that flow into their existing Emacs workflow.
**Current focus:** Phase 2 - Food Logging

## Current Position

Phase: 2 of 4 (Food Logging)
Plan: 2 of 3 in current phase — COMPLETE (device verified on Galaxy S21 FE)
Status: In progress — 02-02 complete, ready for 02-03
Last activity: 2026-04-11 — Completed 02-02 (food logging UI, device verification approved)

Progress: [█████░░░░░] 56% (5/9 plans complete)

## Performance Metrics

**Velocity:**
- Total plans completed: 4
- Average duration: ~11 min
- Total execution time: ~47 min

**By Phase:**
- Phase 1: 3 of 3 plans done, ~39 min total — COMPLETE
- Phase 2: 2 of 3 plans done (02-02 multi-session with device testing)

**Recent Trend:**
- Last 5 plans: 01-01 (12 min), 01-02 (~15 min), 01-03 (~12 min), 02-01 (~8 min)
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
- [01-02]: Property drawers chosen as the canonical org format for all structured data — supersedes the inline pipe notation from 01-01. Format: :PROPERTIES: / :protein: 42 / :carbs: 55 / :fat: 8 / :calories: 460 / :END: under each list item. Property drawers are first-class org-mode elements natively queryable by org-ql, orgparse, Emacs Lisp org-entry-get, and other tooling.
- [02-01]: OrgLibraryEntry separate from OrgFoodEntry — library items use UUID String ids; log entries use epoch millis Long ids. Cannot share one model without breaking type correctness.
- [02-01]: applyTemplate acquires fileMutex once for the full batch and calls addEntryInternal() — avoids per-entry lock overhead, ensures atomic batch application.
- [02-01]: OrgFoodEntry.id defaults to 0L — backward-compatible with all existing tests and legacy log files that predate this field.
- [02-02]: combine() over same-value reassignment for reload — MutableStateFlow.equals() short-circuits; _reloadTrigger counter forces downstream re-execution without changing the primary key
- [02-02]: ViewModel.factory() companion with CreationExtras.createSavedStateHandle() — standard pattern for manual DI without Hilt; used in FoodLogViewModel, to be followed by all future ViewModels
- [02-02]: invokeOnCompletion dismiss pattern for ModalBottomSheet — prevents race between hide animation and onDismiss; applied to all bottom sheets in the project

### Pending Todos

- Document Samsung One UI Auto Blocker disable step in device setup notes

### Blockers/Concerns

- [Phase 1]: Samsung One UI Auto Blocker must be disabled before first ADB install (Settings > Security and privacy > Auto Blocker > Off)
- [Phase 3]: User's 4-day full-body split (exercises, set/rep targets, day order) must be documented before Phase 3 planning
- [General]: Developer verification requirement takes effect August 2026 — register for free Android Developer Console limited distribution account before then
- [Build]: gradlew requires JAVA_HOME=/Users/marcosandrade/.local/jdk/jdk-17.0.18+8/Contents/Home — JDK 17 not on system PATH

## Session Continuity

Last session: 2026-04-11 UTC
Stopped at: 02-02 complete — device verification approved on Galaxy S21 FE
Resume file: None
Next plan: 02-03-PLAN.md (food library/template management screens, if needed)
