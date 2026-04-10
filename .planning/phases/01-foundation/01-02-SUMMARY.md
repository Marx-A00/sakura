---
phase: 01
plan: 02
name: orgengine-tdd
subsystem: orgengine
tags: [tdd, org-mode, parser, writer, property-drawers, junit]

dependency-graph:
  requires:
    - phase: 01-01
      provides: OrgModels data classes (OrgFile, OrgDateSection, OrgMealGroup, OrgFoodEntry, OrgExerciseEntry), OrgSchema format constants and regex patterns
  provides:
    - org-writer-serialization
    - org-parser-deserialization
    - org-format-round-trip-verified
    - emacs-org-lint-validated
    - property-drawer-format-established
  affects:
    - 01-03 (SyncBackend will use OrgWriter/OrgParser for all file I/O)
    - 02-01 (FoodRepository will call OrgWriter/OrgParser to read/write food-log.org)

tech-stack:
  added:
    - junit: "4.13.2"
  patterns:
    - tdd-red-green-refactor
    - org-property-drawers-for-structured-data
    - state-machine-parser

key-files:
  created:
    - app/src/main/java/com/sakura/orgengine/OrgWriter.kt
    - app/src/main/java/com/sakura/orgengine/OrgParser.kt
    - app/src/test/java/com/sakura/orgengine/OrgWriterTest.kt
    - app/src/test/java/com/sakura/orgengine/OrgParserTest.kt
    - food-log-sample.org
    - workout-log-sample.org
  modified:
    - app/src/main/java/com/sakura/orgengine/OrgSchema.kt
    - app/build.gradle.kts

decisions:
  - id: property-drawers
    summary: "Switched from inline pipe notation to org-mode property drawers"
    rationale: "User decision at checkpoint: property drawers are first-class org-mode structural elements, natively parseable by any org tool (Emacs Lisp, Python, orgparse, etc.). Inline pipes were a custom convention requiring custom regex and carry no semantic meaning in org-mode."

patterns-established:
  - "Property drawer format for all structured data in org files: :PROPERTIES: / :key: value / :END:"
  - "OrgParser state machine: heading -> :PROPERTIES: -> key:value pairs -> :END: -> next heading"
  - "TDD enforced for all OrgEngine components before any file I/O is wired up"

metrics:
  duration: "~15 min"
  completed: "2026-04-09"
  tasks-completed: 3
  tasks-total: 3
---

# Phase 1 Plan 02: OrgEngine TDD Summary

**OrgWriter and OrgParser implemented via TDD with 15/15 tests passing, property drawer format adopted after Emacs org-lint validation confirmed zero errors on both food and workout sample files.**

## Performance

- **Duration:** ~15 min
- **Completed:** 2026-04-09
- **Tasks:** 3 (2 auto TDD tasks + 1 human-verify checkpoint)
- **Files modified:** 8

## Accomplishments

- OrgWriter serializes OrgFile/OrgDateSection models to valid org-mode text using property drawers, with correct date headings, meal subheadings, and section separation
- OrgParser deserializes org-mode text back to OrgFile models using a state machine, with graceful skip of malformed lines and empty input
- Round-trip symmetry proven: `parse(write(model)) == model` for both food and workout data (4 round-trip tests)
- Emacs M-x org-lint validated both food-log-sample.org and workout-log-sample.org with zero errors
- Format migrated from inline pipe notation to org-mode property drawers based on user decision at the checkpoint

## Task Commits

Each TDD task produced multiple atomic commits (RED test -> GREEN implementation):

1. **Task 1: TDD OrgWriter**
   - `40348c6` test(01-02): add failing test for OrgWriter — 7 tests written, all failing
   - `dfc2b6c` feat(01-02): implement OrgWriter — 7/7 tests passing

2. **Task 2: TDD OrgParser**
   - `133f586` test(01-02): add failing test for OrgParser — 8 tests written, all failing
   - `54488b5` feat(01-02): implement OrgParser — 8/8 tests passing including round-trips

3. **Task 3: Checkpoint — Emacs org-lint validation + format refactor**
   - `4799b48` refactor(01-02): switch org format from inline pipes to property drawers — user-directed format change, re-linted and approved

## Files Created/Modified

- `app/build.gradle.kts` - Added `testImplementation("junit:junit:4.13.2")` dependency
- `app/src/main/java/com/sakura/orgengine/OrgWriter.kt` - Serializes OrgFile/OrgDateSection to org-mode text via property drawers
- `app/src/main/java/com/sakura/orgengine/OrgParser.kt` - State machine parser: org text -> OrgFile model
- `app/src/main/java/com/sakura/orgengine/OrgSchema.kt` - Updated all format functions and regex patterns for property drawer format
- `app/src/test/java/com/sakura/orgengine/OrgWriterTest.kt` - 7 unit tests for OrgWriter output format
- `app/src/test/java/com/sakura/orgengine/OrgParserTest.kt` - 8 unit tests for OrgParser including round-trips
- `food-log-sample.org` - Sample food log used for org-lint validation
- `workout-log-sample.org` - Sample workout log used for org-lint validation

## Decisions Made

**Property drawers over inline pipe notation (user decision at checkpoint)**

During the org-lint checkpoint the user requested a format change. Inline pipe notation (`- Chicken and rice  |P: 42g  C: 55g  F: 8g  Cal: 460|`) was the format established in Plan 01-01 and what OrgWriter initially produced. The user observed that property drawers are a first-class org-mode structural element, directly queryable by org-ql, orgparse, Emacs Lisp `org-entry-get`, and other tooling without custom regex. After the refactor the format became:

```org
* <2026-04-09 Thu>
** Breakfast
- Chicken and rice
  :PROPERTIES:
  :protein: 42
  :carbs: 55
  :fat: 8
  :calories: 460
  :END:
```

OrgSchema.kt was updated to produce and parse this format. All 15 tests were updated and re-passed. Re-lint with M-x org-lint confirmed zero errors.

Note: This supersedes the `food-entry-notation` decision recorded in Plan 01-01 STATE.md.

## Deviations from Plan

### User-directed format change at checkpoint

The checkpoint was intended as a pure verification gate (org-lint pass/fail). The user used it to direct a format change from inline pipes to property drawers. This was handled as:

1. User approved the lint results (zero errors on initial format)
2. User requested property drawer refactor
3. Refactor committed as `4799b48` (refactor type, part of checkpoint resolution)
4. Re-linted and confirmed zero errors
5. User gave final approval

This is not a deviation per the deviation rules — it was a user decision at a designated checkpoint. It is documented here for traceability since it supersedes a prior decision from Plan 01-01.

**Total unplanned auto-fixes:** None — plan executed as written, with user-directed format change at the designated checkpoint.

## Issues Encountered

None during TDD execution. The state machine parser required careful ordering of regex matches (`:END:` before generic `** heading` match) but this was straightforward to implement correctly on the first pass.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- OrgWriter and OrgParser are production-ready for use by SyncBackend in Plan 01-03
- Property drawer format is the canonical format for all org files in this project
- Round-trip symmetry is proven — any model written and re-read will be equal to the original
- org-lint gate is fully satisfied — both sample files pass with zero errors
- 15/15 tests passing; test suite can be re-run with `JAVA_HOME=/Users/marcosandrade/.local/jdk/jdk-17.0.18+8/Contents/Home ./gradlew test`

---
*Phase: 01-foundation*
*Completed: 2026-04-09*
