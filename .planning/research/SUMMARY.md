# Project Research Summary

**Project:** Origami — Personal Android food/nutrition and workout tracking app
**Domain:** Native Android app (Kotlin + Jetpack Compose), org-mode plain text backend, Syncthing file sync, sideloaded APK on Galaxy S21 FE
**Researched:** 2026-04-09
**Confidence:** HIGH (stack and architecture sourced from official Android documentation; org parser approach MEDIUM due to absence of maintained JVM libraries)

> **Platform pivot note:** This summary replaces the earlier iOS/SwiftUI-focused version. The project moved from iOS (SwiftUI, iCloud Drive, provisioning profiles) to Android (Galaxy S21 FE, Syncthing, ADB sideload). The iOS approach was blocked by: $99/year Apple Developer account required for iCloud entitlement, 7-day provisioning profile expiration on free accounts, and sandboxed file access requiring NSFileCoordinator. All of these constraints disappear on Android.

---

## Executive Summary

Origami is a single-user personal tracking tool: food/macro logging and workout logging written to org-mode plain text files, synced to a desktop Emacs setup via Syncthing. The recommended implementation is native Android — Kotlin 2.3.20 + Jetpack Compose + MVVM + Repository pattern — with a custom org-mode parser/writer built in-house (no viable maintained JVM org library exists). The stack is mature and well-documented; the only novel technical risk is the org-mode serializer, which must produce Emacs-valid output from day one or downstream Emacs workflows break silently.

The Android platform resolves every blocker the iOS approach encountered. `MANAGE_EXTERNAL_STORAGE` grants direct `java.io.File` access to the Syncthing-managed folder — no entitlements, no coordinator APIs, no metadata queries. Sideloading via ADB is permanent (no 7-day expiry). Syncthing on Android is a background daemon that keeps the directory in sync; the app reads and writes files without any awareness of the sync mechanism. The architecture can be summarized as: org files are the database, Syncthing is the network, Emacs is the desktop client.

The primary risk is org file corruption from a bad serializer. A single wrong write that Emacs cannot parse breaks the entire value proposition of the app. The mitigation is clear: build the OrgEngine (parser + writer) first, validate it against real Emacs with `org-lint`, use append-only writes rather than full round-trip reserialize, and require the Emacs `org-lint` check as a mandatory gate before any phase is considered done. Secondary risks are file access permission consistency (the approach — `MANAGE_EXTERNAL_STORAGE` vs. SAF — must be locked in Phase 1 and applied uniformly), Samsung-specific sideloading friction (Auto Blocker), and Google's developer verification requirement post-August 2026 for non-ADB installs.

---

## Key Findings

### Recommended Stack

The Android stack is conventional and well-supported. Kotlin 2.3.20 (K2 compiler) is the language; Jetpack Compose 1.10.6 (via BOM 2026.04.00) is the UI framework; ViewModel + StateFlow + Kotlin Coroutines is the state management pattern. Navigation Compose 2.9.7 handles a three-screen app (Today, Food Log, Workout Log). Navigation 3 is now stable (1.0.1, February 2026) but represents a significant API change; Navigation 2 is the right choice for a three-screen personal app. Jetpack DataStore handles the one small settings concern: the sync directory path. Manual DI via an AppContainer is recommended for v1 given the small class count; Hilt is a clean upgrade path if complexity grows.

No database. The org files are the data store. Room would create dual sources of truth and defeat the purpose. Custom in-memory models parsed from org text at read time, serialized back on write. The `SyncBackend` interface abstracts filesystem access so that a future `LocalServerBackend` can replace `SyncthingFileBackend` by changing one DI binding, with zero changes to any ViewModel or screen.

**Core technologies:**
- Kotlin 2.3.20: Primary language — K2 compiler, Kotlin-first Jetpack libraries, no Java for new code
- Jetpack Compose 1.10.6 (BOM 2026.04.00): Declarative UI, Material 3, coroutine-native — the standard for new Android apps
- ViewModel + StateFlow + `collectAsStateWithLifecycle()`: Standard state management pattern; replaces LiveData for new code
- `MANAGE_EXTERNAL_STORAGE` + `java.io.File`: Direct filesystem access to Syncthing-managed folder — the key architectural simplification vs. iOS
- Custom OrgParser / OrgWriter: No maintained JVM org library exists; a purpose-built parser for the exact format the app produces is simpler and safer than a general one
- Jetpack DataStore (Preferences) 1.2.1: Sync directory path and app settings; replaces SharedPreferences (ANR risk)
- Manual DI (AppContainer in Application class): Proportionate to the dependency graph size for v1; Hilt adds if justified

### Expected Features

Features research is platform-agnostic and remains valid post-pivot. References to "iCloud Drive" in FEATURES.md map to "Syncthing-managed folder" for Android.

**Must have (table stakes — v1):**
- Log food manually (name + protein/carbs/fat/calories) — core logging mechanic
- Custom food library (saved frequent foods) — eliminates repeat entry friction
- Meal templates / saved meals (e.g., "usual breakfast") — daily use accelerant
- Daily macro targets (static, user-set) — need a goal to track against
- Today's home screen (macros so far + today's workout) — the primary daily touchpoint
- Log workout (exercise, sets, reps, weight) with 4-day split templates — core mechanic
- Previous session auto-fill — non-negotiable for progressive overload
- Write to org files and read history from org — the entire architectural requirement
- PR tracking (automatic from workout history) — intrinsic motivation

**Should have (differentiators — v1.x after validation):**
- Barcode scanner — trigger: manual entry proves a bottleneck for packaged foods
- Richer analytics (weekly macro averages, volume trends) — trigger: history has accumulated
- Edit past entries in-app — trigger: errors need correction without opening Emacs

**Defer to v2+:**
- Local server sync (explicit v2 goal) — Syncthing covers v1 completely
- Adaptive calorie targets (TDEE-based) — algorithmic complexity; static targets work
- Micronutrient tracking — macro tracking is the stated v1 goal

**Deliberately not building:**
- Social features, gamification, AI photo logging, notifications, cloud food database, any Google account dependency

### Architecture Approach

MVVM + Repository with an interface-backed sync layer. ViewModels expose `StateFlow<UiState>` (sealed class: Loading/Success/Error) consumed by composables via `collectAsStateWithLifecycle()`. A Repository layer beneath ViewModels decouples them from the org file format and sync mechanism. A `SyncBackend` interface wraps filesystem reads and writes, making the swap from Syncthing to a local server a one-binding change in the DI module. The OrgEngine (OrgParser + OrgWriter) is pure Kotlin with zero Android imports — independently unit-testable without an emulator. It is the most critical component and must be built and validated first.

**Major components:**
1. OrgEngine (OrgParser + OrgWriter + OrgModels) — pure Kotlin; converts org text to typed domain models and back; the entire data contract with Emacs
2. SyncBackend (interface) — `SyncthingFileBackend` v1 uses `java.io.File` on `Dispatchers.IO`; future `LocalServerBackend` slots in via DI swap
3. FoodRepository / WorkoutRepository (interfaces + concrete) — decouple ViewModels from org format and sync mechanism; all file I/O is contained here
4. TodayViewModel / FoodLogViewModel / WorkoutLogViewModel — screen-level state via `StateFlow<UiState>`; call repository methods only, never file I/O directly
5. AppNavHost — Navigation Compose 2.9.7, type-safe routes, three screens, bottom nav bar

### Critical Pitfalls

A notable tension exists between STACK.md and PITFALLS.md on the file access approach: STACK.md recommends `MANAGE_EXTERNAL_STORAGE` + `java.io.File` as simpler and appropriate for a sideloaded personal app; PITFALLS.md recommends SAF (`ACTION_OPEN_DOCUMENT_TREE` + `DocumentFile`) as the correct long-term pattern. **Resolution for v1: use `MANAGE_EXTERNAL_STORAGE`.** The sideload-only constraint removes all Play Store policy concerns, and SAF adds material complexity (URI persistence across reinstalls, `DocumentFile` API vs. `java.io.File`, mode strings for `openOutputStream`). Isolate all file access in `SyncBackend` so migration to SAF, if ever needed, is contained to one class.

1. **Org-mode serializer corrupting Emacs-readable files** — Use append-only writes (never full round-trip reserialize). Hardcode canonical formatting; no configurable whitespace. Generate timestamps with explicit `Locale.ENGLISH`. Require `M-x org-lint` in Emacs on actual output as a mandatory gate before any phase is marked done.

2. **File I/O on the main thread (ANR)** — Every `File.readText()` / `File.writeText()` must run in `withContext(Dispatchers.IO)`. Inject dispatchers for testability. No file I/O in composable function bodies — always in `viewModelScope.launch`.

3. **Stale in-memory file content after Syncthing sync from desktop** — Always read-before-write: re-read the file fresh before every write operation. On app foreground, check `File.lastModified()` and re-parse if the file has changed since last read.

4. **Syncthing conflict files (.sync-conflict) contaminating the active org file** — Filter all file enumeration to exclude filenames containing `.sync-conflict`. Add a conflict-detection warning banner in the UI. Write via temp file then rename to minimize mid-write conflict detection window.

5. **Samsung One UI Auto Blocker silently blocking reinstalls** — Verify Settings > Security and privacy > Auto Blocker is OFF on the Galaxy S21 FE before the first ADB install. This is Phase 0, not Phase 1 — it must be confirmed before any build is attempted.

6. **Google developer verification requirement post-August 2026** — Register for a free Android Developer Console limited distribution account before August 2026. ADB install from a connected Mac bypasses this restriction during active development regardless.

---

## Implications for Roadmap

### Phase 0: Device Setup and Development Environment

**Rationale:** Several blockers exist at the device and toolchain level that silently undermine Phase 1 if not resolved first. These are preconditions, not features.

**Delivers:** A working ADB-connected Galaxy S21 FE, a buildable Android Studio project, Syncthing installed and configured with the org file sync folder, `MANAGE_EXTERNAL_STORAGE` granted.

**Actions:**
- Verify Auto Blocker is disabled (Settings > Security and privacy > Auto Blocker > Off)
- Verify `adb install -r` deploys and the updated version appears on device
- Install Syncthing-Fork from F-Droid; configure sync folder to match the desktop Syncthing share
- Create Android Studio project: Kotlin DSL (`build.gradle.kts`), version catalog (`libs.versions.toml`), full dependency set from STACK.md
- Manually grant `MANAGE_EXTERNAL_STORAGE` in Settings > Apps; verify `Environment.isExternalStorageManager()` returns true
- Register for Android Developer Console limited distribution account (free; required before August 2026)

**Avoids:** Auto Blocker blocking reinstalls; developer verification friction post-August 2026

**Research flag:** Standard setup steps — no additional research needed

---

### Phase 1: Foundation — OrgEngine, File Layer, Permission Setup

**Rationale:** The org-mode serializer is the highest-risk technical unknown. All data features depend on it. Validating round-trip correctness before building any UI means failures are caught cheaply. The file access layer (permission request flow, `SyncBackend`) must be established before any ViewModel or screen can make a real read/write.

**Delivers:** A validated org-mode parser and writer that round-trips correctly through Emacs; a working `SyncthingFileBackend` that reads and writes files from the Syncthing-managed directory; a one-time onboarding screen for the permission grant and sync folder path configuration (user-configurable path stored in DataStore).

**Features addressed:**
- Write to org-mode files (the foundational architectural requirement — validates the format contract with Emacs)
- Sync folder path configuration (stored in DataStore; default path suggested, user can change)

**Architecture components:**
- OrgEngine: OrgParser, OrgWriter, OrgModels (pure Kotlin, zero Android imports)
- SyncBackend interface + SyncthingFileBackend concrete (`java.io.File` on `Dispatchers.IO`)
- DataStore setup for sync directory path
- AppContainer (manual DI) with parser, repository stubs, and sync backend wired up
- Onboarding/setup screen: `MANAGE_EXTERNAL_STORAGE` request flow + folder path configuration

**Pitfalls to avoid:**
- Org serializer corruption: validate with `M-x org-lint` in Emacs on actual output before this phase is complete
- Stale file content: implement read-before-write from the start
- Conflict file filtering: must be in file enumeration from the start; never open `.sync-conflict` files
- Main-thread file I/O: `StrictMode` enabled during development; disk-on-main-thread penalty set

**Research flag:** OrgEngine has no reference library implementation — budget time for iterative Emacs validation. All other components follow well-documented official Android patterns (no research phase needed).

---

### Phase 2: Food Logging MVP

**Rationale:** Food logging is the simpler of the two tracking domains (no active session concept, no previous-session lookup) and delivers immediate daily value. Validate the full data flow — entry form, ViewModel, repository, org write, Emacs round-trip — with the simpler domain before tackling workout sessions.

**Delivers:** Log food entries (name + macros) with meal groupings, view today's food log, see macros logged vs. targets, have entries appear correctly in Emacs after Syncthing syncs.

**Features addressed:**
- Log food manually (name + protein/carbs/fat/calories)
- Meal groupings (breakfast, lunch, dinner, snacks)
- Today's food log view
- Daily macro targets (static, user-configurable)
- Today's macro summary (logged vs. target, remaining)
- Custom food library (saved foods)
- Meal templates / saved meals

**Architecture components:**
- FoodRepository interface + OrgFoodRepository concrete
- FoodLogViewModel with `StateFlow<FoodLogUiState>`
- FoodLogScreen + FoodEntryForm composable
- TodayScreen (food section; workout section added in Phase 3)
- AppNavHost shell: NavHost + bottom nav bar, Today and Food Log destinations

**Pitfalls to avoid:**
- Main-thread file I/O: all `SyncBackend` calls in `withContext(Dispatchers.IO)`
- Form data lost on back navigation: DataStore draft save/restore on every field change
- Numeric keyboard: `KeyboardType.Decimal` for macro fields, test on device not emulator

**Research flag:** Standard Compose + MVVM patterns — no additional research needed

---

### Phase 3: Workout Logging MVP

**Rationale:** Workout logging is more complex than food (active sessions, sets, previous-session auto-fill, split awareness). Building it after Phase 2 means the repository pattern is validated and the second domain module is built with confidence. Previous-session auto-fill is a hard requirement — it cannot be deferred.

**Delivers:** Log a workout session (exercise, sets, reps, weight) using the existing 4-day split templates, with previous session weights/reps auto-filled from org history.

**Features addressed:**
- Log sets, reps, weight per exercise
- Workout templates (4-day full-body split encoded)
- Previous session auto-fill (reads from org history on session start)
- Today's planned workout (which split day is today)
- Rest timer (simple countdown)
- Workout history view (read from org files)
- PR tracking (automatic: scan history for new set-rep or estimated 1RM bests)

**Architecture components:**
- WorkoutRepository interface + OrgWorkoutRepository concrete
- WorkoutLogViewModel with active session state (current exercise, set builder)
- WorkoutLogScreen + WorkoutEntryForm composable
- TodayScreen updated with workout section (today's split day + last session summary)
- Training split definition (the user's 4-day split encoded as data in the app)

**Pitfalls to avoid:**
- History read for auto-fill must always be fresh: re-read from file, never from cached state
- In-progress session state held in ViewModel (ephemeral) until Save; DataStore draft for crash recovery

**Research flag:** Standard patterns — no additional research needed. Training split encoding is design work (what exercises, what order), not a research question.

---

### Phase 4: Sync Hardening and Daily-Use Polish

**Rationale:** Once both core logging flows work, focus on the friction points that matter for daily use: repeat-entry shortcuts, conflict detection, sync feedback, and navigation polish. These are the features that move "it works" to "I actually use this every day."

**Delivers:** Conflict file warning banner, last-synced timestamp display, app-foreground re-read trigger, form draft auto-save, edit/delete logged entries, past-day history views.

**Features addressed:**
- Syncthing conflict warning banner (surfaces `.sync-conflict` presence to user)
- Last-synced timestamp display ("Data from 2 min ago")
- File change detection on foreground via `File.lastModified()` polling
- Edit/delete logged entries (modify org file entry in-place)
- View past food log days (read org history for dates other than today)

**Pitfalls to avoid:**
- Write-via-temp-then-rename to minimize mid-write conflict window (SAF write truncation analog for `MANAGE_EXTERNAL_STORAGE`)
- Modification timestamp drift: log timestamps after writes and verify they advance

**Research flag:** No additional research needed — conflict detection and file polling patterns are well-defined

---

### Phase 5: Analytics and Charts (v1.x)

**Rationale:** History and analytics require accumulated data to be meaningful. Deferred until daily use has produced a real dataset and patterns are clear. Vico version should be spot-checked at Phase 5 planning time.

**Delivers:** Weekly macro averages, workout volume trends (sets × reps × weight per session), Vico-based trend charts.

**Features addressed:**
- Weekly/monthly macro analytics
- Workout volume tracking
- Trend charts (macro adherence, weight-over-time per exercise)

**Stack additions:**
- Vico 2.x (Compose-native chart library) — added at Phase 5 start; verify current version on GitHub before adding

**Research flag:** Vico version should be confirmed at Phase 5 planning time (MEDIUM confidence at research time). Standard patterns otherwise — no research phase needed.

---

### Phase 6: Local Server Sync Backend (v2)

**Rationale:** The explicit v2 goal from the project brief. Deferred because Syncthing covers v1 completely. The architecture is already prepared: implementing `LocalServerBackend : SyncBackend` and updating one DI binding is all that changes in the app.

**Delivers:** HTTP/socket-based sync backend as an alternative to direct file access; settings UI to choose sync provider.

**Architecture components:**
- `LocalServerBackend` concrete implementation of `SyncBackend`
- Retrofit + OkHttp added at this phase (no network dependencies before Phase 6)
- `SyncModule` DI binding updated (one line)

**Research flag:** Needs dedicated research before planning. The local server design — protocol, API shape, auth model, host device — is undefined. This cannot be planned until the server approach is chosen.

---

### Phase Ordering Rationale

- Phase 0 before everything: Device and toolchain blockers (Auto Blocker, ADB, Syncthing, permissions) must be resolved before any code runs on device
- Phase 1 before UI phases: OrgEngine is the foundation every data operation depends on; validating it first means corruption bugs are caught when they are cheapest to fix
- Phase 2 (food) before Phase 3 (workout): Food logging is simpler — no session state, no previous-session lookup — and validates the full stack with lower complexity
- Phase 4 after both logging phases: Polish and hardening requires knowing the actual daily friction points, which only emerge after both flows are working
- Phase 5 after data accumulates: Analytics are only useful with real data; also introduces a chart dependency that has no place in the MVP
- Phase 6 last: Architecture is prepared; defer until there is a concrete local server to implement against

### Research Flags

Phases needing dedicated research before planning task breakdown:
- **Phase 6 (Local Server Sync):** Protocol, API shape, auth model, and host device are all undefined. Cannot be planned until the server design is determined.

Phases with standard, well-documented patterns (skip research phase):
- **Phase 0:** Device setup steps are concrete and documented
- **Phases 2 and 3:** Compose + MVVM + Repository patterns are extensively documented via official Android docs; no novel decisions
- **Phase 4:** Conflict detection, file polling, and draft auto-save follow established patterns
- **Phase 5:** Spot-check Vico version at planning time; otherwise standard integration work

Phase requiring iterative validation discipline (not a research gap):
- **Phase 1 (OrgEngine):** The approach is clear, but the serializer must be validated against real Emacs at every iteration. Budget time for this; the `org-lint` gate is mandatory, not optional.

---

## Confidence Assessment

**Stack — HIGH**
All core library versions verified against official Android Jetpack release pages. Kotlin 2.3.20, Compose BOM 2026.04.00, Navigation 2.9.7, Lifecycle 2.10.0, DataStore 1.2.1 all confirmed. `MANAGE_EXTERNAL_STORAGE` behavior for sideloaded APKs confirmed via official Android storage docs. Vico version for Phase 5 is MEDIUM (library exists and is Compose-native; exact version not pinned at research time).

**Features — MEDIUM**
Food/workout app feature landscape is well-documented via competitive analysis. The org-mode integration is novel — no direct comparable exists. Feature priorities are derived from the project brief and competitive research rather than user testing (inherent for a single-user personal app). The v1 scope is appropriately narrow and well-reasoned.

**Architecture — HIGH**
MVVM + Repository + StateFlow + coroutines is the official Google-recommended architecture for Jetpack Compose apps, sourced from official Android Developers docs. File access via `MANAGE_EXTERNAL_STORAGE` + `java.io.File` is well-documented for sideloaded apps. Custom org parser approach is MEDIUM — correct direction confirmed by absence of viable alternatives, but the parser itself is implementation risk, not a research uncertainty.

**Pitfalls — HIGH (device/platform), MEDIUM-HIGH (Syncthing conflicts), MEDIUM (org serializer)**
Auto Blocker, MANAGE_EXTERNAL_STORAGE, developer verification, and SAF pitfalls sourced from official docs or high-authority sources. Syncthing conflict behavior sourced from official Syncthing docs plus multiple corroborating forum threads. Org serializer corruption risks informed by the org-syntax spec and Emacs behavior, not formal testing.

**Overall confidence:** HIGH

### Gaps to Address

- **MANAGE_EXTERNAL_STORAGE vs. SAF decision:** Must be explicitly confirmed as a Phase 1 architectural decision and documented. The codebase must not have both approaches mixed. The chosen approach (MANAGE_EXTERNAL_STORAGE for v1, isolated in SyncBackend) should be recorded in a lightweight ADR before Phase 1 implementation starts.

- **Org file schema definition:** The exact org-mode format the app will write (heading levels, macro notation format, date heading format, meal label format, workout set format) must be formally defined before OrgWriter is implemented. OrgParser and OrgWriter are a coupled pair — the format is the contract. Write 3-5 sample org entries by hand in Emacs first; confirm they parse correctly; codify that format as the OrgWriter spec. This is a design deliverable of Phase 1 planning, not a research question.

- **Syncthing-Fork version:** PITFALLS.md notes a confirmed spurious conflict bug in Syncthing 1.22.x on Android. The exact current Syncthing-Fork version was not pinned at research time. Verify the installed version against known stable releases before Phase 1 ends.

- **Training split definition:** The user's 4-day full-body split (exercises, set/rep targets, day ordering) must be encoded in the app for Phase 3. This is domain knowledge, not a research question — but it must be captured in writing before Phase 3 planning.

- **Vico version for Phase 5:** Not verified at research time. Spot-check the latest Vico release on GitHub before Phase 5 planning begins.

---

## Sources

### Primary (HIGH confidence)
- https://developer.android.com/jetpack/androidx/releases/compose — Compose BOM 2026.04.00, compose-ui 1.10.6, material3 1.4.0
- https://developer.android.com/jetpack/androidx/releases/navigation — Navigation Compose 2.9.7 latest stable
- https://developer.android.com/jetpack/androidx/releases/navigation3 — Navigation 3 stable 1.0.1, February 2026
- https://developer.android.com/jetpack/androidx/releases/lifecycle — Lifecycle ViewModel Compose 2.10.0
- https://developer.android.com/jetpack/androidx/releases/datastore — DataStore Preferences 1.2.1
- https://developer.android.com/develop/ui/compose/architecture — Compose MVVM, UDF, StateFlow, collectAsStateWithLifecycle
- https://developer.android.com/kotlin/coroutines/coroutines-best-practices — Dispatcher injection, viewModelScope, Dispatchers.IO
- https://developer.android.com/training/dependency-injection/hilt-android — Hilt; manual DI recommendation for simple apps
- https://developer.android.com/training/data-storage/manage-all-files — MANAGE_EXTERNAL_STORAGE, runtime permission check
- https://developer.android.com/guide/topics/providers/document-provider — SAF, ACTION_OPEN_DOCUMENT_TREE, DocumentFile
- https://developer.android.com/training/data-storage — Scoped Storage overview, SAF vs MANAGE_EXTERNAL_STORAGE guidance
- https://blog.jetbrains.com/kotlin/2026/03/kotlin-2-3-20-released/ — Kotlin 2.3.20 latest stable
- https://developer.android.com/build/releases/agp-9-1-0-release-notes — AGP 9.1.0, Gradle 9.3.1, JDK 17 requirement
- https://androidstudio.googleblog.com/2026/04/android-studio-panda-3-202533-now.html — Android Studio Panda 3 (2025.3.3)
- https://android-developers.googleblog.com/2025/05/announcing-jetpack-navigation-3-for-compose.html — Navigation 3 announcement
- https://android-developers.googleblog.com/2026/03/android-developer-verification.html — Developer verification requirement, August 2026
- https://www.sammobile.com/news/galaxy-s21-fe-one-ui-7-update-released-europe-asia/ — Galaxy S21 FE received Android 15 (One UI 7), May 2025
- https://orgmode.org/worg/org-syntax.html — Org-mode syntax specification (official)

### Secondary (MEDIUM confidence)
- https://forum.syncthing.net/t/file-conflict-for-every-modification-on-android/19272 — Syncthing spurious conflict bug on Android; corroborated by multiple threads
- https://forum.syncthing.net/t/android-11-all-files-access-for-the-syncthing-app/14651 — Syncthing uses MANAGE_EXTERNAL_STORAGE for storage access
- https://f-droid.org/packages/com.github.catfriend1.syncthingfork/ — Syncthing-Fork active F-Droid listing, March 2026
- https://github.com/pmiddend/org-parser — JVM org parser: last commit August 2016, no releases, abandoned
- https://github.com/iliayar/kotlin-org-mode — Kotlin org library: GPL-3.0, partial implementation, no Android evidence
- https://mvnrepository.com/artifact/com.google.dagger/hilt-android — Hilt 2.57.1 version cross-reference
- https://www.sammyfans.com/2024/07/24/one-ui-6-1-1-auto-blocker-bans-app-sideloading-on-samsung-phones/ — One UI Auto Blocker sideloading behavior
- https://support.google.com/android-developer-console/answer/16561738 — Developer verification, limited distribution account
- Feature competitive sources: Nutrola, Stronger Mobile, Setgraph, PRPath, Hevy, Askvora, Fitbod (2026 macro/workout app comparison research)

### Tertiary (LOW confidence / informational)
- https://github.com/patrykandpatrick/vico — Vico Compose-native chart library (actively maintained; exact version not pinned at research time)

---
*Research completed: 2026-04-09*
*Platform: Android (refresh after pivot from iOS — replaces iOS-focused SUMMARY.md)*
*Ready for roadmap: yes*
