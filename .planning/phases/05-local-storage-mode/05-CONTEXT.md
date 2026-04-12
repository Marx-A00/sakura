# Phase 5: Local Storage Mode - Context

**Gathered:** 2026-04-12
**Status:** Ready for planning

<domain>
## Phase Boundary

A non-technical user (e.g. mom on Android) can install the app and start logging food and workouts immediately — no Syncthing, no folder paths, no storage permissions. Data lives in app-internal storage with zero configuration. The existing Syncthing "Power User" flow remains unchanged.

</domain>

<decisions>
## Implementation Decisions

### Onboarding flow
- Welcome screen first: app name + tagline, minimal, just a "Get Started" button
- Mode selection on second screen: two side-by-side cards, tap to select
- After selecting "Just me" (local): one confirmation screen ("You're all set!") then straight into the app
- After selecting "Sync across devices": continues existing permission/folder onboarding flow

### Mode naming and presentation
- Local mode: "Just me" with 🌸 emoji, subtitle "Mom, choose this one"
- Syncthing mode: "Sync across devices" with 🤓 emoji, subtitle "For the nerd who set this up"
- Playful, personal tone — this is a family app, not a product

### Mode switching and migration
- Bidirectional switching supported: local ↔ Syncthing
- Migration copies org files to the new location + shows confirmation of what was moved
- Switch accessible from Settings > Storage section

### Mode visibility in-app
- Mode is invisible during normal app use — only visible in Settings
- Settings > Storage shows current mode label + "Change" button
- Sync status badge (Phase 4) hidden completely in local mode
- Syncthing folder path and all Syncthing-specific settings hidden in local mode

### Claude's Discretion
- LocalStorageBackend internal implementation
- AppContainer conditional wiring approach
- Exact confirmation screen design ("You're all set!")
- Migration error handling and edge cases
- Welcome screen tagline copy

</decisions>

<specifics>
## Specific Ideas

- "Mom, choose this one" / "For the nerd who set this up" — the subtitles should feel like inside jokes, not UI copy
- 🌸 and 🤓 emojis on the mode cards reinforce the personality
- The app is built for two people (Marcos and his mom) — the onboarding should reflect that intimacy

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 05-local-storage-mode*
*Context gathered: 2026-04-12*
