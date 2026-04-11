# Phase 3: Workout Logging - Research

**Researched:** 2026-04-11
**Domain:** Workout data modeling (hybrid lift/calisthenics), OrgEngine extension, rest timer in Compose/ViewModel, PR detection logic, training split awareness, workout history
**Confidence:** HIGH (architecture patterns verified against existing codebase + official Android docs; org format decisions follow established Phase 1/2 precedents)

---

## Summary

Phase 3 builds the workout logging layer on top of the same infrastructure that powers Phase 2 (food logging). The architecture is already established: `WorkoutRepository` interface, `OrgWorkoutRepository` implementation, `WorkoutLogViewModel`, and Compose screens wired via `AppNavHost`. The approach directly mirrors the food pattern — no new libraries are needed.

The main design challenges are **not** about picking technology. They are data modeling decisions:

1. The existing `OrgExerciseEntry` model is too simple for Phase 3. It stores one flat `sets/reps/weight/unit` tuple per exercise. Phase 3 requires: per-set logging (set 1: 5 reps @ 80kg, set 2: 5 reps @ 82.5kg), alternative exercise selection ("Hack Squat or Front Squat"), max-reps calisthenics, timed holds (seconds, not reps), and PR tracking. The model must be redesigned before writing any repository code.

2. The rest timer should live in the ViewModel, not as a composable-scoped effect. It needs to survive screen rotation and remain active across navigation events within the session. `viewModelScope` + a coroutine loop emitting `StateFlow<Int>` (seconds remaining) is the correct pattern.

3. PR detection is a read-before-write operation: load all prior sessions for the exercise, compute the historical best, compare the new set. This must use the `fileMutex` pattern from `OrgFoodRepository` to prevent races.

4. Training split awareness (WORK-08, WORK-10) only requires storing one integer in DataStore: the start date of the current program cycle (or equivalently, the Monday of week 1). From this, the app can compute which day in the split any given date is. No complex scheduling logic needed — it's pure arithmetic on `LocalDate`.

**Primary recommendation:** Redesign `OrgExerciseEntry` to support per-set rows, then build `WorkoutRepository`/`OrgWorkoutRepository` following the exact food repository pattern. Use `viewModelScope` coroutine timer in ViewModel. Store split start date in DataStore.

---

## Standard Stack

### Core (no new Gradle dependencies required)

All required functionality is available in the existing dependency set:

**Coroutine timer (rest timer):**
- `kotlinx.coroutines` — already in the project via `lifecycle-viewmodel-compose`.
- Pattern: `MutableStateFlow<Int>` for seconds remaining, `viewModelScope.launch` with `while (remaining > 0) { delay(1000); remaining-- }`.
- Confidence: HIGH — verified against official Android coroutines + ViewModel docs.

**DataStore (split start date):**
- `androidx.datastore:datastore-preferences` — already in `libs.versions.toml` (`datastore = "1.2.1"`).
- Add `longPreferencesKey("split_start_epoch_day")` to `AppPreferencesRepository`. Stores `LocalDate.toEpochDay()` as Long.
- Confidence: HIGH — DataStore supports all primitive types; `longPreferencesKey` is confirmed in official docs.

**Material3 Compose components:**
- `ModalBottomSheet` — exercise picker, set logging form. Already used in Phase 2.
- `AlertDialog` — PR notification dialog (simple title + message + dismiss), confirm-end-workout dialog.
- `BottomSheetScaffold` — persistent rest timer bar. Available in Material3 BOM (same `composeBom = "2025.05.00"`). Allows main content to scroll while a peekable sheet at bottom shows the active timer. Mark `@OptIn(ExperimentalMaterial3Api::class)` as it has been experimental in recent BOM versions.
- `LazyColumn` with `stickyHeader` — exercise sections with set rows. Already used in Phase 2 (`FoodLogScreen` uses `stickyHeader` for meal sections).
- Confidence: HIGH — all in existing BOM.

**No new Gradle dependencies needed.**

---

## Architecture Patterns

### Recommended Project Structure (Phase 3 additions)

```
app/src/main/java/com/sakura/
├── data/
│   └── workout/
│       ├── WorkoutRepository.kt         # Interface (mirrors FoodRepository)
│       ├── OrgWorkoutRepository.kt      # Org-file implementation
│       ├── WorkoutSession.kt            # Domain: one session (date + exercises)
│       ├── ExerciseLog.kt               # Domain: one exercise with list of SetLog
│       ├── SetLog.kt                    # Domain: one logged set (reps, weight, hold_secs)
│       ├── WorkoutTemplate.kt           # Domain: one day's template (exercise list)
│       └── ExerciseDefinition.kt        # Domain: exercise metadata (name, alternatives, type)
│
├── orgengine/
│   ├── OrgModels.kt                     # EXTEND OrgExerciseEntry → OrgSetEntry + OrgExerciseLog
│   ├── OrgSchema.kt                     # EXTEND: formatExerciseLog, formatSetEntry, new prop keys
│   ├── OrgParser.kt                     # EXTEND: WORKOUT mode to parse multi-set structure
│   └── OrgWriter.kt                     # EXTEND: writeSection for multi-set workout sections
│
├── features/
│   └── workoutlog/
│       ├── WorkoutLogScreen.kt          # Session overview: exercise list, set rows
│       ├── WorkoutSessionScreen.kt      # Active session: current exercise, set input
│       ├── WorkoutLogViewModel.kt       # State: session, timer, PR flags
│       ├── WorkoutLogUiState.kt         # Sealed: Loading/Success/Error + SessionState
│       └── WorkoutHistoryScreen.kt      # Past sessions list
│
├── navigation/
│   └── Routes.kt                        # ADD: WorkoutLog, WorkoutSession, WorkoutHistory
│
└── di/
    └── AppContainer.kt                  # ADD: workoutRepository
```

### Pattern 1: OrgExerciseEntry redesign — per-set flat structure

The current `OrgExerciseEntry` stores one aggregate row per exercise. Phase 3 requires per-set data. The recommended org format mirrors Phase 1/2 conventions: each set is a level-4 heading under an exercise level-3 heading.

**New workout-log.org format:**

```org
* <2026-04-11 Sat>
** Workout
:PROPERTIES:
:split_day: monday-lift
:volume: 12450
:duration_min: 62
:END:
*** Bench Press
:PROPERTIES:
:id: 1712661600000
:exercise_type: barbell
:END:
**** Set 1
:PROPERTIES:
:reps: 5
:weight: 80.0
:unit: kg
:rpe: 7
:is_pr: false
:END:
**** Set 2
:PROPERTIES:
:reps: 5
:weight: 82.5
:unit: kg
:rpe: 8
:is_pr: true
:END:
*** Pull-ups
:PROPERTIES:
:id: 1712661600001
:exercise_type: calisthenics
:END:
**** Set 1
:PROPERTIES:
:reps: 11
:weight: 0.0
:unit: bw
:hold_secs: 0
:is_pr: true
:END:
*** Hollow Body Hold
:PROPERTIES:
:id: 1712661600002
:exercise_type: timed
:END:
**** Set 1
:PROPERTIES:
:reps: 1
:weight: 0.0
:unit: bw
:hold_secs: 25
:is_pr: false
:END:
```

**New OrgModels additions:**

```kotlin
// Replace OrgExerciseEntry with:
data class OrgExerciseLog(
    val name: String,
    val id: Long,                          // epoch millis for this exercise in this session
    val exerciseType: String,              // "barbell", "dumbbell", "calisthenics", "timed"
    val sets: List<OrgSetEntry>
)

data class OrgSetEntry(
    val setNumber: Int,                    // 1-indexed, matches heading "Set N"
    val reps: Int,
    val weight: Double,                    // 0.0 for bodyweight
    val unit: String,                      // "kg", "lbs", "bw"
    val holdSecs: Int,                     // 0 for non-timed exercises
    val rpe: Int?,                         // Optional, 6-10
    val isPr: Boolean
)
```

**OrgDateSection** already has `exercises: List<OrgExerciseEntry>` — this field becomes `exercises: List<OrgExerciseLog>`. The old `OrgExerciseEntry` can be removed or aliased.

**NOTE on backward compat:** Old workout-log.org entries (Phase 1 test data) use the old flat format. The parser should handle both: if no level-4 `**** Set N` headings found under an exercise, synthesize a single `OrgSetEntry` from the old `sets/reps/weight/unit` properties. This keeps existing test fixtures working.

### Pattern 2: Workout session as in-memory draft (not written until "Finish")

During an active session, the user adds sets progressively. Do NOT write to `workout-log.org` after every set. Write the complete session atomically when the user finishes.

Rationale: food logging writes after every entry because entries are independent. Workout sets are part of one atomic session — partial writes create incomplete sessions in history.

```kotlin
class WorkoutLogViewModel : ViewModel() {
    // In-memory draft
    private val _sessionDraft = MutableStateFlow<SessionDraft?>(null)

    // Called when user taps "Log Set"
    fun addSetToDraft(exerciseName: String, set: SetLog) {
        _sessionDraft.update { draft ->
            draft?.addSet(exerciseName, set) ?: SessionDraft.new(exerciseName, set)
        }
        checkForPR(exerciseName, set)  // async, reads history
    }

    // Called when user taps "Finish Workout"
    fun finishSession() {
        viewModelScope.launch {
            val draft = _sessionDraft.value ?: return@launch
            workoutRepo.saveSession(draft.toWorkoutSession())
            _sessionDraft.value = null
        }
    }
}
```

### Pattern 3: Rest timer in ViewModel

The rest timer must survive screen rotation. Implement in ViewModel, not composable:

```kotlin
// In WorkoutLogViewModel
private val _restSecondsRemaining = MutableStateFlow(0)
val restSecondsRemaining: StateFlow<Int> = _restSecondsRemaining.asStateFlow()

private var restTimerJob: Job? = null

fun startRestTimer(durationSeconds: Int) {
    restTimerJob?.cancel()
    restTimerJob = viewModelScope.launch {
        var remaining = durationSeconds
        _restSecondsRemaining.value = remaining
        while (remaining > 0) {
            delay(1000)
            remaining--
            _restSecondsRemaining.value = remaining
        }
    }
}

fun cancelRestTimer() {
    restTimerJob?.cancel()
    _restSecondsRemaining.value = 0
}
```

UI observes `restSecondsRemaining` via `collectAsStateWithLifecycle()`. Show a persistent bottom bar (Scaffold `bottomBar`) when `restSecondsRemaining > 0`.

### Pattern 4: PR detection

PR detection is a read-before-check during `addSetToDraft`. Load the exercise's history from `workout-log.org`, find the best previous set, compare.

PR types to detect:
- **Weight PR** (barbell/dumbbell): weight > all prior weights for same exercise, same or more reps
- **Rep PR** (calisthenics): reps > max prior reps (ignores weight when unit = "bw")
- **Hold PR** (timed): holdSecs > max prior holdSecs

```kotlin
// In OrgWorkoutRepository
suspend fun findPersonalBest(exerciseName: String): PersonalBest? {
    val content = syncBackend.readFile(WORKOUT_LOG_FILE)
    if (content.isBlank()) return null
    val orgFile = OrgParser.parse(content, OrgParser.ParseMode.WORKOUT)

    var bestWeight = 0.0
    var bestReps = 0
    var bestHoldSecs = 0

    for (section in orgFile.sections) {
        val exercise = section.exercises.find { it.name == exerciseName } ?: continue
        for (set in exercise.sets) {
            if (set.unit != "bw") bestWeight = maxOf(bestWeight, set.weight)
            bestReps = maxOf(bestReps, set.reps)
            bestHoldSecs = maxOf(bestHoldSecs, set.holdSecs)
        }
    }
    return PersonalBest(weight = bestWeight, reps = bestReps, holdSecs = bestHoldSecs)
}
```

### Pattern 5: Training split awareness

Store `splitStartEpochDay: Long` in DataStore (the `LocalDate.toEpochDay()` of the first Monday of the user's program). Compute current day in split from `LocalDate.now().toEpochDay() - splitStartEpochDay`.

```kotlin
// In AppPreferencesRepository (extension)
val SPLIT_START_EPOCH_DAY = longPreferencesKey("split_start_epoch_day")

fun splitDayForDate(date: LocalDate): SplitDay? {
    val startEpochDay = // read from DataStore
    if (startEpochDay == 0L) return null  // not configured
    val dayOffset = (date.toEpochDay() - startEpochDay).toInt()
    return when (dayOffset % 7) {
        0 -> SplitDay.MONDAY_LIFT
        1 -> SplitDay.TUESDAY_CALISTHENICS
        3 -> SplitDay.THURSDAY_LIFT
        4 -> SplitDay.FRIDAY_CALISTHENICS
        else -> null  // recovery or off day
    }
}
```

Alternatively, store the most recent completed session date and always offer "next" session. This is simpler: if the user last trained on a Monday (Lift), the next workout is Tuesday (Calisthenics). No epoch day arithmetic needed; just walk forward from last session date.

**Recommendation:** Use the last-session-based approach for the home screen "today's workout" card. It's resilient to users skipping days. The epoch-day approach is fragile if the user starts mid-week.

### Pattern 6: Workout templates (hardcoded data)

Templates are NOT user-editable in Phase 3 (no requirement). Encode them as hardcoded Kotlin data in a `WorkoutTemplates` object. No separate org file needed.

```kotlin
object WorkoutTemplates {
    val MONDAY_LIFT = WorkoutTemplate(
        splitDay = SplitDay.MONDAY_LIFT,
        exercises = listOf(
            ExerciseDefinition(
                name = "Hack Squat",
                alternatives = listOf("Front Squat"),
                targetSets = 4,
                targetReps = 5,
                exerciseType = ExerciseType.BARBELL
            ),
            ExerciseDefinition(
                name = "Bench Press",
                alternatives = emptyList(),
                targetSets = 4,
                targetReps = 5,
                exerciseType = ExerciseType.BARBELL
            ),
            // ...
        )
    )
    // TUESDAY_CALISTHENICS, THURSDAY_LIFT, FRIDAY_CALISTHENICS
}
```

When starting a session, load the template for the selected split day and auto-populate exercises.

### Anti-Patterns to Avoid

- **Writing per-set to org file during session**: Creates partial session entries visible in history. Write atomically on "Finish".
- **Timer in composable (LaunchedEffect)**: LaunchedEffect is cancelled when composable leaves composition. Screen rotation cancels the timer. Always put the timer in ViewModel.
- **PR detection on write**: PR detection must read history BEFORE appending the new session. After writing, the new session is in the file, making it ambiguous whether the PR was "this session" or "last session."
- **Using OrgExerciseEntry's single `sets` field as total set count**: Phase 2 workout parser aggregated sets. This must be decomposed to per-set rows for Phase 3.
- **Regex-based MEAL_HEADING_REGEX for `** Workout` heading**: The existing `MEAL_HEADING_REGEX = Regex("""^\*\* (\w+)$""")` matches only single-word labels. "Workout" works but a new `WORKOUT_HEADING_REGEX` should be introduced for clarity and future-proofing.

---

## Don't Hand-Roll

Problems that are already solved by the existing stack:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Countdown timer | Custom Handler/Timer | `viewModelScope` coroutine with `delay(1000)` | Lifecycle-safe, cancellable, testable |
| Split day calculation | Complex scheduling engine | Arithmetic on `LocalDate.toEpochDay()` or last-session date | The split is fixed 7-day cycle |
| Exercise library persistence | Separate org file | Hardcoded `WorkoutTemplates` object | Templates are fixed for v1; no user editing needed |
| PR badge/animation | Custom animation library | `AlertDialog` with simple text | No fancy animation needed for v1 |
| Session persistence across process death | Complex serialization | `SavedStateHandle` for session draft | Same pattern used for food entry drafts |

---

## Common Pitfalls

### Pitfall 1: OrgExerciseEntry level depth conflict

**What goes wrong:** The current parser treats `*** Item` (level 3) as exercise entries. Phase 3 needs exercises at level 3 AND sets at level 4. The parser's `ITEM_HEADING_REGEX` catches both, breaking set parsing.

**Why it happens:** The existing WORKOUT parse mode has only one item heading level. Adding a fourth level (`****`) requires the parser to track whether it's inside an exercise heading.

**How to avoid:** Add a `WORKOUT` parse mode branch that tracks level-3 headings as exercises and level-4 headings as sets. Introduce `SET_HEADING_REGEX = Regex("""^\*{4} Set (\d+)$""")` in OrgSchema.

**Warning signs:** Parser tests that produce `OrgExerciseLog` with `sets.size == 0` when parsing workout-log.org content.

### Pitfall 2: Session draft not surviving rotation

**What goes wrong:** Active session data (sets logged so far, exercises added) is lost on screen rotation.

**Why it happens:** If session draft is stored in composable-local state (`remember { }`) or even `rememberSaveable { }` with complex objects, rotation causes data loss.

**How to avoid:** Store the full in-memory session draft in `WorkoutLogViewModel` as `MutableStateFlow<SessionDraft?>`. ViewModel survives rotation. `SessionDraft` does NOT need `SavedStateHandle` — it lives in memory until "Finish" is tapped (process death during active session is acceptable data loss for v1).

### Pitfall 3: PR false positives on first session

**What goes wrong:** When `workout-log.org` is empty (first session ever), every set is a PR. The UI floods with PR notifications.

**Why it happens:** `findPersonalBest` returns null for all exercises; comparison with null/zero always triggers "PR detected."

**How to avoid:** Only show PR notification if there is at least one prior session for that exercise. If `findPersonalBest` returns `null` (no history), do not show PR banner. Log `is_pr: false` in the org file.

### Pitfall 4: Exercise name case/spacing variation

**What goes wrong:** "Bench Press" in the template and "bench press" in an old log entry are treated as different exercises. PR lookup fails.

**Why it happens:** String comparison is case-sensitive.

**How to avoid:** Normalize exercise names on write and read. Use `name.trim().lowercase()` as the lookup key in `findPersonalBest`. Display names use original casing from template definitions.

### Pitfall 5: Alternative exercises in PR tracking

**What goes wrong:** "Hack Squat" PR is tracked separately from "Front Squat" PR, so the pre-fill logic doesn't know which to show.

**Why it happens:** Alternative exercises are treated as completely separate entries.

**How to avoid:** In `ExerciseDefinition`, track `canonicalName` (the primary exercise). PR lookup searches by canonical name across all alternatives. When pre-filling from last session, use whatever alternative was selected in that session. The `WorkoutTemplates` object defines which exercises are alternatives of each other.

### Pitfall 6: Volume calculation for mixed exercise types

**What goes wrong:** Volume for timed holds (e.g., "25 sec hold") cannot be calculated as `sets × reps × weight`.

**Why it happens:** Volume formula `weight × reps × sets` is meaningless for bodyweight timed exercises.

**How to avoid:** Calculate volume only for exercises with `unit != "bw"`. For calisthenics/timed, track a separate metric (total time under tension, or total reps). Store session `volume` property only when meaningful. The `OrgWorkoutSession` property drawer should store volume as a nullable/optional field.

---

## Code Examples

### New OrgSchema entries needed

```kotlin
// Source: Based on OrgSchema.kt patterns established in Phase 1/2

// New property keys
const val PROP_HOLD_SECS = "hold_secs"
const val PROP_RPE = "rpe"
const val PROP_IS_PR = "is_pr"
const val PROP_EXERCISE_TYPE = "exercise_type"
const val PROP_SPLIT_DAY = "split_day"
const val PROP_VOLUME = "volume"
const val PROP_DURATION_MIN = "duration_min"

// New heading regexes
val WORKOUT_HEADING_REGEX = Regex("""^\*\* Workout$""")
val SET_HEADING_REGEX = Regex("""^\*{4} Set (\d+)$""")

// Format functions
fun formatExerciseLog(exercise: OrgExerciseLog): String =
    "*** ${exercise.name}\n" +
    "$PROPERTIES_START\n" +
    ":$PROP_ID: ${exercise.id}\n" +
    ":$PROP_EXERCISE_TYPE: ${exercise.exerciseType}\n" +
    PROPERTIES_END

fun formatSetEntry(set: OrgSetEntry): String {
    val sb = StringBuilder()
    sb.append("**** Set ${set.setNumber}\n")
    sb.append("$PROPERTIES_START\n")
    sb.append(":$PROP_REPS: ${set.reps}\n")
    sb.append(":$PROP_WEIGHT: ${formatWeight(set.weight)}\n")
    sb.append(":$PROP_UNIT: ${set.unit}\n")
    if (set.holdSecs > 0) sb.append(":$PROP_HOLD_SECS: ${set.holdSecs}\n")
    set.rpe?.let { sb.append(":$PROP_RPE: $it\n") }
    sb.append(":$PROP_IS_PR: ${set.isPr}\n")
    sb.append(PROPERTIES_END)
    return sb.toString()
}
```

### BottomSheetScaffold for rest timer

```kotlin
// Source: Material3 BOM 2025.05.00, BottomSheetScaffold (ExperimentalMaterial3Api)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSessionScreen(viewModel: WorkoutLogViewModel) {
    val restSeconds by viewModel.restSecondsRemaining.collectAsStateWithLifecycle()

    val sheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = if (restSeconds > 0) SheetValue.PartiallyExpanded
                          else SheetValue.Hidden
        )
    )

    BottomSheetScaffold(
        sheetContent = {
            RestTimerContent(restSeconds = restSeconds, onSkip = { viewModel.cancelRestTimer() })
        },
        scaffoldState = sheetState,
        sheetPeekHeight = if (restSeconds > 0) 72.dp else 0.dp,
        topBar = { WorkoutTopBar(...) }
    ) { paddingValues ->
        WorkoutExerciseList(paddingValues = paddingValues, ...)
    }
}
```

### Split day calculation

```kotlin
// In AppPreferencesRepository or a SplitDayCalculator utility
fun splitDayFor(date: LocalDate, lastSessionDate: LocalDate?, lastSplitDay: SplitDay?): SplitDay? {
    if (lastSessionDate == null || lastSplitDay == null) return null
    val daysSince = ChronoUnit.DAYS.between(lastSessionDate, date).toInt()
    // Walk the 7-day cycle from lastSplitDay
    // Mon=0, Tue=1, (Wed recovery), Thu=3, Fri=4, (Sat/Sun off)
    val cycle = listOf(SplitDay.MONDAY_LIFT, SplitDay.TUESDAY_CALISTHENICS, null,
                       SplitDay.THURSDAY_LIFT, SplitDay.FRIDAY_CALISTHENICS, null, null)
    val lastIdx = cycle.indexOfFirst { it == lastSplitDay }
    return cycle[(lastIdx + daysSince) % 7]
}
```

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|-----------------|--------|
| `OrgExerciseEntry` flat model (Phase 1 scaffold) | `OrgExerciseLog` + `OrgSetEntry` (level 3+4) | Enables per-set logging, PR tracking |
| Timer as composable-local state | Timer in ViewModel `StateFlow` | Survives rotation |
| All exercise data in org files | Templates hardcoded in Kotlin | Simpler; no template editing needed v1 |

**Deprecated/outdated:**
- `OrgExerciseEntry` (the existing model in `OrgModels.kt`): replace with `OrgExerciseLog` + `OrgSetEntry`. Keep backward-compat parsing for old flat entries.

---

## Open Questions

1. **Session draft survival across process death**
   - What we know: ViewModel survives rotation; process death during active session would lose draft.
   - What's unclear: Is process-death data loss acceptable for active workout sessions?
   - Recommendation: Yes, acceptable for v1. Sessions are short (60–90 min); user can re-log. Don't over-engineer with `SavedStateHandle` for complex session state.

2. **Home screen integration (WORK-08)**
   - What we know: Home screen shows today's planned workout + last session summary.
   - What's unclear: Is the home screen the `FoodLog` screen (with a workout card added), or a new `HomeScreen` with tabs/navigation to food log?
   - Recommendation: Planner should decide. Adding a workout summary card to the existing `FoodLog` screen is simpler (no new screen). A proper home screen with bottom nav is cleaner but out of scope for Phase 3.

3. **Calisthenics "max reps" target display**
   - What we know: Template says "3x max reps" for pull-ups. The user logs actual reps achieved.
   - What's unclear: How does the UI surface the target? Show "max reps" as placeholder text in the reps field?
   - Recommendation: `ExerciseDefinition.targetReps = -1` to indicate "max reps". UI shows "—" or "max" as placeholder. No special handling needed in the org file; just log actual reps.

4. **Org file format for workout session header**
   - What we know: The current `OrgDateSection` has only `meals` and `exercises` fields. A workout session has metadata: split day, duration, total volume.
   - What's unclear: Should session metadata live in a property drawer on the `** Workout` heading, or in a separate level-2 heading?
   - Recommendation: Property drawer on `** Workout` heading. Consistent with how food log uses property drawers for all structured data (prior decision from Phase 1).

---

## Sources

### Primary (HIGH confidence)
- Existing codebase (`OrgModels.kt`, `OrgParser.kt`, `OrgWriter.kt`, `OrgSchema.kt`, `OrgFoodRepository.kt`, `FoodLogViewModel.kt`) — direct inspection, all patterns verified.
- Official Android docs: ViewModel/viewModelScope — `https://developer.android.com/reference/kotlin/androidx/lifecycle/ViewModel`
- Official Android docs: Compose side-effects (LaunchedEffect) — `https://developer.android.com/develop/ui/compose/side-effects`
- Official Android docs: DataStore Preferences — `https://developer.android.com/topic/libraries/architecture/datastore`
- Official Android docs: Compose state — `https://developer.android.com/develop/ui/compose/state`
- `libs.versions.toml` — all dependency versions confirmed.

### Secondary (MEDIUM confidence)
- Material3 `BottomSheetScaffold` — existence confirmed in Material3 BOM; exact experimental status not verified from docs page (404). Mark `@OptIn(ExperimentalMaterial3Api::class)` defensively.
- `LazyColumn` sticky headers pattern — confirmed present in Compose docs; exact API matches Phase 2 usage.

### Tertiary (LOW confidence)
- `BottomSheetScaffold` peek height and `SheetValue.Hidden` — inferred from known Material3 API patterns; validate in Android Studio before coding.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all components are in existing BOM; no new deps needed
- Architecture: HIGH — directly mirrors food repository pattern from Phase 2; verified against codebase
- OrgEngine redesign: HIGH — based on direct inspection of existing parser/writer/schema
- Rest timer: HIGH — viewModelScope coroutine pattern verified against official docs
- Pitfalls: HIGH — derived from direct codebase analysis

**Research date:** 2026-04-11
**Valid until:** 2026-05-11 (stable stack; no fast-moving dependencies)
