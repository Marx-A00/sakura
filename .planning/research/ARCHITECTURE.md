# Architecture Research

**Domain:** Personal iOS tracking app (food + workout, org-mode file backend)
**Researched:** 2026-04-09
**Confidence:** MEDIUM-HIGH — SwiftUI/MVVM patterns are HIGH confidence from multiple current sources. Org parser approach is MEDIUM (no maintained Swift library; custom parser strategy verified via official org-syntax docs). iCloud Drive coordination details are HIGH from Apple internals and community deep-dives.

---

## Recommended Architecture

**Pattern: MVVM + Repository (Protocol-backed Sync Layer)**

MVVM is the right fit for a single-developer personal app of low-to-medium complexity. It aligns naturally with SwiftUI's reactive data flow (`@Observable`, `@State`, `@Environment`). Adding a thin Repository layer on top of MVVM specifically addresses the sync-backend swap requirement — iCloud Drive now, local server later — without introducing the full overhead of Clean Architecture.

The org-mode parser and writer sit entirely in the data layer, invisible to ViewModels and Views. The sync layer wraps file I/O behind a protocol, making the backend swappable with zero changes to the rest of the app.

---

## System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         View Layer (SwiftUI)                     │
│                                                                  │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│   │  TodayView   │  │  FoodLogView │  │   WorkoutLogView     │  │
│   └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘  │
│          │                 │                      │              │
├──────────┼─────────────────┼──────────────────────┼─────────────┤
│                        ViewModel Layer                           │
│                                                                  │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│   │  TodayVM     │  │  FoodLogVM   │  │   WorkoutLogVM       │  │
│   └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘  │
│          │                 │                      │              │
├──────────┼─────────────────┼──────────────────────┼─────────────┤
│                       Repository Layer                           │
│                                                                  │
│   ┌──────────────────────────────────────────────────────────┐   │
│   │  FoodRepository          WorkoutRepository               │   │
│   │  (protocol)              (protocol)                      │   │
│   └────────────────────────────┬─────────────────────────────┘   │
│                                │                                 │
├────────────────────────────────┼─────────────────────────────────┤
│                        Data Layer                                │
│                                                                  │
│   ┌─────────────────────┐    ┌──────────────────────────────┐    │
│   │   OrgParser          │    │   SyncBackend (protocol)     │    │
│   │   OrgWriter          │    │   ├─ iCloudDriveBackend      │    │
│   │   (domain models)    │    │   └─ LocalServerBackend (v2) │    │
│   └─────────────────────┘    └──────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

---

## Component Responsibilities

**TodayView / TodayVM**
- Responsibility: Drive the home screen — today's food entries, macro totals, today's workout session
- Communicates with: FoodRepository, WorkoutRepository
- Owns: Today-scoped data, macro aggregation display state

**FoodLogView / FoodLogVM**
- Responsibility: Display food log history; compose and submit new food entries
- Communicates with: FoodRepository
- Owns: Entry form state, food history list, inline editing state

**WorkoutLogView / WorkoutLogVM**
- Responsibility: Display workout history; compose sets/reps/weight for a session
- Communicates with: WorkoutRepository
- Owns: Active session state (current exercise, set builder), history list

**FoodRepository / WorkoutRepository (protocols)**
- Responsibility: Single interface for all reads and writes for a domain; decouples ViewModels from file format and sync mechanism
- Communicates with: OrgParser (reads), OrgWriter (writes), SyncBackend
- Pattern: Protocol with one concrete implementation (`OrgFile[Domain]Repository`); swap implementation by injecting a different concrete at app startup

**OrgParser**
- Responsibility: Read an org-mode file string and return typed domain models (FoodEntry, WorkoutSession)
- Communicates with: nothing (pure function — string in, models out)
- Owns: All knowledge of org syntax: headline levels, date headings, property drawers, plain text entry format

**OrgWriter**
- Responsibility: Accept a domain model and produce valid org-mode text to append or update
- Communicates with: nothing (pure function — models in, string out)
- Owns: All formatting decisions — heading format, property block layout, spacing conventions

**SyncBackend (protocol)**
- Responsibility: Abstract file I/O — read file at path, write/append to file at path
- Communicates with: FileManager / iCloud APIs (in concrete implementation)
- Protocol surface: `func readFile(at:) async throws -> String`, `func writeFile(_ content:, to:) async throws`
- Concrete v1: `iCloudDriveBackend` — wraps NSFileCoordinator + FileManager
- Concrete v2: `LocalServerBackend` — HTTP or socket calls, swapped in at app entry point

---

## Recommended Project Structure

```
Origami/
├── App/
│   ├── OrigamiApp.swift          # @main, dependency wiring
│   └── AppContainer.swift        # Assembles concrete implementations
│
├── Features/
│   ├── Today/
│   │   ├── TodayView.swift
│   │   └── TodayViewModel.swift
│   ├── Food/
│   │   ├── FoodLogView.swift
│   │   ├── FoodLogViewModel.swift
│   │   └── FoodEntryForm.swift
│   └── Workout/
│       ├── WorkoutLogView.swift
│       ├── WorkoutLogViewModel.swift
│       └── WorkoutEntryForm.swift
│
├── Repositories/
│   ├── FoodRepository.swift       # protocol
│   ├── WorkoutRepository.swift    # protocol
│   ├── OrgFoodRepository.swift    # concrete: parses food-log.org
│   └── OrgWorkoutRepository.swift # concrete: parses workout-log.org
│
├── Sync/
│   ├── SyncBackend.swift          # protocol
│   ├── iCloudDriveBackend.swift   # NSFileCoordinator wrapper
│   └── LocalServerBackend.swift   # placeholder for v2
│
├── OrgEngine/
│   ├── OrgParser.swift            # String → [domain models]
│   ├── OrgWriter.swift            # domain models → String
│   └── OrgModels.swift            # OrgEntry, OrgDate, raw structs
│
└── Models/
    ├── FoodEntry.swift            # protein/carbs/fat/calories, timestamp
    ├── WorkoutSession.swift       # exercise, sets, reps, weight, date
    └── MacroSummary.swift         # computed totals for today view
```

### Structure Rationale

- **Features/**: Each screen gets its own folder with View + ViewModel co-located. Avoids the flat "Views/" + "ViewModels/" split that makes large apps hard to navigate.
- **Repositories/**: Protocol + concrete in one place. When LocalServerBackend is wired in, you swap the concrete at `AppContainer.swift` — nothing in Features/ changes.
- **Sync/**: Isolated from Repositories. Repositories don't know they're talking to iCloud; they call `SyncBackend.readFile()`.
- **OrgEngine/**: Pure logic, no SwiftUI imports. Makes the parser unit-testable in isolation from any sync or UI concern.
- **Models/**: Typed domain models. These are what ViewModels hold and display. Repositories translate between org text and these models.

---

## Architectural Patterns

### Pattern 1: Protocol-Backed Repository

**What:** Define `FoodRepository` and `WorkoutRepository` as Swift protocols. Inject concrete implementations via the app container at launch.

**When to use:** Any time you need to swap a backend (iCloud → local server) or test without real file I/O.

**Trade-offs:** Small amount of protocol boilerplate upfront. Pays off immediately when writing the LocalServerBackend or any unit tests.

**Example:**
```swift
protocol SyncBackend {
    func readFile(at path: String) async throws -> String
    func writeFile(_ content: String, to path: String) async throws
}

final class iCloudDriveBackend: SyncBackend {
    func readFile(at path: String) async throws -> String {
        // NSFileCoordinator-wrapped read
    }
    func writeFile(_ content: String, to path: String) async throws {
        // NSFileCoordinator-wrapped write
    }
}
```

### Pattern 2: @Observable ViewModels

**What:** Use Swift's `@Observable` macro (iOS 17+) for ViewModels. Drop `ObservableObject` and `@Published`. SwiftUI updates views only for properties actually read in the body.

**When to use:** All new ViewModels in this project. iOS 17 is the minimum target; `@Observable` is the current standard.

**Trade-offs:** Requires iOS 17+. Cleaner than ObservableObject pattern — no @Published on every property, views re-render precisely.

**Example:**
```swift
@Observable
final class TodayViewModel {
    var foodEntries: [FoodEntry] = []
    var macros: MacroSummary = .empty
    private let foodRepo: FoodRepository

    func loadToday() async { ... }
}
```

### Pattern 3: Custom Org Parser (Targeted, Not Full-Featured)

**What:** Write a purpose-built org parser that handles only what this app writes. Not a general org parser.

**When to use:** The only maintained Swift org library (`swift-org`) is a Swift 3 project from 2017 and is not production-viable. Writing a full org parser is unnecessary — the app only needs to handle the exact subset of org syntax it generates.

**Trade-offs:** The parser is tightly coupled to the specific file format this app produces, but that is an acceptable constraint for a personal tool. The format is simple: date headlines, optional property drawers, plain text entries. Minimal parsing surface.

**Org format this app produces:**
```org
* 2026-04-09
** Breakfast
   - Protein: 30g  Carbs: 45g  Fat: 12g  Calories: 412
** Lunch
   ...

* 2026-04-08
...
```

**Parser approach:**
1. Split file by lines
2. Detect level-1 headlines (`* YYYY-MM-DD`) to identify date sections
3. Detect level-2 headlines for meal/exercise labels
4. Parse property-like inline text for macro values
5. Return `[DatedSection]` with typed entries

### Pattern 4: Append-Only Writes with In-Memory Merge

**What:** For new entries (today's log), read the current file, find-or-create today's date heading, append the new entry, write back. Use `NSFileCoordinator` to wrap the read-write cycle as a single coordination block to prevent race conditions with iCloud.

**When to use:** Every write. iCloud Drive can be modified externally (from Mac/Emacs), so the file must be re-read before writing, not cached stale.

**Trade-offs:** Each write does a full file round-trip. Acceptable given: plain text files stay small, this is a single-user app with no concurrent writes expected beyond iCloud sync events.

---

## Data Flow

### Write Flow (New Entry)

```
User taps "Save Entry"
    ↓
ViewModel.saveEntry(entry)
    ↓
FoodRepository.append(entry)
    ↓
SyncBackend.readFile("food-log.org")       ← always read current state first
    ↓
OrgParser.parse(fileContent) → [DatedSection]
    ↓
Merge: find today section, append new OrgEntry
    ↓
OrgWriter.serialize([DatedSection]) → String
    ↓
SyncBackend.writeFile(content, to: "food-log.org")
    ↓
ViewModel state refreshed from updated model
```

### Read Flow (History / Today Dashboard)

```
View appears / pull-to-refresh
    ↓
ViewModel.load()
    ↓
FoodRepository.fetchAll() or .fetchToday()
    ↓
SyncBackend.readFile("food-log.org")
    ↓
OrgParser.parse(fileContent) → [DatedSection]
    ↓
Repository maps [DatedSection] → [FoodEntry]
    ↓
ViewModel.foodEntries = [FoodEntry]
    ↓
SwiftUI re-renders (via @Observable)
```

### Sync Backend Swap Flow

```
App startup (OrigamiApp.swift)
    ↓
AppContainer.make() decides which SyncBackend to instantiate
    ↓
Injects SyncBackend into FoodRepository, WorkoutRepository
    ↓
All downstream code unchanged
```

---

## Suggested Build Order

Dependencies between components dictate this sequence:

**1. OrgEngine (parser + writer)**
No dependencies. Pure Swift. Can be built and tested first with sample org files from the existing Emacs setup. This is the riskiest unknown — validate org parsing works before building any UI.

**2. SyncBackend (iCloudDriveBackend)**
Depends only on Foundation. Build and test independently: can the app read/write a file in the iCloud container? Resolve iCloud entitlements and file coordination here, before UI adds complexity.

**3. Models**
Typed domain structs. No dependencies. Define these once OrgEngine shapes are understood.

**4. Repositories**
Depends on OrgEngine + SyncBackend + Models. Wire them together. Write integration tests or a simple harness: read a real org file → parse → write back → verify roundtrip.

**5. Today feature (ViewModel + View)**
The highest-value screen. Depends on FoodRepository + WorkoutRepository. Build this before the log history views — it exercises reads and writes and delivers the core daily value.

**6. Food Log history view**
Depends on FoodRepository (read path). Simpler than the Today view once the repository layer is solid.

**7. Workout Log history view**
Same as Food Log — repository read path.

**8. LocalServerBackend (v2, future)**
Implement this whenever the local server pivot is ready. Drop in at `AppContainer.make()`. Nothing else changes.

---

## Anti-Patterns

### Anti-Pattern 1: Caching the Org File in Memory Across App Sessions

**What people do:** Load the file once on app launch, keep an in-memory model, only read again on explicit refresh.

**Why it's wrong:** iCloud Drive can modify the file between app launches (Emacs on Mac writes an entry). The in-memory model goes stale. User sees app data that doesn't match what Emacs shows.

**Do this instead:** Always re-read via `SyncBackend.readFile()` when the view needs fresh data. For the Today screen, load on `onAppear`. File reads are fast for small org text files.

---

### Anti-Pattern 2: Parsing org features you don't write

**What people do:** Pull in a full org parser (or write one) that handles all of org-syntax — LaTeX, macros, agenda, clocks, drawers, etc.

**Why it's wrong:** `swift-org` is unmaintained Swift 3. Writing a full org parser is weeks of work for features you don't need.

**Do this instead:** Write a parser that handles exactly the format `OrgWriter` produces. If `OrgWriter` only emits date headlines and plain text entries, `OrgParser` only needs to handle those. Lock down the format in the writer and the parser is trivial.

---

### Anti-Pattern 3: File I/O directly in ViewModels

**What people do:** Call `FileManager` or `SyncBackend` directly from a ViewModel.

**Why it's wrong:** Bypasses the repository abstraction. Means swapping the sync backend requires touching every ViewModel. Also makes testing harder — you'd need real files to test ViewModel logic.

**Do this instead:** ViewModels call repository methods. Repositories call the sync backend. The ViewModel doesn't know how data is stored.

---

### Anti-Pattern 4: Skipping NSFileCoordinator for iCloud writes

**What people do:** Use `String.write(to:)` directly on an iCloud-synced file path without coordination.

**Why it's wrong:** iCloud's daemon (`bird`) can be reading or modifying the file simultaneously. Uncoordinated writes corrupt files or silently lose data.

**Do this instead:** Wrap every read and write in `NSFileCoordinator.coordinateReading` / `coordinateWriting` blocks. For a personal app with one writer, this is simple boilerplate but non-negotiable.

---

## Integration Points

**iCloud Drive (v1 sync)**
- Integration: `NSFileCoordinator` + `FileManager` in `iCloudDriveBackend`
- Container: `iCloud.[BundleID]` — set in Xcode entitlements
- File path: `FileManager.default.url(forUbiquityContainerIdentifier: nil)?.appendingPathComponent("Documents/food-log.org")`
- Key gotcha: Must enable iCloud Documents capability in Xcode + Developer portal; app must be sideloaded with paid account ($99/yr) to maintain entitlements

**Emacs / org-roam (external reader)**
- Integration: None at runtime — Emacs reads the same iCloud Drive files on Mac
- Key requirement: `OrgWriter` must produce valid org-mode syntax. Test output by opening it in Emacs and confirming it parses correctly. No API, no protocol — this is a file format contract.

**Local Server (v2 sync, future)**
- Integration: HTTP or UNIX socket calls wrapped in `LocalServerBackend: SyncBackend`
- Swap point: `AppContainer.make()` — replace `iCloudDriveBackend` with `LocalServerBackend`
- Nothing in Features/, Repositories/, or OrgEngine changes

---

## Scaling Considerations

This is a single-user personal app. Scaling is not a concern. The relevant "scale" question is file size over time.

A food-log.org with 3 meals/day × 365 days = ~1,095 entries. At roughly 100 bytes per entry, that's ~110 KB after one year. Plain text parsing of 110 KB is milliseconds. No pagination, lazy loading, or database is needed for this use case.

If org files grow to several MB over multiple years, consider: parsing only the last N date sections for the history view (scan from file end), and caching today's parsed section between write operations. Both are straightforward optimizations if ever needed.

---

## Sources

- Architecture pattern comparison (MVVM vs Clean vs TCA): https://7span.com/blog/mvvm-vs-clean-architecture-vs-tca — MEDIUM confidence (verified against multiple sources)
- @Observable macro (iOS 17+): https://developer.apple.com/documentation/SwiftUI/Migrating-from-the-observable-object-protocol-to-the-observable-macro — HIGH confidence (official Apple docs)
- iCloud Drive + NSFileCoordinator: https://fatbobman.com/en/posts/in-depth-guide-to-icloud-documents/ — HIGH confidence (deep technical writeup, consistent with Apple docs)
- iCloud file write pattern: https://dev.to/nemecek_f/ios-saving-files-into-user-s-icloud-drive-using-filemanager-4kpm — MEDIUM confidence (community, consistent with docs)
- Repository pattern in Swift: https://avanderlee.com/swift/repository-design-pattern/ — HIGH confidence (widely cited, well-maintained SwiftLee)
- Org-mode syntax specification: https://orgmode.org/worg/org-syntax.html — HIGH confidence (official org-mode project)
- swift-org library status: https://github.com/orgapp/swift-org — HIGH confidence (last commit 2017, Swift 3, not viable)
- SwiftUI project structure: https://dev.to/__be2942592/how-to-structure-a-swiftui-project-in-2026-41m8 — MEDIUM confidence (community, 2026, consistent with established patterns)

---

*Architecture research for: Origami — personal iOS food and workout tracking app*
*Researched: 2026-04-09*
