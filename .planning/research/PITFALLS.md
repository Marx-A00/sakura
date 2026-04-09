# Pitfalls Research

**Domain:** Personal iOS app — org-mode plain text tracking, iCloud Drive sync, SwiftUI, sideloaded distribution
**Researched:** 2026-04-09
**Confidence:** MEDIUM-HIGH (iCloud and SwiftUI pitfalls verified via official Apple docs and well-regarded community sources; org-mode parsing via spec + ecosystem survey; sideloading via current Apple developer docs)

---

## Critical Pitfalls

### Pitfall 1: Reading Placeholder Files Without Downloading First

**What goes wrong:**
When an org file synced via iCloud Drive hasn't been fully downloaded to the device, it exists as a `.icloud` placeholder. Attempting to read a placeholder file using standard `Data(contentsOf:)` returns empty data — not an error. The file appears to exist, the read succeeds, and the app silently interprets an empty org file as "no entries." If the app then writes an entry and saves, it overwrites the cloud version with a blank file.

**Why it happens:**
iCloud Drive's lazy download model means files are not guaranteed to be local. Developers test in the simulator (which has no real iCloud latency) or right after a fresh install when files are fresh. The failure only surfaces after the device has been offline, storage-optimized, or on a slow connection.

**How to avoid:**
Before reading any org file, check `NSMetadataUbiquitousItemDownloadingStatusKey` via `NSMetadataQuery`. If status is `NotDownloaded`, call `FileManager.default.startDownloadingUbiquitousItem(at:)` and wait for the download to complete before proceeding. Never use `NSFileCoordinator` to trigger this download — doing so may cause a deadlock. Show a loading state in the UI during the download.

**Warning signs:**
- Tests pass in the simulator but fail on device after being offline
- App shows "no entries" despite Emacs showing data in the file
- File size shows as zero after a read

**Phase to address:**
Phase 1 (sync foundation) — before any data is read or written, the download-before-read pattern must be established as the baseline file access primitive.

---

### Pitfall 2: Bypassing NSFileCoordinator for File Reads and Writes

**What goes wrong:**
Using `Data(contentsOf:)` or `String(contentsOf:)` directly on an iCloud Drive file path bypasses the iCloud coordination layer. When the iCloud daemon is simultaneously uploading or downloading the same file, uncoordinated access causes data corruption: truncated files, interleaved writes, or silent data loss. The corruption is non-deterministic and hard to reproduce.

**Why it happens:**
Direct file I/O is the natural Swift pattern. NSFileCoordinator has verbose, unfamiliar API. Tutorials for local file I/O don't mention it. The bug only manifests under concurrent access, which rarely happens in early development.

**How to avoid:**
All reads and writes to files in the iCloud ubiquitous container must go through `NSFileCoordinator`. Use `coordinateReading(itemAt:options:error:byAccessor:)` for reads and `coordinateWriting(itemAt:options:error:byAccessor:)` for writes. Wrap this in a dedicated `SyncProvider` protocol so the correct pattern is enforced in one place and all callers use it automatically.

**Warning signs:**
- Occasional truncated org files noticed in Emacs after mobile edits
- iCloud conflict badges appearing on files unexpectedly
- File writes appear to succeed but Emacs shows old content

**Phase to address:**
Phase 1 (sync foundation) — the `SyncProvider` abstraction layer should encode NSFileCoordinator usage internally so no other layer can bypass it.

---

### Pitfall 3: Org-Mode Write-Back That Corrupts Emacs-Readable Files

**What goes wrong:**
The app reads an org file, parses it into a model, appends a new entry, serializes the model back to org syntax, and writes it. If the serialization is even slightly wrong — wrong blank lines around property drawers, missing space after asterisks, malformed timestamps, drawer not closed with `:END:`, or property drawers not immediately following the heading — Emacs will misparse or silently corrupt the file structure on next open.

Specific rules that third-party parsers commonly violate:
- Headlines require exactly one space after asterisks: `* Heading` not `*Heading`
- Planning lines (`SCHEDULED:`, `DEADLINE:`) must immediately follow the heading with no blank lines between them and the heading
- `PROPERTIES:` drawers must immediately follow the heading (or its planning line), also with no blank lines
- Blank lines have structural meaning in lists: two consecutive blank lines end a list item
- Indentation uses spaces, not tabs (tabs count as 8 spaces in org-mode)
- Timestamps use a rigid format: `<2026-04-09 Wed>` including the day-of-week abbreviation

**Why it happens:**
Org-mode has no formal machine-readable grammar. The spec lives in the manual and `org-syntax.html`. Third-party parsers implement approximations. The swift-org library (last updated 2017, Swift 3) is incomplete by its own admission. Custom serializers written for the "happy path" miss edge cases that Emacs enforces.

**How to avoid:**
- Do not use swift-org (abandoned, Swift 3, incomplete) for write-back
- Build a minimal, append-only serializer rather than a full round-trip parser: always append new entries under date headings rather than parsing and re-serializing the whole file
- Validate output format with a unit test suite that round-trips through Emacs in CI (or at least manually before each release)
- Use `DateFormatter` with a fixed locale and the `EEE` day-of-week component for timestamp generation — do not hardcode abbreviated day names
- Write an Emacs batch script that loads test org files and confirms they parse without warnings; run it as a smoke test

**Warning signs:**
- Emacs opens the file and shows raw `PROPERTIES:` text instead of a property drawer
- `org-agenda` stops picking up entries from the file after a mobile edit
- Extra blank lines appear between headings in Emacs view

**Phase to address:**
Phase 1 (org-mode file layer) — the serializer must be built and validated against Emacs before any UI work begins.

---

### Pitfall 4: iCloud Conflict Versions Silently Consuming Storage and Causing Data Loss

**What goes wrong:**
When the same org file is edited on the iPhone and Mac simultaneously (or while offline), iCloud creates multiple conflicting versions. For document-based apps (`UIDocument`), iOS handles conflicts automatically. For plain-file apps (which Origami is), conflicts create hidden version files that are never surfaced, never resolved, and silently replicated to all iCloud peers — consuming quota indefinitely. Additionally, iCloud may resolve the conflict by silently keeping one version, discarding edits from the other device without notification.

**Why it happens:**
Plain-file iCloud apps do not get automatic conflict UI. Developers don't implement `NSFilePresenter` protocol methods for conflict callbacks (`presentedItemDidGainVersion`, `presentedItemDidLoseVersion`, `presentedItemDidResolveConflictVersion`) because they aren't obviously required. During early development, the Mac and iPhone are never edited simultaneously, so the bug never appears.

**How to avoid:**
- Implement `NSFilePresenter` and handle `presentedItemDidGainVersion` to detect conflicts
- When a conflict is detected, show a banner in the app ("File was modified on another device — your latest entry has been saved, older entries may differ")
- Explicitly call `NSFileVersion.removeOtherVersionsOfItemAtURL()` after detecting and surfacing conflicts, to prevent quota accumulation
- For Origami specifically: since entries are date-stamped and append-only, a simple "last-write-wins with both entries visible" merge is safe enough for v1 — the data model doesn't require true three-way merge

**Warning signs:**
- iCloud storage usage grows unexpectedly over weeks
- User notices entries missing after editing on both devices
- Emacs shows `<file> (modified on another device)` banners

**Phase to address:**
Phase 2 (iCloud integration hardening) — implement conflict detection and resolution as a distinct step after basic sync works.

---

### Pitfall 5: @State Model Objects Being Recreated on Every View Rebuild

**What goes wrong:**
When using the `@Observable` macro (iOS 17+, the modern pattern), the model object is passed as `@State` instead of `@StateObject`. Unlike `@StateObject`, `@State` does not use `@autoclosure` — it calls the initializer every time SwiftUI rebuilds the view hierarchy. If the `LogViewModel` (or equivalent) does any setup work in `init` (reading files, setting up NSMetadataQuery, registering for notifications), each view rebuild spawns a new observer instance. Multiple instances pile up in memory, each firing notification callbacks independently — creating "last write wins" races on the org file and potential duplicate writes.

**Why it happens:**
The migration from `ObservableObject`/`@StateObject` to `@Observable`/`@State` is a natural refactor for Swift 5.9+ projects. The initialization behavior difference is subtle and not prominent in migration guides. The bug is intermittent because it depends on view rebuild frequency.

**How to avoid:**
- Place all app-level observable state (file readers, sync coordinators) in the `App` struct as a single root-level `@State`, not inside individual views
- Never perform file I/O, NSMetadataQuery setup, or notification registration inside model initializers — do it in a separate `setup()` method called from `.onAppear` or `.task` exactly once
- Use `@Bindable` (not `@Binding`) when child views need to write back to `@Observable` models
- If the project targets iOS 16, keep `ObservableObject` + `@StateObject` for app-level state (safer lifecycle guarantee)

**Warning signs:**
- Multiple "file loaded" debug log lines firing on each navigation
- Duplicate entries appearing in org files after rapid UI interactions
- Memory usage climbing during normal use

**Phase to address:**
Phase 1 (SwiftUI architecture foundation) — establish where root state lives and which pattern is used before building any views.

---

## Technical Debt Patterns

**Shortcut:** Skip NSFileCoordinator for "simple" reads
- Immediate benefit: Less boilerplate, faster initial development
- Long-term cost: Data corruption bugs that only appear under real sync conditions; very hard to diagnose and fix later
- When acceptable: Never — coordinator usage must be the default from day one

**Shortcut:** Use swift-org library as-is for parsing and write-back
- Immediate benefit: Don't need to write a parser
- Long-term cost: Swift 3, abandoned library, incomplete spec coverage, no write-back safety guarantee; will produce org files Emacs misparsess
- When acceptable: Never for write-back; read-only use is marginal risk

**Shortcut:** Parse-and-reserialize the whole org file on each write
- Immediate benefit: Simpler data model — always treat file as source of truth
- Long-term cost: Round-trip parser must be perfect to avoid corruption; any serialization bug affects all existing entries, not just the new one
- When acceptable: Only if serializer is validated against Emacs in a full test suite; append-only is safer for v1

**Shortcut:** Hardcode day-of-week in timestamp strings
- Immediate benefit: Saves writing a formatter
- Long-term cost: Wrong day-of-week makes timestamps incorrect in Emacs; `org-agenda` shows wrong scheduling
- When acceptable: Never

**Shortcut:** Free Apple developer account (7-day re-signing)
- Immediate benefit: $0/year
- Long-term cost: App expires every 7 days, requires Mac with Xcode nearby, can't run in background if expired, 3-app slot limit
- When acceptable: Only for initial proof-of-concept; switch to $99/year paid account before the app becomes a daily-use tool

---

## Integration Gotchas

**iCloud Drive / NSMetadataQuery**
- Common mistake: Starting NSMetadataQuery on a background thread
- Correct approach: Start and stop NSMetadataQuery on the main thread; results callbacks arrive on main thread by default

**iCloud Drive / NSFileCoordinator**
- Common mistake: Using NSFileCoordinator to trigger `startDownloadingUbiquitousItem`
- Correct approach: Call `FileManager.default.startDownloadingUbiquitousItem(at:)` directly (without coordinator); using coordinator here can deadlock

**iCloud Drive / File eviction**
- Common mistake: Using NSFileCoordinator to call `evictUbiquitousItem`
- Correct approach: Call `FileManager.default.evictUbiquitousItem(at:)` directly; same deadlock risk as above

**iCloud Drive / CloudKit throttling**
- Common mistake: Rapid successive file writes (e.g., saving on every keystroke)
- Correct approach: Debounce or batch writes; CloudKit enforces a 30-second minimum interval between rapid operations; throttling causes sync stalls

**Org-mode / Timestamps**
- Common mistake: Generating timestamps with system locale (`Date` default formatting)
- Correct approach: Use a fixed locale (`en_US_POSIX`) with `DateFormatter`; org-mode timestamps require English day abbreviations regardless of device locale

**Org-mode / Drawer placement**
- Common mistake: Inserting a blank line between a heading and its PROPERTIES drawer
- Correct approach: Drawer must be on the very next line after the heading (or after the planning line if one exists)

---

## Performance Traps

**NSMetadataQuery without predicate scoping**
- Symptoms: Excessive notification callbacks, UI hitching when iCloud has many files
- Prevention: Scope query to the specific filenames (`food-log.org`, `workout-log.org`) using `NSPredicate`; don't query all ubiquitous items
- When it breaks: As soon as the iCloud container has more than a few files

**Full org file re-parse on every app foreground**
- Symptoms: Noticeable delay when returning to the app after background
- Prevention: Cache the parsed model in memory; re-parse only when file modification date has changed (check `URLResourceKey.contentModificationDateKey`)
- When it breaks: Once the log files grow beyond a few hundred entries (months of use)

**Synchronous file reads on the main actor**
- Symptoms: UI freezes during file load, spinning wheel on launch
- Prevention: All file I/O (including NSFileCoordinator coordination blocks) must run on a background actor or `Task { }` with `await`; publish results back to `@MainActor` for UI updates
- When it breaks: Immediately if the org file is >100KB or sync is slow

---

## Security Mistakes

This is a personal, single-user, sideloaded app with no network backend. Standard web security concerns don't apply. Domain-specific concerns:

**iCloud container entitlement misconfiguration**
- Risk: App cannot access iCloud Drive; files land in wrong container; other apps can read the container if bundle ID is incorrectly specified
- Prevention: Use an explicit, project-specific iCloud container ID (`iCloud.com.username.origami`); verify the entitlement matches in both Xcode capability settings and provisioning profile

**Provisioning profile expiry causing silent background failure**
- Risk: App continues to appear open but all background sync stops when profile expires; data entered doesn't sync until re-signed
- Prevention: With $99 paid account, profiles last 1 year; calendar a reminder to re-sign 2 weeks before expiry; note that certificates are now capped at 460 days (as of Feb 2025)

---

## UX Pitfalls

**Showing stale data while sync is in progress**
- User impact: User logs a meal on the iPhone, opens Emacs immediately, sees the entry missing — assumes the app failed
- Better approach: Show a sync status indicator ("Syncing..." / "Synced 2 min ago") so the user understands sync is asynchronous; design for minutes-of-delay, not seconds

**No feedback when iCloud is unavailable**
- User impact: App silently fails to save; user loses a logged workout
- Better approach: Detect iCloud availability at launch (`FileManager.ubiquityIdentityToken`); show a non-blocking warning banner if unavailable; queue writes locally and sync when available

**Form data lost when navigating back without saving**
- User impact: User is halfway through a 6-set workout log, hits back by mistake, loses everything
- Better approach: Auto-save form state to `@AppStorage` or a draft model on each field change; restore draft on next open; explicitly clear draft on successful save

**Numeric keyboard without decimal support for macros**
- User impact: Cannot enter `28.5g` protein; forced to round or switch keyboards manually
- Better approach: Use `.numberPad` for whole-number fields (sets, reps) and `.decimalPad` for macro fields (protein, carbs, fat, calories); test this on device, not simulator

---

## "Looks Done But Isn't" Checklist

- [ ] **iCloud sync works after offline period**: Test by enabling Airplane Mode for 30 min, logging entries, re-enabling WiFi — verify entries appear in Emacs within 5 minutes
- [ ] **Org files open cleanly in Emacs**: After every new serialization change, open the actual file in Emacs and run `M-x org-lint` to check for syntax warnings
- [ ] **Placeholder file handling**: Test by evicting the org file from local storage (`Files` app > iCloud Drive > right-click > Remove Download), then launching Origami — it should show a loading state, not empty data
- [ ] **Conflict resolution**: Edit the file simultaneously in Emacs and Origami while offline on both sides; verify both devices end up with a complete (not silently truncated) file
- [ ] **Provisioning profile expiry**: Re-sign and test on a device at least once before shipping; verify background sync continues after re-signing
- [ ] **App runs without iCloud enabled**: Some users may have iCloud disabled per-app; app should degrade gracefully with a clear message, not crash
- [ ] **Day-of-week in timestamps is correct**: Log an entry on a known weekday; open in Emacs and confirm `<2026-04-09 Thu>` has the correct abbreviation

---

## Recovery Strategies

**Org file corruption from bad write-back**
- Recovery cost: MEDIUM
- Recovery steps: Keep Emacs-side git history of the org files (commit health files regularly via Emacs); roll back to last known-good commit; the mobile entry is lost but the existing data is restored

**iCloud conflict version storage leak**
- Recovery cost: LOW
- Recovery steps: Use `NSFileVersion.otherVersionsOfItem(at:)` to enumerate and remove conflict versions programmatically; add a "resolve conflicts" action in a debug menu during development

**Provisioning profile expired mid-use**
- Recovery cost: LOW
- Recovery steps: Re-sign via Xcode (`Product > Run` with device connected); with paid account this is a 2-minute operation; maintain a reminder calendar event 2 weeks before annual expiry

**@Observable model recreation causing duplicate writes**
- Recovery cost: HIGH (requires architecture change)
- Recovery steps: Remove duplicate org entries manually in Emacs; refactor to move root state to `App` struct level; audit all `@State` model instantiation sites

---

## Pitfall-to-Phase Mapping

**Placeholder file read before download** — Phase 1 (sync layer foundation)
- Verification: Write a test that evicts the file and confirms the app shows a loading state rather than empty data

**NSFileCoordinator bypass** — Phase 1 (sync layer foundation)
- Verification: All file access goes through a `SyncProvider` protocol; `Data(contentsOf:)` / `String(contentsOf:)` never appear in the codebase outside of the provider implementation

**Org-mode write-back corruption** — Phase 1 (org-mode file layer)
- Verification: Run test org files through the serializer and open in Emacs; run `org-lint`; check timestamps have correct day-of-week

**iCloud conflict version handling** — Phase 2 (sync hardening)
- Verification: Simulate conflict by editing on two devices while offline; verify app shows conflict banner and removes extra versions

**@Observable model recreation** — Phase 1 (SwiftUI architecture)
- Verification: Add `print("init")` to root model; confirm it fires exactly once per app launch, not on each view rebuild

**Provisioning profile expiry** — Phase 3 (distribution / maintenance)
- Verification: Calendar reminder set; test re-sign process at least once before treating as daily driver

**Form data loss on back navigation** — Phase 2 (UX polish)
- Verification: Fill a log form halfway, kill the app, reopen — draft should be restored

**CloudKit throttling from rapid writes** — Phase 2 (sync hardening)
- Verification: Confirm saves are debounced (minimum 500ms between disk writes); check Console.app for CloudKit throttle warnings during heavy logging sessions

---

## Sources

- Apple Developer Documentation: NSFileCoordinator — https://developer.apple.com/documentation/foundation/nsfilecoordinator
- Apple Technical Note TN2336: Handling version conflicts in the iCloud environment — https://developer.apple.com/library/archive/technotes/tn2336/_index.html
- fatbobman.com: In-Depth Guide to iCloud Documents — https://fatbobman.com/en/posts/in-depth-guide-to-icloud-documents/
- fatbobman.com: Advanced iCloud Documents — https://fatbobman.com/en/posts/advanced-icloud-documents/
- Carlo Zottmann: iOS iCloud Drive Synchronization Deep Dive (2025-09-08) — https://zottmann.org/2025/09/08/ios-icloud-drive-synchronization-deep.html
- Jesse Squires: Swift @Observable macro is not a drop-in replacement for ObservableObject (2024) — https://www.jessesquires.com/blog/2024/09/09/swift-observable-macro/
- Apple Developer: Migrating from ObservableObject to @Observable macro — https://developer.apple.com/documentation/SwiftUI/Migrating-from-the-observable-object-protocol-to-the-observable-macro
- Org-mode Syntax Specification — https://orgmode.org/worg/org-syntax.html
- objc.io: Mastering the iCloud Document Store — https://www.objc.io/issues/10-syncing-data/icloud-document-store/
- Capawesome: iOS Certificates and Provisioning Profiles Explained — https://capawesome.io/blog/ios-certificates-and-provisioning-profiles-explained/
- swift-org library (GitHub, orgapp/swift-org) — last active 2017, Swift 3, incomplete
- beorg app + community: iCloud sync delay reports and workarounds (2024 user reports)

---
*Pitfalls research for: Personal iOS org-mode tracking app (Origami) — sideloaded, iCloud Drive sync, SwiftUI*
*Researched: 2026-04-09*
