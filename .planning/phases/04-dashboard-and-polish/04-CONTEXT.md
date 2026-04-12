# Phase 4: Dashboard and Polish - Context

**Gathered:** 2026-04-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Unified home screen summarizing the day at a glance, history views for food and workout, analytics charts showing weekly patterns, training split calendar, and sync status visibility. No new logging capabilities — this phase polishes what Phases 2-3 built and adds read-only analytics on top.

</domain>

<decisions>
## Implementation Decisions

### Home screen layout
- Vertical stack: food progress card on top, workout summary card below
- "Good morning" greeting + "Today, [date]" title in header
- Sync status as a green pill badge ("Synced") in the header, top-right
- Each card is a **horizontal pager** (swipeable): page 1 = today's data, page 2 = weekly analytics
- Page indicator dots at bottom of each card to hint at swipe
- Bottom nav with 4 tabs: FOOD, WORKOUT, HOME (active), SETTINGS

### Food progress card (page 1)
- Big calorie number (1,420 style) with "/ 2,200 kcal" and "780 left" in green
- Full-width horizontal progress bar for calories
- Three equal macro cards below (Protein, Carbs, Fat) each with value, target, and colored mini progress bar
- Colors: green for protein, coral for carbs, warm brown for fat
- "Recent" row at bottom showing last 3 days with date + calorie total

### Food weekly card (page 2 — swipe right)
- Title: "Weekly Macros" with 1W / 2W / 4W segmented tabs
- Color legend: Protein (green), Carbs (coral), Fat (brown)
- Grouped bar chart: 3 bars per day (M T W T F S S), one per macro
- Sunday faded/transparent if no data yet
- Averages row: three mini cards showing "Avg Protein: 132g", "Avg Carbs: 218g", "Avg Fat: 61g"

### Workout summary card (page 1)
- Header: "Workout" with dumbbell icon in sakura pink
- Split info: "Monday — Lift" with "In progress" pink badge (or "Complete" green badge, or "Rest" muted)
- Exercise list: rows showing exercise name, set progress ("2 of 4 sets"), and weight
- "Recent" row at bottom showing last 3 days with date + split name + completion checkmark

### Workout volume card (page 2 — swipe right)
- Title: "Weekly Volume" with 1W / 2W / 4W segmented tabs
- Legend: pink square for "Volume (kg)", green line for "Trend"
- Bar chart: pink bars per day showing total volume
- Forest-green trend line overlaid on bars
- Today/current day bar uses darker sakura pink; future days use muted gray

### Charts and analytics
- Weekly macro averages: grouped bar chart (protein/carbs/fat per day)
- Workout volume trends: combo chart — bars for individual sessions + line for trend
- Time range: selectable 1W / 2W / 4W tabs (default: 1W)
- Charts live inside card pager (page 2), not as separate scrollable sections

### Training split calendar
- Grid calendar — traditional month-style layout
- 4-week rolling view: 2 weeks past + 2 weeks future
- Each cell shows: split label + color coding by split type + completion checkmark
- Lives in the **workout tab** (above or below the daily workout log), not on home screen

### History navigation
- Existing date chevrons (left/right) for day-by-day browsing — no changes needed
- **Tap the date title** (e.g. "Today, Apr 12") to open a calendar date picker for jumping farther back
- Past days use the exact same view as today — no special read-only mode or extra polish
- Home screen "Recent" rows under each card provide quick glance at last 2-3 days

### Sync status
- Green pill badge in home screen header: "Synced" with checkmark icon
- Shows last-synced timestamp when tapped (or conflict warning if .sync-conflict files exist)

### Claude's Discretion
- Exact Compose implementation of horizontal pager (HorizontalPager vs ViewPager2)
- Chart rendering library choice (Compose Canvas, Vico, or custom)
- Calendar picker implementation (Material3 DatePicker or custom)
- Grid calendar component approach
- Exact animation/transition for card swipe
- Loading and error states for charts
- Empty state design when no weekly data exists

</decisions>

<specifics>
## Specific Ideas

- Cards should feel swipeable like iOS widget stacks — smooth horizontal page transition with dot indicators
- Weekly macro chart uses the same green/coral/brown color coding as today's macro cards for consistency
- Workout volume uses sakura pink for bars (brand color) with forest-green trend line for contrast
- "Recent" row at bottom of each card serves dual purpose: quick history glance + hint that there's more data to explore
- The design was prototyped in Pencil MCP — reference the .pen file for exact visual specs

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 04-dashboard-and-polish*
*Context gathered: 2026-04-12*
