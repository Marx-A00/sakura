# Phase 2: Food Logging - Context

**Gathered:** 2026-04-10
**Status:** Ready for planning

<domain>
## Phase Boundary

Complete food tracking end-to-end: log food entries with macros under meal groups, view today's progress against targets, browse past days, manage a reusable food library, apply meal templates, and have all entries persist correctly to food-log.org. Quick-add as a separate concept is dropped — unnamed entries cover that case.

</domain>

<decisions>
## Implementation Decisions

### Food entry flow
- Bottom sheet form (not full-screen or inline)
- Meal group selected inside the form via picker, defaults by time of day (morning → breakfast, midday → lunch, evening → dinner, late → snacks), editable
- Fields top to bottom: Name (optional) → Serving size + unit → Protein → Carbs → Fat → Calories (auto-calculated from macros, overridable) → Notes (optional)
- Leaving name blank logs as an unnamed entry — this replaces the quick-add concept entirely
- After saving: bottom sheet dismisses, undo toast appears ("Logged [name] — Undo")
- Field order is protein-first: protein → carbs → fat → calories (auto)

### Today's progress display
- Macro progress bars at the top of the screen (calories, protein, carbs, fat)
- Each bar shows "logged / target" text (e.g., "87g / 150g")
- Below the progress bars: collapsible meal sections (breakfast, lunch, dinner, snacks)
- Meal section headers show section totals (e.g., "Lunch — 650 cal")
- Tap a section to expand and see individual entries

### Food library & meal templates
- Save to library via toggle/checkbox on the entry bottom sheet during logging
- Also saveable from a logged entry on the today screen (action on expanded entry)
- Bottom sheet opens with Recent + Library tabs for picking saved items; selecting auto-fills all fields
- Meal templates created two ways: "Save as template" from a logged meal section, or manually from a library management screen
- Templates applied from two entry points: "Apply template" on a meal section header, or from a Templates tab in the bottom sheet
- When applying a template, all items log at once to the selected meal group

### Past day browsing & editing
- Date navigation: left/right arrows for day-by-day, tap date text to open a calendar picker for bigger jumps
- Past days display as read-only by default; an "Edit" button unlocks modifications (add, edit, delete)
- Macro targets set in a dedicated settings screen (not inline on the today screen)

### Visual design & color palette
- Sakura-inspired palette (not generic green wellness)
- Primary accent: cherry blossom pink (#C9758B) — buttons, active tab, save actions
- Light accent: pale sakura (#F5DDE5) — auto-calc highlight, add-food badges
- Dark accent: deep rose (#A25670) — text on pink backgrounds
- Secondary: warm pastel brown (#B8A089) — secondary elements, carbs bar
- Forest green (#3B5D44) — sparingly for protein bar / positive indicators only
- Base: warm cream (#F5F4F1) with white cards — already established from style guide
- Design mockups: `02-food-logging-designs.pen` (save from Pencil editor)

### Claude's Discretion
- Editing interaction pattern for entries (tap, swipe, or long-press — pick what fits best)
- Calories auto-calculation implementation details
- Loading states and error handling
- Exact spacing and typography (follow Outfit font from design mockups)
- How the "save to library" action surfaces on logged entries (long-press, swipe, menu)
- Empty state design for days with no entries

</decisions>

<specifics>
## Specific Ideas

- Protein-first field order reflects macro priority for the user
- Collapsible meal sections keep the today screen compact — you see totals at a glance, drill into details when needed
- Recent tab in the bottom sheet means frequently eaten foods are always one tap away without needing to save everything to the library
- Template application from both the meal section and bottom sheet means you can get to templates from whichever path you're already on
- Sakura pink palette gives the app a distinct identity matching its name — warm, personal, not clinical

</specifics>

<deferred>
## Deferred Ideas

- Quick-add as a distinct feature — DROPPED from success criteria. Unnamed entries (name field optional) cover this use case. Roadmap criterion #3 should be updated.

</deferred>

---

*Phase: 02-food-logging*
*Context gathered: 2026-04-10*
