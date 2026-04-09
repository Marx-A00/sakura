# Origami

## What This Is

A personal Android app for logging food (with full macros) and workouts (sets/reps/weight) that reads and writes org-formatted files. It syncs via Syncthing (or similar) to integrate with an existing Emacs/org-roam knowledge base on Mac. Not for the Play Store — just a personal tool on a Galaxy S21 FE.

## Core Value

One cohesive system for food and workout tracking that the user fully controls, with data living in org files that flow into their existing Emacs workflow.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Log food entries with full macros (protein, carbs, fat, calories)
- [ ] Log workouts with sets, reps, and weight per exercise
- [ ] Today-focused home screen showing meals logged, macro progress, and today's workout
- [ ] Read and write org-formatted files (food-log.org, workout-log.org)
- [ ] Sync org files between phone and Mac (Syncthing or similar)
- [ ] View food and workout history in the app
- [ ] Swappable sync backend (Syncthing/file-based now, local server later)

### Out of Scope

- Play Store distribution — personal use only, sideloaded APK
- Barcode scanning — v2 enhancement
- Food database lookup (USDA, OpenFoodFacts, etc.) — v2 enhancement
- Social features — not relevant, single user
- iOS version — may add later, starting Android (Galaxy S21 FE available for free)
- Google Fit / Health Connect integration — not needed for v1

## Context

- User has an existing org-roam knowledge base at ~/roaming/ with health files at ~/roaming/health/ (nutrition.org, workouts.org)
- Existing workout routine is a 4-day full body split (push, pull, legs, compound) already documented in workouts.org
- Previous attempt used Apple Shortcuts to append to org files on iPhone — worked functionally but the Shortcuts visual builder was a terrible development experience
- Nutrition tracking has been blocked by the inability to find apps that allow easy data export
- The name "origami" comes from sounding like "org" — no deeper meaning
- User has a Galaxy S21 FE available for free — eliminates Apple Developer account cost and sideloading friction
- Pivoted from iOS to Android because: no dev account fee, no provisioning profiles, no re-signing, full filesystem access, Syncthing just works, APK installs permanently
- User may add iOS support later (cross-platform or native port) once Android v1 is validated

## Constraints

- **Platform**: Android / Kotlin + Jetpack Compose — native Android app, sideloaded APK on Galaxy S21 FE
- **Data format**: Org-mode plain text files — must be valid org syntax readable by Emacs
- **Sync v1**: File-based sync (Syncthing between phone and Mac over Wi-Fi) — files land in a synced folder
- **Sync architecture**: Abstracted backend — file sync is the first implementation, local server swap must be low-friction
- **Single user**: No auth, no accounts, no multi-device beyond phone + Mac
- **Org file structure**: Single log files with date headings (food-log.org, workout-log.org), entries append under date headings

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Android over iOS | Free device available, no $99/year dev fee, no signing headaches, full filesystem access | — Pending |
| Kotlin + Jetpack Compose | Standard modern Android stack, equivalent to SwiftUI | — Pending |
| Manual food entry for v1 | Keep scope small, barcode/DB lookup is v2 | — Pending |
| Syncthing file sync | Simplest phone-to-Mac sync, bidirectional, no server needed | — Pending |
| Abstracted sync layer | User planning local server eventually, iOS pivot possible | — Pending |
| Single org log files per domain | Avoids file sprawl, works with org-agenda, easy to search | — Pending |
| Read + write (two-way) | User wants to see history and progress in app, not just log | — Pending |

---
*Last updated: 2026-04-09 after platform pivot to Android*
