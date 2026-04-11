---
phase: 03-workout-logging
plan: 01
subsystem: data
tags: [kotlin, android, org-mode, workout, data-layer, repository-pattern]

# Dependency graph
requires:
  - phase: 02-food-logging
    provides: OrgFoodRepository pattern (Mutex, read-modify-write, SyncBackend), OrgModels, OrgParser/OrgWriter, AppContainer, DataStore prefs
provides:
  - WorkoutRepository interface with saveSession/loadSession/loadHistory/findPersonalBest/loadLastSessionForSplitDay
  - OrgWorkoutRepository backed by workout-log.org via SyncBackend with Mutex-serialized writes
  - Full data/workout/ domain model package (ExerciseType, SplitDay, ExerciseDefinition, WorkoutTemplate, WorkoutTemplates, SetLog, ExerciseLog, WorkoutSession, PersonalBest)
  - WorkoutTemplates encoding all 4 split days from 03-CONTEXT.md
  - OrgExerciseLog + OrgSetEntry models; OrgDateSection extended with exerciseLogs/splitDay/volume/durationMin fields
  - OrgParser WORKOUT mode with level-3 exercise + level-4 set parsing and backward compat for old flat format
  - OrgWriter exerciseLogs branch (Phase 3 format) alongside legacy exercises branch
  - OrgSchema new constants: PROP_HOLD_SECS/RPE/IS_PR/EXERCISE_TYPE/SPLIT_DAY/VOLUME/DURATION_MIN; WORKOUT_HEADING_REGEX; SET_HEADING_REGEX
  - OrgSchema new format functions: formatWorkoutHeading, formatExerciseLog, formatSetEntry
  - DataStore prefs: LAST_WORKOUT_SPLIT_DAY, LAST_WORKOUT_DATE, DEFAULT_REST_TIMER_SECS
  - Routes: WorkoutLog, WorkoutSession, WorkoutHistory
  - AppContainer: workoutRepository wired
affects:
  - 03-02 (workout UI — depends on WorkoutRepository, WorkoutTemplates, domain models)
  - future phases using workout history or PR data

# Tech tracking
tech-stack:
  added: []
  patterns:
    - OrgWorkoutRepository follows identical Mutex + read-modify-write pattern as OrgFoodRepository
    - OrgExerciseLog/OrgSetEntry at org levels 3+4 (exercise heading / set heading)
    - Backward compat parsing: old flat format synthesizes single-set OrgExerciseLog
    - findPersonalBest returns null on no history to prevent PR false positives on first session
    - Normalized lowercase name comparison in findPersonalBest to handle case/spacing variation

key-files:
  created:
    - app/src/main/java/com/sakura/data/workout/ExerciseType.kt
    - app/src/main/java/com/sakura/data/workout/SplitDay.kt
    - app/src/main/java/com/sakura/data/workout/ExerciseDefinition.kt
    - app/src/main/java/com/sakura/data/workout/WorkoutTemplate.kt
    - app/src/main/java/com/sakura/data/workout/WorkoutTemplates.kt
    - app/src/main/java/com/sakura/data/workout/SetLog.kt
    - app/src/main/java/com/sakura/data/workout/ExerciseLog.kt
    - app/src/main/java/com/sakura/data/workout/WorkoutSession.kt
    - app/src/main/java/com/sakura/data/workout/PersonalBest.kt
    - app/src/main/java/com/sakura/data/workout/WorkoutRepository.kt
    - app/src/main/java/com/sakura/data/workout/OrgWorkoutRepository.kt
  modified:
    - app/src/main/java/com/sakura/orgengine/OrgModels.kt
    - app/src/main/java/com/sakura/orgengine/OrgSchema.kt
    - app/src/main/java/com/sakura/orgengine/OrgParser.kt
    - app/src/main/java/com/sakura/orgengine/OrgWriter.kt
    - app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt
    - app/src/main/java/com/sakura/di/AppContainer.kt
    - app/src/main/java/com/sakura/navigation/Routes.kt
    - app/src/test/java/com/sakura/orgengine/OrgParserTest.kt

key-decisions:
  - "OrgExerciseLog + OrgSetEntry at org levels 3+4 instead of flattening sets; backward compat via synthesized single-set when old :sets: property found"
  - "findPersonalBest returns null on no prior history (not PersonalBest(0,0,0)) — prevents PR false positives on first session for any exercise"
  - "OrgDateSection.toWorkoutSession() returns null when splitDay is null — ensures legacy sections without split day metadata don't surface as WorkoutSessions"
  - "exerciseLogs branch takes priority over exercises branch in OrgWriter (Phase 3 format preferred over legacy)"

patterns-established:
  - "OrgWorkoutRepository: fileMutex.withLock { withContext(ioDispatcher) { ... } } for all mutations — same pattern as OrgFoodRepository"
  - "WorkoutSession.toOrgDateSection() and OrgDateSection.toWorkoutSession() private extension functions for clean domain <-> org conversion"
  - "upsertSection(): find-or-insert with date-descending sort order — mirrors upsertEntry() in OrgFoodRepository"

# Metrics
duration: 5min
completed: 2026-04-11
---

# Phase 3 Plan 01: Workout Data Layer Summary

**Per-set workout org format (OrgExerciseLog + OrgSetEntry at levels 3+4), full domain model package, OrgWorkoutRepository with Mutex, WorkoutTemplates encoding all 4 split days, and backward-compat parsing of legacy flat entries**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-11T07:38:40Z
- **Completed:** 2026-04-11T07:43:40Z
- **Tasks:** 2
- **Files modified:** 19

## Accomplishments

- Complete data/workout/ package: 10 domain model files + WorkoutRepository interface + OrgWorkoutRepository implementation
- WorkoutTemplates encodes all 4 split days (Monday Lift, Tuesday Calisthenics, Thursday Lift, Friday Calisthenics) with all exercises, alternatives, target sets/reps/hold times from 03-CONTEXT.md
- OrgEngine extended with per-set workout format: OrgExerciseLog (level-3) + OrgSetEntry (level-4), new property constants, WORKOUT_HEADING_REGEX, SET_HEADING_REGEX, formatWorkoutHeading/formatExerciseLog/formatSetEntry
- OrgParser WORKOUT mode fully rewritten with multi-level state machine; backward compat path for old flat format
- 5 new OrgParserTest cases; all 24 tests pass after both tasks

## Task Commits

1. **Task 1: Domain models, workout templates, OrgEngine extensions, and split day preferences** - `fc57ff8` (feat)
2. **Task 2: OrgWorkoutRepository implementation and AppContainer wiring** - `975f353` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `app/src/main/java/com/sakura/data/workout/` (11 new files) - Complete workout domain layer
- `app/src/main/java/com/sakura/orgengine/OrgModels.kt` - OrgExerciseLog, OrgSetEntry added; OrgDateSection extended
- `app/src/main/java/com/sakura/orgengine/OrgSchema.kt` - New workout constants, regexes, format functions
- `app/src/main/java/com/sakura/orgengine/OrgParser.kt` - WORKOUT mode rewritten with level-3/4 state machine
- `app/src/main/java/com/sakura/orgengine/OrgWriter.kt` - exerciseLogs branch added before legacy exercises branch
- `app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt` - LAST_WORKOUT_SPLIT_DAY/DATE, DEFAULT_REST_TIMER_SECS
- `app/src/main/java/com/sakura/di/AppContainer.kt` - workoutRepository wired
- `app/src/main/java/com/sakura/navigation/Routes.kt` - WorkoutLog, WorkoutSession, WorkoutHistory routes
- `app/src/test/java/com/sakura/orgengine/OrgParserTest.kt` - 5 new Phase 3 workout tests

## Decisions Made

- `OrgExerciseLog + OrgSetEntry` at org levels 3+4 (not flattened): enables per-set logging, PR tracking, and timed hold data without changing the date/session heading structure
- `findPersonalBest` returns `null` (not `PersonalBest(0,0,0)`) when no prior history exists — caller shows no PR notification on first session for any exercise
- `OrgDateSection.toWorkoutSession()` returns `null` when `splitDay` is missing — prevents legacy sections (pre-Phase 3) without split day metadata from surfacing as WorkoutSessions
- Backward compat via synthesized single `OrgSetEntry` when old `:sets:` property found on exercise drawer — old workout-log.org entries parse into the new model without any file migration

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. Both tasks compiled cleanly on first attempt.

## Next Phase Readiness

- Workout data layer is complete and ready for 03-02 (workout UI)
- WorkoutRepository, WorkoutTemplates, and all domain models are available from AppContainer
- OrgParser/OrgWriter round-trip verified with 5 new tests covering Phase 3 format, backward compat, timed exercises, and write structure
- Blocker from STATE.md resolved: user's 4-day split is encoded in WorkoutTemplates from 03-CONTEXT.md

---
*Phase: 03-workout-logging*
*Completed: 2026-04-11*
