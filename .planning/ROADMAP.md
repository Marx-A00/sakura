# Roadmap: Sakura

## Overview

Sakura is a personal Android app (Kotlin + Jetpack Compose) for logging food and workouts to org-mode plain text files that sync to an Emacs/org-roam setup via Syncthing. The roadmap delivers in four phases: a validated org file engine and sync foundation, full food tracking end-to-end, full workout tracking end-to-end, and a unified dashboard with analytics. Every data feature depends on the org engine being correct from day one — that is the singular risk this ordering protects against.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Foundation** - Device setup, org engine, file layer, and onboarding
- [ ] **Phase 2: Food Logging** - Complete food tracking end-to-end with org file output
- [ ] **Phase 3: Workout Logging** - Complete workout tracking with templates and auto-fill
- [ ] **Phase 4: Dashboard and Polish** - Unified home screen, history views, and analytics
- [ ] **Phase 5: Local Storage Mode** - Non-technical user support with zero-config local storage

## Phase Details

### Phase 1: Foundation
**Goal**: The app builds, deploys to the Galaxy S21 FE, can read and write valid org files in the Syncthing folder, and onboards the user through permissions and folder selection.
**Depends on**: Nothing (first phase)
**Requirements**: FOUND-01, FOUND-02, FOUND-03, FOUND-04, FOUND-05, FOUND-06, SYNC-01, SYNC-02, SYNC-03, SYNC-06
**Success Criteria** (what must be TRUE):
  1. App installs via ADB on Galaxy S21 FE and launches without crashing
  2. Onboarding flow requests MANAGE_EXTERNAL_STORAGE permission and lets user select the Syncthing folder path; selected path persists across restarts
  3. A food entry written by the app appears in food-log.org under the correct date heading and passes `M-x org-lint` in Emacs with zero errors
  4. A workout entry written by the app appears in workout-log.org under the correct date heading and passes `M-x org-lint` in Emacs with zero errors
  5. When the Syncthing folder is unavailable, the app shows a graceful error instead of crashing; .sync-conflict files are never opened as the active log
**Plans**: 3 plans in 3 waves

Plans:
- [x] 01-01-PLAN.md — Project scaffold (AGP 9.1.0, Compose, dependencies) + OrgModels and OrgSchema
- [x] 01-02-PLAN.md — OrgWriter + OrgParser TDD with Emacs org-lint validation checkpoint
- [x] 01-03-PLAN.md — SyncBackend, SyncthingFileBackend, DataStore, onboarding flow, and device deploy

### Phase 2: Food Logging
**Goal**: User can log every food they eat with full macros, see today's progress against targets, browse past days, manage a food library, and have all entries appear correctly in food-log.org.
**Depends on**: Phase 1
**Requirements**: FOOD-01, FOOD-02, FOOD-03, FOOD-04, FOOD-05, FOOD-06, FOOD-07, FOOD-08, FOOD-09, SYNC-05
**Success Criteria** (what must be TRUE):
  1. User can log a food entry (name, protein, carbs, fat, calories) under a meal group (breakfast, lunch, dinner, snacks) and it persists to food-log.org
  2. User can set daily macro targets and see a today summary showing macros logged vs. targets with remaining amounts
  3. User can quick-add calories/macros without entering a food name
  4. User can save a food to their library and select it from the library on subsequent entries
  5. User can apply a saved meal template to pre-fill multiple food entries at once
  6. User can browse and edit or delete entries from a past day's food log
  7. In-progress entry form state survives the app being backgrounded and restored
**Plans**: 2 plans in 2 waves

Plans:
- [x] 02-01-PLAN.md — Data layer: domain models, FoodRepository, OrgFoodRepository, OrgSchema updates, macro target preferences, AppContainer wiring
- [x] 02-02-PLAN.md — Food Log UI: FoodLogScreen with progress bars and meal sections, entry/library bottom sheets, date navigation, macro targets settings, SavedStateHandle draft save, navigation wiring, device verification

### Phase 3: Workout Logging
**Goal**: User can log a complete workout session using their 4-day split templates, with previous session weights auto-filled, a rest timer between sets, PR detection, and all data written to workout-log.org.
**Depends on**: Phase 2
**Requirements**: WORK-01, WORK-02, WORK-03, WORK-04, WORK-05, WORK-06, WORK-07, WORK-08, WORK-09, WORK-10
**Success Criteria** (what must be TRUE):
  1. User can start a workout by selecting a split day (push / pull / legs / compound) and have the exercise list pre-populated from that day's template
  2. When starting a session, sets/reps/weight from the previous session for that split day are auto-filled as starting values
  3. User can log sets, reps, and weight per exercise; a rest timer starts automatically between sets
  4. The app detects a new personal record during a session and surfaces it to the user
  5. User can view workout history showing past sessions with exercises, sets, reps, and weight logged
  6. The home screen shows which split day is today and a summary of the last session for that day
**Plans**: TBD

Plans:
- [ ] 03-01: WorkoutRepository, OrgWorkoutRepository, WorkoutLogViewModel, and Workout Log screen
- [ ] 03-02: Workout templates (4-day split), exercise library, previous session auto-fill, rest timer, PR tracking, split awareness, and volume tracking

### Phase 4: Dashboard and Polish
**Goal**: A unified home screen summarizes the day at a glance, history views for both domains are polished, analytics charts show weekly patterns, and sync status is always visible.
**Depends on**: Phase 3
**Requirements**: DASH-01, DASH-02, DASH-03, DASH-04, DASH-05, DASH-06, SYNC-04
**Success Criteria** (what must be TRUE):
  1. The home screen shows today's macro progress (logged vs. target) and today's planned workout in a single view
  2. Food log history view and workout history view are accessible and show past entries read from org files
  3. Weekly macro averages and workout volume trends are displayed as charts
  4. Training split calendar shows which day falls on which date across recent weeks
  5. A sync status indicator shows the last-synced timestamp and surfaces any .sync-conflict file warnings
**Plans**: TBD

Plans:
- [ ] 04-01: TodayScreen with unified macro + workout summary, navigation polish, and sync status indicator
- [ ] 04-02: Weekly macro averages chart, workout volume trends chart, and training split calendar

### Phase 5: Local Storage Mode
**Goal**: A non-technical user (e.g. mom on Android) can install the app and start logging food and workouts immediately — no Syncthing, no folder paths, no storage permissions. Data lives in app-internal storage with zero configuration.
**Depends on**: Phase 4
**Requirements**: LOCAL-01, LOCAL-02, LOCAL-03, LOCAL-04
**Success Criteria** (what must be TRUE):
  1. A new user selecting "Simple" mode during onboarding can start logging within 30 seconds — no permission prompts, no folder paths
  2. LocalStorageBackend reads/writes org files to app-internal storage, passing all existing OrgParser/OrgWriter tests
  3. Existing Syncthing "Power User" flow remains unchanged and fully functional
  4. Storage mode selection persists across restarts; the app launches into the correct backend on every cold start
  5. All food and workout features (Phases 2-4) work identically regardless of storage mode
**Plans**: TBD

Plans:
- [ ] 05-01: LocalStorageBackend implementation, storage mode in AppPreferencesRepository, AppContainer conditional wiring
- [ ] 05-02: Simplified onboarding flow with mode selection, skip permission/folder screens for local mode

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation | 3/3 | Complete | 2026-04-09 |
| 2. Food Logging | 2/2 | Complete | 2026-04-11 |
| 3. Workout Logging | 0/2 | Not started | - |
| 4. Dashboard and Polish | 0/2 | Not started | - |
| 5. Local Storage Mode | 0/2 | Not started | - |
