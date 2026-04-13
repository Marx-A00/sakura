---
phase: 06-fix-data-bugs
plan: 01
subsystem: workout
tags: [org-parser, workout-session, template-name, pr-badge, round-trip, unit-test]

# Dependency graph
requires:
  - phase: 03-workout-core
    provides: OrgWorkoutRepository toWorkoutSession(), OrgParser set-level drawer, OrgSchema.formatSetEntry()
  - phase: 05-local-storage-mode
    provides: full AppContainer wiring for cold-start paths
provides:
  - templateName correctly derived from splitDay in toWorkoutSession() after cold start
  - isPr round-trip unit test confirming formatSetEntry -> parse preserves isPr=true
affects:
  - phase: 06-fix-data-bugs/06-02
  - Dashboard display of workout template name

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Derive display-only fields (templateName) from persisted enum fields (splitDay) at parse time"
    - "Round-trip test pattern: format via OrgSchema -> embed in minimal org string -> parse -> assert"

key-files:
  created: []
  modified:
    - app/src/main/java/com/sakura/data/workout/OrgWorkoutRepository.kt
    - app/src/test/java/com/sakura/orgengine/OrgParserTest.kt

key-decisions:
  - "No parser fix needed: PROPERTY_REGEX .+ anchored to $ strips trailing whitespace correctly; isPr round-trip passes without modification"
  - "templateName is derived at parse time (not persisted separately) — single source of truth is splitDay"

patterns-established:
  - "Display-only fields derived from persisted enum at toWorkoutSession() time, not stored redundantly"

# Metrics
duration: 1min
completed: 2026-04-13
---

# Phase 06 Plan 01: Fix Data Bugs (templateName + isPr) Summary

**templateName null after cold start fixed by deriving from splitDayParsed?.displayName in toWorkoutSession(); isPr round-trip confirmed correct by new unit test using OrgSchema.formatSetEntry -> OrgParser**

## Performance

- **Duration:** 1 min
- **Started:** 2026-04-13T17:08:29Z
- **Completed:** 2026-04-13T17:09:30Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Fixed cold-start templateName null bug: WorkoutSession.templateName now populated from splitDayParsed?.displayName in toWorkoutSession(), so Dashboard shows "Monday — Lift (Heavy Compounds)" instead of "Freestyle" after app restart
- Added isPr round-trip unit test (test_parse_workout_isPr_round_trip) that exercises the exact OrgSchema.formatSetEntry -> OrgParser.parse path used by addSet at runtime
- Confirmed no parser bug: PROPERTY_REGEX captures values without trailing whitespace, toBooleanStrictOrNull() receives clean "true"/"false" strings

## Task Commits

1. **Task 1: Fix templateName derivation in toWorkoutSession()** - `7ff8f56` (fix)
2. **Task 2: Verify and fix isPr round-trip persistence** - `a9e55e7` (test)

**Plan metadata:** (pending docs commit)

## Files Created/Modified

- `app/src/main/java/com/sakura/data/workout/OrgWorkoutRepository.kt` - Added `templateName = splitDayParsed?.displayName` to WorkoutSession constructor in toWorkoutSession()
- `app/src/test/java/com/sakura/orgengine/OrgParserTest.kt` - Added Test 20: test_parse_workout_isPr_round_trip verifying isPr=true survives formatSetEntry -> parse

## Decisions Made

- No parser fix required: the isPr round-trip test passed immediately. The PROPERTY_REGEX `^:(\w+):\s+(.+)$` with `$` anchor and `\s+` separator correctly handles whitespace. Values in drawerProperties are clean strings.
- templateName derivation at parse time (not as a persisted field) maintains single source of truth: splitDay label in org file is the canonical store; displayName is ephemeral.

## Deviations from Plan

None - plan executed exactly as written. The isPr test passed on first run, confirming no parser fix was needed (as the plan anticipated).

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 06-02 (next plan in phase) can proceed; WorkoutSession templateName is now correct for all repository reads
- PR badge persistence confirmed correct end-to-end via unit test

---
*Phase: 06-fix-data-bugs*
*Completed: 2026-04-13*
