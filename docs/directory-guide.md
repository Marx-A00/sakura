# Directory & File Guide

All source code lives under `app/src/main/java/com/sakura/`. Here's every directory and file, with what it does.

## Top-Level Files

- [**SakuraApplication.kt**](../app/src/main/java/com/sakura/SakuraApplication.kt) — App entry point. Creates `AppPreferencesRepository` on startup. Like your `index.js` in a React app.
- [**MainActivity.kt**](../app/src/main/java/com/sakura/MainActivity.kt) — The single Activity (Android's "window"). Reads theme + onboarding state from DataStore, creates the DI container, mounts `AppNavHost`. Like your root `App.tsx`.

## `data/` — Domain Models

These are your TypeScript interfaces — plain data classes that define what things look like.

### `data/food/`

- [**FoodEntry.kt**](../app/src/main/java/com/sakura/data/food/FoodEntry.kt) — A single logged food item. ID is epoch millis. Has name, protein, carbs, fat, calories, optional serving info and notes.
- [**MealGroup.kt**](../app/src/main/java/com/sakura/data/food/MealGroup.kt) — A labeled collection of FoodEntries (e.g., "Breakfast"). Has computed totals for all macros.
- [**FoodLibraryItem.kt**](../app/src/main/java/com/sakura/data/food/FoodLibraryItem.kt) — A reusable saved food for quick-add. UUID string ID.
- [**MealTemplate.kt**](../app/src/main/java/com/sakura/data/food/MealTemplate.kt) — A saved collection of food items that can be bulk-applied to a meal.
- [**FoodRepository.kt**](../app/src/main/java/com/sakura/data/food/FoodRepository.kt) — Interface defining all food CRUD operations. Like a TypeScript interface for your API service.
- [**OrgFoodRepository.kt**](../app/src/main/java/com/sakura/data/food/OrgFoodRepository.kt) — Implementation of `FoodRepository` that reads/writes .org files via `SyncBackend`. This is the actual "backend" logic.

### `data/workout/`

- [**WorkoutSession.kt**](../app/src/main/java/com/sakura/data/workout/WorkoutSession.kt) — A complete workout for a date. Contains ExerciseLogs, optional SplitDay, duration, completion flag.
- [**ExerciseLog.kt**](../app/src/main/java/com/sakura/data/workout/ExerciseLog.kt) — A single logged exercise with its SetLogs. ID is epoch millis.
- [**SetLog.kt**](../app/src/main/java/com/sakura/data/workout/SetLog.kt) — One set within an exercise — reps, weight, unit, optional RPE/hold/duration/distance.
- [**ExerciseType.kt**](../app/src/main/java/com/sakura/data/workout/ExerciseType.kt) — Enum for equipment/modality: BARBELL, DUMBBELL, MACHINE, CALISTHENICS, TIMED, CARDIO, STRETCH. Also has the `toCategory()` extension function.
- [**ExerciseCategory.kt**](../app/src/main/java/com/sakura/data/workout/ExerciseCategory.kt) — Enum that determines which UI input fields appear: WEIGHTED, BODYWEIGHT, TIMED, CARDIO, STRETCH.
- [**ExerciseDefinition.kt**](../app/src/main/java/com/sakura/data/workout/ExerciseDefinition.kt) — Template definition for an exercise within a WorkoutTemplate. Has target sets/reps/hold and alternative exercises.
- [**ExerciseLibrary.kt**](../app/src/main/java/com/sakura/data/workout/ExerciseLibrary.kt) — Built-in exercise library with predefined exercises for each muscle group.
- [**SplitDay.kt**](../app/src/main/java/com/sakura/data/workout/SplitDay.kt) — Enum for the fixed 4-day cycle: MONDAY_LIFT, TUESDAY_CALISTHENICS, THURSDAY_LIFT, FRIDAY_CALISTHENICS.
- [**WorkoutTemplate.kt**](../app/src/main/java/com/sakura/data/workout/WorkoutTemplate.kt) — Data class for a template that maps to a SplitDay.
- [**WorkoutTemplates.kt**](../app/src/main/java/com/sakura/data/workout/WorkoutTemplates.kt) — Singleton holding all 4 hardcoded split day templates.
- [**PersonalBest.kt**](../app/src/main/java/com/sakura/data/workout/PersonalBest.kt) — Best recorded weight/reps/hold for an exercise across all history.
- [**WorkoutRepository.kt**](../app/src/main/java/com/sakura/data/workout/WorkoutRepository.kt) — Interface for all workout CRUD operations.
- [**OrgWorkoutRepository.kt**](../app/src/main/java/com/sakura/data/workout/OrgWorkoutRepository.kt) — Implementation that reads/writes `workout-log.org`.

## `di/` — Dependency Injection

- [**AppContainer.kt**](../app/src/main/java/com/sakura/di/AppContainer.kt) — Manual DI container. Constructs all app dependencies based on the active StorageMode. Like a React context provider that holds all your services. See [architecture.md](architecture.md#dependency-injection--appcontainer).

## `features/` — Feature Modules

Each subdirectory is a feature screen with its own Screen, ViewModel, and UiState. Like organizing React components into feature folders.

### `features/dashboard/` — Home Tab

- [**DashboardScreen.kt**](../app/src/main/java/com/sakura/features/dashboard/DashboardScreen.kt) — Main home screen layout with summary cards.
- [**DashboardViewModel.kt**](../app/src/main/java/com/sakura/features/dashboard/DashboardViewModel.kt) — Aggregates today's food/workout data, weekly analytics, sync status.
- [**DashboardUiState.kt**](../app/src/main/java/com/sakura/features/dashboard/DashboardUiState.kt) — State model for the dashboard.
- [**FoodProgressCard.kt**](../app/src/main/java/com/sakura/features/dashboard/FoodProgressCard.kt) — Card showing today's macro progress vs targets.
- [**FoodWeeklyCard.kt**](../app/src/main/java/com/sakura/features/dashboard/FoodWeeklyCard.kt) — Card showing weekly calorie/macro trends.
- [**WorkoutSummaryCard.kt**](../app/src/main/java/com/sakura/features/dashboard/WorkoutSummaryCard.kt) — Card showing today's workout overview.
- [**WorkoutVolumeCard.kt**](../app/src/main/java/com/sakura/features/dashboard/WorkoutVolumeCard.kt) — Card showing weekly volume metrics.
- [**SyncStatusBadge.kt**](../app/src/main/java/com/sakura/features/dashboard/SyncStatusBadge.kt) — Indicator for Syncthing sync status and conflict detection.

### `features/foodlog/` — Food Log Tab

- [**FoodLogScreen.kt**](../app/src/main/java/com/sakura/features/foodlog/FoodLogScreen.kt) — Main food logging UI. Shows date navigation, calendar, collapsible meal sections with entries.
- [**FoodLogViewModel.kt**](../app/src/main/java/com/sakura/features/foodlog/FoodLogViewModel.kt) — Date navigation, CRUD operations, draft field persistence, calendar loading, library data.
- [**FoodLogUiState.kt**](../app/src/main/java/com/sakura/features/foodlog/FoodLogUiState.kt) — Sealed interface: Loading, Success (with meals + targets), Error variants.
- [**FoodCalendar.kt**](../app/src/main/java/com/sakura/features/foodlog/FoodCalendar.kt) — 4-week rolling calendar grid with dots showing which days have food logged.
- [**FoodEntryBottomSheet.kt**](../app/src/main/java/com/sakura/features/foodlog/FoodEntryBottomSheet.kt) — Bottom sheet form for adding/editing a food entry (name, macros, serving info).
- [**FoodLibraryBottomSheet.kt**](../app/src/main/java/com/sakura/features/foodlog/FoodLibraryBottomSheet.kt) — Bottom sheet for quick-adding from saved library items and templates.

### `features/foodlibrary/` — Food Library Screen

- [**FoodLibraryScreen.kt**](../app/src/main/java/com/sakura/features/foodlibrary/FoodLibraryScreen.kt) — Browse, search, and manage saved food items.
- [**FoodLibraryViewModel.kt**](../app/src/main/java/com/sakura/features/foodlibrary/FoodLibraryViewModel.kt) — CRUD operations for library items.

### `features/workoutlog/` — Workout Log Tab

- [**WorkoutLogScreen.kt**](../app/src/main/java/com/sakura/features/workoutlog/WorkoutLogScreen.kt) — Main workout logging UI. Exercise list, set-by-set input, split day navigation.
- [**WorkoutLogViewModel.kt**](../app/src/main/java/com/sakura/features/workoutlog/WorkoutLogViewModel.kt) — Session loading, exercise CRUD, set management, timer bridge, incremental writes.
- [**WorkoutLogUiState.kt**](../app/src/main/java/com/sakura/features/workoutlog/WorkoutLogUiState.kt) — State sealed class for workout screen.
- [**ExercisePickerSheet.kt**](../app/src/main/java/com/sakura/features/workoutlog/ExercisePickerSheet.kt) — Bottom sheet to pick exercises from the library or templates.
- [**SetInputSheet.kt**](../app/src/main/java/com/sakura/features/workoutlog/SetInputSheet.kt) — Bottom sheet for entering set-level data (weight, reps, RPE, etc).
- [**ScrollWheelPicker.kt**](../app/src/main/java/com/sakura/features/workoutlog/ScrollWheelPicker.kt) — Custom scrollable number picker for weight/reps input.
- [**SplitCalendar.kt**](../app/src/main/java/com/sakura/features/workoutlog/SplitCalendar.kt) — Calendar with split day indicators for the 4-day training cycle.
- [**RestTimerService.kt**](../app/src/main/java/com/sakura/features/workoutlog/RestTimerService.kt) — Android foreground service that runs the rest timer with notifications.
- [**TimerAdjustSheet.kt**](../app/src/main/java/com/sakura/features/workoutlog/TimerAdjustSheet.kt) — Bottom sheet for modifying timer duration mid-workout.
- [**WorkoutHistoryScreen.kt**](../app/src/main/java/com/sakura/features/workoutlog/WorkoutHistoryScreen.kt) — Browse past workout sessions.

### `features/progress/` — Progress Tab

- [**ProgressScreen.kt**](../app/src/main/java/com/sakura/features/progress/ProgressScreen.kt) — Charts and analytics for food/workout history.
- [**ProgressViewModel.kt**](../app/src/main/java/com/sakura/features/progress/ProgressViewModel.kt) — Historical data aggregation for charts.
- [**ProgressUiState.kt**](../app/src/main/java/com/sakura/features/progress/ProgressUiState.kt) — State model for progress screen.

### `features/settings/` — Settings Tab

- [**SettingsScreen.kt**](../app/src/main/java/com/sakura/features/settings/SettingsScreen.kt) — App preferences: theme mode, storage mode, rest timer settings.
- [**MacroTargetsScreen.kt**](../app/src/main/java/com/sakura/features/settings/MacroTargetsScreen.kt) — Configure daily nutrition goals (calories, protein, carbs, fat).

### `features/onboarding/` — First Launch

- [**OnboardingScreen.kt**](../app/src/main/java/com/sakura/features/onboarding/OnboardingScreen.kt) — First-launch flow for storage mode selection.
- [**OnboardingViewModel.kt**](../app/src/main/java/com/sakura/features/onboarding/OnboardingViewModel.kt) — Handles storage mode selection and onboarding completion flag.

### `features/loading/` — Loading Animation

- [**LoadingDemoActivity.kt**](../app/src/main/java/com/sakura/features/loading/LoadingDemoActivity.kt) — Separate Activity for splash screen prototype.
- [**SakuraLoadingAnimation.kt**](../app/src/main/java/com/sakura/features/loading/SakuraLoadingAnimation.kt) — Animated sakura petal loading animation.

## `navigation/` — Routing & Nav

- [**Routes.kt**](../app/src/main/java/com/sakura/navigation/Routes.kt) — Type-safe route definitions. Each route is a `@Serializable object`. Like your route path constants in React Router.
- [**AppNavHost.kt**](../app/src/main/java/com/sakura/navigation/AppNavHost.kt) — Navigation host with bottom bar, route-to-screen mapping, and ViewModel factory injection. Like your `<Routes>` + `<Route>` setup.
- [**RadialMenu.kt**](../app/src/main/java/com/sakura/navigation/RadialMenu.kt) — The cherry blossom branch center button with context-aware radial menu (long-press + drag to select actions). Includes `RadialContext`, `RadialAction` enums and gesture handling.

## `orgengine/` — Org File Parser & Writer

- [**OrgModels.kt**](../app/src/main/java/com/sakura/orgengine/OrgModels.kt) — Data classes for the org file AST: `OrgFile`, `OrgDateSection`, `OrgMealGroup`, `OrgFoodEntry`, `OrgExerciseLog`, `OrgSetEntry`, etc. These are the intermediate representation between raw text and domain models.
- [**OrgSchema.kt**](../app/src/main/java/com/sakura/orgengine/OrgSchema.kt) — Single source of truth for the org file format: date formatters, heading regexes, property key constants, serialization functions. Both parser and writer reference this exclusively.
- [**OrgParser.kt**](../app/src/main/java/com/sakura/orgengine/OrgParser.kt) — State machine that deserializes org text into `OrgFile` models. Supports FOOD and WORKOUT parse modes.
- [**OrgWriter.kt**](../app/src/main/java/com/sakura/orgengine/OrgWriter.kt) — Serializes domain models back to org text. Handles incremental updates (find + replace sections).

See [org-engine.md](org-engine.md) for a deep dive.

## `preferences/` — User Settings

- [**AppPreferencesRepository.kt**](../app/src/main/java/com/sakura/preferences/AppPreferencesRepository.kt) — Jetpack DataStore wrapper for all user settings: storage mode, onboarding flag, macro targets, theme, rest timer config, user exercise library. All reads are exposed as `Flow<T>` (reactive streams), all writes are `suspend` functions. Also defines `StorageMode` enum and `MacroTargets` data class.

## `sync/` — Storage Backend

- [**SyncBackend.kt**](../app/src/main/java/com/sakura/sync/SyncBackend.kt) — Interface defining file I/O operations: readFile, writeFile, fileExists, listOrgFiles, checkSyncStatus. Also defines `SyncStatus` data class.
- [**LocalStorageBackend.kt**](../app/src/main/java/com/sakura/sync/LocalStorageBackend.kt) — Implementation using Android internal storage. Atomic writes. No permissions needed. For non-technical users.
- [**SyncthingFileBackend.kt**](../app/src/main/java/com/sakura/sync/SyncthingFileBackend.kt) — Implementation using an external shared folder synced by Syncthing. Handles conflict detection and folder accessibility checks.
- [**SyncBackendError.kt**](../app/src/main/java/com/sakura/sync/SyncBackendError.kt) — Sealed class for storage errors: FolderUnavailable, PermissionDenied, Generic.

## `ui/theme/` — Visual Design

- [**Color.kt**](../app/src/main/java/com/sakura/ui/theme/Color.kt) — All color definitions. Rose/sakura pinks for primary, dark surfaces for dark mode, warm creams for light mode, semantic colors (ForestGreen for protein, WarmBrown for carbs, WorkoutBlue for workout metrics).
- [**Theme.kt**](../app/src/main/java/com/sakura/ui/theme/Theme.kt) — `SakuraTheme` composable that wraps the app in Material 3 theming. Supports DARK, LIGHT, SYSTEM modes.
- [**Type.kt**](../app/src/main/java/com/sakura/ui/theme/Type.kt) — Typography definitions for the Material 3 type scale.
