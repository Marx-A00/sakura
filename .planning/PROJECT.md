# Sakura

## What This Is

A personal Android app for logging food (with full macros) and workouts (sets/reps/weight) that reads and writes org-formatted files. Supports two modes: Syncthing sync to integrate with an existing Emacs/org-roam knowledge base on Mac, or zero-config local storage for non-technical users. Not for the Play Store — just a personal tool sideloaded on a Galaxy S21 FE.

## Core Value

One cohesive system for food and workout tracking that the user fully controls, with data living in org files that flow into their existing Emacs workflow.

## Requirements

### Validated

- Log food entries with full macros (protein, carbs, fat, calories) — v1.0
- Log workouts with sets, reps, and weight per exercise — v1.0
- Today-focused home screen showing meals logged, macro progress, and today's workout — v1.0
- Read and write org-formatted files (food-log.org, workout-log.org) — v1.0
- Sync org files between phone and Mac (Syncthing or similar) — v1.0
- View food and workout history in the app — v1.0
- Swappable sync backend (Syncthing/file-based now, local server later) — v1.0
- Local storage mode for non-technical users (zero-config, no Syncthing needed) — v1.0

### Active

(None yet — define for next milestone)

### Out of Scope

- Play Store distribution — personal use only, sideloaded APK
- Barcode scanning — v2 enhancement
- Food database lookup (USDA, OpenFoodFacts, etc.) — v2 enhancement
- Social features — not relevant, single user
- iOS version — may add later, starting Android (Galaxy S21 FE available for free)
- Google Fit / Health Connect integration — not needed
- AI photo food logging — unreliable, overkill for single user
- Gamification (streaks, badges) — doesn't match user's workflow style
- Cloud food database API — network dependency, offline gym use broken

## Context

- Shipped v1.0 with 12,517 lines of Kotlin across 7 phases in 5 days
- Tech stack: Kotlin + Jetpack Compose, Vico charts, DataStore preferences, custom OrgEngine
- User has an existing org-roam knowledge base at ~/roaming/ with health files at ~/roaming/health/ (nutrition.org, workouts.org)
- Existing workout routine is a 4-day full body split (push, pull, legs, compound) already documented in workouts.org
- Previous attempt used Apple Shortcuts to append to org files on iPhone — worked functionally but the Shortcuts visual builder was a terrible development experience
- The name "Sakura" (cherry blossom) — chosen for the aesthetic; warm pink/rose identity on dark backgrounds
- User has a Galaxy S21 FE available for free — eliminates Apple Developer account cost and sideloading friction
- Pivoted from iOS to Android because: no dev account fee, no provisioning profiles, no re-signing, full filesystem access, Syncthing just works, APK installs permanently

## Constraints

- **Platform**: Android / Kotlin + Jetpack Compose — native Android app, sideloaded APK on Galaxy S21 FE
- **Data format**: Org-mode plain text files — must be valid org syntax readable by Emacs
- **Sync v1**: File-based sync (Syncthing between phone and Mac over Wi-Fi) — files land in a synced folder
- **Sync architecture**: Abstracted backend — file sync is the first implementation, local server swap must be low-friction
- **Single user**: No auth, no accounts, no multi-device beyond phone + Mac
- **Multi-persona**: Power users get Syncthing flow; non-technical users (e.g. family) get zero-config local storage — same app, same features, different backend
- **Org file structure**: Single log files with date headings (food-log.org, workout-log.org), entries append under date headings

## Key Decisions

- Android over iOS: Free device available, no $99/year dev fee, no signing headaches, full filesystem access — Good
- Kotlin + Jetpack Compose: Standard modern Android stack, equivalent to SwiftUI — Good
- Manual food entry for v1: Keep scope small, barcode/DB lookup is v2 — Good
- Syncthing file sync: Simplest phone-to-Mac sync, bidirectional, no server needed — Good
- Abstracted sync layer: User planning local server eventually, iOS pivot possible — Good
- Single org log files per domain: Avoids file sprawl, works with org-agenda, easy to search — Good
- Read + write (two-way): User wants to see history and progress in app, not just log — Good
- Custom OrgEngine: No maintained JVM org library exists — Good
- No Room database: Org files are the sole data store, avoids dual source of truth — Good
- Property drawers as canonical org format: All structured data uses org property drawers — Good
- MANAGE_EXTERNAL_STORAGE for v1: Sideload-only removes Play Store policy concerns — Good
- Dual storage mode: Syncthing for power users, local storage for family — Good

---
*Last updated: 2026-04-13 after v1.0 milestone*
