# Stack Research

**Domain:** Personal Android native app — food/macro tracking + workout logging, org-mode plain text files, Syncthing file-based sync
**Researched:** 2026-04-09
**Confidence:** HIGH (core Android stack is well-established; org-mode parsing and storage access nuances MEDIUM)

> **Platform pivot note:** This document replaces the earlier iOS/SwiftUI STACK.md. The project moved from iOS to Android because: Galaxy S21 FE available for free, no $99/year Apple Developer account, no provisioning profiles, no re-signing every 7 days, full filesystem access via `MANAGE_EXTERNAL_STORAGE`, Syncthing works natively on Android. Several architectural challenges from the iOS version (iCloud-requires-paid-account, no forced sync, sandboxed file access) go away entirely.

---

## Recommended Stack

### Core Technologies

**Language**

- Technology: Kotlin
- Version: 2.3.20 (latest stable, released March 2026)
- Purpose: Primary language for all app code
- Why Recommended: Kotlin is the official, Google-recommended language for Android. Kotlin 2.x introduced a new K2 compiler with measurably faster build times and improved type inference. All Jetpack libraries are Kotlin-first. Do not use Java for new Android code in 2026 — Kotlin is the standard and all tooling, documentation, and community knowledge assumes it.

**UI Framework**

- Technology: Jetpack Compose
- Version: 1.10.6 (via BOM 2026.04.00)
- Purpose: All UI — screens, navigation, theming
- Why Recommended: Jetpack Compose is Google's modern declarative UI toolkit and the standard for new Android apps. It replaces the XML View system entirely. Material 3 (Material You) is the current design system. Compose integrates natively with Kotlin coroutines, StateFlow, and ViewModel — no adapters needed. By 2026, Compose is production-stable, enterprise-adopted, and the only direction Google is investing in for Android UI.

**Build System**

- Technology: Android Studio Panda 3 (2025.3.3) + AGP 9.1.0 + Gradle 9.3.1
- Version: Android Studio 2025.3.3 (latest stable, April 2026); AGP 9.1.0 (March 2026)
- Purpose: IDE, build toolchain, APK generation, device deployment
- Why Recommended: Android Studio Panda 3 is the current stable release. AGP 9.1.0 supports API 36.1, requires Gradle 9.3.1 and JDK 17 minimum, and includes built-in Kotlin support (no separate `org.jetbrains.kotlin.android` plugin needed starting with AGP 9.0). For a sideloaded APK, Android Studio handles signing with a debug keystore or a self-generated release keystore — no Play Store enrollment required.

**State Management**

- Technology: ViewModel + StateFlow + Kotlin Coroutines
- Version: lifecycle-viewmodel-compose 2.10.0; kotlinx-coroutines 1.10.2
- Purpose: Screen-level state, async operations, reactive UI
- Why Recommended: ViewModel survives configuration changes (screen rotation). StateFlow is the current standard for exposing UI state from a ViewModel — it replaces LiveData for new code. The pattern is: ViewModel holds a `MutableStateFlow<UiState>`, exposes it as `StateFlow<UiState>`, composables collect with `collectAsStateWithLifecycle()`. This is Google's officially recommended architecture for Compose apps in 2025/2026. LiveData is not deprecated but is no longer the recommended approach for new code — StateFlow integrates cleanly with coroutines and Compose without adapters.

**Dependency Injection**

- Technology: Manual DI (no framework for v1)
- Version: N/A
- Purpose: Wire up repositories, parsers, and services
- Why Recommended: Hilt is the standard DI framework for larger Android apps and is appropriate when you have complex dependency graphs, multi-module projects, or team conventions to enforce. For a single-user personal app with a handful of classes, Hilt adds meaningful boilerplate: a Gradle plugin, a KSP annotation processor, an `@HiltAndroidApp` Application class, `@AndroidEntryPoint` on every Activity and composable entry point, and `@HiltViewModel` on every ViewModel. The Google documentation itself recommends manual DI for simple cases. For Origami v1: wire up the parser, repository, and sync service as singletons in a hand-rolled `AppContainer` object passed through the Application class. If the codebase grows in complexity, add Hilt later — it's a well-understood migration path.

**Navigation**

- Technology: Navigation Compose (Navigation 2)
- Version: 2.9.7 (latest stable, January 2026)
- Purpose: Screen navigation, back stack management
- Why Recommended: Navigation Compose 2.9.7 is the current stable, production-proven navigation solution for Compose. Navigation 3 (Nav3) was announced at Google I/O 2025 and is philosophically cleaner (developer-owned back stack as a `SnapshotStateList`), but it remains in alpha as of April 2026 — APIs are subject to change. Use Navigation 2 for v1. It's not going away, and the migration to Nav3 when it reaches stable is straightforward. For a 3-4 screen app like Origami, Navigation 2 is entirely sufficient.

---

### Data Layer

**In-App Data Storage**

- Technology: No database. Custom in-memory models + org-mode file I/O
- Purpose: Primary data store
- Why Recommended: The core project constraint is that data lives in org files. Room (SQLite) and DataStore store data in their own formats. Using Room would create two sources of truth (the database AND the org files), which defeats the purpose — the whole point is Emacs interoperability via a shared plain-text format. Architecture: parse org files into Kotlin data classes on load, mutate in memory, serialize back to org text on write. This is simple, testable, and produces zero abstraction complexity.

**Preferences / App Config**

- Technology: Jetpack DataStore (Preferences)
- Version: 1.2.1 (latest stable, March 2026)
- Purpose: Small app-level settings (selected sync folder path, any UI preferences)
- Why Recommended: DataStore replaces SharedPreferences as the recommended key-value store. It's async/coroutine-native (Flow-based), avoids the ANR risk of synchronous SharedPreferences reads on the main thread, and integrates cleanly with the rest of the coroutine stack. Use only for tiny settings (the path to the Syncthing folder, theme preference) — not for tracking data, which lives in org files.

**Org-Mode Parser**

- Technology: Custom Kotlin parser
- Purpose: Read and write org-mode formatted plain text files
- Why Recommended: Two JVM-targeted org-mode libraries were found:
  - `pmiddend/org-parser`: Written in Kotlin, targets JVM, created 2016, only 19 commits, no releases, no version tags, appears abandoned. Aspirational README but unrealized ambitions.
  - `orgapp/swift-org` and `xiaoxinghu/OrgMarker`: Swift-only, irrelevant for Android.
  No viable, maintained Kotlin/JVM org-mode library exists. The org-mode subset needed for Origami is narrow and well-defined: headings (`*`, `**`), property drawers (`:PROPERTIES:` … `:END:`), planning keywords (`SCHEDULED:`, date headings), timestamps (`<2026-04-09>`), plain list items (`-`), and maybe tags (`:tag:`). This is a tractable parsing problem with Kotlin's standard library `Regex` + `BufferedReader` line-by-line processing. Build a bespoke parser for the exact org constructs Origami uses — abstract it behind an interface so it's swappable and independently testable.

  ```kotlin
  interface OrgFileParser {
      fun parseFood(content: String): List<FoodEntry>
      fun parseWorkout(content: String): List<WorkoutEntry>
      fun serializeFood(entries: List<FoodEntry>): String
      fun serializeWorkout(entries: List<WorkoutEntry>): String
  }
  ```

**File Access / Syncthing Integration**

- Technology: `java.io.File` + `MANAGE_EXTERNAL_STORAGE` permission
- Purpose: Read and write org files in the Syncthing sync folder
- Why Recommended: This is the key architectural simplification vs. the iOS approach. Android's `MANAGE_EXTERNAL_STORAGE` permission (introduced in Android 11 / API 30, a.k.a. "All files access") grants an app unrestricted read/write access to the entire external storage filesystem, including directories managed by Syncthing. For a sideloaded personal app — not distributed via Play Store — this is straightforward:

  1. Declare `MANAGE_EXTERNAL_STORAGE` in `AndroidManifest.xml`
  2. At runtime, check `Environment.isExternalStorageManager()` and prompt the user to grant it in Settings if needed (a one-time setup)
  3. Once granted, use plain `java.io.File` APIs to read/write org files in the Syncthing folder (e.g., `/storage/emulated/0/Sync/health/food-log.org`)

  Syncthing on Android already uses this same `MANAGE_EXTERNAL_STORAGE` permission to access its sync directories. The org files land in a Syncthing-managed folder; the app reads/writes them directly using the filesystem. No iCloud, no file coordinator, no metadata queries — just `File.readText()` and `File.writeText()`.

  **Play Store caveat:** Google Play restricts `MANAGE_EXTERNAL_STORAGE` to apps with specific use cases and requires policy review. This is irrelevant for Origami — it's a sideloaded APK, never submitted to the Play Store. The restriction does not apply.

  **SAF (Storage Access Framework) alternative:** SAF is the correct API when distributing an app publicly and needing to request access to user-selected directories without broad filesystem access. It works via URI permissions granted through the system file picker. It's more restrictive and more complex. For a personal sideloaded app, `MANAGE_EXTERNAL_STORAGE` is simpler and more appropriate.

**Sync Abstraction Layer**

- Technology: Custom `SyncRepository` interface
- Purpose: Abstract the sync mechanism so file-based sync (Syncthing) can be replaced with a local server later
- Why Recommended: The project explicitly requires a swappable sync backend. The abstraction lives at the repository layer, not the file I/O layer:

  ```kotlin
  interface OrgRepository {
      suspend fun loadFoodLog(): List<FoodEntry>
      suspend fun saveFoodEntry(entry: FoodEntry)
      suspend fun loadWorkoutLog(): List<WorkoutEntry>
      suspend fun saveWorkoutEntry(entry: WorkoutEntry)
      fun observeFoodLog(): Flow<List<FoodEntry>>
      fun observeWorkoutLog(): Flow<List<WorkoutEntry>>
  }
  ```

  v1 implementation: `SyncthingOrgRepository` — reads/writes org files directly from the Syncthing folder using `java.io.File`. A future `LocalServerOrgRepository` (HTTP, SSH, or WebDAV) slots in without changing any ViewModel or UI code.

---

### Supporting Libraries

**Charts & Data Visualization**

- Library: Compose Charts (Vico or equivalent)
- Options: `patrykandpatrick/vico` (Compose-native, actively maintained) or `himanshoe/charty`
- Version: Vico 2.x (verify latest on GitHub before adding)
- Purpose: Macro trend charts, workout progress over time
- When to Use: Phase 2+ once history view is built. Not needed for v1 logging MVP. Use a Compose-native chart library rather than wrapping a legacy View-based chart library — wrapping adds complexity and styling friction.
- Confidence: MEDIUM (verified that Vico exists and is Compose-native; version not verified against current release)

**Coroutines / Flow**

- Library: `kotlinx-coroutines-android`
- Version: 1.10.2 (latest stable)
- Purpose: Async file I/O (reads/writes must be off main thread), reactive state streams
- When to Use: Always — this is the foundation of the entire async + state model.

**Lifecycle / Compose Integration**

- Library: `lifecycle-viewmodel-compose`
- Version: 2.10.0 (latest stable, November 2025)
- Purpose: `viewModel()` composable function, `collectAsStateWithLifecycle()`
- When to Use: Always — required to wire ViewModels into composables and collect StateFlow safely with respect to Android lifecycle.

---

### Development Tools

- Tool: Android Studio Panda 3 (2025.3.3)
- Purpose: IDE, Compose preview, Layout Inspector, device sideloading
- Notes: Use Compose Preview from day one — it eliminates most need to deploy to device during UI development. Enable "Compose Preview" in the IDE for hot-reload-like iteration.

- Tool: Gradle 9.3.1 (Kotlin DSL — `build.gradle.kts`)
- Purpose: Build system, dependency management
- Notes: Use Kotlin DSL (`build.gradle.kts`) rather than Groovy — it gives IDE autocomplete and type safety in build scripts. Version catalog (`gradle/libs.versions.toml`) is recommended for managing all dependency versions in one place.

- Tool: KSP (Kotlin Symbol Processing)
- Purpose: Annotation processing (required if Hilt is added later; also used by some libraries)
- Notes: KSP replaces KAPT for annotation processing and is up to 2x faster. Set it up from the start even if no libraries require it yet — it's the modern default.

- Tool: ADB (Android Debug Bridge)
- Purpose: Sideload APK, logcat, device shell
- Notes: `adb install -r origami.apk` for reinstall without uninstall. No Play Store, no signing ceremony beyond a self-signed debug or release keystore.

- Tool: Android Lint + ktlint
- Purpose: Code quality, style
- Notes: Android Lint is built into Android Studio. Add ktlint or detekt for formatting consistency — pick one and configure it early to avoid reformatting churn later.

---

## Alternatives Considered

**Hilt vs. Manual DI**
- Recommended: Manual DI for v1
- Alternative: Hilt (com.google.dagger:hilt-android:2.57.1)
- When to Use Alternative: When the dependency graph becomes complex (3+ layers, multiple modules), when the team needs standardized DI patterns, or when testing with `HiltRule` is valuable. For Origami v1 with ~3 classes (parser, repository, ViewModel), manual DI is proportionate. Hilt is a straightforward add if the app grows.

**Room vs. No Database**
- Recommended: No database — org files are the data
- Alternative: Room 2.7+ (with SQLite)
- When to Use Alternative: If read performance becomes a problem with large org files (unlikely for a personal log), a Room cache could supplement the org files. But this creates a sync problem — the cache and the files must stay in sync. Don't add this complexity unless there's a measured performance issue.

**Navigation 3 (alpha) vs. Navigation 2 (stable)**
- Recommended: Navigation 2 (navigation-compose:2.9.7)
- Alternative: Navigation 3 (alpha, announced I/O 2025)
- When to Use Alternative: When Navigation 3 reaches stable (likely 2026). Nav3's developer-owned back stack is cleaner for complex adaptive layouts. For a 3-4 screen personal app, the difference is academic — use the stable library.

**Storage Access Framework (SAF) vs. MANAGE_EXTERNAL_STORAGE**
- Recommended: `MANAGE_EXTERNAL_STORAGE` for this sideloaded use case
- Alternative: SAF (DocumentFile, URI permissions, ACTION_OPEN_DOCUMENT_TREE)
- When to Use Alternative: If the app were ever published on Play Store. SAF is the Play-Store-compliant way to get write access to user directories. For a sideloaded personal app, SAF adds complexity (SAF URIs are not interchangeable with File paths, require persistence via `takePersistableUriPermission()`, and are harder to work with) for no benefit.

**Kotlin Multiplatform (KMP) vs. Native Android**
- Recommended: Native Android (Kotlin + Compose)
- Alternative: Kotlin Multiplatform with Compose Multiplatform
- When to Use Alternative: If iOS support is added later. KMP allows sharing business logic (parser, repository, models) between Android and iOS while writing platform-specific UI. However, KMP adds build complexity and the iOS Compose story is still maturing. For Android-only v1, native Android is the right choice. If iOS is added, consider extracting the core logic into a KMP module at that point rather than retrofitting.

**Vico vs. MPAndroidChart**
- Recommended: Vico (Compose-native)
- Alternative: MPAndroidChart (View-based, wrapped in `AndroidView`)
- When to Use Alternative: Never for new Compose code. MPAndroidChart is the dominant chart library for the View system but requires `AndroidView` interop in Compose, which adds styling friction and breaks Compose's theming. Use a Compose-native library.

---

## What NOT to Use

**Android View System (XML layouts)**
- Why: Compose is the standard for greenfield Android apps in 2026. XML layouts are the legacy system. Mixing Compose and Views (via `AndroidView` or `ComposeView`) adds complexity and is only justified for specific controls that don't exist in Compose (rare). Start Compose-only.

**LiveData**
- Why: LiveData is lifecycle-aware but predates Kotlin coroutines as a first-class tool. It doesn't compose cleanly with Flow operators. The recommended replacement is `StateFlow` collected with `collectAsStateWithLifecycle()`, which is lifecycle-safe AND coroutine-native.
- Use Instead: `StateFlow` + `collectAsStateWithLifecycle()`

**KAPT (Kotlin Annotation Processing)**
- Why: KAPT is the legacy annotation processing approach. KSP (Kotlin Symbol Processing) is up to 2x faster and is the current standard. Starting a new project with KAPT means migrating to KSP later.
- Use Instead: KSP for any annotation processing needs.

**Groovy Gradle DSL (`build.gradle`)**
- Why: The Kotlin DSL (`build.gradle.kts`) provides IDE autocomplete, type safety, and refactoring support. Groovy build scripts are stringly-typed. New projects should use Kotlin DSL from the start.
- Use Instead: `build.gradle.kts` + `gradle/libs.versions.toml` version catalog.

**RxJava**
- Why: RxJava was the standard reactive framework before Kotlin coroutines existed. Kotlin coroutines + Flow achieve the same patterns with less ceremony and better IDE tooling. Mixing RxJava into a coroutines codebase creates unnecessary complexity.
- Use Instead: Kotlin Flow / StateFlow / SharedFlow.

**Retrofit / OkHttp (for v1)**
- Why: v1 has no network calls. The sync layer is file-based (Syncthing handles the network). Don't add networking dependencies that aren't needed.
- Use Instead: Nothing. If a future local server is added, add Retrofit at that point.

**Firebase (any product)**
- Why: Firebase is a cloud platform. This app is explicitly single-user, local-first, no-auth, no-cloud. Firebase adds Google account dependencies, privacy considerations, and complexity that directly conflict with the core value of user-controlled data.

**SharedPreferences**
- Why: SharedPreferences performs synchronous disk I/O on the main thread, which is an ANR risk. Partially deprecated in favor of DataStore.
- Use Instead: Jetpack DataStore (Preferences) 1.2.1 for any small settings needs.

---

## How Android Differs from (and Is Simpler Than) the iOS Approach

The iOS approach faced several non-trivial constraints that disappear entirely on Android:

**Filesystem access**
- iOS: All file access sandboxed. iCloud Drive requires `NSFileCoordinator`, `NSMetadataQuery`, and a paid Apple Developer account ($99/year) for the iCloud entitlement. Free accounts cannot enable iCloud capability — a hard blocker.
- Android: `MANAGE_EXTERNAL_STORAGE` grants direct filesystem access. Read/write org files with `java.io.File`. No entitlements, no certificates, no account fees.

**Sync mechanism**
- iOS: iCloud Drive sync is system-controlled. No API to force sync. Apps must design around eventual consistency and throttling. `NSMetadataQuery` is needed just to observe sync status.
- Android: Syncthing runs as a background service on the phone and syncs files directly over Wi-Fi. The app reads/writes the files; Syncthing handles the rest. The app doesn't need to think about sync at all — it just reads what's there.

**Sideloading**
- iOS: Free Apple ID gives 7-day provisioning profiles that expire. Reinstall required weekly. Paid account required for longer certificates.
- Android: `adb install` installs the APK permanently. No expiration, no certificates, no account needed.

**No forced sync / eventual consistency**
- iOS: The app had to design for "files may not be on device yet" with graceful degradation.
- Android: The Syncthing folder is local. Files are there or they're not. No polling, no metadata queries — just read the file.

The Android stack is genuinely simpler for this use case. The iOS stack was workable but carried significant infrastructure overhead that had nothing to do with the app's actual functionality.

---

## Stack Patterns by Variant

**v1 — File-based sync (Syncthing)**
- `MANAGE_EXTERNAL_STORAGE` permission
- `SyncthingOrgRepository` reads/writes from `/storage/emulated/0/Sync/health/`
- Manual DI via `AppContainer` in Application class

**If a local server is added (v2)**
- Add Retrofit + OkHttp at that point
- Implement `LocalServerOrgRepository` conforming to `OrgRepository`
- No changes to ViewModels or UI

**If iOS support is added (future)**
- Extract parser + repository interfaces into a Kotlin Multiplatform module
- Android UI remains Compose; iOS UI is SwiftUI or Compose Multiplatform
- The org-mode parser is pure Kotlin logic with no Android dependencies — it moves to KMP cleanly

---

## Version Compatibility

| Dependency | Version | Notes |
|---|---|---|
| Kotlin | 2.3.20 | Compose compiler ships with Kotlin since 2.0 — always compatible |
| AGP | 9.1.0 | Requires Gradle 9.3.1, JDK 17 minimum |
| Compose BOM | 2026.04.00 | Maps to compose-ui 1.10.6, material3 1.4.0 |
| Navigation Compose | 2.9.7 | Navigation 2; Nav3 is alpha, not production-ready |
| Lifecycle ViewModel Compose | 2.10.0 | Minimum SDK bumped to API 23 in this version |
| kotlinx-coroutines-android | 1.10.2 | Compatible with Kotlin 2.3.x |
| DataStore Preferences | 1.2.1 | |
| Hilt (if added) | 2.57.1 | With hilt-navigation-compose 1.3.0 |
| Target SDK | API 35 (Android 15) | Galaxy S21 FE running One UI 7 (Android 15) |
| Minimum SDK | API 26 (Android 8) | Covers >99% of devices in use; API 23 minimum but no reason to go lower |

**Target / Minimum SDK rationale:** The Galaxy S21 FE received the One UI 7 (Android 15, API 35) update in May 2025. Target SDK 35 is appropriate. For minimum SDK, API 26 (Android 8.0) covers virtually all active devices and avoids needing compatibility shims for coroutines and modern APIs. There is no reason to support Android 7 or earlier for a personal app.

---

## Installation / Project Setup

```kotlin
// gradle/libs.versions.toml
[versions]
kotlin = "2.3.20"
agp = "9.1.0"
compose-bom = "2026.04.00"
navigation-compose = "2.9.7"
lifecycle = "2.10.0"
coroutines = "1.10.2"
datastore = "1.2.1"
# hilt = "2.57.1"  # add when needed

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-activity = { group = "androidx.activity", name = "activity-compose", version = "1.10.1" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation-compose" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

```kotlin
// app/build.gradle.kts (relevant sections)
android {
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        targetSdk = 35
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val bom = platform(libs.compose.bom)
    implementation(bom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    implementation(libs.datastore.preferences)

    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

```xml
<!-- AndroidManifest.xml — permissions needed for Syncthing folder access -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission
    android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
    tools:ignore="ScopedStorage" />
```

```kotlin
// Runtime permission check (one-time setup screen)
if (!Environment.isExternalStorageManager()) {
    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
    intent.data = Uri.fromParts("package", packageName, null)
    startActivity(intent)
}
```

---

## Sources

- https://developer.android.com/jetpack/androidx/releases/compose — Compose BOM 2026.04.00 confirmed; compose-ui 1.10.6, material3 1.4.0 [HIGH]
- https://developer.android.com/jetpack/androidx/releases/navigation — Navigation Compose 2.9.7 latest stable [HIGH]
- https://developer.android.com/jetpack/androidx/releases/lifecycle — Lifecycle ViewModel Compose 2.10.0 latest stable [HIGH]
- https://developer.android.com/jetpack/androidx/releases/datastore — DataStore Preferences 1.2.1 latest stable [HIGH]
- https://developer.android.com/develop/ui/compose/libraries — Official Compose library recommendations, hilt-navigation-compose 1.3.0 [HIGH]
- https://android-developers.googleblog.com/2025/05/announcing-jetpack-navigation-3-for-compose.html — Navigation 3 announced at I/O 2025, currently alpha [HIGH]
- https://blog.jetbrains.com/kotlin/2026/03/kotlin-2-3-20-released/ — Kotlin 2.3.20 latest stable (March 2026) [HIGH]
- https://developer.android.com/build/releases/agp-9-1-0-release-notes — AGP 9.1.0 (March 2026), requires Gradle 9.3.1, JDK 17 [HIGH]
- https://androidstudio.googleblog.com/2026/04/android-studio-panda-3-202533-now.html — Android Studio Panda 3 (2025.3.3) latest stable [HIGH]
- https://developer.android.com/training/dependency-injection/hilt-android — Hilt official docs; recommends manual DI for simple apps [HIGH]
- https://developer.android.com/training/data-storage — Android storage overview; SAF vs MANAGE_EXTERNAL_STORAGE [HIGH]
- https://github.com/pmiddend/org-parser — JVM org-mode parser: created 2016, 19 commits, no releases, appears abandoned [MEDIUM — verified by fetch]
- https://forum.syncthing.net/t/android-11-all-files-access-for-the-syncthing-app/14651 — Syncthing uses MANAGE_EXTERNAL_STORAGE for full storage access [MEDIUM]
- https://www.sammobile.com/news/galaxy-s21-fe-one-ui-7-update-released-europe-asia/ — Galaxy S21 FE received Android 15 (One UI 7) in May 2025 [HIGH]
- https://github.com/Kotlin/kotlinx.coroutines — kotlinx-coroutines 1.10.2 latest stable [HIGH]
- https://mvnrepository.com/artifact/com.google.dagger/hilt-android — Hilt 2.57.1 latest stable [MEDIUM — cross-referenced with official docs]

---

*Stack research for: Origami — Personal Android food/nutrition and workout tracking app*
*Researched: 2026-04-09*
*Replaces: iOS/SwiftUI STACK.md (platform pivot to Android)*
