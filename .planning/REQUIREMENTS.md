# Requirements: Origami

**Defined:** 2026-04-09
**Core Value:** One cohesive system for food and workout tracking that the user fully controls, with data living in org files that flow into their existing Emacs workflow.

## v1 Requirements

### Foundation

- [ ] **FOUND-01**: Custom org-mode parser reads org files into Kotlin models
- [ ] **FOUND-02**: Org writer produces valid org syntax verified by Emacs org-lint
- [ ] **FOUND-03**: Org file schema defined for food and workout entries
- [ ] **FOUND-04**: SyncBackend interface abstracts sync (Syncthing now, server later)
- [ ] **FOUND-05**: SyncthingFileBackend reads/writes via direct filesystem access
- [ ] **FOUND-06**: Onboarding flow for storage permission + Syncthing folder selection

### Food Tracking

- [ ] **FOOD-01**: Log food entry with name, protein, carbs, fat, calories
- [ ] **FOOD-02**: Meal groupings (breakfast, lunch, dinner, snacks)
- [ ] **FOOD-03**: Daily macro targets (static, user-configurable)
- [ ] **FOOD-04**: Today's food summary (macros logged vs targets)
- [ ] **FOOD-05**: Quick-add calories/macros without named food
- [ ] **FOOD-06**: Custom food library (save frequently eaten foods)
- [ ] **FOOD-07**: Meal templates / saved meals
- [ ] **FOOD-08**: View past day's food log
- [ ] **FOOD-09**: Edit/delete logged food entries

### Workout Tracking

- [ ] **WORK-01**: Log sets, reps, weight per exercise
- [ ] **WORK-02**: Previous session auto-fill from last logged session
- [ ] **WORK-03**: Workout templates (encode 4-day full body split)
- [ ] **WORK-04**: Exercise library (user's actual exercises)
- [ ] **WORK-05**: PR tracking (automatic new bests detection)
- [ ] **WORK-06**: Workout history view
- [ ] **WORK-07**: Rest timer
- [ ] **WORK-08**: Today's planned workout on home screen
- [ ] **WORK-09**: Volume tracking per session
- [ ] **WORK-10**: Training split awareness (knows current day in split)

### Sync & Infrastructure

- [ ] **SYNC-01**: Write org files to Syncthing-managed folder
- [ ] **SYNC-02**: Read org files from Syncthing-managed folder (two-way)
- [ ] **SYNC-03**: Handle Syncthing .sync-conflict files gracefully
- [ ] **SYNC-04**: Sync status indicator
- [ ] **SYNC-05**: Draft save/restore for entry forms
- [ ] **SYNC-06**: Graceful degradation when sync folder unavailable

### Dashboard & Analytics

- [ ] **DASH-01**: Today home screen (macros progress + today's workout)
- [ ] **DASH-02**: Food log history view
- [ ] **DASH-03**: Workout history view (past sessions)
- [ ] **DASH-04**: Weekly macro averages (charts)
- [ ] **DASH-05**: Volume tracking trends
- [ ] **DASH-06**: Training split calendar

## v2 Requirements

### Local Server Sync

- **SERV-01**: LocalServerBackend implementation (HTTP/WebDAV/SFTP)
- **SERV-02**: Settings UI to choose sync provider
- **SERV-03**: Migration path from Syncthing files to server sync

### Enhanced Food Tracking

- **FOOD-10**: Barcode scanner for packaged foods
- **FOOD-11**: Embedded USDA food database subset for lookup
- **FOOD-12**: Edit past entries in-app (vs editing in Emacs)

### Enhanced Analytics

- **DASH-07**: Richer weekly/monthly trend analysis
- **DASH-08**: Body weight tracking and trends

### Platform Expansion

- **PLAT-01**: iOS version (native SwiftUI or cross-platform)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Play Store distribution | Personal use only, sideloaded APK |
| Social features | Single user, antithetical to private design |
| AI photo food logging | Unreliable, overkill for single user |
| Gamification (streaks, badges) | Doesn't match user's workflow style |
| Cloud food database API | Network dependency, offline gym use broken |
| Apple Watch / Wear OS companion | Adds scope, phone logging is sufficient |
| Meal planning / recipe generation | Separate workflow from tracking |
| Notifications / reminders | Not how power users work |
| Adaptive calorie targets (TDEE) | Static targets sufficient for v1 |
| Google Fit / Health Connect | Not needed for v1 |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| FOUND-01 | Phase 1 | Pending |
| FOUND-02 | Phase 1 | Pending |
| FOUND-03 | Phase 1 | Pending |
| FOUND-04 | Phase 1 | Pending |
| FOUND-05 | Phase 1 | Pending |
| FOUND-06 | Phase 1 | Pending |
| FOOD-01 | Phase 2 | Pending |
| FOOD-02 | Phase 2 | Pending |
| FOOD-03 | Phase 2 | Pending |
| FOOD-04 | Phase 2 | Pending |
| FOOD-05 | Phase 2 | Pending |
| FOOD-06 | Phase 2 | Pending |
| FOOD-07 | Phase 2 | Pending |
| FOOD-08 | Phase 2 | Pending |
| FOOD-09 | Phase 2 | Pending |
| WORK-01 | Phase 3 | Pending |
| WORK-02 | Phase 3 | Pending |
| WORK-03 | Phase 3 | Pending |
| WORK-04 | Phase 3 | Pending |
| WORK-05 | Phase 3 | Pending |
| WORK-06 | Phase 3 | Pending |
| WORK-07 | Phase 3 | Pending |
| WORK-08 | Phase 3 | Pending |
| WORK-09 | Phase 3 | Pending |
| WORK-10 | Phase 3 | Pending |
| SYNC-01 | Phase 1 | Pending |
| SYNC-02 | Phase 1 | Pending |
| SYNC-03 | Phase 1 | Pending |
| SYNC-04 | Phase 4 | Pending |
| SYNC-05 | Phase 2 | Pending |
| SYNC-06 | Phase 1 | Pending |
| DASH-01 | Phase 4 | Pending |
| DASH-02 | Phase 4 | Pending |
| DASH-03 | Phase 4 | Pending |
| DASH-04 | Phase 4 | Pending |
| DASH-05 | Phase 4 | Pending |
| DASH-06 | Phase 4 | Pending |

**Coverage:**
- v1 requirements: 37 total
- Mapped to phases: 37
- Unmapped: 0

---
*Requirements defined: 2026-04-09*
*Last updated: 2026-04-09 — phase mappings added*
