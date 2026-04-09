# Origami

## What This Is

A personal iOS app for logging food (with full macros) and workouts (sets/reps/weight) that reads and writes org-formatted files. It syncs via iCloud Drive to integrate with an existing Emacs/org-roam knowledge base. Not for the App Store — just a personal tool on one iPhone.

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
- [ ] Sync org files via iCloud Drive (read and write)
- [ ] View food and workout history in the app
- [ ] Swappable sync backend (iCloud Drive now, local server later)

### Out of Scope

- App Store distribution — personal use only, sideloaded via Xcode
- Barcode scanning — v2 enhancement
- Food database lookup (USDA, OpenFoodFacts, etc.) — v2 enhancement
- Social features — not relevant, single user
- Apple HealthKit integration — not needed for v1
- Android version — may pivot to Pixel later but starting iOS

## Context

- User has an existing org-roam knowledge base at ~/roaming/ with health files at ~/roaming/health/ (nutrition.org, workouts.org)
- Existing workout routine is a 4-day full body split (push, pull, legs, compound) already documented in workouts.org
- Previous attempt used Apple Shortcuts to append to org files — worked functionally but the Shortcuts visual builder was a terrible development experience
- Nutrition tracking has been blocked by the inability to find apps that allow easy data export
- The name "origami" comes from sounding like "org" — no deeper meaning
- User is considering switching to Android/Pixel long-term; the sync abstraction layer supports this future pivot
- $99/year Apple Developer account likely needed to avoid 7-day re-signing with free account

## Constraints

- **Platform**: iOS / SwiftUI — native iPhone app, sideloaded via Xcode
- **Data format**: Org-mode plain text files — must be valid org syntax readable by Emacs
- **Sync v1**: iCloud Drive — files land in a shared folder the Mac can access
- **Sync architecture**: Abstracted backend — iCloud is the first implementation, local server swap must be low-friction
- **Single user**: No auth, no accounts, no multi-device beyond iPhone + Mac
- **Org file structure**: Single log files with date headings (food-log.org, workout-log.org), entries append under date headings

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| SwiftUI native | Best iOS integration, clean development experience | — Pending |
| Manual food entry for v1 | Keep scope small, barcode/DB lookup is v2 | — Pending |
| iCloud Drive sync | Simplest iPhone sync option, no server needed | — Pending |
| Abstracted sync layer | User planning local server eventually, Android pivot possible | — Pending |
| Single org log files per domain | Avoids file sprawl, works with org-agenda, easy to search | — Pending |
| Read + write (two-way) | User wants to see history and progress in app, not just log | — Pending |

---
*Last updated: 2026-04-09 after initialization*
