# Phase 1: Foundation - Research

**Researched:** 2026-04-09
**Domain:** Android project scaffold, MANAGE_EXTERNAL_STORAGE permission flow, Jetpack DataStore, org-mode file format, SyncBackend file I/O, Navigation Compose onboarding
**Confidence:** HIGH (all core areas verified against official Android documentation; org-mode timestamp locale requirement flagged as MEDIUM — official spec is ambiguous, community evidence is clear)

---

## Summary

Phase 1 builds three things: the Android project scaffold with ADB deploy working, the OrgEngine (parser + writer validated against Emacs), and the SyncBackend + onboarding flow. The tech is well-understood; the two areas that require care are (1) the org-mode file format and (2) the onboarding permission flow.

The standard approach for every component is established and sourced from official Android docs. AGP 9.1.0 ships built-in Kotlin support, so `org.jetbrains.kotlin.android` is no longer applied manually. `MANAGE_EXTERNAL_STORAGE` is a special permission — it cannot use `ActivityResultContracts.RequestPermission`; it requires redirecting to Settings and re-checking via `Environment.isExternalStorageManager()` on Activity resume. DataStore Preferences 1.2.1 is the correct solution for persisting the folder path, using the `preferencesDataStore` delegate and `dataStore.edit {}` for writes. The OrgEngine must produce org files that pass `M-x org-lint` — the only reliable gate. Writing to a temp file and renaming (Android's `AtomicFile` class) protects against partial-write corruption. Navigation Compose conditional onboarding should use the main screen as `startDestination`, check DataStore before NavHost renders, and `popUpTo<Onboarding>(inclusive = true)` when onboarding completes.

**Primary recommendation:** Build OrgEngine first. Validate every format variant against `M-x org-lint` before building SyncBackend. The format is the contract — get it right before writing infrastructure around it.

---

## Standard Stack

### Core (Phase 1 specifics, beyond what STACK.md already documents)

**AGP 9.1.0 + Built-in Kotlin:**
- AGP 9.0 introduced built-in Kotlin support. The `org.jetbrains.kotlin.android` plugin is no longer applied manually in `build.gradle.kts`.
- AGP 9.1.0 requires: Gradle 9.3.1, JDK 17, SDK Build Tools 36.0.0.
- Confidence: HIGH — verified against official AGP 9.1.0 release notes.

**Jetpack DataStore Preferences 1.2.1 (stable, March 2026):**
- For storing: sync folder path, onboarding complete flag.
- 1.3.0-alpha07 exists with encryption support (`datastore-tink`) but is alpha — use 1.2.1.
- Confidence: HIGH — verified against official DataStore release page.

**Android AtomicFile (android.util.AtomicFile):**
- For safe org file writes: write to temp, `finishWrite()` does atomic rename to target.
- In AndroidX: `androidx.core.util.AtomicFile` (same API, backward compatible).
- Suitable for text files of any size under a few MB.
- Confidence: HIGH — verified against official Android API reference.

### Supporting

**MANAGE_EXTERNAL_STORAGE permission:**
- Special permission class — does NOT use `ActivityResultContracts.RequestPermission`.
- Check: `Environment.isExternalStorageManager()` (not `ContextCompat.checkSelfPermission`).
- Request: `Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)` (whole settings page) or `Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", packageName, null))` (direct to app's entry).
- ADB testing shortcut: `adb shell appops set --uid PACKAGE_NAME MANAGE_EXTERNAL_STORAGE allow`.
- Confidence: HIGH — verified against official Android storage documentation.

### Alternatives Considered

- AtomicFile vs. manual temp-file-rename: AtomicFile is the built-in Android solution. Use it.
- DataStore Proto vs. Preferences: Proto DataStore requires protobuf schema setup. Preferences DataStore is correct for simple key-value settings (path string, bool flag).
- `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION` vs. `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`: The latter deep-links directly to the app's All Files Access toggle. Prefer this for better UX.

### Installation (additions to STACK.md)

No new dependencies beyond what STACK.md already specifies. All required libraries are covered:
- `androidx.datastore:datastore-preferences:1.2.1`
- `java.io.File` and `android.util.AtomicFile` are Android stdlib — no Gradle dependency.

---

## Architecture Patterns

### Recommended Project Structure (Phase 1 scope)

```
app/src/main/java/com/sakura/
├── SakuraApplication.kt            # AppContainer init
│
├── di/
│   └── AppContainer.kt             # Manual DI — parser, backend, prefs repo wired here
│
├── orgengine/
│   ├── OrgParser.kt                # String → List<OrgDateSection>
│   ├── OrgWriter.kt                # OrgDateSection → String (append-only writer)
│   ├── OrgModels.kt                # OrgDateSection, OrgFoodEntry, OrgWorkoutEntry
│   └── OrgSchema.kt                # Constants: date format, meal labels, field names
│
├── sync/
│   ├── SyncBackend.kt              # interface: readFile, writeFile, fileExists, listOrgFiles
│   ├── SyncthingFileBackend.kt     # java.io.File + AtomicFile + Dispatchers.IO
│   └── SyncBackendError.kt         # sealed class: FolderUnavailable, PermissionDenied, ConflictDetected
│
├── preferences/
│   └── AppPreferencesRepository.kt # DataStore wrapper: syncFolderPath, onboardingComplete
│
└── features/
    └── onboarding/
        ├── OnboardingScreen.kt     # permission explain → grant → folder select → done
        └── OnboardingViewModel.kt  # StateFlow<OnboardingUiState>, checks permission + path
```

### Pattern 1: AGP 9.1.0 Project Scaffold

**What:** AGP 9.0+ ships built-in Kotlin. Remove the `org.jetbrains.kotlin.android` plugin.

```kotlin
// build.gradle.kts (root)
plugins {
    id("com.android.application") version "9.1.0" apply false
    // NO org.jetbrains.kotlin.android here — built in to AGP 9.0+
}

// app/build.gradle.kts
plugins {
    id("com.android.application")  // Built-in Kotlin — no kotlin plugin needed
}

android {
    namespace = "com.sakura"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sakura"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}
```

Source: https://developer.android.com/build/migrate-to-built-in-kotlin [HIGH]

### Pattern 2: MANAGE_EXTERNAL_STORAGE Permission Flow in Compose

**What:** Special permission that cannot use standard permission launcher. Requires Settings redirect and onResume check.

**The lifecycle problem:** When the user leaves the app to grant permission in Settings and returns, the Activity calls `onResume()`. In a Compose-only app with a single Activity, you must observe `Lifecycle.Event.ON_RESUME` to re-check `Environment.isExternalStorageManager()`.

```kotlin
// AndroidManifest.xml
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
    tools:ignore="ScopedStorage" />

// OnboardingViewModel.kt
class OnboardingViewModel(
    private val prefsRepo: AppPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.CheckingPermission)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun checkPermission() {
        _uiState.value = if (Environment.isExternalStorageManager()) {
            OnboardingUiState.PermissionGranted
        } else {
            OnboardingUiState.NeedsPermission
        }
    }
}

// OnboardingScreen.kt
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, onOnboardingDone: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-check permission when app resumes (user returned from Settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (val state = uiState) {
        is OnboardingUiState.NeedsPermission -> {
            // Show explanation + "Grant Access" button
            Button(onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.fromParts("package", context.packageName, null)
                )
                context.startActivity(intent)
            }) { Text("Grant All Files Access") }
        }
        is OnboardingUiState.PermissionGranted -> {
            // Proceed to folder selection step
        }
        // ... other states
    }
}
```

Source: https://developer.android.com/training/data-storage/manage-all-files [HIGH], https://developer.android.com/training/permissions/requesting-special [HIGH]

### Pattern 3: DataStore for Folder Path Persistence

**What:** `preferencesDataStore` delegate at Context extension level, repository class wraps it.

```kotlin
// preferences/AppPreferencesRepository.kt
private val Context.appDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "sakura_prefs")

class AppPreferencesRepository(private val context: Context) {

    companion object {
        val SYNC_FOLDER_PATH = stringPreferencesKey("sync_folder_path")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    val syncFolderPath: Flow<String?> = context.appDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs -> prefs[SYNC_FOLDER_PATH] }

    val onboardingComplete: Flow<Boolean> = context.appDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs -> prefs[ONBOARDING_COMPLETE] ?: false }

    suspend fun setSyncFolderPath(path: String) {
        context.appDataStore.edit { prefs ->
            prefs[SYNC_FOLDER_PATH] = path
        }
    }

    suspend fun setOnboardingComplete() {
        context.appDataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETE] = true
        }
    }
}
```

Source: https://developer.android.com/topic/libraries/architecture/datastore [HIGH], https://developer.android.com/codelabs/android-preferences-datastore [HIGH]

### Pattern 4: Conditional Onboarding Navigation

**What:** Check DataStore before rendering NavHost. Main screen is `startDestination`. Onboarding completes with `popUpTo` to remove it from back stack.

**Key insight from Android docs:** "Conditional screens should not be the start destination. The main content should be the start destination. Check whether to navigate to onboarding and trigger that navigation event."

**White flash prevention:** Read DataStore once before setting `startDestination`. Use `null` as a sentinel — don't show NavHost until the check is complete. Show a blank/splash composable while loading.

```kotlin
// MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SakuraTheme {
                val prefsRepo = (application as SakuraApplication).container.prefsRepo
                val onboardingDone by prefsRepo.onboardingComplete
                    .collectAsStateWithLifecycle(initialValue = null)

                // null = still loading from DataStore, don't render NavHost yet
                when (val done = onboardingDone) {
                    null -> { /* blank screen or splash — avoid flash */ }
                    else -> {
                        val navController = rememberNavController()
                        AppNavHost(
                            navController = navController,
                            startWithOnboarding = !done
                        )
                    }
                }
            }
        }
    }
}

// AppNavHost.kt
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    startWithOnboarding: Boolean
) {
    NavHost(
        navController = navController,
        startDestination = if (startWithOnboarding) Onboarding else MainGraph
    ) {
        composable<Onboarding> {
            OnboardingScreen(
                onCompleted = {
                    navController.navigate(MainGraph) {
                        popUpTo<Onboarding> { inclusive = true }
                    }
                }
            )
        }
        composable<MainGraph> {
            // Placeholder for Phase 1 — just a "setup complete" screen
            SetupCompleteScreen()
        }
    }
}
```

Source: https://developer.android.com/develop/ui/compose/navigation [HIGH], https://developer.android.com/guide/navigation/backstack [HIGH]

### Pattern 5: SyncBackend Interface and SyncthingFileBackend

**What:** Interface-backed file I/O layer. All reads/writes on `Dispatchers.IO`. `AtomicFile` for writes to avoid partial-write corruption.

```kotlin
// sync/SyncBackend.kt
interface SyncBackend {
    suspend fun readFile(filename: String): String
    suspend fun writeFile(filename: String, content: String)
    suspend fun fileExists(filename: String): Boolean
    suspend fun listOrgFiles(): List<String>  // returns filenames only, filters conflicts
}

// sync/SyncthingFileBackend.kt
class SyncthingFileBackend(
    private val prefsRepo: AppPreferencesRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SyncBackend {

    private suspend fun resolveFile(filename: String): File {
        val path = prefsRepo.syncFolderPath.first()
            ?: throw SyncBackendError.FolderUnavailable("No sync folder path configured")
        return File(path, filename)
    }

    override suspend fun readFile(filename: String): String = withContext(ioDispatcher) {
        val file = resolveFile(filename)
        if (!file.exists()) return@withContext ""
        file.readText(Charsets.UTF_8)
    }

    override suspend fun writeFile(filename: String, content: String) = withContext(ioDispatcher) {
        val file = resolveFile(filename)
        val atomicFile = AtomicFile(file)
        val stream = atomicFile.startWrite()
        try {
            stream.write(content.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)  // atomic rename
        } catch (e: IOException) {
            atomicFile.failWrite(stream)    // rolls back
            throw e
        }
    }

    override suspend fun listOrgFiles(): List<String> = withContext(ioDispatcher) {
        val path = prefsRepo.syncFolderPath.first() ?: return@withContext emptyList()
        File(path).listFiles()
            ?.filter { f ->
                f.name.endsWith(".org")
                && !f.name.contains(".sync-conflict")
                && !f.name.startsWith(".")
            }
            ?.map { it.name }
            ?: emptyList()
    }
}
```

Source: https://developer.android.com/reference/android/util/AtomicFile [HIGH], https://developer.android.com/kotlin/coroutines/coroutines-best-practices [HIGH]

### Pattern 6: OrgEngine Design

**What:** Pure Kotlin classes, zero Android imports. OrgWriter defines the format; OrgParser handles exactly what OrgWriter produces.

**The format contract (what OrgWriter MUST produce):**

The org-mode specification confirms:
- Headings: exactly one space after asterisks. `* 2026-04-09` not `*2026-04-09`.
- Timestamps: `<YYYY-MM-DD DOW>` format. The day-of-week is optional per spec but added by Emacs by default. **Include DOW always** to match what Emacs produces and to avoid downstream org-agenda issues.
- Timestamp DOW language: The official org spec does not specify locale. However, org-mode's own code always uses English abbreviations (Mon, Tue, Wed, Thu, Fri, Sat, Sun). **Use `Locale.ENGLISH` when formatting**. Device locale affects `LocalDate.format()` silently — this must be explicit.
- List items: `-` followed by a space, then content. One blank line between a heading and its list items is fine; two consecutive blank lines end a list.
- No tabs — use spaces for indentation.
- No property drawers needed for Phase 1's simple log format.

**Proposed org file schema for Phase 1 validation:**

```org
* 2026-04-09
** Breakfast
- Chicken and rice  |P: 42g  C: 55g  F: 8g  Cal: 460|
** Lunch
- Salmon salad  |P: 35g  C: 20g  F: 18g  Cal: 374|

* 2026-04-08
** Breakfast
- Oats  |P: 12g  C: 60g  F: 5g  Cal: 333|
```

```org
* 2026-04-09
** Push Day
- Bench Press  |3x5  80kg|
- Overhead Press  |3x8  50kg|
- Tricep Pushdown  |3x12  30kg|
```

**Important:** This is a proposed schema. The exact format — specifically how macros are notated (inline `|P: Xg ...` vs. separate property lines) — must be hand-authored in Emacs before OrgWriter implementation starts. Write 3–5 entries by hand, verify they pass `M-x org-lint`, and use that as the spec. This is a design deliverable of Phase 1 planning (Plan 01-02).

**org-lint gate:** `org-lint` checks for: misplaced planning lines, malformed timestamps, broken list structure, incomplete code blocks, duplicate footnotes, incorrect drawer placement. Running it on actual OrgWriter output is the only reliable validation. Run it after every significant serializer change.

**Non-interactive org-lint invocation (for scripted checking):**

```bash
emacs --batch --eval "(progn
  (require 'org)
  (find-file \"food-log.org\")
  (let ((issues (org-lint)))
    (if issues
      (progn (message \"LINT ERRORS:\") (mapc (lambda (i) (message \"%s\" i)) issues) (kill-emacs 1))
      (message \"org-lint: OK\")
      (kill-emacs 0))))"
```

Source: https://github.com/emacs-mirror/emacs/blob/master/lisp/org/org-lint.el [HIGH — source code], https://orgmode.org/manual/Timestamps.html [HIGH]

### Pattern 7: Graceful Degradation (SYNC-06)

**What:** When the sync folder is unavailable, show an error state instead of crashing.

Conditions that must not crash:
- `Environment.isExternalStorageManager()` returns false (permission revoked after onboarding)
- The configured folder path does not exist or is not accessible
- `File.readText()` throws `IOException`

```kotlin
sealed class SyncBackendError : Exception() {
    data class FolderUnavailable(override val message: String) : SyncBackendError()
    data class PermissionDenied(override val message: String) : SyncBackendError()
    data class ConflictDetected(val filename: String) : SyncBackendError()
}

// In ViewModel:
fun loadOrgFiles() {
    viewModelScope.launch {
        _uiState.value = UiState.Loading
        try {
            val content = syncBackend.readFile("food-log.org")
            _uiState.value = UiState.Success(content)
        } catch (e: SyncBackendError.FolderUnavailable) {
            _uiState.value = UiState.Error.FolderUnavailable
        } catch (e: SyncBackendError.PermissionDenied) {
            _uiState.value = UiState.Error.PermissionRequired
        } catch (e: IOException) {
            _uiState.value = UiState.Error.ReadFailed(e.message ?: "Unknown error")
        }
    }
}
```

### Anti-Patterns to Avoid

- **Applying `org.jetbrains.kotlin.android` with AGP 9.1.0:** It is no longer needed and may cause issues. Leave it out.
- **Using `ContextCompat.checkSelfPermission` for MANAGE_EXTERNAL_STORAGE:** This is a special permission. Use `Environment.isExternalStorageManager()` exclusively.
- **Using `ActivityResultContracts.RequestPermission` for MANAGE_EXTERNAL_STORAGE:** This launcher does not work for special permissions. It will silently fail or throw. Use Intent to Settings.
- **Hardcoding the sync folder path:** Store it in DataStore. Default to a reasonable suggestion but let the user change it in onboarding.
- **File writes on Dispatchers.Main:** Every `File.readText()`, `File.writeText()`, and `AtomicFile` operation must be inside `withContext(ioDispatcher)`. No exceptions.
- **Calling `dataStore.data.first()` synchronously in a ViewModel constructor:** This blocks the constructor thread. Read preferences in a coroutine via `viewModelScope.launch`.
- **Skipping conflict file filtering in `listOrgFiles()`:** Filter at the `SyncBackend` level. Never let `.sync-conflict` filenames propagate up to repositories.
- **Writing tabs for indentation in org output:** Org-mode uses spaces. Tabs count as 8 spaces and will visually misalign entries in Emacs.
- **Not testing the OrgWriter output in actual Emacs:** Unit tests assert structure; `M-x org-lint` asserts correctness with the actual parser. Both are required. Neither replaces the other.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Atomic file writes | Custom temp-file logic | `android.util.AtomicFile` | Built-in Android class, handles rename atomically, includes rollback on failure |
| App settings persistence | SharedPreferences / raw file | `DataStore<Preferences>` | Async, coroutine-native, no ANR risk, official replacement for SharedPreferences |
| Dispatcher injection | Hardcoded `Dispatchers.IO` | Injected `@IoDispatcher CoroutineDispatcher` | Enables test substitution with `UnconfinedTestDispatcher` |
| Full org-mode parsing | General org parser library | Custom targeted parser | No maintained Kotlin/JVM library exists; custom parser for the subset the app writes is simpler and safer |

**Key insight:** The AtomicFile class is the part most likely to be hand-rolled incorrectly. The temp-file-rename pattern looks trivial but `File.renameTo()` is not atomic on all filesystems and does not throw `IOException` on failure. `AtomicFile` handles this correctly.

---

## Common Pitfalls

### Pitfall 1: MANAGE_EXTERNAL_STORAGE Permission Check on Wrong Resume

**What goes wrong:** The onboarding screen checks `Environment.isExternalStorageManager()` once at composable composition time. User goes to Settings, grants permission, returns. The composable was already composed — it does not recheck.

**Why it happens:** Composables do not automatically recompose when Android system state changes. The permission check needs to be driven by a lifecycle event.

**How to avoid:** Use `DisposableEffect(lifecycleOwner)` with a `LifecycleEventObserver` that calls `viewModel.checkPermission()` on `Lifecycle.Event.ON_RESUME`. The ViewModel updates its StateFlow, which triggers recomposition.

**Warning signs:** User grants permission in Settings, returns to app, onboarding screen still shows "grant permission" button.

### Pitfall 2: org-mode Timestamp Day-of-Week Locale

**What goes wrong:** `LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd EEE"))` formats the day-of-week in the device's locale. On a device set to Portuguese, `<2026-04-09 Qui>` is written instead of `<2026-04-09 Thu>`. Emacs parses `<2026-04-09 Qui>` and either silently ignores the DOW or produces malformed timestamps.

**Why it happens:** `DateTimeFormatter.ofPattern()` without a `Locale` argument defaults to `Locale.getDefault()`, which varies by device settings.

**How to avoid:** Always format timestamps with an explicit locale:
```kotlin
private val ORG_DATE_FORMATTER = DateTimeFormatter
    .ofPattern("yyyy-MM-dd EEE", Locale.ENGLISH)

fun formatOrgTimestamp(date: LocalDate): String =
    "<${date.format(ORG_DATE_FORMATTER)}>"
// Produces: <2026-04-09 Thu>
```

**Warning signs:** Org files look fine on the phone but timestamps appear wrong or broken in Emacs on a Mac.

### Pitfall 3: DataStore Not Initialized Before NavHost

**What goes wrong:** `startDestination` is computed from a `Flow<Boolean>` that hasn't emitted yet. NavHost renders with the wrong start destination, briefly shows the wrong screen, then re-navigates. User sees a flash.

**Why it happens:** DataStore reads are async. The first `collect` emission may not happen before the first composition frame.

**How to avoid:** Use `collectAsStateWithLifecycle(initialValue = null)` (nullable Boolean). When the value is `null` (not yet loaded), render nothing or a blank scaffold — do not instantiate NavHost. NavHost only renders once the value is `false` or `true`.

**Warning signs:** Brief flash of wrong screen on app launch. Navigating "back" takes user to a screen they already passed.

### Pitfall 4: AtomicFile and Syncthing Interaction

**What goes wrong:** `AtomicFile.startWrite()` creates a backup file named `food-log.org.bak` and a new write target before committing. If Syncthing is monitoring the folder, it may sync the `.bak` file or the intermediate write target before `finishWrite()` is called.

**Why it happens:** Syncthing watches for file changes. Intermediate files created by `AtomicFile` are visible to the filesystem watcher.

**How to avoid:** Add the `.bak` extension to Syncthing's `.stignore` in the sync folder:
```
*.bak
*.tmp
```
This is a configuration step for the Syncthing folder, not an app change. Document it as a setup step.

**Warning signs:** `.bak` files appearing in the Syncthing folder on the Mac side.

### Pitfall 5: Org File Encoding

**What goes wrong:** `File.writeText()` uses the platform's default charset if not specified. On Android this is UTF-8, but it is not guaranteed by the API contract. If the app ever runs on a device or configuration where the default is different, non-ASCII characters in food names (accents, special characters) produce mojibake in Emacs.

**How to avoid:** Always specify charset explicitly:
```kotlin
file.readText(Charsets.UTF_8)
file.writeText(content, Charsets.UTF_8)
```
And in `AtomicFile`:
```kotlin
stream.write(content.toByteArray(Charsets.UTF_8))
```

**Warning signs:** Accented characters in food names appear garbled when viewed in Emacs.

---

## Code Examples

### OrgWriter Timestamp

```kotlin
// Source: org-mode specification + Locale.ENGLISH requirement (PITFALL confirmed)
object OrgTimestamp {
    private val ORG_DATE_FORMATTER = DateTimeFormatter
        .ofPattern("yyyy-MM-dd EEE", Locale.ENGLISH)

    fun active(date: LocalDate): String = "<${date.format(ORG_DATE_FORMATTER)}>"
    fun inactive(date: LocalDate): String = "[${date.format(ORG_DATE_FORMATTER)}]"
}

// Produces:
// Active:   <2026-04-09 Thu>
// Inactive: [2026-04-09 Thu]
```

### OrgWriter Date Section Heading

```kotlin
// Source: org-syntax specification — exactly one space after asterisk
fun writeDateHeading(date: LocalDate): String =
    "* ${OrgTimestamp.active(date)}"
// Produces: * <2026-04-09 Thu>
```

### DataStore Onboarding Complete Flag

```kotlin
// Source: official DataStore codelab
suspend fun markOnboardingComplete() {
    context.appDataStore.edit { prefs ->
        prefs[ONBOARDING_COMPLETE] = true
    }
}

val onboardingComplete: Flow<Boolean> = context.appDataStore.data
    .catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }
    .map { prefs -> prefs[ONBOARDING_COMPLETE] ?: false }
```

### AtomicFile Write

```kotlin
// Source: android.util.AtomicFile API reference
suspend fun writeOrgFile(file: File, content: String) = withContext(ioDispatcher) {
    val atomicFile = AtomicFile(file)
    val stream = atomicFile.startWrite()
    try {
        stream.write(content.toByteArray(Charsets.UTF_8))
        atomicFile.finishWrite(stream)
    } catch (e: IOException) {
        atomicFile.failWrite(stream)
        throw e
    }
}
```

### Conflict File Filtering

```kotlin
// Source: Syncthing documentation on conflict file naming convention
fun File.isConflictFile(): Boolean =
    name.contains(".sync-conflict")

fun listOrgFiles(dir: File): List<File> =
    dir.listFiles()
        ?.filter { f ->
            f.isFile
            && f.name.endsWith(".org")
            && !f.isConflictFile()
            && !f.name.startsWith(".")
        }
        ?: emptyList()
```

---

## State of the Art

- **Built-in Kotlin in AGP 9.0+:** Old approach was `apply plugin: 'org.jetbrains.kotlin.android'`. Current approach: removed entirely. AGP handles Kotlin compilation natively. Changed: AGP 9.0 (early 2025).
- **DataStore replaces SharedPreferences:** SharedPreferences is still available but ANR-prone. DataStore 1.2.1 is the current stable replacement. Changed: DataStore 1.0 (2021), now fully stable.
- **AtomicFile for safe writes:** The temp-file-rename pattern predates AtomicFile but `android.util.AtomicFile` has been available since API 17. It is the idiomatic Android approach.
- **Type-safe Navigation Compose routes (`@Serializable`):** Old approach used string-based routes. Current: `@Serializable` data classes as route types. Available since Navigation 2.8.0.
- **`collectAsStateWithLifecycle()` replaces `collectAsState()`:** `collectAsState()` still works but does not stop collection when the app is backgrounded. `collectAsStateWithLifecycle()` is lifecycle-aware and the current recommended pattern.

---

## Open Questions

1. **Org file schema: exact notation format for macro fields**
   - What we know: The format must produce org entries that pass `M-x org-lint` and are human-readable in Emacs. The research confirms that simple list items (`- item text`) with inline plain text are valid org.
   - What's unclear: Whether the macro values should be inline plain text (`|P: 42g  C: 55g|`), separate list properties (`:protein: 42`), or a plain-text table. Property drawers are syntactically heavier but machine-readable. Inline plain text is lighter but requires a custom regex to parse back.
   - Recommendation: This is a design decision, not a research question. Write 3–5 sample entries by hand in Emacs for both variants. Run `M-x org-lint` on each. Pick whichever passes lint AND is the most readable in Emacs. The format you hand-author IS the OrgWriter spec. This is a blocker for Plan 01-02; do it first.

2. **Syncthing-Fork version to pin**
   - What we know: Syncthing 1.22.x had a confirmed spurious conflict bug on Android. Hotfixed in 1.22.1.1. The F-Droid listing shows the fork as actively maintained as of March 2026.
   - What's unclear: The exact current version of Syncthing-Fork on F-Droid and whether any active Android-specific conflict bugs exist in that version.
   - Recommendation: Check F-Droid for the installed version after device setup. If it is 1.22.x, update to the latest. Note the version in project docs as part of the Phase 1 device setup task.

3. **AtomicFile `.bak` file Syncthing interaction severity**
   - What we know: `AtomicFile` creates a `.bak` backup during writes. `.stignore` rules can prevent Syncthing from syncing it.
   - What's unclear: Whether Syncthing-Fork on Android will generate spurious conflicts from seeing the `.bak` file before the rename completes, given Syncthing's known sensitivity to partial writes on Android.
   - Recommendation: Add `*.bak` to `.stignore` as a setup step. If conflicts still appear during testing, consider using the app's private storage (`context.cacheDir`) for the temp file and copying to the Syncthing folder on finalize — at the cost of a copy instead of a rename.

---

## Sources

### Primary (HIGH confidence)

- https://developer.android.com/build/migrate-to-built-in-kotlin — AGP 9.0+ built-in Kotlin; `org.jetbrains.kotlin.android` not needed
- https://developer.android.com/build/releases/agp-9-1-0-release-notes — AGP 9.1.0 requires Gradle 9.3.1, JDK 17
- https://developer.android.com/training/data-storage/manage-all-files — MANAGE_EXTERNAL_STORAGE: manifest, `Environment.isExternalStorageManager()`, Settings intent, ADB test command
- https://developer.android.com/training/permissions/requesting-special — Special permissions flow; onResume check pattern
- https://developer.android.com/topic/libraries/architecture/datastore — DataStore Preferences API: `preferencesDataStore` delegate, `dataStore.edit {}`, `Flow` read pattern
- https://developer.android.com/codelabs/android-preferences-datastore — DataStore codelab: single-instance pattern, IOExceptions handling, repository wrapping
- https://developer.android.com/jetpack/androidx/releases/datastore — DataStore 1.2.1 stable (March 2026), 1.3.0-alpha07 with encryption (alpha, not for use)
- https://developer.android.com/reference/android/util/AtomicFile — AtomicFile: startWrite/finishWrite/failWrite pattern, suitability for text files
- https://developer.android.com/develop/ui/compose/navigation — Navigation Compose: conditional onboarding, `popUpTo { inclusive = true }`, `@Serializable` type-safe routes
- https://developer.android.com/guide/navigation/backstack — Navigation back stack: main screen as `startDestination` for conditional flows
- https://developer.android.com/develop/ui/compose/side-effects — Compose side effects: `DisposableEffect` for lifecycle observer, `LaunchedEffect` for one-time navigation
- https://developer.android.com/kotlin/coroutines/coroutines-best-practices — Dispatcher injection pattern for testability
- https://orgmode.org/manual/Timestamps.html — Timestamp format: `<YYYY-MM-DD DOW>`, DOW optional per spec but included by Emacs; active vs. inactive distinction
- https://orgmode.org/worg/org-syntax.html — Heading format (one space after asterisks), list item syntax, whitespace rules

### Secondary (MEDIUM confidence)

- https://github.com/emacs-mirror/emacs/blob/master/lisp/org/org-lint.el — org-lint source: what checks it runs (timestamps, drawers, planning lines, list structure)
- https://kiwix.gnuisnotunix.com/emacs.stackexchange.com_en_all_2021-04/A/question/42196.html — Non-interactive org-lint invocation pattern for scripted validation
- https://developer.android.com/jetpack/androidx/versions/stable-channel — Confirms DataStore 1.2.1 as current stable

---

## Metadata

**Confidence breakdown:**

- Standard stack (AGP, DataStore, AtomicFile): HIGH — all versions and APIs verified against official documentation
- Architecture patterns (permission flow, conditional navigation, onboarding): HIGH — sourced from official Android docs and codelabs
- OrgEngine format: MEDIUM-HIGH — timestamp format confirmed by org-mode official spec; exact field notation for macros is a design decision that must be validated in Emacs before implementation
- Pitfalls (Locale.ENGLISH, onResume re-check, AtomicFile+Syncthing): HIGH for the first two (confirmed against official sources); MEDIUM for AtomicFile+Syncthing interaction (confirmed as a known class of problem; exact severity with Syncthing-Fork untested)

**Research date:** 2026-04-09
**Valid until:** 2026-07-01 (stable stack; DataStore and Navigation versions unlikely to shift in this window; verify AGP/Compose BOM against official release pages before starting a new plan)
