# Phase 5: Local Storage Mode - Research

**Researched:** 2026-04-12
**Domain:** Android internal storage, DataStore preferences, Jetpack Compose onboarding flow, dependency injection wiring
**Confidence:** HIGH (all findings grounded in direct codebase inspection and well-established Android APIs)

---

## Summary

Phase 5 adds a second `SyncBackend` implementation — `LocalStorageBackend` — that reads/writes org files to `Context.filesDir` (app-internal storage, no permissions required). The existing `SyncthingFileBackend` is untouched. The main integration work is: (1) wiring `AppContainer` to select the right backend based on a persisted `StorageMode` preference, (2) expanding the onboarding flow with a Welcome screen and a mode-selection screen, and (3) conditionally hiding sync-specific UI in settings and the dashboard.

The codebase is already well-structured for this: `SyncBackend` is an interface, `AppContainer` is a manual DI container, and `AppPreferencesRepository` uses DataStore — all three need surgical additions without structural changes.

The critical subtlety is **cold-start backend selection**. `AppContainer` is created synchronously in `SakuraApplication.onCreate()`, but the stored `StorageMode` lives in DataStore (a coroutine-based async API). This mismatch must be handled deliberately — `MainActivity` already shows this pattern: it reads `onboardingComplete` with `initialValue = null` and delays rendering NavHost until DataStore emits. The same guard must cover backend initialization.

**Primary recommendation:** Implement `LocalStorageBackend` as a `Context.filesDir`-backed drop-in for `SyncBackend`, add `STORAGE_MODE` to `AppPreferencesRepository`, and make `AppContainer` lazy-initialize the backend after DataStore emits — mirroring the existing `onboardingComplete` guard in `MainActivity`.

---

## Standard Stack

No new libraries are required. Everything needed is already in the project.

### Core (existing, already in use)

- `androidx.datastore.preferences` — StorageMode preference key, same pattern as all existing prefs
- `android.content.Context.filesDir` — app-internal storage directory, no permissions, survives across app restarts, deleted on uninstall
- `android.util.AtomicFile` — already used by `SyncthingFileBackend` for crash-safe writes; use identically in `LocalStorageBackend`
- `kotlinx.coroutines` (`Dispatchers.IO`, `withContext`) — same pattern as `SyncthingFileBackend`

### What LocalStorageBackend does NOT need
- `MANAGE_EXTERNAL_STORAGE` permission — internal storage is always accessible
- `Environment.isExternalStorageManager()` check — no permission check required
- `prefsRepo.syncFolderPath` — path is always `context.filesDir`

---

## Architecture Patterns

### Recommended File Structure

New files to create:

```
com/sakura/sync/
  LocalStorageBackend.kt          ← new SyncBackend impl using filesDir

com/sakura/features/onboarding/
  OnboardingScreen.kt             ← expand existing (add Welcome + ModeSelection screens)
  OnboardingViewModel.kt          ← expand existing (add StorageMode state)

com/sakura/features/settings/
  StorageSettingsSection.kt       ← new composable for Settings > Storage section
  (or expand MacroTargetsScreen to add Storage section)
```

Modified files:

```
com/sakura/preferences/AppPreferencesRepository.kt   ← add STORAGE_MODE key + storageMode Flow + setStorageMode()
com/sakura/di/AppContainer.kt                        ← conditional backend wiring
com/sakura/MainActivity.kt                           ← await storageMode alongside onboardingComplete
com/sakura/features/dashboard/DashboardScreen.kt     ← hide SyncStatusBadge in local mode
com/sakura/features/settings/MacroTargetsScreen.kt   ← add Storage section
com/sakura/navigation/Routes.kt                      ← add Welcome route (if needed)
```

### Pattern 1: LocalStorageBackend

`LocalStorageBackend` is a simpler version of `SyncthingFileBackend`. It receives `Context` (not `AppPreferencesRepository`) because the path is always `context.filesDir`.

Key differences from `SyncthingFileBackend`:
- No `checkPermission()` method — internal storage is always accessible
- No `resolveFile()` needing a pref lookup — path is `File(context.filesDir, filename)`
- `checkSyncStatus()` returns `SyncStatus(lastSyncedAt = null, hasConflicts = false, folderAccessible = true)` — always accessible, no sync concept
- All other methods (`readFile`, `writeFile`, `fileExists`, `listOrgFiles`) are structurally identical using `AtomicFile`

```kotlin
// Source: codebase inspection of SyncthingFileBackend.kt
class LocalStorageBackend(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SyncBackend {

    private fun resolveFile(filename: String): File =
        File(context.filesDir, filename)

    override suspend fun readFile(filename: String): String = withContext(ioDispatcher) {
        val file = resolveFile(filename)
        if (!file.exists()) return@withContext ""
        file.readText(Charsets.UTF_8)
    }

    override suspend fun writeFile(filename: String, content: String): Unit = withContext(ioDispatcher) {
        val file = resolveFile(filename)
        val atomicFile = AtomicFile(file)
        var stream = atomicFile.startWrite()
        try {
            stream.write(content.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
            stream = null
        } catch (e: IOException) {
            if (stream != null) atomicFile.failWrite(stream)
            throw e
        }
    }

    override suspend fun fileExists(filename: String): Boolean = withContext(ioDispatcher) {
        resolveFile(filename).exists()
    }

    override suspend fun listOrgFiles(): List<String> = withContext(ioDispatcher) {
        context.filesDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".org") && !it.name.startsWith(".") }
            ?.map { it.name }
            ?: emptyList()
    }

    override suspend fun checkSyncStatus(): SyncStatus =
        SyncStatus(lastSyncedAt = null, hasConflicts = false, folderAccessible = true)
}
```

### Pattern 2: StorageMode preference in AppPreferencesRepository

Add a `stringPreferencesKey("storage_mode")` with an enum or sealed class:

```kotlin
enum class StorageMode { LOCAL, SYNCTHING }

val STORAGE_MODE = stringPreferencesKey("storage_mode")

val storageMode: Flow<StorageMode?> = context.appDataStore.data
    .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    .map { prefs ->
        prefs[STORAGE_MODE]?.let { runCatching { StorageMode.valueOf(it) }.getOrNull() }
    }
// null = not yet selected (onboarding not complete)

suspend fun setStorageMode(mode: StorageMode) {
    context.appDataStore.edit { it[STORAGE_MODE] = mode.name }
}
```

Using `null` to mean "not yet selected" aligns with the existing pattern for `onboardingComplete` (`initialValue = null` in `MainActivity`).

### Pattern 3: AppContainer conditional wiring — the cold-start problem

`AppContainer` is instantiated synchronously in `SakuraApplication.onCreate()`. DataStore is async. The existing codebase already solves the cold-start problem in `MainActivity`:

```kotlin
// From MainActivity.kt (existing pattern)
val onboardingDone by container.prefsRepo.onboardingComplete
    .collectAsStateWithLifecycle(initialValue = null)
if (onboardingDone != null) { ... }
```

For backend selection, there are two clean approaches:

**Option A: Lazy val in AppContainer (recommended)**
Make `syncBackend` a `lateinit var` or use a `Flow`-backed approach. The cleanest is: keep `syncBackend` as a `val` but read `StorageMode` synchronously from DataStore using `runBlocking` only in `AppContainer` init. This is acceptable because `SakuraApplication.onCreate()` already runs on the main thread and DataStore reads from an on-disk file — the first read is fast (~1-2ms from cache after first access).

Actually, the safer pattern (avoids `runBlocking` on main thread startup): make `AppContainer` hold a `Flow<SyncBackend>` and let `MainActivity` await it similarly to `onboardingComplete`. However, this requires all repositories to be reconstructed or to accept a mutable backend reference.

**Option B: Single combined await in MainActivity (cleanest)**
Read both `onboardingComplete` AND `storageMode` from DataStore before rendering. Create the correct `AppContainer` variant once both are known. This requires `AppContainer` to accept a `StorageMode` parameter at construction, not read it internally.

```kotlin
// AppContainer(context, storageMode) — receives the mode, doesn't fetch it
val syncBackend: SyncBackend = when (storageMode) {
    StorageMode.LOCAL -> LocalStorageBackend(context)
    StorageMode.SYNCTHING -> SyncthingFileBackend(prefsRepo)
}
```

In `MainActivity`:

```kotlin
val onboardingDone by container.prefsRepo.onboardingComplete
    .collectAsStateWithLifecycle(initialValue = null)
val storageMode by container.prefsRepo.storageMode
    .collectAsStateWithLifecycle(initialValue = null)

if (onboardingDone != null && storageMode != null) {
    // now safe to use container.syncBackend
}
```

But `container` is created in `SakuraApplication` before `storageMode` is known. A cleaner approach: move `AppContainer` creation into `MainActivity`'s composition, gate it on both values, and pass the `storageMode` to the container constructor. Or create the container lazily after both values emit.

**Recommended approach (Option B variant):** Add `storageMode: StorageMode` as an `AppContainer` constructor parameter. `SakuraApplication` creates a minimal `prefsRepo`-only container. `MainActivity` reads both `onboardingComplete` and `storageMode` from `prefsRepo`, then creates the full `AppContainer` once both are non-null. This makes the backend selection explicit and testable.

This is a clean extension of the existing cold-start guard — just a second `null` check before building.

### Pattern 4: Onboarding flow expansion

Current onboarding states: `CheckingPermission → NeedsPermission → NeedsFolder → Complete`

New flow for local mode: `Welcome → ModeSelection → LocalComplete`
New flow for Syncthing mode: `Welcome → ModeSelection → NeedsPermission → NeedsFolder → SyncthingComplete`

The `OnboardingUiState` sealed interface must expand:

```kotlin
sealed interface OnboardingUiState {
    data object Welcome : OnboardingUiState           // new
    data object ModeSelection : OnboardingUiState     // new
    data object NeedsPermission : OnboardingUiState   // existing
    data object NeedsFolder : OnboardingUiState       // existing
    data object LocalComplete : OnboardingUiState     // new (was: Complete)
    data object SyncthingComplete : OnboardingUiState // existing Complete renamed, or reuse Complete
}
```

`OnboardingViewModel.checkPermission()` now only runs in the Syncthing path. The ViewModel gets a `fun onModeSelected(mode: StorageMode)` method.

### Pattern 5: Hiding sync UI in local mode

`DashboardScreen` renders `SyncStatusBadge` — this must be hidden in local mode. Two approaches:

1. Pass `storageMode` down from `AppNavHost` → `DashboardViewModel` → `DashboardTodayState`, and conditionally render the badge
2. Rely on `SyncStatus.folderAccessible = false` from `LocalStorageBackend.checkSyncStatus()` — but this shows "Offline" badge, which is wrong

Option 1 is correct. The cleanest: add `isLocalMode: Boolean` to `DashboardTodayState`, set it from `storageMode` flow in `DashboardViewModel`, and use it in `DashboardScreen` to skip rendering `SyncStatusBadge`.

Similarly, `MacroTargetsScreen` needs a Storage section that shows current mode + "Change" button. This is new UI — a `StorageSection` composable within the settings screen.

### Pattern 6: Migration between modes

Migration copies org files from source path to destination path. The files to copy are the same filenames `SyncthingFileBackend` and `LocalStorageBackend` work with (e.g., `food-log.org`, `food-library.org`, `workout-log.org`).

Migration direction:
- Local → Syncthing: copy from `context.filesDir/*.org` to configured sync folder path
- Syncthing → Local: copy from sync folder path to `context.filesDir`

Migration must:
1. List all `.org` files in source
2. Copy each to destination (if destination doesn't already have the file, or overwrite with confirmation)
3. Show a result screen ("X files moved")
4. Update `STORAGE_MODE` in DataStore
5. Trigger `AppContainer` to re-create with new backend — this likely requires restarting the Activity or recomposing `AppNavHost`

Activity restart is the simplest migration completion: call `recreate()` on the activity after mode switch, which triggers `MainActivity` to re-read `storageMode` and rebuild `AppContainer` with the new backend.

---

## Don't Hand-Roll

- **Atomic file writes:** `android.util.AtomicFile` is already used. Do not replace with custom rename-swap logic.
- **DataStore reads before first emission:** Do not use `runBlocking` on main thread for DataStore. Use the existing `initialValue = null` pattern from `MainActivity`.
- **Permission checks in LocalStorageBackend:** Internal storage never requires runtime permissions. Do not add `checkPermission()` calls — they'll always be wrong and confusing.

---

## Common Pitfalls

### Pitfall 1: AppContainer created before StorageMode is known

**What goes wrong:** `SakuraApplication.onCreate()` creates `AppContainer` synchronously. If `syncBackend` is wired at that point (as it is today), the backend is always `SyncthingFileBackend` regardless of stored mode.

**Why it happens:** DataStore is async; the container constructor is sync.

**How to avoid:** Pass `StorageMode` as a constructor parameter to `AppContainer`. Split container creation: `SakuraApplication` holds only `prefsRepo`; `MainActivity` creates the full container once `storageMode` emits from DataStore.

**Warning signs:** App always opens with Syncthing backend regardless of selected mode on cold start.

### Pitfall 2: Showing "Offline" badge instead of hiding badge in local mode

**What goes wrong:** `LocalStorageBackend.checkSyncStatus()` returns `folderAccessible = true` but `lastSyncedAt = null`. The badge renders "Synced" with no timestamp. Or if `folderAccessible = false`, it shows "Offline".

**Why it happens:** The dashboard rendering logic doesn't distinguish between "no sync concept" and "sync broken".

**How to avoid:** Add `isLocalMode: Boolean` to `DashboardTodayState` and skip rendering `SyncStatusBadge` entirely when true.

### Pitfall 3: filesDir files lost on app data clear / reinstall

**What goes wrong:** `context.filesDir` is cleared when user clears app data or uninstalls. This is expected behavior for local mode, but users should understand it.

**Why it matters:** No action needed in code, but the "You're all set!" confirmation screen or Settings > Storage section could note "Data is stored on this device only."

### Pitfall 4: Migration overwrite race condition

**What goes wrong:** User triggers migration while a write is in flight (e.g., adding food while migrating).

**How to avoid:** Migration should be a user-initiated flow from Settings, not background. The existing `fileMutex` in `OrgFoodRepository` serializes concurrent writes within one backend, but doesn't lock across the migration. Simplest safe approach: migration copies files in a one-shot suspend function before switching the backend.

### Pitfall 5: OnboardingViewModel re-checks permission in local mode

**What goes wrong:** The existing `DisposableEffect` in `OnboardingScreen` calls `viewModel.checkPermission()` on `ON_RESUME`. This is only meaningful for Syncthing path. In local mode, there's no permission to check.

**How to avoid:** Guard `checkPermission()` calls behind the selected mode. Or remove the `DisposableEffect` from `OnboardingScreen` and only attach it to the `NeedsPermission` sub-screen.

### Pitfall 6: `storageMode == null` on first launch (no mode selected yet)

**What goes wrong:** A new user hasn't selected a mode yet. `storageMode` emits `null`. If code treats `null` the same as `LOCAL`, the user skips mode selection.

**How to avoid:** `null` = no mode selected = show onboarding. `LOCAL` or `SYNCTHING` = mode chosen, use it. The `startWithOnboarding` logic in `MainActivity`/`AppNavHost` should check both `onboardingComplete` and `storageMode != null`.

---

## Code Examples

### LocalStorageBackend: checkSyncStatus for local mode

```kotlin
// No sync concept in local mode — always accessible, never conflicted, no timestamp
override suspend fun checkSyncStatus(): SyncStatus =
    SyncStatus(lastSyncedAt = null, hasConflicts = false, folderAccessible = true)
```

### AppPreferencesRepository: StorageMode preference

```kotlin
enum class StorageMode { LOCAL, SYNCTHING }

val STORAGE_MODE = stringPreferencesKey("storage_mode")

val storageMode: Flow<StorageMode?> = context.appDataStore.data
    .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    .map { prefs ->
        prefs[STORAGE_MODE]?.let { runCatching { StorageMode.valueOf(it) }.getOrNull() }
    }

suspend fun setStorageMode(mode: StorageMode) {
    context.appDataStore.edit { it[STORAGE_MODE] = mode.name }
}
```

### AppContainer: accept mode as constructor parameter

```kotlin
class AppContainer(context: Context, storageMode: StorageMode) {
    val prefsRepo = AppPreferencesRepository(context)
    val syncBackend: SyncBackend = when (storageMode) {
        StorageMode.LOCAL -> LocalStorageBackend(context)
        StorageMode.SYNCTHING -> SyncthingFileBackend(prefsRepo)
    }
    // ... rest unchanged
}
```

### MainActivity: await both prefs before building container

```kotlin
// SakuraApplication holds only prefsRepo now
val prefsRepo = (application as SakuraApplication).prefsRepo

val onboardingDone by prefsRepo.onboardingComplete
    .collectAsStateWithLifecycle(initialValue = null)
val storageMode by prefsRepo.storageMode
    .collectAsStateWithLifecycle(initialValue = null)

if (onboardingDone != null) {
    val resolvedMode = storageMode ?: StorageMode.LOCAL  // null = first launch, onboarding will set it
    val container = remember(resolvedMode) { AppContainer(applicationContext, resolvedMode) }
    AppNavHost(
        navController = rememberNavController(),
        appContainer = container,
        startWithOnboarding = onboardingDone != true
    )
}
```

Note: `remember(resolvedMode)` ensures the container is re-created when mode changes (after migration).

### OnboardingViewModel: expanded state machine

```kotlin
sealed interface OnboardingUiState {
    data object Welcome : OnboardingUiState
    data object ModeSelection : OnboardingUiState
    data object NeedsPermission : OnboardingUiState   // Syncthing path only
    data object NeedsFolder : OnboardingUiState        // Syncthing path only
    data object Complete : OnboardingUiState           // Both paths land here
}

fun onGetStarted() { _uiState.value = OnboardingUiState.ModeSelection }

fun onModeSelected(mode: StorageMode) {
    when (mode) {
        StorageMode.LOCAL -> {
            viewModelScope.launch {
                prefsRepo.setStorageMode(StorageMode.LOCAL)
                prefsRepo.setOnboardingComplete()
                _uiState.value = OnboardingUiState.Complete
            }
        }
        StorageMode.SYNCTHING -> {
            viewModelScope.launch {
                prefsRepo.setStorageMode(StorageMode.SYNCTHING)
                // Don't set onboarding complete yet — continue to permission/folder screens
                _uiState.value = OnboardingUiState.NeedsPermission
            }
        }
    }
}
```

---

## State of the Art

- **Internal storage (Context.filesDir):** No changes since Android 4.0. Always available, no permissions. Files are private to the app. Correct for LOCAL-04.
- **AtomicFile:** Available since API 1. The existing usage in `SyncthingFileBackend` is the right pattern for crash-safe file writes.
- **DataStore Preferences:** The existing `AppPreferencesRepository` pattern is correct and current. No migration needed — just add a new key.

---

## Open Questions

1. **AppContainer lifetime during mode switch**
   - What we know: `remember(resolvedMode)` in `MainActivity` re-creates `AppContainer` when mode changes. This works for in-session mode switches.
   - What's unclear: Does `remember(resolvedMode)` properly dispose the old container's coroutine scopes? (There are none — `AppContainer` holds no coroutine scope itself, only DataStore flows. Safe.)
   - Recommendation: Use `remember(resolvedMode)` — it is correct.

2. **Migration UX flow placement**
   - What we know: Migration is triggered from Settings > Storage section.
   - What's unclear: Should migration be a bottom sheet, a separate screen, or an inline dialog?
   - Recommendation: Defer to planner. A simple confirmation dialog ("Move X files to internal storage?") followed by a success snackbar is sufficient complexity.

3. **SakuraApplication refactor scope**
   - What we know: Currently `SakuraApplication` creates the full `AppContainer`. Phase 5 moves container creation to `MainActivity`.
   - What's unclear: Whether `SakuraApplication` should hold a `PrefsOnlyContainer` or just a bare `AppPreferencesRepository`.
   - Recommendation: `SakuraApplication` holds only `AppPreferencesRepository(this)`. Clean and minimal.

---

## Sources

### Primary (HIGH confidence — direct codebase inspection)

- `SyncthingFileBackend.kt` — existing backend pattern; `LocalStorageBackend` is a direct structural parallel
- `AppContainer.kt` — current DI wiring; shows where backend selection is injected
- `AppPreferencesRepository.kt` — existing DataStore patterns; `StorageMode` key follows same structure
- `MainActivity.kt` — `initialValue = null` guard for async DataStore on cold start; the key architectural precedent
- `OnboardingViewModel.kt` / `OnboardingScreen.kt` — current onboarding state machine; Phase 5 expands it
- `AndroidManifest.xml` — confirms `MANAGE_EXTERNAL_STORAGE` is the only storage permission declared
- `app/build.gradle.kts` — confirms no new dependencies are needed

### Secondary (MEDIUM confidence)

- Android developer docs (training knowledge, high stability): `Context.filesDir` is app-internal, no permissions required, available API 1+. This is fundamental Android API — extremely stable.
- `android.util.AtomicFile` (training knowledge): crash-safe write pattern; identical usage already in `SyncthingFileBackend.kt`.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new libraries; all tools already in project
- Architecture patterns: HIGH — grounded in direct codebase analysis; `LocalStorageBackend` is a structural copy of existing `SyncthingFileBackend`
- AppContainer cold-start approach: HIGH — extends existing `initialValue = null` pattern already in `MainActivity`
- Migration: MEDIUM — pattern is clear but exact UX (dialog vs screen) deferred to planner
- Pitfalls: HIGH — derived from reading actual code paths

**Research date:** 2026-04-12
**Valid until:** Stable — no fast-moving dependencies. Re-verify if Android Gradle Plugin or DataStore version changes.
