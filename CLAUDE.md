# Sakura

Personal food & workout tracking app for Android (Jetpack Compose, Kotlin). Data persists to `.org` files via a sync backend abstraction.

## Build

```
JAVA_HOME=/Users/marcosandrade/.local/jdk/jdk-17.0.18+8/Contents/Home ./gradlew assembleDebug
```

Deploy to device: `./gradlew installDebug` (same JAVA_HOME).

## Ubiquitous Language

Canonical definitions for every domain term. Use these names exactly — avoid the listed aliases.

### Food Logging

| Term | Definition | Aliases to avoid |
|------|-----------|-----------------|
| FoodEntry | A single logged food item with macros (protein, carbs, fat, calories). ID is epoch millis. | Food, Item, LogEntry |
| MealGroup | A labeled collection of FoodEntries for one meal period. Labels are fixed: "Breakfast", "Lunch", "Dinner", "Snacks". | Meal (too vague — could mean a single food or the group), MealSection |
| Meal Label | One of the four canonical strings: "Breakfast", "Lunch", "Dinner", "Snacks". Always title-case. | Category, MealType |
| MacroTargets | User's daily nutrition goals — calories (kcal), protein (g), carbs (g), fat (g). Stored in DataStore. | Goals, Limits, DailyTargets |
| FoodLibraryItem | A reusable saved food with macros. UUID string ID. Used for quick-add. | Favorite, SavedFood |
| MealTemplate | A saved collection of food items that can bulk-applied to a MealGroup. | Preset, SavedMeal |

### Workout Logging

| Term | Definition | Aliases to avoid |
|------|-----------|-----------------|
| ExerciseDefinition | Template definition for an exercise within a WorkoutTemplate. Has targets (sets, reps, hold) and alternatives. | Exercise (ambiguous — could be definition, log, or library item) |
| ExerciseLog | An actual logged exercise for a given day, containing SetLogs. ID is epoch millis. | Exercise, ExerciseEntry |
| SetLog | A single logged set within an ExerciseLog. 1-indexed setNumber, reps, weight, unit, optional RPE/PR/hold/duration/distance. | Set (too generic), Rep |
| ExerciseType | Equipment/modality: BARBELL, DUMBBELL, MACHINE, CALISTHENICS, TIMED, CARDIO, STRETCH. | Type (too vague) |
| ExerciseCategory | Determines which UI input fields appear: WEIGHTED, BODYWEIGHT, TIMED, CARDIO, STRETCH. Derived from ExerciseType. | Type, InputMode |
| LibraryExercise | An exercise available to pick from — either built-in or user-created. Has name, category, muscleGroups. | Exercise |
| SplitDay | A named day in the fixed 4-day cycle: MONDAY_LIFT, TUESDAY_CALISTHENICS, THURSDAY_LIFT, FRIDAY_CALISTHENICS. | Split (too vague), Day |
| WorkoutSession | A complete workout for a date with optional SplitDay, list of ExerciseLogs, duration, completion flag. | Session (too vague), Workout (ambiguous) |
| WorkoutTemplate | Hardcoded template for a SplitDay with predefined ExerciseDefinitions. | Routine, Program, Plan |
| PersonalBest | Best recorded weight, reps, or hold for an exercise across all history. | PR (acceptable shorthand in UI only), Record |
| Volume | Weight × reps summed across sets. 0 for bodyweight/cardio/stretch. | Total, Score |
| Freestyle Day | A workout without a SplitDay assignment (null splitDay). | Custom workout, Unplanned |

### Rest Timer

| Term | Definition | Aliases to avoid |
|------|-----------|-----------------|
| TimerState | Sealed interface: Idle, Running(remainingSecs, totalSecs), Done. | Timer (too vague) |
| RestTimerBridge | Shared singleton StateFlow for timer communication between ViewModel and Service. | TimerManager |

### Storage & Sync

| Term | Definition | Aliases to avoid |
|------|-----------|-----------------|
| SyncBackend | Interface over file storage. Implementations: LocalStorageBackend, SyncthingFileBackend. | Backend (ok as shorthand), FileManager |
| StorageMode | User-selected backend: LOCAL (internal storage) or SYNCTHING (external shared folder). | Mode (too vague) |
| OrgFile | Top-level container for a parsed .org log file (food-log.org or workout-log.org). | File (too generic), Document |
| OrgDateSection | A single date heading in an org log file. Contains meals (food) or exercises (workout). | Section, DateBlock |
| OrgSchema | Centralized format spec — date format ("yyyy-MM-dd EEE", Locale.ENGLISH), property keys, canonical meal labels. | Format, Schema |

### Navigation & UI

| Term | Definition | Aliases to avoid |
|------|-----------|-----------------|
| Sakura Radial Button | The raised cherry blossom branch in the center of the bottom nav bar. Tap = navigate home. Long-press + drag = context-aware radial menu fans out with 3 options based on current tab. | HomeButton, FAB, CenterButton |
| RadialContext | Which set of radial options to show: DEFAULT (nav shortcuts), FOOD (Add Entry / From Library / Food Library), WORKOUT (Add Exercise / From Template / Log Weight). Auto-set by current tab. | MenuMode |
| RadialAction | Specific action triggered from radial menu (e.g., FOOD_ADD_ENTRY, FOOD_LIBRARY, NAV_SETTINGS). | Action (too generic) |

### Key Distinctions

- **"Exercise"** is ambiguous — always qualify as ExerciseDefinition (template), ExerciseLog (logged), or LibraryExercise (library).
- **"Session" vs "Day"**: Old model used session lifecycle (draft/save). Current model is day-based — incremental writes, no session lifecycle. "WorkoutSession" is the data class name but the UX is day-oriented.
- **"Type" vs "Category"**: ExerciseType = what equipment. ExerciseCategory = what UI fields to show. Type maps to Category via `toCategory()`.
- **"Complete" flag**: Soft flag on WorkoutSession. Does NOT delete data, just marks logical completion.
- **"Template"**: In food = MealTemplate (saved food items to bulk-apply). In workout = WorkoutTemplate (predefined exercises for a SplitDay).
