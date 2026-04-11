# Phase 3 Context: Workout Logging

## User's Program: Hybrid Lifting + Calisthenics

Weekly schedule with 4 training days, 2 recovery days, 1 off day.

### Monday - LIFT (Heavy Compounds @ Gym)
- Hack Squat or Front Squat: 4x5
- Bench: 4x5
- Barbell Row or Lat Pulldown: 3x8
- DB RDL: 3x8
- Face Pulls: 3x12

### Tuesday - CALISTHENICS (@ Home Rack)
- Pull-ups: 3x max reps (currently 8-10, target 3x12)
- Pike push-ups: 3x8-12
- Ring rows: 3x12
- Pistol squat progression: 3x5/leg
- Hollow body holds: 3x20-30 sec
- Dead hangs: 2x30-45 sec

### Wednesday - Recovery
- Walking pad: 30-60 min
- Light stretching: 10-15 min

### Thursday - LIFT (Moderate Volume @ Gym)
- OHP: 4x6
- Goblet Squat or Bulgarian Split Squat: 3x8
- Lat Pulldown: 3x8
- Hip Thrusts: 3x10
- Curls or Tricep work: 3x10
- Lateral Raises: 3x12

### Friday - CALISTHENICS (@ Home Rack)
- Ring dips (or rack dips): 3x max
- Archer push-ups or diamond push-ups: 3x8-12
- Single-leg glute bridges: 3x12/leg
- L-sit progression: 3x10-20 sec holds
- Single-leg RDL (bodyweight): 3x8/leg
- Hanging knee raises or leg raises: 3x10

### Saturday - Active Recovery
- Walking 30-60 min, stretching, foam rolling

### Sunday - Off

## UX Model: Day-Based Logging (redesign from session model)

The workout tab follows the **same day-based container model as food logging**. There is no "start session" / "finish session" lifecycle.

### Core Flow
1. User opens Workout tab → sees today (or selected day via date nav)
2. If empty: "Start from Template" button seeds the day with a template's exercises, or "Add Exercise" to add one at a time
3. Exercises appear as inline cards. User taps "+ Add Set" to log a set → opens Set Input Sheet → set is **written immediately** (like food entries)
4. User can add, remove, reorder, and modify exercises freely — template is a suggestion, not a constraint
5. "Complete" is a **soft optional flag** — signals the day is done but doesn't lock anything
6. Multiple workouts per day supported (e.g., walking at lunch + lifting at 4pm)
7. User picks which workout template to use — **no auto-suggestion by day**

### Exercise Categories (extensible)
Categories determine which input fields are shown. Stored as data, not a rigid enum. Category lives on the exercise definition — user picks it once when creating, never again.

Built-in categories:
- **Weighted** (barbell, dumbbell, machine) → weight + reps per set
- **Bodyweight** (pull-ups, push-ups) → reps per set only
- **Timed Hold** (planks, dead hangs) → hold duration per set
- **Cardio** (walking, running, cycling) → duration + optional distance
- **Stretch** → duration only

The set log data model uses nullable fields: `weight`, `reps`, `holdSecs`, `durationMin`, `distanceKm`. The category determines which fields the UI shows.

### Exercise Library
- Built-in list of common exercises (pre-categorized)
- User-added exercises **persist** and appear in search
- Searchable by name, filterable by category
- "Create New Exercise" flow: name → pick category → saved to library

### Design References
Screen mockups in this directory:
- `03-workout-empty-state.png` — empty day, template/add buttons
- `03-workout-active.png` — exercises loaded, sets logged, PR badge
- `03-exercise-picker.png` — searchable exercise list with category filters
- `03-set-input-sheet.png` — bottom sheet for logging a weighted set

## Key Design Implications

1. **Not a traditional 4-day split** — it's 2 gym lift days + 2 home calisthenics days
2. **Day templates** are: Monday-Lift, Tuesday-Calisthenics, Thursday-Lift, Friday-Calisthenics
3. **Some exercises have alternatives** (e.g., "Hack Squat or Front Squat") — user picks one per session
4. **Calisthenics use "max reps"** for some sets — need to log actual reps achieved
5. **Timed holds** (hollow body, L-sit, dead hangs) — measured in seconds, not reps
6. **Progression scheme**: work 3x5 → 3x12 on a variation, then advance to harder variation
7. **Equipment context**: gym has machines/barbells; home has squat rack with pull-up bar, rings, bands
8. **Recovery days** (Wed/Sat) are walking + stretching — may or may not be logged
9. **PR tracking** should work for both weight-based lifts AND bodyweight progressions (max reps, hold duration)
10. **Volume tracking** needs to handle: weight × sets × reps (lifts), bodyweight × sets × reps (calisthenics), and timed holds
11. **Rest timer** is nice-to-have, not a focus
12. **Exercise library** with user-persisted exercises is in scope for Phase 3
