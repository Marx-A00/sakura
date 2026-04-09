# Project Research Summary

**Project:** Origami — Personal iOS Food and Workout Tracker with Org-Mode Sync
**Domain:** Personal iOS native app (single-user, sideloaded) — food/nutrition logging, workout logging, org-mode plain text files via iCloud Drive
**Researched:** 2026-04-09
**Confidence:** MEDIUM-HIGH

## Executive Summary

Origami is a greenfield personal iOS app with a clear architectural constraint that drives every other decision: data must live in org-mode plain text files, accessible directly from Emacs. This eliminates SwiftData, CoreData, CloudKit, and any traditional database approach — the org files are the database. The recommended implementation is a native SwiftUI app (iOS 17 minimum, Swift 6) with a custom org-mode parser built in Swift Regex/RegexBuilder, plain-file iCloud Drive sync via NSFileCoordinator/NSMetadataQuery, and an abstracted SyncProvider protocol that allows swapping iCloud for a local server in v2 with no changes to the app's other layers. No third-party dependencies are needed or recommended for v1.

The feature scope is well-defined and appropriately narrow for a personal daily-use tool. Both food and workout tracking cover all table-stakes features (manual entry, meal groupings, set/rep/weight logging, previous session auto-fill, PR tracking, today's summary) and the primary differentiator — org-mode output with full data ownership — is the entire reason this app exists. Commercial alternatives such as MyFitnessPal and Hevy lock data behind paywalls and proprietary formats; Origami's export is its format. The product scope is already lean: barcode scanning, AI photo logging, social features, and adaptive calorie targets are correctly deferred.

The dominant risks are in the file I/O layer, not the UI. The org-file write-back must produce syntactically valid org-mode or Emacs will silently misparse entries. iCloud Drive requires NSFileCoordinator on every read and write or data corruption occurs. Placeholder files (not-yet-downloaded .icloud stubs) must be checked and triggered for download before reading, or the app silently treats a cloud file as empty and overwrites it. These pitfalls are all avoidable by building the SyncBackend and OrgEngine layers first, with unit tests and Emacs validation, before writing any UI. The recommended build order from architecture research is: OrgEngine → SyncBackend → Models → Repositories → UI.

## Key Findings

### Recommended Stack

The entire stack is Apple first-party. Swift 6.3 (Xcode 26.4) with iOS 17 as the minimum deployment target provides access to `@Observable`, `@Bindable`, NavigationStack, Swift Charts, Swift Testing, and RegexBuilder — everything this app needs without any third-party packages. The `@Observable` macro replaces the older ObservableObject/`@Published` pattern entirely and is the current standard for new SwiftUI apps in 2026.

One non-obvious constraint has significant upstream impact: a **paid Apple Developer account ($99/year) is required** to sideload an app with iCloud Drive access. Free Apple IDs cannot add the iCloud capability in Xcode. This must be resolved before the sync layer can be tested on device. For early development, a `LocalFileSyncProvider` reading from the app sandbox is a valid placeholder while running on a free account.

**Core technologies:**

- Swift 6.3 / SwiftUI (iOS 17+): primary language and UI framework — current standard, `@Observable` available, no legacy shims needed
- `@Observable` macro + `@State`/`@Environment`: state management — replaces ObservableObject/`@Published`; more precise re-renders, less boilerplate
- NavigationStack + NavigationPath: navigation — `NavigationView` is deprecated; stack gives programmatic path control without a heavy architecture framework
- Custom Swift org-mode parser (RegexBuilder): data format layer — no viable maintained Swift org library exists; both known libraries are Swift 3 from 2017
- NSFileCoordinator + NSMetadataQuery: iCloud Drive sync — Apple's first-party file sync API; files land in iCloud Drive and are readable by Emacs directly
- Swift Charts (first-party): data visualization — macro trends, workout progress; zero additional dependencies
- Swift Testing (first-party): unit tests — simpler than XCTest, native async/await, better failure messages

### Expected Features

The feature set is divided cleanly between what must ship in v1 (the org-file round-trip loop), what can be added once the loop is validated (v1.x), and what is explicitly deferred (v2+).

**Must have (table stakes — v1):**

- Manual food entry (name + protein/carbs/fat/calories) — core logging mechanic
- Custom food library (saved frequent foods) — eliminates repeat entry friction
- Meal templates / saved meals (e.g., "usual breakfast") — daily use accelerant
- Daily macro targets (static, user-configurable) — need a goal to track against
- Today's home screen summary (macros so far, today's workout) — answers "where am I today?"
- Workout log (exercise, sets, reps, weight) using 4-day split templates — core mechanic
- Previous session auto-fill for workouts — non-negotiable for progressive overload
- Write to org files in iCloud Drive — the entire architectural requirement
- Read history from org (two-way sync) — enables auto-fill, history, PR tracking
- PR tracking (automatic from workout history) — intrinsic motivation

**Should have (competitive — v1.x, after validation):**

- Barcode scanner — trigger: manual entry is a bottleneck for packaged foods
- Richer analytics (weekly averages, volume trends) — trigger: history is accumulating
- Edit past entries in-app — trigger: mistakes need correction without opening Emacs

**Defer (v2+):**

- Local server sync (replace iCloud Drive) — iCloud covers the use case now
- Adaptive calorie targets (TDEE-based) — adds algorithmic complexity; static targets work
- Apple Watch companion — adds WatchKit scope; iPhone logging is workable
- Micronutrient tracking — macro tracking is the stated v1 goal

**Explicit anti-features (never build):**

- Social features — antithetical to private personal app design
- Cloud food database / Nutritionix API — network dependency; embed USDA subset locally instead
- Gamification (streaks, badges) — doesn't match the reflective Emacs-user mindset
- AI photo logging — unreliable, latency, cost; overkill for single-user app

### Architecture Approach

The recommended pattern is MVVM + Repository with a protocol-backed sync layer. This is the right fit for a single-developer personal app: MVVM aligns with SwiftUI's reactive data flow, the Repository layer decouples ViewModels from the file format and sync mechanism, and the SyncBackend protocol enables the iCloud-to-local-server swap in v2 with zero changes outside the `Sync/` module. The OrgEngine (parser + writer) is pure Swift with no SwiftUI imports — it can be fully unit-tested in isolation, which is essential given that it is the highest-risk component.

**Major components:**

1. OrgEngine (OrgParser + OrgWriter) — pure Swift string-to-model and model-to-string transformation; no external dependencies; must be built and validated first
2. SyncBackend protocol (iCloudDriveBackend) — wraps NSFileCoordinator + FileManager; isolated in its own module; swap to LocalServerBackend in v2 by changing one line in AppContainer
3. FoodRepository / WorkoutRepository (protocols + concrete implementations) — single interface for all domain reads and writes; translate between OrgEngine models and typed domain structs
4. Feature ViewModels (`@Observable`) — TodayViewModel, FoodLogViewModel, WorkoutLogViewModel; call repository methods, never touch file I/O directly
5. Feature Views (SwiftUI) — TodayView, FoodLogView, WorkoutLogView; receive data from ViewModels via `@Observable`; no business logic

### Critical Pitfalls

1. **Reading placeholder .icloud files before download** — check `NSMetadataUbiquitousItemDownloadingStatusKey` before every read; call `startDownloadingUbiquitousItem` if not local; show a loading state; never read a zero-byte file and treat it as "no entries." Must be addressed in Phase 1 (sync foundation).

2. **Bypassing NSFileCoordinator** — all reads and writes to iCloud Drive files must go through `coordinateReading`/`coordinateWriting`; direct `Data(contentsOf:)` causes non-deterministic data corruption under concurrent iCloud sync. The SyncBackend protocol enforces this pattern in one place. Must be addressed in Phase 1.

3. **Org-mode write-back that corrupts Emacs files** — org-mode has strict whitespace rules (one space after asterisks, no blank line before PROPERTIES drawer, `:END:` required, timestamps must include day-of-week abbreviation using `en_US_POSIX` locale). Validate output with `M-x org-lint` in Emacs after every serialization change. Must be addressed in Phase 1 (OrgEngine).

4. **@Observable model objects recreated on every view rebuild** — with `@Observable`, root models must live at the App struct level as `@State`, not inside individual views; initializing NSMetadataQuery or file readers inside model `init` means each view rebuild spawns a new observer with its own callbacks. This causes duplicate writes and memory growth. Must be addressed in Phase 1 (SwiftUI architecture foundation).

5. **iCloud conflict versions silently accumulating** — when the file is edited on both iPhone and Mac while offline, iCloud creates hidden conflict versions; for plain-file apps (not UIDocument), these are never resolved automatically; implement `NSFilePresenter` to detect conflicts and call `NSFileVersion.removeOtherVersionsOfItemAtURL()`. Address in Phase 2 (sync hardening).

## Implications for Roadmap

The dependency graph from both FEATURES.md and ARCHITECTURE.md is unambiguous: the OrgEngine and SyncBackend layers are load-bearing. Every user-visible feature depends on them. The recommended build order is bottom-up: data layer → sync layer → domain models → repositories → UI features. The Today screen (the highest-value daily touchpoint) should be the first UI phase, not the last — it exercises the full read+write stack and validates the architecture before investing in secondary views.

### Phase 1: Foundation — OrgEngine + SyncBackend + SwiftUI Architecture

**Rationale:** The riskiest unknowns are file format correctness and iCloud file coordination, not UI. Building these first lets the rest of the app sit on a proven foundation. Every other feature depends on org files being written correctly and read safely.
**Delivers:** A working org-mode parser and writer validated against Emacs; an iCloudDriveBackend that reads/writes safely under NSFileCoordinator; the root state architecture (@Observable at App level); the SyncBackend and Repository protocols wired together with tests; no UI yet.
**Addresses from FEATURES.md:** "Write to org files" and "Read history from org" — the two foundational architectural requirements
**Avoids from PITFALLS.md:** Placeholder file read pitfall (download-before-read pattern established as the baseline), NSFileCoordinator bypass (SyncBackend enforces it), org write-back corruption (OrgWriter validated with org-lint before any UI is built), @Observable model recreation (root state placement decided before any views are written)

### Phase 2: Core Food Tracking

**Rationale:** Food logging is the higher-frequency daily action (3+ times per day vs. 4x per week for workouts). Getting it right first produces immediate daily value and validates the full user-facing read/write loop.
**Delivers:** Manual food entry form, custom food library, meal groupings (breakfast/lunch/dinner/snacks), meal templates/saved meals, daily macro targets, today's food summary on a home screen. All entries write to `food-log.org` in iCloud Drive.
**Uses from STACK.md:** SwiftUI forms with `.decimalPad` for macro fields, Swift Charts for daily macro progress bar, `@Observable` FoodLogViewModel
**Implements from ARCHITECTURE.md:** FoodLogView + FoodLogViewModel, FoodRepository (OrgFoodRepository concrete), Today screen food section

### Phase 3: Core Workout Tracking

**Rationale:** Workout logging depends on the same OrgEngine and SyncBackend established in Phase 1. Building it after food tracking means the repository pattern is validated and the second domain module can be built with confidence.
**Delivers:** Workout log (exercise, sets, reps, weight), 4-day split templates, previous session auto-fill (reads workout history to pre-populate), today's planned workout on home screen, PR tracking (scans history for new bests).
**Uses from STACK.md:** Same OrgEngine and SyncBackend; NavigationStack for set-by-set workout flow
**Implements from ARCHITECTURE.md:** WorkoutLogView + WorkoutLogViewModel, WorkoutRepository (OrgWorkoutRepository concrete), Today screen workout section

### Phase 4: Sync Hardening + UX Polish

**Rationale:** The first two feature phases will surface edge cases in real daily use. Phase 4 addresses known pitfalls that only manifest under real conditions: offline/online transitions, iCloud conflicts, form state loss.
**Delivers:** iCloud conflict detection and resolution (NSFilePresenter), sync status indicator ("Syncing..." / "Synced 2 min ago"), draft save/restore for food and workout entry forms, graceful degradation when iCloud is unavailable, debounced writes to prevent CloudKit throttling.
**Avoids from PITFALLS.md:** iCloud conflict version accumulation (Phase 2 in pitfall mapping), form data loss on back navigation, no-feedback-when-iCloud-unavailable, CloudKit throttling from rapid writes

### Phase 5: Analytics + History Views

**Rationale:** History and analytics require accumulated data to be meaningful. Building this phase after daily use has produced real data means the charts are useful immediately, not empty.
**Delivers:** Workout history view, food log history view, weekly macro averages (Swift Charts), volume tracking per session, training split calendar awareness.
**Uses from STACK.md:** Swift Charts (bar charts for weekly macro breakdown, line charts for weight-over-time per exercise)
**Implements from ARCHITECTURE.md:** Read path for FoodRepository and WorkoutRepository; no new sync or parsing work required

### Phase 6: Local Server Sync (v2 Goal)

**Rationale:** The SyncBackend protocol from Phase 1 makes this a bounded, isolated task. When the local server is ready, implement `LocalServerBackend: SyncBackend` and swap it in at `AppContainer.make()`. Nothing in Features, Repositories, or OrgEngine changes.
**Delivers:** Alternative sync backend (HTTP, WebDAV, or SFTP depending on server choice); settings UI to choose sync provider; migration path from iCloud files to local server files.
**Avoids:** Architecture regression — all changes are contained to `Sync/LocalServerBackend.swift` plus one line in AppContainer

### Phase Ordering Rationale

- OrgEngine and SyncBackend must come first because every other layer depends on them. Testing them in isolation before UI exists is the only way to catch the critical pitfalls (corrupt org output, coordinator bypass, placeholder reads) before they are buried under UI complexity.
- Food before workout because it is higher-frequency use and simpler data structure — better to validate the full stack with the simpler domain first.
- Sync hardening after initial features because some failure modes (conflicts, offline writes) only appear in real use over days or weeks, not in development.
- Analytics last because they are reads-only and require real data to validate.

### Research Flags

Phases likely needing deeper research during planning:

- **Phase 1 (OrgEngine):** The specific org-mode schema for food and workout entries needs to be designed and locked down before any parsing code is written. The format is app-defined, but it must round-trip correctly through Emacs. Recommend a short design spike: write 3-5 sample org entries by hand, open in Emacs, confirm they parse as expected, then codify that format as the OrgWriter target.
- **Phase 6 (Local Server Sync):** The server-side protocol is undefined (HTTP API? WebDAV? SFTP? Syncthing?). This cannot be planned until the server approach is chosen. Defer planning entirely until Phase 5 is complete and the server design is determined.

Phases with well-documented standard patterns (can skip research-phase):

- **Phase 2 + 3 (Food and Workout UI):** SwiftUI form patterns, NavigationStack flows, and `@Observable` ViewModel wiring are extremely well-documented in 2026. Standard MVVM + form + list patterns apply directly.
- **Phase 4 (NSFilePresenter conflict handling):** Apple Technical Note TN2336 and the fatbobman.com deep-dive cover this exhaustively. Implementation is boilerplate, not novel.
- **Phase 5 (Swift Charts):** First-party framework with official documentation and many examples. No surprises expected.

## Confidence Assessment

**Stack — HIGH**
All core technologies are Apple first-party frameworks with official documentation. The iCloud paid-account constraint is confirmed by both Apple docs and community reports. The org-mode library survey (both dead) is confirmed by direct GitHub inspection.

**Features — MEDIUM**
Food/workout app feature landscape is well-documented via commercial app research. The org-mode integration has no direct comparables — the feature design for org output is informed by the org-mode spec and first principles, not comparable products. The v1 scope decisions are based on solid reasoning but are not battle-tested.

**Architecture — MEDIUM-HIGH**
MVVM + Repository patterns in SwiftUI are HIGH confidence from multiple current sources. The OrgEngine custom parser strategy is MEDIUM (no maintained library exists; the custom parser approach is confirmed correct but implementation details will surface during build). iCloud Drive coordination patterns are HIGH from Apple internals and community deep-dives.

**Pitfalls — MEDIUM-HIGH**
iCloud pitfalls (placeholder reads, NSFileCoordinator, conflict versions) are verified via Apple documentation and a 2025 community deep-dive. The @Observable lifecycle pitfall is confirmed by Jesse Squires' 2024 analysis and Apple's migration guide. Org-mode formatting rules are verified against the official org-syntax specification.

**Overall confidence:** MEDIUM-HIGH

### Gaps to Address

- **Org-mode file schema:** The specific org-mode format for food entries and workout entries is not yet defined. This is the most important design decision before writing any parsing code. Recommend defining the exact file format (with real example files) as the first deliverable of Phase 1. The schema must be validated in Emacs before implementation begins.

- **Food database source:** FEATURES.md recommends embedding a USDA subset locally. The exact dataset, size, and embedding approach (Swift file? SQLite? JSON bundle?) are not specified. This needs a decision before Phase 2. A minimal approach (embed common macros for 200-500 frequently eaten foods as a JSON bundle) is probably sufficient for a personal app with a custom food library.

- **iCloud account status:** The $99/year paid developer account is required for iCloud Drive access. If this is not yet purchased, Phase 1's SyncBackend work can proceed with a `LocalFileSyncProvider` placeholder, but actual iCloud testing is blocked. Confirm account status before scoping Phase 1.

- **Training split definition:** The user's 4-day full-body split needs to be encoded in the app. The exact exercises, set/rep targets, and day ordering are domain knowledge that must be provided as input to Phase 3 planning. The architecture supports this (workout templates), but the content is user-specific.

## Sources

### Primary (HIGH confidence)

- https://developer.apple.com/documentation/SwiftUI/Migrating-from-the-observable-object-protocol-to-the-observable-macro — @Observable migration and lifecycle
- https://developer.apple.com/documentation/foundation/nsfilecoordinator — NSFileCoordinator official docs
- https://developer.apple.com/library/archive/technotes/tn2336/_index.html — TN2336: iCloud conflict handling
- https://developer.apple.com/support/compare-memberships/ — Developer account tiers (paid account required for iCloud)
- https://orgmode.org/worg/org-syntax.html — Org-mode syntax specification (official)
- https://xcodereleases.com/ — Xcode 26.4 / Swift 6.3 confirmed latest stable
- https://github.com/orgapp/swift-org — Confirmed abandoned (last commit 2017, Swift 3)
- https://github.com/xiaoxinghu/OrgMarker — Confirmed abandoned (last commit 2017, Swift 3)
- https://developer.apple.com/documentation/charts — Swift Charts official docs
- https://telemetrydeck.com/survey/apple/iOS/majorSystemVersions/ — iOS 26/18/17 adoption rates as of March 2026

### Secondary (MEDIUM-HIGH confidence)

- https://fatbobman.com/en/posts/in-depth-guide-to-icloud-documents/ — NSFileCoordinator, NSMetadataQuery, NSFilePresenter patterns in depth
- https://fatbobman.com/en/posts/advanced-icloud-documents/ — Advanced iCloud sync patterns
- https://zottmann.org/2025/09/08/ios-icloud-drive-synchronization-deep.html — iCloud Drive sync gotchas (no forced sync, throttling, background limits)
- https://www.jessesquires.com/blog/2024/09/09/swift-observable-macro/ — @Observable lifecycle pitfall (@State model recreation)
- https://avanderlee.com/swift/repository-design-pattern/ — Repository pattern in Swift (widely cited)
- https://www.swift.org/blog/swift-6.2-released/ — Swift 6.2 concurrency changes

### Tertiary (MEDIUM confidence)

- https://dimillian.medium.com/swiftui-in-2025-forget-mvvm-262ff2bbd2ed — Community signal: ViewModels not needed for simple apps (informed the "keep it simple" recommendation)
- https://www.hackingwithswift.com/forums/swiftui/icloud-capability-not-available-in-xcode/4980 — Free provisioning profile does not support iCloud
- https://dev.to/__be2942592/how-to-structure-a-swiftui-project-in-2026-41m8 — SwiftUI project structure conventions (2026)
- Feature landscape sources: Nutrola, Stronger Mobile, Setgraph, Hevy, PRPath, Askvora, Fitbod blogs (2026 app comparison research)

---
*Research completed: 2026-04-09*
*Ready for roadmap: yes*
