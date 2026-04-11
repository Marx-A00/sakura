---
phase: 03-workout-logging
plan: 02
subsystem: database
tags: [kotlin, org-mode, workout, data-layer, coroutines, mutex, datastore, json, kotlinx-serialization]

requires:
  - phase: 03-01
    provides: OrgExerciseLog/OrgSetEntry org models, OrgWorkoutRepository skeleton, WorkoutSession/ExerciseLog/SetLog domain models, OrgParser/OrgWriter workout mode

provides:
  - ExerciseCategory enum (5 categories driving UI field selection)
  - ExerciseLibrary singleton with 34 built-in exercises + @Synchronized user exercise mutation
  - ExerciseType expanded with CARDIO and STRETCH, plus toCategory() mapping
  - SetLog with nullable durationMin/distanceKm cardio fields
  - ExerciseDefinition with category and muscleGroups
  - WorkoutRepository interface with incremental addExercise/addSet/removeExercise/removeSet/markComplete/loadPreviousSetsForExercise methods
  - OrgWorkoutRepository implementing all incremental methods with fileMutex.withLock (mirrors OrgFoodRepository.addEntry)
  - WorkoutSession with nullable splitDay for freestyle days and isComplete flag
  - User exercise persistence via DataStore JSON (AppPreferencesRepository.userExercisesJson)
  - OrgParser/OrgWriter support for category, duration_min, distance_km, complete properties
  - 4 new parser tests (cardio fields, freestyle day, complete flag, cardio round-trip)

affects:
  - 03-03-PLAN (workout logging UI — WorkoutDayScreen, ExercisePickerSheet, SetInputSheet; uses all new data layer)

tech-stack:
  added: []
  patterns:
    - "Incremental per-set write: fileMutex.withLock { read → parse → mutate → write } per addSet call, mirroring OrgFoodRepository.addEntry exactly"
    - "Nullable splitDay: WorkoutSession.splitDay is SplitDay? — null means freestyle day, valid session, no template required"
    - "JSON user exercise persistence: LibraryExercise serialized to DataStore stringPreferencesKey via kotlinx.serialization, not a separate org file"
    - "@Synchronized on mutable singleton methods: ExerciseLibrary.addUserExercise and loadUserExercises guard _userExercises MutableList"

key-files:
  created:
    - app/src/main/java/com/sakura/data/workout/ExerciseCategory.kt
    - app/src/main/java/com/sakura/data/workout/ExerciseLibrary.kt
  modified:
    - app/src/main/java/com/sakura/data/workout/ExerciseType.kt
    - app/src/main/java/com/sakura/data/workout/SetLog.kt
    - app/src/main/java/com/sakura/data/workout/ExerciseDefinition.kt
    - app/src/main/java/com/sakura/data/workout/WorkoutTemplates.kt
    - app/src/main/java/com/sakura/data/workout/WorkoutSession.kt
    - app/src/main/java/com/sakura/data/workout/ExerciseLog.kt
    - app/src/main/java/com/sakura/data/workout/WorkoutRepository.kt
    - app/src/main/java/com/sakura/data/workout/OrgWorkoutRepository.kt
    - app/src/main/java/com/sakura/orgengine/OrgModels.kt
    - app/src/main/java/com/sakura/orgengine/OrgSchema.kt
    - app/src/main/java/com/sakura/orgengine/OrgParser.kt
    - app/src/main/java/com/sakura/orgengine/OrgWriter.kt
    - app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt
    - app/src/main/java/com/sakura/di/AppContainer.kt
    - app/src/test/java/com/sakura/orgengine/OrgParserTest.kt

key-decisions:
  - "ExerciseCategory kept as enum (not sealed class) — categories are stable, drive UI behavior; user-creatable things are exercises not categories"
  - "User exercises in DataStore JSON (stringPreferencesKey) not a separate org file — flat list, simple structure, avoids new org format"
  - "Private readFile() helper is suspend — syncBackend.readFile() is a suspend function requiring coroutine context"
  - "Cardio set properties reuse PROP_DURATION_MIN constant — same semantic at different nesting levels (set vs session), context disambiguates"

patterns-established:
  - "Incremental write pattern: suspend fun addX(date, entity): Result<Unit> = fileMutex.withLock { withContext(IO) { read → parse → upsert → write → success } }"
  - "Nullable-safe splitDay: session.splitDay?.label ?: fallback everywhere in UI"

duration: 8min
completed: 2026-04-11
---

# Phase 3 Plan 02: Workout Data Layer (Incremental Writes) Summary

**Incremental per-set workout writes with fileMutex, ExerciseCategory/Library system, cardio support, and nullable freestyle splitDay**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-04-11T10:01:06Z
- **Completed:** 2026-04-11T10:09:00Z
- **Tasks:** 2
- **Files modified:** 17 (2 created, 15 modified)

## Accomplishments

- Replaced atomic saveSession model with incremental per-set writes (addExercise/addSet/removeExercise/removeSet/markComplete), all Mutex-locked
- ExerciseCategory enum + ExerciseLibrary singleton with 34 built-in exercises across WEIGHTED/BODYWEIGHT/TIMED/CARDIO/STRETCH
- WorkoutSession.splitDay is now nullable — freestyle days (no template) are first-class citizens
- SetLog, OrgSetEntry, OrgParser, OrgWriter all support cardio fields (durationMin, distanceKm)
- User exercises persist across restarts via DataStore JSON; ExerciseLibrary.loadUserExercises is @Synchronized

## Task Commits

1. **Task 1: Categories, library, cardio model fields, org schema** - `4f64158` (feat)
2. **Task 2: Incremental repository, nullable splitDay, parser tests** - `6754ae4` (feat)

## Files Created

- `/app/src/main/java/com/sakura/data/workout/ExerciseCategory.kt` — 5-entry enum with fromLabel()
- `/app/src/main/java/com/sakura/data/workout/ExerciseLibrary.kt` — singleton, 34 built-ins, search(), @Synchronized mutation

## Files Modified

- `ExerciseType.kt` — added CARDIO/STRETCH entries, toCategory() extension
- `SetLog.kt` — added nullable durationMin/distanceKm
- `ExerciseDefinition.kt` — added category (default from toCategory()), muscleGroups
- `WorkoutTemplates.kt` — added muscleGroups to all exercise definitions
- `WorkoutSession.kt` — splitDay nullable, templateName, isComplete added
- `ExerciseLog.kt` — category field, totalDurationMin computed property
- `WorkoutRepository.kt` — full interface with 6 new incremental methods + library persistence
- `OrgWorkoutRepository.kt` — complete rewrite with all incremental methods, JSON user exercise persistence
- `OrgModels.kt` — OrgSetEntry cardio fields, OrgExerciseLog category, OrgDateSection complete flag
- `OrgSchema.kt` — PROP_DISTANCE_KM, PROP_CATEGORY, PROP_COMPLETE; updated format functions
- `OrgParser.kt` — parse category, duration_min, distance_km, complete from org files
- `OrgWriter.kt` — pass complete flag to formatWorkoutHeading
- `AppPreferencesRepository.kt` — userExercisesJson DataStore key + save/load methods
- `AppContainer.kt` — pass prefsRepo to OrgWorkoutRepository
- `OrgParserTest.kt` — 4 new tests (cardio, freestyle, complete flag, round-trip); 20 total, all pass

## Decisions Made

- ExerciseCategory is an enum not a sealed class — categories are stable, UI behavior is the driver; future migration to sealed class is straightforward if needed
- User exercises stored as DataStore JSON (not a separate org file) — flat list, simple, avoids creating a new org file format
- Cardio set properties reuse existing PROP_DURATION_MIN constant — context (set vs session nesting) disambiguates meaning
- Private readFile() helper is marked suspend — required because syncBackend.readFile() is a coroutine suspend function

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ExerciseBadge when expression non-exhaustive in WorkoutSessionScreen**
- **Found during:** Task 1 compilation check
- **Issue:** When expression on ExerciseType lacked CARDIO and STRETCH branches; Kotlin enforced exhaustiveness as compile error
- **Fix:** Added `ExerciseType.CARDIO -> "CD"` and `ExerciseType.STRETCH -> "ST"` branches
- **Files modified:** `WorkoutSessionScreen.kt`
- **Verification:** compileDebugKotlin succeeds
- **Committed in:** `4f64158` (Task 1 commit)

**2. [Rule 1 - Bug] Null safety for WorkoutSession.splitDay in existing UI code**
- **Found during:** Task 2 — WorkoutSession.splitDay changed from SplitDay to SplitDay?
- **Issue:** WorkoutLogViewModel (line 250), WorkoutLogScreen (line 278), WorkoutHistoryScreen (lines 105, 139) called .label/.displayName on non-nullable splitDay that is now nullable
- **Fix:** Wrapped with null-safe operators: `?.let { }`, `?.label ?: "freestyle"`, `?.displayName ?: "Freestyle"`
- **Files modified:** `WorkoutLogViewModel.kt`, `WorkoutLogScreen.kt`, `WorkoutHistoryScreen.kt`
- **Verification:** compileDebugKotlin and all 20+10 tests pass
- **Committed in:** `6754ae4` (Task 2 commit)

**3. [Rule 1 - Bug] readFile() helper needed suspend modifier**
- **Found during:** Task 2 compilation check
- **Issue:** Private helper `readFile()` called `syncBackend.readFile()` which is suspend, but helper was not marked suspend
- **Fix:** Added `suspend` to `private suspend fun readFile()`
- **Files modified:** `OrgWorkoutRepository.kt`
- **Verification:** compileDebugKotlin succeeds
- **Committed in:** `6754ae4` (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (all Rule 1 - Bug fixes)
**Impact on plan:** All fixes required for compilation and type correctness. No scope creep.

## Issues Encountered

None beyond the auto-fixed compilation errors above.

## Next Phase Readiness

- Incremental data layer complete — 03-03 (workout logging UI) can now implement WorkoutDayScreen using addExercise/addSet directly
- ExerciseLibrary.search() ready for ExercisePicker — category filter and text search both work
- ExerciseCategory drives UI field selection logic in SetInputSheet (WEIGHTED=weight+reps, BODYWEIGHT=reps, TIMED=hold, CARDIO=duration+distance, STRETCH=duration)
- loadPreviousSetsForExercise() is ready for auto-fill in the set input UI
- No blockers for 03-03

---
*Phase: 03-workout-logging*
*Completed: 2026-04-11*
