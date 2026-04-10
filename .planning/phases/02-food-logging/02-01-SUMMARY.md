---
phase: 02-food-logging
plan: 01
subsystem: database
tags: [kotlin, orgmode, coroutines, mutex, datastore, food-tracking]

# Dependency graph
requires:
  - phase: 01-foundation
    provides: SyncBackend (readFile/writeFile), OrgParser/OrgWriter, OrgModels, AppContainer, AppPreferencesRepository
provides:
  - FoodRepository interface (all CRUD for log, library, templates)
  - OrgFoodRepository (org-file-backed implementation, Mutex-serialized mutations)
  - FoodEntry, MealGroup, FoodLibraryItem, MealTemplate domain models
  - OrgFoodEntry with id/servingSize/servingUnit/notes fields
  - OrgLibraryEntry, OrgLibraryFile, OrgTemplateFile, OrgMealTemplate org models
  - OrgSchema format functions for food-library.org and meal-templates.org
  - OrgParser.parseLibrary() and OrgParser.parseTemplates()
  - OrgWriter.writeLibrary() and OrgWriter.writeTemplates()
  - MacroTargets data class + flows/setters in AppPreferencesRepository
  - FoodLog and Settings routes in Routes.kt
  - foodRepository wired into AppContainer
affects:
  - 02-02 (food logging UI — depends entirely on FoodRepository and domain models)
  - 02-03 (food library/template screens)
  - 03-xx (workout phase — AppContainer pattern established here)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "FoodRepository interface + OrgFoodRepository implementation (interface/impl split for data layer)"
    - "fileMutex (kotlinx.coroutines.sync.Mutex) for serializing concurrent file mutations"
    - "addEntryInternal() pattern: internal method assumes mutex held, applyTemplate acquires once for batch"
    - "Separate OrgLibraryEntry model for UUID-keyed items (vs epoch-millis-keyed OrgFoodEntry for log entries)"
    - "MacroTargets convenience data class + combined Flow via combine()"

key-files:
  created:
    - app/src/main/java/com/sakura/data/food/FoodEntry.kt
    - app/src/main/java/com/sakura/data/food/MealGroup.kt
    - app/src/main/java/com/sakura/data/food/FoodLibraryItem.kt
    - app/src/main/java/com/sakura/data/food/MealTemplate.kt
    - app/src/main/java/com/sakura/data/food/FoodRepository.kt
    - app/src/main/java/com/sakura/data/food/OrgFoodRepository.kt
  modified:
    - app/src/main/java/com/sakura/orgengine/OrgModels.kt
    - app/src/main/java/com/sakura/orgengine/OrgSchema.kt
    - app/src/main/java/com/sakura/orgengine/OrgParser.kt
    - app/src/main/java/com/sakura/orgengine/OrgWriter.kt
    - app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt
    - app/src/main/java/com/sakura/navigation/Routes.kt
    - app/src/main/java/com/sakura/di/AppContainer.kt

key-decisions:
  - "OrgLibraryEntry separate from OrgFoodEntry: library items use UUID String ids; log entries use epoch millis Long ids — can't share one model without a String id field on OrgFoodEntry"
  - "applyTemplate acquires fileMutex once for the full batch and calls addEntryInternal() to avoid per-entry mutex acquisition overhead"
  - "OrgFoodEntry.id defaults to 0L for backward compatibility with all existing test data and legacy log files that predate this field"
  - "SyncthingFileBackend returns empty string for missing files — no NotFound exception needed; parsers return empty results on blank content"

patterns-established:
  - "Domain model split: OrgXxx (org-file layer) and domain Xxx (business logic layer) are separate classes with explicit conversion functions"
  - "All FoodRepository mutations: fileMutex.withLock { withContext(ioDispatcher) { ... } }"
  - "Read operations do not acquire mutex (reads are idempotent)"
  - "Result<Unit> return for all mutation operations; read operations return data directly"

# Metrics
duration: 8min
completed: 2026-04-10
---

# Phase 2 Plan 1: Food Data Layer Summary

**FoodRepository interface + OrgFoodRepository backed by food-log.org, food-library.org, meal-templates.org with Mutex-serialized mutations and MacroTargets in DataStore**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-04-10T23:35:48Z
- **Completed:** 2026-04-10T23:43:30Z
- **Tasks:** 2
- **Files modified:** 13 files (6 created, 7 modified)

## Accomplishments

- Complete food data layer: 5 domain model files + FoodRepository interface + OrgFoodRepository implementation
- Updated OrgEngine for Phase 2: new OrgFoodEntry fields (id, servingSize, servingUnit, notes), OrgLibraryEntry, library/template parsers and writers, new OrgSchema format functions
- Macro target preferences wired into DataStore with defaults (2000 cal / 150g protein / 250g carbs / 65g fat)
- AppContainer wired with foodRepository; FoodLog + Settings routes added

## Task Commits

Each task was committed atomically:

1. **Task 1: Domain models, OrgSchema updates, macro target preferences** - `1723de8` (feat)
2. **Task 2: OrgFoodRepository implementation and AppContainer wiring** - `dcf9635` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `app/src/main/java/com/sakura/data/food/FoodEntry.kt` - Domain model for a single food log entry (id, macros, serving info, notes)
- `app/src/main/java/com/sakura/data/food/MealGroup.kt` - Domain model for a meal grouping with computed macro totals
- `app/src/main/java/com/sakura/data/food/FoodLibraryItem.kt` - Domain model for a saved library item (UUID id)
- `app/src/main/java/com/sakura/data/food/MealTemplate.kt` - Domain model for a saved meal template
- `app/src/main/java/com/sakura/data/food/FoodRepository.kt` - Interface defining all food data operations
- `app/src/main/java/com/sakura/data/food/OrgFoodRepository.kt` - Org-file-backed implementation with fileMutex, all 12 methods
- `app/src/main/java/com/sakura/orgengine/OrgModels.kt` - Added id/servingSize/servingUnit/notes to OrgFoodEntry; added OrgLibraryEntry, OrgLibraryFile, OrgTemplateFile, OrgMealTemplate
- `app/src/main/java/com/sakura/orgengine/OrgSchema.kt` - Added PROP_ID/SERVING_SIZE/SERVING_UNIT/NOTES constants, formatFoodEntry updated, formatLibraryEntry/formatTemplateHeading/formatTemplateItem added
- `app/src/main/java/com/sakura/orgengine/OrgParser.kt` - Updated FOOD mode to read new property keys; added parseLibrary() and parseTemplates()
- `app/src/main/java/com/sakura/orgengine/OrgWriter.kt` - Added writeLibrary() and writeTemplates()
- `app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt` - Added macro target keys, flows, setters, MacroTargets data class
- `app/src/main/java/com/sakura/navigation/Routes.kt` - Added FoodLog and Settings routes
- `app/src/main/java/com/sakura/di/AppContainer.kt` - Added foodRepository: FoodRepository = OrgFoodRepository(syncBackend)

## Decisions Made

- **OrgLibraryEntry separate from OrgFoodEntry:** library items use UUID String ids; log entries use epoch millis Long ids. Reusing OrgFoodEntry would require adding a String id field which would break the clean type boundary. A dedicated `OrgLibraryEntry` model keeps the types correct.
- **applyTemplate mutex pattern:** acquires fileMutex once for the full batch, calls internal `addEntryInternal()` per item. Avoids per-entry lock overhead and ensures atomicity of the entire template application.
- **OrgFoodEntry.id defaults to 0L:** ensures all existing tests and any legacy food-log.org files without `:id:` in drawers continue to work without modification.
- **SyncthingFileBackend returns "" for missing files:** no `NotFound` exception path needed; all parsers return empty results for blank content.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] OrgLibraryFile used OrgFoodEntry which has Long id — incompatible with UUID strings**

- **Found during:** Task 1 (OrgModels/OrgSchema/OrgParser/OrgWriter design)
- **Issue:** The plan specified `OrgLibraryFile(val items: List<OrgFoodEntry>)` but OrgFoodEntry.id is `Long` (epoch millis). Library items use UUID string ids like `"a1b2c3d4"`. Storing a UUID string in a Long field via `toLongOrNull()` silently loses the UUID identity (returns 0L).
- **Fix:** Introduced a separate `OrgLibraryEntry(id: String, ...)` model. OrgLibraryFile uses `List<OrgLibraryEntry>`. OrgMealTemplate.items also uses `List<OrgLibraryEntry>`. Conversion functions `toFoodLibraryItem()` and `toOrgLibraryEntry()` bridge the gap.
- **Files modified:** OrgModels.kt, OrgParser.kt, OrgWriter.kt, OrgFoodRepository.kt
- **Verification:** Build succeeds; parseLibrary test correctly asserts `item.id == "a1b2c3d4"`
- **Committed in:** 1723de8 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - type correctness bug in model design)
**Impact on plan:** Fix was required for correct identity management of library items. No scope creep; all other plan outputs delivered exactly as specified.

## Issues Encountered

None beyond the deviation documented above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Food data layer is complete and tested. Plan 02-02 (food logging UI) can be built directly on FoodRepository.
- All three org files are handled: food-log.org, food-library.org, meal-templates.org.
- MacroTargets DataStore keys are ready for use in the UI's progress bar/ring component.
- No blockers.

---
*Phase: 02-food-logging*
*Completed: 2026-04-10*
