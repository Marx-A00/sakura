# Pitfalls Research

**Domain:** Personal Android app — org-mode plain text tracking, Syncthing file sync, Jetpack Compose, sideloaded APK on Galaxy S21 FE
**Researched:** 2026-04-09
**Confidence:** HIGH for file permissions (official Android docs verified), HIGH for Syncthing conflict behavior (official docs + forum threads), MEDIUM-HIGH for Compose pitfalls (official docs + 2025 community sources), MEDIUM for sideloading policy (official Google blog posts verified, enforcement timeline in flux)

---

## Critical Pitfalls

### Pitfall 1: SAF Tree URI Grants Silently Revoked After App Reinstall or OS Update

**What goes wrong:**
The app uses `ACTION_OPEN_DOCUMENT_TREE` to let the user select their Syncthing-managed folder (e.g., `~/Sync/org/`). The user grants access and the app persists the URI via `takePersistableUriPermission()`. On the next reinstall, a system permission reset, or certain OS updates, the persisted URI grant is silently revoked. The app then tries to read or write using the stale URI, gets a `SecurityException`, and either crashes or silently does nothing — appearing to the user as data loss.

**Why it happens:**
Android's SAF persisted URI grants are tied to the app's install identity. Reinstall == new identity == grants gone. This is expected behavior per Android documentation: "The system can revoke the permission at any time." During development, frequent reinstalls via ADB trigger this constantly. The failure is non-obvious because the `ContentResolver` call throws a `SecurityException` at runtime rather than at URI construction time.

**How to avoid:**
- Always call `contentResolver.getPersistedUriPermissions()` on app startup and verify the expected URI is still present before using it
- If the URI is missing, immediately redirect the user to re-grant access via `ACTION_OPEN_DOCUMENT_TREE` — do not silently fail
- Store the URI string in `DataStore` (not `SharedPreferences`, which can be cleared) so it survives app data migration
- Build a `StoragePermissionGuard` abstraction that all file access passes through — it checks grant validity before every read/write session, not just on startup
- Note the grant limit: apps can hold max 512 persisted grants on API 30+. With only 2 org files this is not a concern, but worth knowing

**Warning signs:**
- `SecurityException: Permission Denial` in logs after reinstall
- App silently shows empty data after an OS update
- File write appears to succeed (no exception thrown at the call site) but nothing appears on disk

**Phase to address:**
Phase 1 (storage foundation) — the `StoragePermissionGuard` must be the first primitive built, before any UI or data layer.

---

### Pitfall 2: MANAGE_EXTERNAL_STORAGE Is the Wrong Permission for This App

**What goes wrong:**
Developer hears "the Syncthing folder is on external storage" and reaches for `MANAGE_EXTERNAL_STORAGE` (the `All Files Access` permission) as the straightforward solution. On a sideloaded APK this works — the permission can be granted manually in Settings. But the mental model is wrong and creates two compounding problems. First, `MANAGE_EXTERNAL_STORAGE` requires the user to navigate to Settings > Apps > Special app access > All files access every time the app is reinstalled. Second, it grants an unnecessarily broad attack surface and is a habit that would permanently block Play Store distribution if the project ever migrated there.

**Why it happens:**
Tutorials for "read files from Syncthing folder" written pre-Android 11 recommend broad storage permissions. Android 11's Scoped Storage change made these tutorials wrong, but they still rank highly in search results. The permission works on sideloaded APKs with no friction, so developers don't discover the cost until much later.

**How to avoid:**
- Use `ACTION_OPEN_DOCUMENT_TREE` (SAF) to let the user select the Syncthing folder once
- Store the returned tree URI with `takePersistableUriPermission()` for long-term access
- Use `DocumentFile.fromTreeUri()` and `DocumentFile.listFiles()` to enumerate files under that tree
- Read and write files via `contentResolver.openInputStream()` / `contentResolver.openOutputStream()` using document URIs — never via raw `File` paths
- Never add `MANAGE_EXTERNAL_STORAGE` to the manifest; if you encounter "can't access path X" debug by checking SAF grant coverage, not by escalating permissions

**Warning signs:**
- Developer is using `File("/sdcard/Sync/org/food-log.org")` as a path anywhere in the codebase
- `READ_EXTERNAL_STORAGE` appears in the manifest (deprecated on Android 13+; ignored on Android 13+)
- `MANAGE_EXTERNAL_STORAGE` appears in the manifest

**Phase to address:**
Phase 1 (storage foundation) — file access strategy must be locked in before any file reading or writing is implemented.

---

### Pitfall 3: Org-Mode Write-Back That Corrupts Emacs-Readable Files

**What goes wrong:**
The app reads an org file through SAF, parses it into a model, appends a new food or workout entry, serializes back to org syntax, and writes via `contentResolver.openOutputStream()`. If the serializer is even slightly wrong — wrong blank lines around property drawers, missing space after asterisks, malformed timestamps, drawer not closed with `:END:`, tabs instead of spaces — Emacs misparsed the file on next open. Org-agenda stops picking up entries. Data appears to be lost.

Specific rules that Android/JVM parsers commonly violate:
- Headlines require exactly one space after asterisks: `* Heading` not `*Heading`
- Planning lines (`SCHEDULED:`, `DEADLINE:`) must immediately follow the heading with no blank lines between them and the heading
- `PROPERTIES:` drawers must immediately follow the heading (or planning line), also with no blank lines
- Blank lines have structural meaning in lists: two consecutive blank lines end a list item
- Indentation uses spaces, not tabs (tabs count as 8 spaces in org-mode)
- Timestamps use a rigid format: `<2026-04-09 Thu>` including the English day-of-week abbreviation regardless of device locale
- Property values must be on their own lines, `:KEY: value` not embedded inline

**Why it happens:**
The org-mode specification has no formal machine-readable grammar. It lives in `org-syntax.html` and the Emacs manual. The pmiddend/org-parser library for the JVM (the only current Kotlin-compatible option) is a read-only parser — it does not support write-back. Custom serializers built from scratch miss edge cases that Emacs enforces. Testing in isolation (unit tests) never catches the failure; only opening the actual file in Emacs does.

**How to avoid:**
- Do not attempt full round-trip parse-then-reserialize; use append-only strategy instead: always append new entries under a date heading, never reparse and rewrite the whole file
- Build a minimal, deterministic serializer with exactly zero configurable whitespace options — hardcode the canonical format
- Generate timestamps using `java.time.LocalDate` with an explicit `Locale.ENGLISH` formatter; never use device locale for day-of-week abbreviations
- Validate every serializer change by opening the actual output file in Emacs and running `M-x org-lint` — make this a manual checklist item before any phase is considered done
- Write unit tests that assert the exact byte-for-byte output of the serializer for every entry type (food log, workout set)

**Warning signs:**
- Emacs opens the file and shows raw `PROPERTIES:` text instead of a collapsed property drawer
- `org-agenda` stops picking up entries from the file after a mobile edit
- Extra blank lines appear between headings in Emacs view
- `org-lint` reports warnings after any mobile write

**Phase to address:**
Phase 1 (org-mode file layer) — the serializer must be built and validated against actual Emacs before any UI work begins.

---

### Pitfall 4: Syncthing Conflict Files (.sync-conflict) Silently Accumulating and Corrupting the Active File

**What goes wrong:**
Two scenarios trigger `.sync-conflict` files in this setup:

**Scenario A — Genuine conflict:** The user logs food on the phone while Syncthing hasn't synced yet (e.g., away from home Wi-Fi), then edits the same org file in Emacs before reconnecting. When the phone reconnects, Syncthing detects both versions differ and renames the older one to `food-log.sync-conflict-<date>-<time>-<deviceID>.org`. The app then opens `food-log.org` (the winner) but the conflict copy is now also present in the directory and gets listed by the app's `DocumentFile.listFiles()` call if the app iterates the folder without filtering. Worse: if the app opened the conflict copy by mistake, any write to it creates yet another conflict.

**Scenario B — Spurious conflict from Syncthing bug:** Syncthing versions around 1.22.x on Android generated `.sync-conflict` files for nearly every modification even with no genuine conflict. This was a confirmed bug with a hotfix (1.22.1.1), but the pattern repeats with other versions. Syncthing on Android has historically been fragile about file modification timestamps due to Android's storage stack routing calls through SAF on Android 11+, which poorly implements inotify.

**Why it happens:**
Syncthing's conflict detection uses modification time, size, and permission bits. Android's SAF layer can cause the file system to report stale or imprecise modification timestamps when files are written through `ContentResolver`. Syncthing then sees a file that appears modified on both ends (even if only one end actually changed) and creates a conflict file.

**How to avoid:**
- Filter `DocumentFile.listFiles()` results to exclude any filename matching `.sync-conflict` in the middle — never open or write to conflict files
- After every write to an org file, log the modification timestamp the system reports back and verify it matches the write time (detection of timestamp drift)
- Keep Syncthing updated and pin to a known-stable version; watch the Syncthing release notes for Android-specific fixes
- Add a "Conflicts detected" warning banner in the UI: on each app open, check whether any `*.sync-conflict-*.org` files exist alongside the active org files; if so, surface the conflict to the user with guidance to resolve in Emacs
- For write-through safety: write to a temporary file first (`.org.tmp`), then rename/replace the target. This minimizes the window in which Syncthing sees a partially-written file and flags a conflict

**Warning signs:**
- `*.sync-conflict-*.org` files visible in the Syncthing folder on the desktop
- App shows fewer entries than Emacs (opened the conflict file instead of the active one)
- File modification timestamps don't advance after writes (SAF timestamp bug indicator)

**Phase to address:**
Phase 1 (file access layer) — conflict file filtering must be in the file enumeration code from the start; Phase 2 (sync hardening) — add the UI warning banner and write-via-tempfile strategy.

---

### Pitfall 5: Samsung One UI Auto Blocker Silently Blocking Reinstalls

**What goes wrong:**
On newer Samsung Galaxy devices with One UI 6.1.1 and later, the Auto Blocker feature is **enabled by default**. Auto Blocker fully blocks installation of apps from unauthorized sources — including ADB sideloading and APK file installs — even if the individual app has been granted `REQUEST_INSTALL_PACKAGES`. The Galaxy S21 FE shipped with One UI 4 (likely upgraded to One UI 6 via Samsung's update cycle). If Auto Blocker is on, any attempt to install an updated APK either silently fails or shows a generic "blocked for security" dialog with no explanation that Auto Blocker is the cause.

**Why it happens:**
Samsung introduced Auto Blocker quietly as an "opt-in" feature in One UI 6.0 and then made it opt-out (on by default) in One UI 6.1.1. It is separate from the per-app "Install unknown apps" permission. It acts at a lower level and overrides per-app permissions entirely. Developers who rely on ADB installs during development may not notice it during initial setup but hit it after a firmware update enables it.

**How to avoid:**
- Before starting any development, verify Auto Blocker status on the S21 FE: Settings > Security and privacy > Auto Blocker
- Disable Auto Blocker for the duration of development/personal use
- Document this as a one-time setup step — the setting persists across reboots and app updates
- Note: disabling Auto Blocker still leaves individual app-level "Install unknown apps" permissions in place, which remain the normal guard for user-installed APK installers (e.g., a file manager)

**Warning signs:**
- ADB `adb install` command completes with exit code 0 but the new version doesn't appear on device
- APK installed via file manager shows "App not installed" with no further error details
- App version on device doesn't change after what appeared to be a successful ADB install

**Phase to address:**
Phase 0 (device setup / development environment) — verify Auto Blocker status before the first build.

---

### Pitfall 6: Google Developer Verification Required for Sideloading After August 2026

**What goes wrong:**
Starting August 2026, Google is rolling out Android developer verification requirements that affect all APK distribution, including sideloading. Apps from unregistered developers will require an "advanced flow" for installation: the user must enable developer mode, confirm non-coercion, and complete a biometric wait step. This is a multi-step friction process, not a simple one-tap. If Origami is under development past August 2026 and the developer is not registered, every reinstall on the S21 FE will require this friction flow.

**Why it happens:**
Google is requiring developer identity verification to prevent malware distribution via sideloading. For personal and hobby use, Google provides a free limited distribution account (up to 20 devices, no government ID required) as an alternative to the full paid registration.

**How to avoid:**
- Register for a free Android Developer Console limited distribution account before August 2026 — this eliminates the "advanced flow" burden for the developer's own devices
- The limited distribution account has no cost and allows distribution to up to 20 devices
- Full registration (Play Store) remains the paid path; limited distribution is the personal/hobby path
- ADB sideloading remains fully available as always and bypasses this restriction entirely — ADB install from a connected Mac is unaffected by the new policy

**Warning signs:**
- APK installs begin requiring multi-step biometric + delay process on device
- Device shows "app from unverified developer" warning where none appeared before

**Phase to address:**
Phase 0 (distribution setup) — register for limited distribution account before August 2026; use ADB install during active development to sidestep the friction regardless.

---

## Technical Debt Patterns

**Shortcut:** Use raw `File("/sdcard/Sync/org/")` paths instead of SAF tree URIs
- Immediate benefit: Familiar Java I/O API, no SAF boilerplate
- Long-term cost: Breaks on Android 10+ Scoped Storage enforcement; requires `MANAGE_EXTERNAL_STORAGE` which is a Play Store blocker; file paths are not stable across OS updates or storage remounts
- When acceptable: Never — even on a personal sideloaded app, SAF is the correct pattern and establishes good habits

**Shortcut:** Parse-and-reserialize the whole org file on each write
- Immediate benefit: Simpler data model — always treat file as canonical source of truth
- Long-term cost: Round-trip parser must be perfect to avoid corruption; any serialization bug affects all existing entries, not just the new one; no JVM/Kotlin library supports write-back safely
- When acceptable: Only if serializer is validated against Emacs in a comprehensive test suite; append-only is safer for v1

**Shortcut:** Hardcode day-of-week abbreviations in timestamp strings
- Immediate benefit: Saves writing a formatter
- Long-term cost: Wrong day-of-week makes timestamps incorrect in Emacs; `org-agenda` shows wrong scheduling; the device locale affects `LocalDateTime.format()` silently
- When acceptable: Never — always use `Locale.ENGLISH` explicitly

**Shortcut:** Use `remember { mutableStateOf(...) }` in a screen-level composable for ViewModel state
- Immediate benefit: Feels natural in Compose; no ViewModel setup
- Long-term cost: State is lost on configuration change (screen rotation); state is recreated on every recomposition; file I/O side effects fire multiple times
- When acceptable: Only for ephemeral UI state (dialog open/closed, text field content); never for data loaded from files

**Shortcut:** Skip conflict file filtering in `DocumentFile.listFiles()`
- Immediate benefit: Simpler code
- Long-term cost: App may open a `.sync-conflict` file as the active org file, silently losing the canonical version, or surface duplicate entries
- When acceptable: Never — conflict file filtering must be in the enumeration primitive

---

## Integration Gotchas

**SAF / DocumentFile API**
- Common mistake: Using `DocumentFile.fromFile()` instead of `DocumentFile.fromTreeUri()` — the former bypasses SAF and fails on Scoped Storage
- Correct approach: Always use `DocumentFile.fromTreeUri(context, treeUri)` with the URI obtained from `ACTION_OPEN_DOCUMENT_TREE`

**SAF / ContentResolver streams**
- Common mistake: Calling `openOutputStream()` without specifying mode, defaulting to `"w"` which truncates the file before writing
- Correct approach: Use `"wa"` mode for append operations; use `"rwt"` or write to a temp document then rename for safe full-file replacement

**Syncthing / Timestamp precision**
- Common mistake: Assuming Android file system timestamps are millisecond-precise when accessed via SAF
- Correct approach: Treat file modification timestamps as second-precision only; do not use them for sub-second ordering of writes

**Syncthing / File name filtering**
- Common mistake: Iterating all files in the org directory without filtering out Syncthing metadata files
- Correct approach: Filter for files matching `*.org` exactly; exclude files containing `.sync-conflict`, `.stversions`, or beginning with `.`

**Jetpack Compose / File I/O in composable body**
- Common mistake: Calling file reads directly inside a composable function body (runs on every recomposition)
- Correct approach: File I/O always lives in a `ViewModel` coroutine launched via `viewModelScope.launch`; composables only observe `StateFlow` or `State` from the ViewModel

**Jetpack Compose / LaunchedEffect key selection**
- Common mistake: `LaunchedEffect(Unit)` used to trigger a file reload that needs to re-run when the source file changes
- Correct approach: Pass the relevant trigger (e.g., a file-changed flag from a `FileObserver`) as the key to `LaunchedEffect(fileChangedKey)` so it re-executes when needed

**FileObserver / Garbage collection**
- Common mistake: Creating a `FileObserver` as a local variable inside a function — it gets garbage collected and stops firing events
- Correct approach: Hold the `FileObserver` reference in the `ViewModel` as a member variable; start/stop it in `init`/`onCleared()`

**FileObserver / SAF paths on Android 11+**
- Common mistake: Trying to use `FileObserver` on a SAF-managed path — inotify is not fully implemented on Android 11+ SAF paths
- Correct approach: Poll for file changes on a timer (e.g., every 30 seconds when the app is foregrounded) by comparing stored modification timestamps via `DocumentFile.lastModified()`; do not rely on `FileObserver` for files accessed through SAF

---

## Performance Traps

**Full org file re-parse on every app foreground**
- Symptoms: Noticeable delay when returning to the app after background, especially as log files grow
- Prevention: Cache the parsed model in memory in the ViewModel; re-parse only when `DocumentFile.lastModified()` has advanced since last parse
- When it breaks: Once log files grow beyond a few hundred entries (months of daily use)

**Synchronous ContentResolver reads on the main thread**
- Symptoms: UI freezes on launch or foreground; ANR dialog after 5 seconds
- Prevention: All `contentResolver.openInputStream()` / `openOutputStream()` calls must run in `withContext(Dispatchers.IO)` inside a coroutine; never on the main dispatcher
- When it breaks: Immediately if the org file is >50KB or the device is under load

**DocumentFile.listFiles() called on main thread**
- Symptoms: Perceptible lag on folder open; frame drops
- Prevention: `DocumentFile.listFiles()` is a blocking IPC call to the document provider — always call it in `Dispatchers.IO`; cache the result
- When it breaks: Immediately — this call can take 100-500ms even for small directories

**Unstable data classes in Compose causing full list recomposition**
- Symptoms: Every time a single log entry is added, the entire list recomposes; UI jank on the log history screen
- Prevention: Use `@Immutable` or `@Stable` annotations on data classes used in Compose; use `ImmutableList` from kotlinx-collections-immutable rather than `List<T>`
- When it breaks: As soon as the log list has more than ~20 items and a new entry is added

---

## Security Mistakes

This is a personal, single-user, sideloaded app with no network backend. Standard web security concerns (XSS, injection) do not apply. Domain-specific concerns:

**Persisted SAF URI stored in SharedPreferences (cleartext)**
- Risk: Another app with READ_EXTERNAL_STORAGE or a backup tool can read the URI. For a personal org folder this is low sensitivity, but the URI itself grants document access if misappropriated.
- Prevention: Use `EncryptedSharedPreferences` or Jetpack `DataStore` for storing the persisted URI string; mark the DataStore file as `Context.MODE_PRIVATE`

**Temp file visible to Syncthing during write**
- Risk: If the app writes a temp file (`.org.tmp`) and Syncthing syncs it before rename, Emacs receives a `.org.tmp` file in the sync folder
- Prevention: Name temp files with a prefix Syncthing is configured to ignore (add a `.stignore` rule for `*.tmp`) or use the app's private storage for temp files and only move to the SAF location on completion

---

## UX Pitfalls

**SAF folder picker shown with no explanation**
- User impact: User sees an unfamiliar system folder picker, doesn't know what to select, picks the wrong folder
- Better approach: Show a dedicated onboarding screen before launching the picker, with a screenshot or illustration of the correct Syncthing folder path; persist the chosen path and don't ask again unless revoked

**Showing stale data while the org file is being written by Syncthing**
- User impact: User opens the app immediately after Syncthing finishes a sync; sees old entries for 2-3 seconds before the app detects the file has changed
- Better approach: Show a last-synced timestamp in the UI ("Data from 2 min ago"); trigger a re-read when the app foregrounds and `DocumentFile.lastModified()` has advanced

**No feedback when SAF permission is lost**
- User impact: App silently fails to save; user enters a complete workout log, taps Save, nothing happens; data is lost
- Better approach: Wrap every write in a try/catch for `SecurityException`; on catch, show a non-dismissible alert: "Storage permission lost — tap here to re-grant access." Never discard the unsaved data until the permission is restored and the write succeeds

**Numeric keyboard without decimal support for macros**
- User impact: Cannot enter `28.5g` protein; forced to round or switch keyboards manually
- Better approach: Use `KeyboardType.Number` with `KeyboardType.Decimal` for macro fields (protein, carbs, fat, calories); use `KeyboardType.Number` for whole-number fields (sets, reps); test on device, not the emulator

**Form data lost when navigating back without saving**
- User impact: User is halfway through a 6-set workout log, hits back, loses everything
- Better approach: Auto-save draft state to `DataStore` on every field change; restore draft on next open; explicitly clear draft on successful save

---

## "Looks Done But Isn't" Checklist

- [ ] **SAF permission survives reinstall:** Reinstall the APK via ADB; verify the app immediately re-prompts for folder access rather than silently failing with a `SecurityException`
- [ ] **Conflict file filtering works:** Drop a `.sync-conflict-20260409-120000-DEVICEID.org` file into the Syncthing folder; verify the app does not open it as the active file or show its contents as real entries
- [ ] **Org files open cleanly in Emacs:** After every serializer change, write a test entry via the app, then open the file in Emacs and run `M-x org-lint` — zero warnings required
- [ ] **Timestamps are correct in Emacs locale:** Log an entry on a known weekday on a device set to a non-English locale (e.g., Portuguese); open in Emacs and confirm `<2026-04-09 Thu>` has the English day abbreviation
- [ ] **File reads are off the main thread:** Use Android Studio's CPU profiler or StrictMode to confirm no `ContentResolver` I/O occurs on the main thread
- [ ] **Auto Blocker is disabled on S21 FE:** Settings > Security and privacy > Auto Blocker — verify Off before the first ADB install session
- [ ] **Syncthing sync works end-to-end:** Log an entry in the app; within 2 minutes on the same Wi-Fi, verify the entry appears correctly in Emacs on the desktop machine
- [ ] **SAF write mode is non-truncating for appends:** Open an existing org file with entries; append a new entry; verify all existing entries are still present in the file
- [ ] **Developer verification account registered:** Confirm registration in Android Developer Console before August 2026 to avoid the "advanced flow" friction

---

## Recovery Strategies

**SAF URI grant revoked**
- Recovery cost: LOW
- Recovery steps: App detects `SecurityException` on next file access; shows re-grant prompt; user re-selects folder via `ACTION_OPEN_DOCUMENT_TREE`; no data loss (files unchanged on disk)

**Org file corruption from bad serializer**
- Recovery cost: MEDIUM
- Recovery steps: Keep Emacs-side git history of the org files (commit `food-log.org` and `workout-log.org` via Emacs after each sync); roll back to last known-good commit; the mobile-appended entry is lost but existing data is restored

**Syncthing conflict file opened by mistake**
- Recovery cost: LOW-MEDIUM
- Recovery steps: Identify the canonical file (largest / most recently modified); rename conflict file to `.bak`; if entries from the conflict copy are needed, manually merge in Emacs using `diff`

**Auto Blocker blocking reinstalls during critical development**
- Recovery cost: LOW
- Recovery steps: Settings > Security and privacy > Auto Blocker > Off; the setting takes effect immediately; no reboot required

**Unsaved form data lost on back navigation**
- Recovery cost: HIGH (user visible, no programmatic recovery)
- Recovery steps: Ship the DataStore draft save/restore mechanism in Phase 1 to prevent this; post-loss, data cannot be recovered

---

## Pitfall-to-Phase Mapping

**SAF tree URI grant revocation**
- Prevention phase: Phase 1 (storage foundation)
- Verification: `StoragePermissionGuard.checkGrant()` called on every app launch; reinstall test triggers re-grant prompt correctly

**MANAGE_EXTERNAL_STORAGE anti-pattern**
- Prevention phase: Phase 1 (storage foundation)
- Verification: `grep -r "MANAGE_EXTERNAL_STORAGE" app/src/` returns zero results; no raw `File("/sdcard/")` paths in codebase

**Org-mode serializer corruption**
- Prevention phase: Phase 1 (org-mode file layer)
- Verification: Unit tests assert byte-for-byte serializer output; manual `org-lint` passes on every Phase 1 completion

**Syncthing conflict file contamination**
- Prevention phase: Phase 1 (file enumeration layer)
- Verification: Conflict file filtering test passes; conflict warning banner appears in UI when conflict files are present

**Samsung Auto Blocker blocking installs**
- Prevention phase: Phase 0 (device setup)
- Verification: `adb install -r app-debug.apk` succeeds and new version appears on device

**Google developer verification friction**
- Prevention phase: Phase 0 (distribution setup)
- Verification: Android Developer Console limited distribution account active before August 2026

**Compose state on wrong tier**
- Prevention phase: Phase 1 (Compose architecture)
- Verification: All `StateFlow` sources live in ViewModel; no file I/O calls in composable function bodies; StrictMode enabled during development with disk-on-main-thread penalty

**FileObserver garbage collection / SAF inotify failure**
- Prevention phase: Phase 1 (file change detection)
- Verification: `FileObserver` reference held in ViewModel (not local variable); polling fallback used for SAF paths rather than inotify

**Syncthing timestamp precision causing spurious conflicts**
- Prevention phase: Phase 2 (sync hardening)
- Verification: Write-via-temp-then-rename implemented; modification timestamp logging shows stable values post-write

**SAF write truncating existing content**
- Prevention phase: Phase 1 (file write layer)
- Verification: Append test writes new entry and confirms all prior entries intact; `openOutputStream()` mode is `"wa"` for appends

---

## Sources

- Android Developers: Data and file storage overview — https://developer.android.com/training/data-storage
- Android Developers: Storage use cases and best practices — https://developer.android.com/training/data-storage/use-cases
- Android Developers: Open files using the Storage Access Framework — https://developer.android.com/guide/topics/providers/document-provider
- Android Developers: Side-effects in Compose — https://developer.android.com/develop/ui/compose/side-effects
- Android Developers: State and Jetpack Compose — https://developer.android.com/develop/ui/compose/state
- Android Developers: FileObserver API reference — https://developer.android.com/reference/android/os/FileObserver
- Syncthing Documentation: Understanding Synchronization — https://docs.syncthing.net/users/syncing.html
- Syncthing Community Forum: File conflict for every modification on Android — https://forum.syncthing.net/t/file-conflict-for-every-modification-on-android/19272
- Syncthing Community Forum: Filesystem Watcher Errors — Android — https://forum.syncthing.net/t/filesystem-watcher-errors-android/16436/12
- Google Android Developers Blog: Android developer verification — https://android-developers.googleblog.com/2026/03/android-developer-verification.html
- Android Developer Console Help: Understanding Android developer verification — https://support.google.com/android-developer-console/answer/16561738
- Gadget Hacks: Google's New Android Sideloading Rules Start August 2026 — https://android.gadgethacks.com/news/googles-new-android-sideloading-rules-start-august-2026/
- Sammy Fans: One UI 6.1.1 Auto Blocker bans app sideloading — https://www.sammyfans.com/2024/07/24/one-ui-6-1-1-auto-blocker-bans-app-sideloading-on-samsung-phones/
- Android Authority: Enable sideloading on One UI 6.1.1 — https://www.androidauthority.com/enable-sideloading-one-ui-6-1-1-3463446/
- CommonsWare: Count Your SAF Uri Persisted Permissions — https://commonsware.com/blog/2020/06/13/count-your-saf-uri-permission-grants.html
- ITNEXT: Navigating the Challenges of Scoped Storage — https://itnext.io/navigating-the-challenges-of-scoped-storage-lessons-for-modern-android-apps-b5fd8318a02c
- Brahimoubbad.com: Jetpack Compose Recomposition Debug & Optimize Performance Guide (2025) — https://www.brahimoubbad.com/2025/06/jetpack-compose-recomposition-debug-and-optimize-performance-guide.html
- Orgzly-revived: Future of OrgMode on Android discussion — https://github.com/orgzly-revived/orgzly-android-revived/discussions/562
- pmiddend/org-parser: JVM org-mode parser — https://github.com/pmiddend/org-parser
- Org-mode Syntax Specification — https://orgmode.org/worg/org-syntax.html

---
*Pitfalls research for: Personal Android org-mode tracking app (Origami) — sideloaded APK, Syncthing file sync, Jetpack Compose, Galaxy S21 FE*
*Researched: 2026-04-09*
