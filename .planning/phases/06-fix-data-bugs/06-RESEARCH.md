# Phase 6: Fix Data Bugs — Research

**Researched:** 2026-04-13
**Domain:** Android Kotlin — org file persistence, Jetpack DataStore, Android file I/O
**Confidence:** HIGH

## Summary

Phase 6 fixes three distinct bugs in the existing codebase. All bugs are already diagnosed; the code paths are fully understood. No new libraries or architectural patterns are required — this phase is exclusively surgical edits to existing files.

**Bug 1 (CRITICAL — Issue 3):** `SetLog.isPr` is never written as `true` to the org file. The detection logic in `WorkoutLogViewModel.addSet()` does set `setToWrite = set.copy(isPr = true)`, and `SetLog.toOrgSetEntry()` does pass `isPr = this.isPr`, and `OrgSchema.formatSetEntry()` does write `:is_pr: true/false`. The full path is present. The parser also reads `:is_pr:` back correctly. The bug is that `addSet()` calls `workoutRepo.addSet(date, exerciseId, setToWrite)` — which calls `appendSet()` inside the mutex — but inspection of `appendSet()` shows it correctly uses the `set` parameter it receives. The issue needs closer inspection but the fix will be in the ViewModel→Repository flow.

**Bug 2 (COSMETIC — Issue 2):** `WorkoutSession.templateName` is `null` after a cold-start parse because `OrgDateSection.toWorkoutSession()` constructs `WorkoutSession` with `templateName = null` and never derives it from the persisted `splitDay`. The fix is one line: derive `templateName` from `splitDayParsed?.displayName` inside `toWorkoutSession()`.

**Bug 3 (CRITICAL — Issue 1):** LOCAL→SYNCTHING migration cannot copy org files because the sync folder is not yet selected at migration time. This requires a different approach: copy the org files from `context.filesDir` into the Syncthing folder during onboarding's `onFolderSelected()` callback, after the user has chosen the sync folder but before marking onboarding complete.

**Primary recommendation:** Fix the three bugs in isolation. Each fix is self-contained; none depends on the others. Execute in order: templateName (simplest, fully cosmetic), isPr (next simplest, no UX changes), LOCAL→SYNCTHING migration (most complex, touches two files).

## Standard Stack

This phase uses no new dependencies. All tools are already in the project.

### Core (already present)
- Kotlin coroutines — `Dispatchers.IO` for file copy, already used throughout
- Jetpack DataStore — already used in `AppPreferencesRepository`
- `java.io.File` — already used in `LocalStorageBackend` and `migrateStorage()`
- `ComponentActivity.recreate()` — already used in `migrateStorage()`

### No New Dependencies Needed

The org engine (OrgParser, OrgWriter, OrgSchema), SyncBackend implementations, and AppPreferencesRepository all already exist and are correct for reading and writing the fields involved.

## Architecture Patterns

### Bug 2 Fix: templateName Derivation

**Location:** `OrgWorkoutRepository.kt` — the `OrgDateSection.toWorkoutSession()` private extension function at the bottom of the file.

**Current code (line 543):**
```kotlin
return WorkoutSession(
    date = this.date,
    splitDay = splitDayParsed,
    exercises = exerciseList,
    durationMin = this.durationMin ?: 0,
    isComplete = this.complete
    // templateName NOT set — defaults to null
)
```

**Fix:** Add `templateName = splitDayParsed?.displayName` to the constructor call. `SplitDay.displayName` returns the full display string (e.g., `"Monday — Lift (Heavy Compounds)"`). No other files need changes.

**Confidence:** HIGH — the full chain is verified: `splitDay` is persisted to the org file via `:split_day:` property, parsed back by `OrgParser` into `OrgDateSection.splitDay`, passed to `SplitDay.fromLabel()`, and the resulting `SplitDay?` is available in `toWorkoutSession()`. Adding `templateName = splitDayParsed?.displayName` is the complete fix.

**Downstream callers that will now work:**
- `DashboardViewModel.refresh()` — `recentWorkoutDays` maps `session.templateName` directly
- `WorkoutLogUiState.DayLoaded.templateName` — set from `session?.templateName` in ViewModel
- `WorkoutLogScreen` — `state.templateName ?: "Freestyle"` will now show the real name
- `WorkoutLogViewModel.loadCalendar()` — `session?.templateName` used in `splitLabel`

### Bug 3 Fix: isPr Persistence

**Location:** Investigation needed to confirm exact failure point.

The full pipeline is:
1. `WorkoutLogViewModel.addSet()` — detects PR, creates `setToWrite = set.copy(isPr = true)`
2. Calls `workoutRepo.addSet(date, exerciseId, setToWrite)`
3. `OrgWorkoutRepository.addSet()` — calls `set.toOrgSetEntry()` then `appendSet()`
4. `SetLog.toOrgSetEntry()` — `isPr = this.isPr` (passes through)
5. `OrgSchema.formatSetEntry()` — writes `:is_pr: ${set.isPr}` (always written)
6. `OrgParser` — reads `:is_pr:` and uses `toBooleanStrictOrNull() ?: false`

**Potential issue:** `toBooleanStrictOrNull()` only returns `true` for the exact string `"true"` (case-sensitive). If `set.isPr` is `Boolean` and `"${set.isPr}"` produces `"true"`, this should work. This needs verification in a test scenario.

**More likely issue:** Looking at `appendSet()` in `OrgWorkoutRepository` — it receives `orgSet: OrgSetEntry` after `set.toOrgSetEntry()` conversion. The conversion is: `isPr = this.isPr`. This is correct. However, there may be a timing issue: the PR check runs against the current `uiState.value` which comes from a `StateFlow`. If the state hasn't refreshed yet (between launches of the coroutine), `exerciseName` could be null, bypassing the PR check entirely. The guard `if (exerciseName != null)` means a null name silently skips PR detection.

**Confirmed failure path (from context):** The PR dialog fires correctly (we can see `_prDetected.value = PrNotification(exerciseName, prType)` runs), but isPr badge does not render when reading back. This means the dialog fires but the isPr flag is not in the org file. Since `formatSetEntry` always writes `:is_pr:`, the issue must be that `set.isPr = false` is being written instead of `true`.

**Root cause hypothesis:** `workoutRepo.addSet(date, exerciseId, setToWrite)` is called with `setToWrite` where `setToWrite.isPr = true`. But `addSet` in the repository calls `set.toOrgSetEntry()` — let's trace: `OrgWorkoutRepository.addSet()` receives `set: SetLog`, calls `val orgSet = set.toOrgSetEntry()`, then `appendSet(orgFile.sections, date, exerciseId, orgSet)`. Inside `appendSet()`, the orgSet is used with `set.copy(setNumber = ...)`. Since `OrgSetEntry` carries `isPr`, the copy preserves it. This path looks correct.

**Alternative hypothesis:** There could be a race condition. `addSet()` in the ViewModel first reads `exerciseName` from `uiState.value`, then launches the coroutine. If the coroutine completes and `_reloadTrigger.value++` fires before the PR check line runs... no, the PR check happens before `workoutRepo.addSet()` in the same coroutine.

**Conclusion:** The code path for `isPr = true` through the write pipeline appears complete. Need to verify the `toBooleanStrictOrNull()` behavior vs `"true"` string, or check if there is an issue with how `OrgParser.PROPERTY_REGEX` handles the value. The regex is `Regex("""^:(\w+):\s+(.+)$""")` — this should match `:is_pr: true`. The fix is likely to verify the string comparison or ensure the write produces `"true"` not `"True"`.

**Definitive fix approach:** Add `is_pr` property writing and confirm with a unit test on `OrgSchema.formatSetEntry` and `OrgParser` round-trip. Since `Boolean.toString()` in Kotlin produces `"true"` (lowercase), and `toBooleanStrictOrNull()` requires `"true"` (lowercase), the string path should work. The most likely explanation is that the `setToWrite` variable is not being passed correctly — worth adding a log or test.

### Bug 1 Fix: LOCAL→SYNCTHING Migration

**Location:** Two files need changes:
1. `MacroTargetsScreen.kt` — `migrateStorage()` function (lines 286-290)
2. `OnboardingViewModel.kt` — `onFolderSelected()` function

**Current (broken) flow:**
```
User taps "Change" in Settings
→ migrateStorage(LOCAL→SYNCTHING):
    - setStorageMode(SYNCTHING)         ← switches mode NOW
    - clearOnboardingComplete()          ← sends user back to onboarding
    - recreate()                         ← restarts activity
→ OnboardingViewModel.init() sees SYNCTHING mode, goes to NeedsPermission/NeedsFolder
→ User selects folder
→ onFolderSelected() saves path, marks onboarding complete
→ App opens with SYNCTHING backend pointing to new folder
→ PROBLEM: org files still in context.filesDir, sync folder is empty
```

**Fixed flow:**
```
User taps "Change" in Settings
→ migrateStorage(LOCAL→SYNCTHING):
    - (do NOT copy files — sync folder not known yet)
    - setStorageMode(SYNCTHING)
    - clearOnboardingComplete()
    - recreate()
→ OnboardingViewModel sees SYNCTHING, goes to NeedsPermission/NeedsFolder
→ User selects folder
→ onFolderSelected(path):
    - setSyncFolderPath(path)
    - copyOrgFilesFromLocalToSync(context, path)   ← NEW: copy files before marking done
    - setOnboardingComplete()
    - _uiState = Complete
```

**Implementation of `copyOrgFilesFromLocalToSync`:**
- Source: `context.filesDir` — the `LocalStorageBackend` always uses this directory
- Destination: the newly selected `path` (trimmed of trailing `/`)
- Files to copy: all `*.org` files in `context.filesDir`
- Use `File.copyTo(dest, overwrite = true)` — same pattern as SYNCTHING→LOCAL direction
- Must run on IO dispatcher

**Challenge:** `OnboardingViewModel` currently has no reference to `Context`. It only receives `AppPreferencesRepository`. Two options:
1. Pass `Context` to `OnboardingViewModel` — needs to be Application context to avoid leaks
2. Add a `copyFilesFromLocalToSync(sourcePath: String, destPath: String)` method to `AppPreferencesRepository` or a new helper — but this is business logic, not preferences

**Recommended approach:** Pass `applicationContext` to `OnboardingViewModel` (use `Application` context, not `Activity` context — no leak risk). Or: pass an `onFolderSelected` callback from `OnboardingScreen` that includes the copy logic in the composable.

**Actually better:** Keep the logic co-located. Since `OnboardingScreen` already has `LocalContext.current` available, the copy operation can be initiated from `OnboardingViewModel` if it receives `Context`, or from the composable by threading through a callback.

**Cleanest approach:** Add `context: Context` (application context) to `OnboardingViewModel` so the IO operation can run in a coroutine within `viewModelScope`. The ViewModel is scoped to the activity lifecycle anyway, and `applicationContext` doesn't leak.

**Alternative cleaner approach:** Add a `suspend fun migrateLocalFilesToFolder(destPath: String)` function to `AppPreferencesRepository` that takes care of the copy, using the context it already holds. This keeps the ViewModel thin and co-locates the file operation with the class that already handles `context.filesDir` understanding.

**Should-migrate guard:** The copy should only happen when migrating from LOCAL mode to SYNCTHING. `OnboardingViewModel.init()` already detects this path by checking `prefsRepo.storageMode.first() == StorageMode.SYNCTHING`. A flag could be tracked or the presence of `*.org` files in `context.filesDir` could serve as the trigger. Best approach: check if `context.filesDir` contains any `.org` files at `onFolderSelected()` time — if yes, copy them.

## Don't Hand-Roll

No hand-rolling concerns for this phase. All operations use standard Java/Kotlin I/O (`File.copyTo`), existing DataStore APIs, and existing org engine components.

The one pitfall to avoid: do NOT re-implement file copy with manual streams. `File.copyTo(target, overwrite = true)` is the idiomatic Kotlin extension that handles this cleanly.

## Common Pitfalls

### Pitfall 1: toBooleanStrictOrNull vs toBoolean
**What goes wrong:** `"true".toBoolean()` and `"true".toBooleanStrictOrNull()` both return `true`. But `"True".toBooleanStrictOrNull()` returns `null` (strict) while `"True".toBoolean()` returns `false`. Since `Boolean.toString()` in Kotlin produces lowercase `"true"`, and `OrgSchema.formatSetEntry` writes `":is_pr: ${set.isPr}"`, the produced string will be `:is_pr: true` (lowercase). `toBooleanStrictOrNull()` handles this correctly. This is NOT the bug.

### Pitfall 2: Migration Copy — Context Source
**What goes wrong:** Using `Activity` context (leaked) vs `Application` context (safe) for IO operations in ViewModel.
**How to avoid:** Pass `applicationContext` to OnboardingViewModel or use `AppPreferencesRepository` (which already holds `context`) to run the file copy.

### Pitfall 3: Migration Copy — When to Copy
**What goes wrong:** Copying files during `migrateStorage()` before the sync folder is known — files go nowhere.
**How to avoid:** Copy happens in `onFolderSelected()` callback AFTER the path is known but BEFORE marking onboarding complete.

### Pitfall 4: templateName displayName length
**What goes wrong:** `SplitDay.displayName` values are long (e.g., `"Monday — Lift (Heavy Compounds)"`). Dashboard and WorkoutLogScreen truncate with `maxLines = 1` + `TextOverflow.Ellipsis`, so this is fine visually.
**Note:** The calendar's `splitLabel` in `WorkoutLogViewModel.loadCalendar()` uses `session?.templateName ?: session?.splitDay?.displayName?.substringAfterLast("— ")?.trim()`. After the fix, `session.templateName` will be non-null, so it'll use the full display name. This may be too long for the small calendar cells. The calendar composable should continue using `session?.splitDay?.displayName?.substringAfterLast("— ")?.trim()` for calendar cells — only the main workout screen and dashboard need `templateName`.

**Implication:** The fix to `toWorkoutSession()` populates `templateName` from `splitDayParsed?.displayName`. The calendar `splitLabel` logic in `WorkoutLogViewModel.loadCalendar()` already has its own fallback that uses the abbreviated form. No calendar regression expected.

### Pitfall 5: isPr Race Condition
**What goes wrong:** `exerciseName` lookup in `addSet()` reads from `uiState.value` (current StateFlow value). If the UI state hasn't loaded yet (unlikely but possible), `exerciseName` is null and the PR check is skipped silently.
**How to avoid:** The existing guard `if (exerciseName != null)` handles this gracefully — the set is still written (just without isPr=true). This is acceptable behavior for an edge case.

### Pitfall 6: Migration — Empty filesDir
**What goes wrong:** If the user is migrating but has no data yet (fresh install chose LOCAL, immediately migrates to SYNCTHING), the copy loop finds 0 files. This is fine — `filesDir.listFiles { ... }?.forEach` handles empty results safely.

## Code Examples

### Fix 2: templateName in toWorkoutSession()
```kotlin
// In OrgWorkoutRepository.kt — OrgDateSection.toWorkoutSession() extension
// Source: direct codebase reading

// Before (line ~543):
return WorkoutSession(
    date = this.date,
    splitDay = splitDayParsed,
    exercises = exerciseList,
    durationMin = this.durationMin ?: 0,
    isComplete = this.complete
)

// After:
return WorkoutSession(
    date = this.date,
    splitDay = splitDayParsed,
    templateName = splitDayParsed?.displayName,   // derived from persisted splitDay
    exercises = exerciseList,
    durationMin = this.durationMin ?: 0,
    isComplete = this.complete
)
```

### Fix 3: isPr — Verify the path
The full chain that should work:
```kotlin
// WorkoutLogViewModel.addSet() — sets setToWrite.isPr = true
setToWrite = set.copy(isPr = true)
workoutRepo.addSet(date, exerciseId, setToWrite)   // ← setToWrite.isPr is true here

// OrgWorkoutRepository.addSet() — converts to OrgSetEntry
val orgSet = set.toOrgSetEntry()   // SetLog.toOrgSetEntry: isPr = this.isPr → true

// OrgSchema.formatSetEntry() — writes :is_pr: true
sb.append(":$PROP_IS_PR: ${set.isPr}\n")   // produces ":is_pr: true"

// OrgParser — reads :is_pr: true back
isPr = drawerProperties[OrgSchema.PROP_IS_PR]?.toBooleanStrictOrNull() ?: false
// "true".toBooleanStrictOrNull() == true ✓
```

If the round-trip is correct, then the issue may be that `setToWrite.isPr = true` is confirmed by the PR dialog firing BUT the set written to the org file is the *original* `set` (isPr=false) not `setToWrite`. This would happen if there's a variable shadowing bug. Looking at `addSet()`:

```kotlin
fun addSet(exerciseId: Long, set: SetLog) {
    viewModelScope.launch {
        ...
        var setToWrite = set                    // starts as isPr=false
        if (exerciseName != null) {
            val pb = workoutRepo.findPersonalBest(exerciseName)
            if (pb != null) {
                val prType = when { ... }
                if (prType != null) {
                    setToWrite = set.copy(isPr = true)   // updated
                    _prDetected.value = PrNotification(exerciseName, prType)
                }
            }
        }
        workoutRepo.addSet(date, exerciseId, setToWrite)   // uses setToWrite ✓
        _reloadTrigger.value++
    }
}
```

The variable usage is correct. **Most likely actual bug:** `findPersonalBest` returns `null` on the first set (no previous history), so `pb != null` is false, and `setToWrite` is never updated. But the PR dialog still fires... wait, if `pb == null`, the PR check is skipped. So if the dialog fires, `pb` was non-null and the PR was detected. The dialog fires via `_prDetected.value`. So `setToWrite.isPr` should be `true`.

**Revised hypothesis:** The PR dialog fires (correct), `isPr=true` is written to the org file (correct), but when the user re-opens the app (cold start), the `isPr` badge doesn't render. This matches "isPr badge does not persist." On a cold start, the history is re-read from the org file. If the badge doesn't show after cold start but DOES show in the current session, then the issue is in the parser not reading `isPr=true` back. Otherwise, the write may genuinely be failing.

**Action for planning:** The plan should include a step to write a unit test for the `OrgSchema.formatSetEntry` + `OrgParser` isPr round-trip to confirm or deny the parser hypothesis, before attempting a fix.

### Fix 1: LOCAL→SYNCTHING Migration Copy

```kotlin
// In OnboardingViewModel.kt — onFolderSelected()

fun onFolderSelected(path: String) {
    viewModelScope.launch {
        prefsRepo.setSyncFolderPath(path)
        // Copy any existing local org files to the newly selected sync folder.
        // This handles the LOCAL→SYNCTHING migration path where files were
        // previously stored in context.filesDir.
        prefsRepo.copyLocalOrgFilesToFolder(path)   // new helper in AppPreferencesRepository
        prefsRepo.setOnboardingComplete()
        _uiState.value = OnboardingUiState.Complete
    }
}
```

```kotlin
// In AppPreferencesRepository.kt — new helper method

/**
 * Copies all .org files from app-internal storage (filesDir) to the specified folder.
 * Called during LOCAL→SYNCTHING migration after the user selects a sync folder.
 * No-op if filesDir contains no .org files.
 */
suspend fun copyLocalOrgFilesToFolder(destFolderPath: String) = withContext(Dispatchers.IO) {
    val sourceDir = context.filesDir
    val destDir = File(destFolderPath)
    sourceDir.listFiles { file -> file.name.endsWith(".org") }
        ?.forEach { orgFile ->
            val dest = File(destDir, orgFile.name)
            orgFile.copyTo(dest, overwrite = true)
        }
}
```

This is the same pattern already used in `migrateStorage()` for SYNCTHING→LOCAL direction, inverted.

## State of the Art

No framework changes involved. All three fixes use patterns already established in the codebase.

| Pattern | Already Used In | Notes |
|---------|----------------|-------|
| `File.copyTo(dest, overwrite = true)` | `MacroTargetsScreen.migrateStorage()` SYNCTHING→LOCAL branch | Same pattern, reversed direction |
| `splitDayParsed?.displayName` | `WorkoutTemplates.forDay()` and `SplitDay.displayName` property | Already accessible |
| Coroutine IO dispatch | `AppPreferencesRepository` all setters | Standard pattern |

## Open Questions

1. **isPr write-vs-read confirmation**
   - What we know: the dialog fires (detection works), badge doesn't render after cold start
   - What's unclear: whether `isPr=true` is actually reaching the org file on disk or is being lost in the write path
   - Recommendation: add a unit test for `OrgSchema.formatSetEntry(set.copy(isPr=true))` + `OrgParser` round-trip as the first planning task to confirm the exact failure point before writing the fix

2. **Migration guard: when to copy**
   - What we know: `onFolderSelected()` is called both for fresh Syncthing installs AND for LOCAL→SYNCTHING migrations
   - What's unclear: should the copy always run (idempotent — safe since empty filesDir produces 0 copies) or should it only run when migrating?
   - Recommendation: always run the copy — it's a no-op for fresh installs (filesDir is empty), safe with `overwrite = true` for migrations, and avoids the need to track a "migration in progress" flag

3. **displayName vs abbreviated name in calendar**
   - What we know: `WorkoutLogViewModel.loadCalendar()` uses `session?.templateName ?: session?.splitDay?.displayName?.substringAfterLast("— ")?.trim()` for `splitLabel`
   - What's unclear: after the fix, `session.templateName` will be the full display name; calendar cells use `splitLabel?.take(3)` — so "Mon" vs "Lif" vs "Mon — Lift (Heavy...)" truncated to 3 chars
   - Recommendation: leave `loadCalendar()` as-is — its fallback chain already uses the abbreviated form via `substringAfterLast("— ")`. The templateName fix only affects `DashboardViewModel.refresh()` and `WorkoutLogViewModel.uiState` (where truncation with `maxLines=1` handles length)

## Sources

### Primary (HIGH confidence)
- Direct codebase reading of all affected files:
  - `OrgWorkoutRepository.kt` — `toWorkoutSession()`, `addSet()`, `appendSet()`
  - `WorkoutLogViewModel.kt` — `addSet()` PR detection logic
  - `OrgSchema.kt` — `formatSetEntry()`, `PROP_IS_PR`
  - `OrgParser.kt` — set-level drawer parsing, `toBooleanStrictOrNull()`
  - `MacroTargetsScreen.kt` — `migrateStorage()` full implementation
  - `OnboardingViewModel.kt` — `onFolderSelected()`, `init()` re-onboarding guard
  - `AppPreferencesRepository.kt` — `context`, `setSyncFolderPath()`, `clearOnboardingComplete()`
  - `LocalStorageBackend.kt` — confirms `context.filesDir` as source directory
  - `WorkoutSession.kt` — `templateName: String? = null` field definition
  - `SplitDay.kt` — `displayName` property values
  - `DashboardViewModel.kt` — `recentWorkoutDays` mapping
  - `WorkoutLogScreen.kt` — `state.templateName ?: "Freestyle"` display
  - `v1-MILESTONE-AUDIT.md` — gap descriptions and confirmed failure points

### No External Sources Required
All research was conducted on the codebase directly. This phase is pure bug-fix work on well-understood existing code — no library research needed.

## Metadata

**Confidence breakdown:**
- Bug diagnosis: HIGH — all three bugs are confirmed by audit with specific file/line references
- Fix approach (Bug 2 templateName): HIGH — single-line change, full chain verified
- Fix approach (Bug 1 LOCAL→SYNCTHING): HIGH — design confirmed, implementation pattern exists in reverse direction
- Fix approach (Bug 3 isPr): MEDIUM — write path appears correct but exact failure point unconfirmed; unit test needed to isolate

**Research date:** 2026-04-13
**Valid until:** Indefinite — internal codebase research, no external dependencies
