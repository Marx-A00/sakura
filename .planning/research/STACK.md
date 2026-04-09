# Stack Research

**Domain:** Personal iOS native app — food/macro tracking + workout logging, org-mode plain text files, iCloud Drive sync
**Researched:** 2026-04-09
**Confidence:** MEDIUM-HIGH (core stack HIGH; org-mode parsing and iCloud-with-free-account nuances MEDIUM)

---

## Recommended Stack

### Core Technologies

**Language & Platform**

- Technology: Swift 6.2 / 6.3
- Version: 6.3 (ships with Xcode 26.4, latest stable as of April 2026)
- Purpose: Primary language for all app code
- Why Recommended: Swift 6 is the current standard. Swift 6.2 specifically introduced "single-threaded by default" concurrency — dramatically reducing boilerplate for a personal app that doesn't need parallelism. Swift 6 strict concurrency catches data races at compile time, which matters when coordinating iCloud file reads/writes with UI updates. Do not use Objective-C; it is not relevant for greenfield iOS work in 2026.

- Technology: SwiftUI
- Version: iOS 17+ (targets ~95% of active devices as of March 2026, per TelemetryDeck)
- Purpose: UI framework
- Why Recommended: The only serious choice for greenfield native iOS development in 2026. iOS 17 unlocks the `@Observable` macro (replaces `ObservableObject` + `@Published` entirely), `@Bindable`, and the full `NavigationStack` API. Targeting iOS 17 as minimum gives access to every modern API this project needs without maintaining legacy shims.

- Technology: Xcode 26.4
- Version: 26.4 (latest stable, released March 24 2026)
- Purpose: IDE, build toolchain, Simulator, device provisioning
- Why Recommended: Required for Swift 6.3 and iOS 26 SDK. For sideloading to a personal device, a free Apple ID works — but see the critical iCloud constraint below.

**State Management**

- Technology: Swift Observation (`@Observable` macro)
- Version: iOS 17+ (Swift 5.9+)
- Purpose: App-wide and per-screen state management
- Why Recommended: The 2025/2026 standard replaces `ObservableObject`. No `@Published` required — all stored properties are automatically trackable. SwiftUI only re-renders views that read a property that changed (more precise than the old system). For a small personal app, use `@Observable` models injected via `@Environment` rather than MVVM ViewModels. The community consensus in 2025 is: ViewModels add unnecessary indirection for apps of this scope. Keep logic in model objects, services, and async `task {}` blocks inside views.

- Technology: `@State` / `@Environment` / `@Bindable`
- Version: iOS 17+
- Purpose: View-local and cross-view state wiring
- Why Recommended: With `@Observable`, `@StateObject` and `@ObservedObject` are gone. Use `@State` when the view owns the object, bare property injection when passed down, and `@Environment` for shared singletons (e.g., the sync service, the org file repository).

**Navigation**

- Technology: `NavigationStack` + `NavigationPath`
- Version: iOS 16+ (refine with iOS 17 APIs)
- Purpose: Screen navigation, deep linking
- Why Recommended: `NavigationView` is deprecated. `NavigationStack` gives programmatic path control, which enables clean routing without a heavy third-party architecture like TCA. TCA (The Composable Architecture) is overkill for a single-user personal app with no complex side-effect orchestration — it adds significant learning curve and boilerplate for zero benefit here.

---

### Data Layer

**In-App Data Storage**

- Technology: Custom in-memory models + org-mode file I/O (NO SwiftData, NO CoreData)
- Version: N/A
- Purpose: Primary data store
- Why Recommended: The project constraint is that data lives in org files. SwiftData/CoreData are relational persistence stores that generate their own internal file formats. Using them would mean maintaining two sources of truth (the database AND the org files), which defeats the entire purpose of this project. Keep it simple: parse org files into Swift structs on load, mutate in memory, serialize back to org text on write.

**Org-Mode Parser**

- Technology: Custom Swift parser using Swift Regex / `RegexBuilder`
- Version: RegexBuilder available iOS 16+
- Purpose: Read and write org-mode formatted plain text files
- Why Recommended: No viable maintained Swift org-mode library exists. Both known libraries — `swift-org` (orgapp) and `OrgMarker` (xiaoxinghu) — were last updated in 2017 and target Swift 3. They will not compile with modern Xcode without substantial rewrites. The Flutter-based `orgro` parser (`org_parser`) is Dart, not Swift.

  The org-mode subset needed for this project is narrow and well-defined: headings (`*`, `**`), properties (`:PROPERTIES:` drawers), timestamps (`<2026-04-09>`), plain lists (`-`), and tags (`:tag:`). This is a tractable parsing problem with Swift's `RegexBuilder` DSL (available iOS 16+, composable, type-safe, readable). Writing a bespoke parser for the specific org constructs Origami uses will take less time than fighting a dead library and produces a parser you fully control.

  The parser is a core domain component — abstract it behind a protocol so the file format could theoretically change.

**iCloud Drive Sync**

- Technology: `NSFileCoordinator` + `NSMetadataQuery` + `FileManager` (iCloud Documents API)
- Version: iOS 8+ (stable, well-documented)
- Purpose: Two-way file sync between device and iCloud Drive
- Why Recommended: This is Apple's first-party API for syncing plain text documents via iCloud Drive. Files appear under the app's iCloud container and are accessible from Files.app on iOS and Finder on macOS — including by Emacs on macOS.

  **CRITICAL CONSTRAINT — Paid Developer Account Required:** iCloud entitlements are NOT available with a free Apple ID (Personal Team) in Xcode. Free accounts are limited to a 7-day provisioning profile and cannot add the iCloud capability. A paid Apple Developer Program membership ($99/year) is required to sideload an app with iCloud Drive access. This is confirmed by Apple documentation and community reports (2024/2025). Plan for this before starting implementation.

  **CRITICAL CONSTRAINT — No Forced Sync:** There is no API to force iCloud sync. The system controls all sync timing based on network conditions, battery state, and thermal management. Apps must design around this: show file sync status via `NSMetadataQuery` notifications, implement graceful degradation when sync is delayed, and never block the UI waiting for sync.

  **Abstraction Layer (Required for v1):** Wrap all sync operations behind a `SyncProvider` protocol. v1 implements `iCloudSyncProvider`. A future `LocalServerSyncProvider` (e.g., SSH/SFTP, syncthing, or a custom HTTP server) slots in without touching the rest of the app. This is the most important architectural decision for meeting the "swap sync layer later" constraint.

  ```swift
  protocol SyncProvider {
      func availableFiles() async throws -> [OrgFile]
      func read(_ file: OrgFile) async throws -> String
      func write(_ content: String, to file: OrgFile) async throws
      func observeChanges() -> AsyncStream<[OrgFile]>
  }
  ```

---

### Supporting Libraries

**Charts & Data Visualization**

- Library: Swift Charts (Apple first-party)
- Version: iOS 16+ (enhanced in iOS 17)
- Purpose: Macro trend charts, workout progress visualization
- When to Use: Any data visualization in the app. Bar charts for macros per day, line charts for weight lifted over time. Zero third-party dependencies — this is now a first-class Apple framework.

**Testing**

- Library: Swift Testing (Apple first-party)
- Version: iOS 17+ / Swift 5.9+ (replaces XCTest for unit tests)
- Purpose: Unit tests for the org-mode parser, data models, sync provider logic
- When to Use: Prefer Swift Testing over XCTest for new tests. Simpler syntax (`@Test`, `@Suite`), native async/await support, better failure messages. XCTest still needed for UI tests.

---

### Development Tools

- Tool: Xcode 26.4
- Purpose: IDE, compiler, Simulator, device deployment
- Notes: Enable "Complete Concurrency Checking" from day one (Swift 6 mode). Do not start in Swift 5 compatibility mode and plan to migrate later.

- Tool: Swift Package Manager (SPM)
- Purpose: Dependency management
- Notes: No third-party dependencies are expected for v1. SPM is built into Xcode; no setup required.

- Tool: Instruments (Apple first-party)
- Purpose: Memory and performance profiling
- Notes: Use Time Profiler to verify file I/O and parse operations don't block the main thread.

---

## Alternatives Considered

**SwiftData vs. Custom Org File I/O**
- Recommended: Custom org file I/O
- Alternative: SwiftData
- Why Not: SwiftData stores data in its own internal SQLite format. Using it means the org files are NOT the source of truth, which breaks the Emacs interoperability requirement. SwiftData + iCloud sync via CloudKit also requires a paid developer account AND loses the plain-text file format. Not appropriate here.

**CoreData vs. Custom Org File I/O**
- Recommended: Custom org file I/O
- Alternative: CoreData
- Why Not: Same reason as SwiftData. CoreData is a heavy ORM that owns the data format. Additionally, CoreData is being deprecated in favor of SwiftData for new projects.

**TCA (The Composable Architecture) vs. @Observable + NavigationStack**
- Recommended: @Observable + NavigationStack
- Alternative: TCA (pointfreeco/swift-composable-architecture)
- Why Not: TCA is a heavyweight unidirectional architecture designed for large team codebases with complex side effects and testability requirements. For a single-user personal app, it adds significant boilerplate and learning investment with no proportional benefit. The @Observable + NavigationStack combo achieves clean state management and navigation without the overhead.

**Existing org-mode library (swift-org / OrgMarker) vs. Custom Parser**
- Recommended: Custom parser
- Alternative: swift-org or OrgMarker
- Why Not: Both last updated in 2017, target Swift 3, will not compile with Xcode 26.4 without major rewrites. The org-mode subset needed for Origami is narrow. Building a bespoke parser is lower risk and produces a more maintainable artifact than patching a dead library.

**Combine vs. async/await + AsyncStream**
- Recommended: async/await + AsyncStream
- Alternative: Combine
- Why Not: Combine is not deprecated but is no longer the recommended approach for new code in 2025/2026. Swift Concurrency (async/await, AsyncStream, AsyncSequence) is the standard. Mixing Combine pipelines into a Swift 6 codebase complicates sendability analysis. Use Combine only if integrating with legacy code that already uses it.

**CloudKit (database sync) vs. iCloud Drive (file sync)**
- Recommended: iCloud Drive (NSFileCoordinator / file-based)
- Alternative: CloudKit
- Why Not: CloudKit syncs structured records in Apple's cloud database. It does NOT produce human-readable files on disk. The fundamental requirement is that data lives in org files accessible from Emacs on macOS. CloudKit does not satisfy this. iCloud Drive file sync puts actual `.org` files in the user's iCloud Drive folder, readable by Emacs directly.

---

## What NOT to Use

**UIKit**
- Why: Greenfield SwiftUI project. UIKit adds complexity, inconsistency, and goes against the grain of the SwiftUI architecture. Use UIKit only if a specific control genuinely doesn't exist in SwiftUI (rare in iOS 17+).

**ObservableObject / @Published / @StateObject / @ObservedObject**
- Why: Replaced by `@Observable` in iOS 17+. Using the old system means more boilerplate, less precise view updates, and mixing APIs that Apple is steering away from.
- Use Instead: `@Observable` macro with `@State` / `@Environment`.

**NavigationView**
- Why: Deprecated in iOS 16. Replaced by `NavigationStack`.
- Use Instead: `NavigationStack` with `NavigationPath`.

**Third-party networking libraries (Alamofire, etc.)**
- Why: Not needed. v1 has no server networking. If a future sync backend is added, URLSession with async/await is sufficient for a single-user personal app.

**Any third-party state management framework (Redux-Swift, RxSwift, Combine-heavy MVVM)**
- Why: Overcomplicated for this scope. The native Swift Observation + async/await stack is complete for a personal app.

**Barcode scanning / food database libraries (Open Food Facts SDK, Nutritionix)**
- Why: v1 constraint is manual food entry only. Barcode scanning and remote food databases are explicitly deferred. Don't bring in dependencies that aren't needed yet.

---

## Stack Patterns by Variant

**If the free developer account limitation is acceptable:**
- Use local-only file storage (no iCloud), manually transfer org files via AirDrop or cable sync through Finder
- This is a valid v0 approach for development before purchasing the paid account
- Implement the `SyncProvider` protocol from day one; plug in `LocalFileSyncProvider` (reads from app sandbox) as a placeholder

**If the $99/year paid account is purchased:**
- Enable iCloud Drive capability in Xcode, implement `iCloudSyncProvider`
- Files appear at `FileManager.default.url(forUbiquityContainerIdentifier: nil)?.appendingPathComponent("Documents")`
- Use `NSMetadataQuery` to watch for remote changes pushed from macOS Emacs

**If a future local server is added (v2+):**
- Implement a second `SyncProvider` conforming to whatever protocol the server exposes (HTTP, SSH, WebDAV)
- No app-side architectural changes required

---

## Version Compatibility

- Swift 6.3 — requires Xcode 26.4+, macOS 26.2+ on development machine
- SwiftUI iOS 17 features (@Observable, @Bindable) — iOS 17.0 minimum deployment target
- Swift Charts — iOS 16.0+; set minimum to iOS 17.0 and you get all available chart marks
- RegexBuilder — iOS 16.0+; included with iOS 17 minimum target
- NSFileCoordinator / iCloud Drive — iOS 8.0+; fully stable
- Swift Testing — Xcode 16+; available for test targets targeting iOS 17+

---

## Installation

This project uses no third-party Swift packages in v1. All dependencies are Apple first-party frameworks.

```bash
# No package dependencies for v1.
# All frameworks are included with the iOS SDK:
#   SwiftUI, Swift Charts, RegexBuilder, HealthKit (if added later)
#
# Xcode project setup:
# 1. New project → App → SwiftUI interface, Swift language
# 2. Minimum deployment target: iOS 17.0
# 3. Swift Language Version: Swift 6 (in Build Settings)
# 4. Strict Concurrency Checking: Complete (in Build Settings)
# 5. Signing & Capabilities → iCloud → iCloud Documents
#    (requires paid Apple Developer account)
# 6. Add NSUbiquitousContainers to Info.plist with resolved bundle ID
```

---

## Sources

- https://xcodereleases.com/ — Xcode 26.4 confirmed latest stable (Swift 6.3, March 24 2026) [HIGH]
- https://www.swift.org/blog/swift-6.2-released/ — Swift 6.2 release (September 15 2025), concurrency features [HIGH]
- https://telemetrydeck.com/survey/apple/iOS/majorSystemVersions/ — iOS 26 at 79%, iOS 18 at 16%, iOS 17 and earlier ~5% as of March 2026 [HIGH]
- https://developer.apple.com/documentation/SwiftUI/Migrating-from-the-observable-object-protocol-to-the-observable-macro — @Observable migration guide [HIGH]
- https://fatbobman.com/en/posts/in-depth-guide-to-icloud-documents/ — NSFileCoordinator, NSMetadataQuery, NSFilePresenter patterns [MEDIUM-HIGH]
- https://zottmann.org/2025/09/08/ios-icloud-drive-synchronization-deep.html — iCloud Drive sync gotchas: no forced sync, throttling, background limits (September 2025) [MEDIUM-HIGH]
- https://www.hackingwithswift.com/forums/swiftui/icloud-capability-not-available-in-xcode/4980 — Paid developer account required for iCloud capability [MEDIUM]
- https://developer.apple.com/forums/thread/669516 — Free provisioning profile does not support iCloud capability [MEDIUM]
- https://github.com/orgapp/swift-org — swift-org: last release 2017, Swift 3, unmaintained [HIGH — confirmed by fetch]
- https://github.com/xiaoxinghu/OrgMarker — OrgMarker: last commit 2017, unmaintained [HIGH — confirmed by fetch]
- https://developer.apple.com/documentation/charts — Swift Charts official docs [HIGH]
- https://dimillian.medium.com/swiftui-in-2025-forget-mvvm-262ff2bbd2ed — Community signal: ViewModels not needed for simple SwiftUI apps (2025) [MEDIUM]
- https://developer.apple.com/support/compare-memberships/ — Apple Developer Program membership tiers [HIGH]
- https://github.com/horseshoe7/CloudServiceFileSync — Pattern reference for SyncProvider protocol abstraction [MEDIUM]

---

*Stack research for: Origami — Personal iOS food/nutrition and workout tracking app*
*Researched: 2026-04-09*
