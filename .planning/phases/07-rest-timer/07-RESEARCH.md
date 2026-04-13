# Phase 7: Rest Timer - Research

**Researched:** 2026-04-13
**Domain:** Android countdown timer, foreground service, vibration/sound, Compose UI state
**Confidence:** HIGH (core patterns), MEDIUM (foreground service details)

## Summary

Phase 7 adds a rest timer between workout sets. The core implementation is a ViewModel-hosted countdown using `viewModelScope` + coroutine `delay(1000)` + `MutableStateFlow<TimerState>`. This pattern is well-established and fits the existing codebase's ViewModel/StateFlow architecture perfectly. No new libraries are needed for the in-app timer itself.

The two areas requiring more care are: (1) **foreground service** for background notification (off by default per context decisions — the simpler in-app path is the primary path), and (2) **per-exercise rest duration** which requires extending `LibraryExercise` and `SerializableLibraryExercise` with a new `restSecs: Int?` field.

The app bar amber color change, countdown text on exercise cards, and tap-to-adjust popup are all pure Compose UI — straightforward to implement using existing patterns in the codebase.

**Primary recommendation:** Implement the timer entirely in `WorkoutLogViewModel` as a `TimerState` StateFlow. The foreground service is a feature-flagged optional path (settings toggle), not the primary path — scope it separately and defer it if needed.

## Standard Stack

### Core (no new dependencies needed)
- `kotlinx.coroutines` — already in project. `viewModelScope.launch`, `delay(1000)`, `Job` cancellation.
- `kotlinx.coroutines.flow.MutableStateFlow` — already used throughout the app for all ViewModel state.
- `androidx.compose.material3.TopAppBar` + `TopAppBarDefaults.topAppBarColors(containerColor = ...)` — already used in `WorkoutLogScreen.kt`.
- `android.os.Vibrator` / `VibrationEffect` — built into Android SDK (minSdk = 26, `VibrationEffect` available from API 26). Needs `VIBRATE` permission in manifest.
- `android.media.RingtoneManager` + `Ringtone` — for optional sound-on-completion. Built into Android SDK.

### Supporting (optional foreground service path)
- `android.app.Service` subclass — for background notification when user enables that setting.
- `android.app.NotificationChannel` + `NotificationCompat` (already part of `androidx.core.ktx` in the project) — for the persistent foreground notification.
- `FOREGROUND_SERVICE` permission + `foregroundServiceType="shortService"` — manifest additions.

### Alternatives Considered
- `CountDownTimer` (android.os) — Don't use. Less flexible, harder to cancel/reset mid-count, no coroutine integration.
- `Handler.postDelayed` — Legacy. Coroutines handle this better.
- External timer library — Not needed. Standard coroutines are sufficient.

**Installation:** No new Gradle dependencies required. All needed APIs are in the existing dependency set.

## Architecture Patterns

### Recommended Project Structure
```
features/workoutlog/
├── WorkoutLogViewModel.kt        # Add TimerState + timer coroutine logic HERE
├── WorkoutLogUiState.kt          # Add timerState field to DayLoaded (or keep separate)
├── WorkoutLogScreen.kt           # Render countdown text + amber app bar
├── TimerAdjustSheet.kt           # NEW: bottom sheet for ±15s/±30s + free input
└── RestTimerService.kt           # NEW (optional path): foreground service for bg notification

data/workout/
└── ExerciseLibrary.kt            # Add restSecs: Int? = null to LibraryExercise

data/workout/
└── OrgWorkoutRepository.kt       # Extend SerializableLibraryExercise with restSecs

preferences/
└── AppPreferencesRepository.kt   # Add: timerEnabled, autoStart, notificationType,
                                  # backgroundNotification DataStore keys
                                  # (DEFAULT_REST_TIMER_SECS already exists)
```

### Pattern 1: ViewModel-Hosted Countdown

The timer lives entirely in `WorkoutLogViewModel`. A single coroutine `Job` controls the countdown. Cancelling the Job stops the timer. Starting a new countdown cancels the previous Job first (one global timer).

```kotlin
// Source: established Kotlin coroutine + StateFlow ViewModel pattern
// (verified via dev.to/aniketsmk and proandroiddev.com)

sealed interface TimerState {
    data object Idle : TimerState
    data class Running(val remainingSecs: Int, val totalSecs: Int) : TimerState
    data object Done : TimerState
}

private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)
val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

private var timerJob: Job? = null

fun startTimer(durationSecs: Int) {
    timerJob?.cancel()
    timerJob = viewModelScope.launch {
        for (remaining in durationSecs downTo 1) {
            _timerState.value = TimerState.Running(remaining, durationSecs)
            delay(1_000L)
        }
        _timerState.value = TimerState.Done
        onTimerComplete()
        delay(2_500L)  // "Done" shown for 2.5 seconds (within 2-3s range per context)
        _timerState.value = TimerState.Idle
    }
}

fun dismissTimer() {
    timerJob?.cancel()
    timerJob = null
    _timerState.value = TimerState.Idle
}
```

**Key:** `viewModelScope` ensures the timer survives configuration changes (rotation, language). The coroutine is auto-cancelled when the ViewModel is destroyed (no leaks).

### Pattern 2: Amber App Bar via Dynamic containerColor

The top app bar already uses `TopAppBarDefaults.topAppBarColors(containerColor = ...)` in `WorkoutLogScreen.kt`. The amber color is driven directly from the `timerState` StateFlow collected in the composable.

```kotlin
// Source: developer.android.com/develop/ui/compose/components/app-bars
val timerState by viewModel.timerState.collectAsStateWithLifecycle()

val appBarColor = when (timerState) {
    is TimerState.Running, is TimerState.Done ->
        Color(0xFFE65100)  // Deep amber/orange — "rest" signal
    else -> MaterialTheme.colorScheme.surface  // Normal
}

TopAppBar(
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = appBarColor
    ),
    ...
)
```

The `containerColor` parameter accepts any `Color` — passing a state-derived color directly is the standard Compose pattern. No animation boilerplate required (Compose recomposes automatically).

### Pattern 3: Countdown Text Inline on Exercise Card

The countdown is shown in the `ExerciseCard` header row, next to the exercise name. The timer is global (one active at a time), so the card showing the countdown is the one whose set was most recently logged.

Track `activeTimerExerciseId: Long?` in the ViewModel alongside `timerState`. In `ExerciseCard`, show the countdown text only when `timerState is Running && exerciseId == activeTimerExerciseId`.

Format: `"${remaining / 60}:${String.format("%02d", remaining % 60)}"` — e.g. "1:30".

### Pattern 4: Vibration on Timer Completion

```kotlin
// Source: developer.android.com/reference/android/os/VibrationEffect
// minSdk = 26, VibrationEffect available from API 26 — no version gate needed for this project

private fun vibrate(context: Context) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    val effect = VibrationEffect.createOneShot(400L, VibrationEffect.DEFAULT_AMPLITUDE)
    vibrator.vibrate(effect)
}
```

**Manifest addition needed:**
```xml
<uses-permission android:name="android.permission.VIBRATE" />
```

`VIBRATE` does not require a runtime permission dialog — it is an install-time permission. Safe to add unconditionally.

For sound, use `RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)).play()`. Handles silent mode appropriately.

### Pattern 5: Per-Exercise Rest Duration

Add `restSecs: Int? = null` to `LibraryExercise` (null = use global default). Extend `SerializableLibraryExercise` in `OrgWorkoutRepository.kt` with the same field (with default = null for backward compat with existing JSON). The `kotlinx.serialization` `@Serializable` annotation handles null fields gracefully with default values — existing persisted JSON without `restSecs` will deserialize cleanly.

```kotlin
// In OrgWorkoutRepository.kt (existing pattern, extend it):
@Serializable
private data class SerializableLibraryExercise(
    val name: String,
    val categoryLabel: String,
    val muscleGroups: List<String> = emptyList(),
    val isBuiltIn: Boolean = false,
    val restSecs: Int? = null   // NEW — null = use global default
)
```

### Pattern 6: Settings Preferences (new DataStore keys)

The `AppPreferencesRepository` already has `DEFAULT_REST_TIMER_SECS` (90s default). Add:

```kotlin
val TIMER_ENABLED = booleanPreferencesKey("rest_timer_enabled")           // master on/off
val TIMER_AUTO_START = booleanPreferencesKey("rest_timer_auto_start")     // auto vs manual
val TIMER_NOTIFICATION_TYPE = stringPreferencesKey("rest_timer_notif_type") // VIBRATION/SOUND/BOTH/NONE
val TIMER_BG_NOTIFICATION = booleanPreferencesKey("rest_timer_bg_notif")  // foreground service toggle
```

Follow the exact same pattern as existing `defaultRestTimerSecs` flow.

### Pattern 7: Foreground Service (optional, off by default)

**Only needed when user enables "Background notification" in settings.** This is a narrow, optional code path.

```xml
<!-- AndroidManifest.xml additions for background notification path -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".features.workoutlog.RestTimerService"
    android:foregroundServiceType="shortService"
    android:exported="false" />
```

`shortService` is the right type — it's for brief, user-initiated work. It requires no type-specific permission beyond `FOREGROUND_SERVICE`. The ~3-minute timeout is well beyond any rest timer duration (typically 60-180 seconds). Must call `stopSelf()` in `onTimeout()` callback.

The service communicates timer state back to the UI via a `SharedFlow` or bound service pattern. For simplicity, a `companion object` singleton `StateFlow` that both the service and ViewModel observe is a common approach in fitness apps.

**Note:** POST_NOTIFICATIONS requires a runtime permission dialog on Android 13+ (API 33+). The app targets API 35. Must request this permission before starting the service for background notifications.

### Anti-Patterns to Avoid

- **Storing timer state in Composable local state:** Timer must survive recomposition and configuration changes. Always host in ViewModel.
- **Using `CountDownTimer` (android.os):** Difficult to cancel mid-count, no Flow integration, prone to drift.
- **Launching the timer in a LaunchedEffect in Compose:** LaunchedEffect cancels on recomposition/navigation. The ViewModel is the correct home.
- **Making the foreground service the primary timer driver:** Foreground service is the optional background path. The in-app timer runs in the ViewModel; the service is only for showing a persistent notification when backgrounded.
- **Assuming `shortService` supports sticky/restart:** `shortService` does NOT support `START_STICKY`. The service stops when done. This is correct behavior for a rest timer.

## Don't Hand-Roll

- **Backward-compatible JSON deserialization with new fields:** `kotlinx.serialization` handles this with default parameter values in `@Serializable` data classes — no custom deserializer needed.
- **Coroutine cancellation on ViewModel destroy:** `viewModelScope` handles this automatically — don't add manual `onCleared()` cleanup unless holding non-coroutine resources.
- **Vibration on API 26+:** Use `VibrationEffect.createOneShot` — don't use the deprecated `Vibrator.vibrate(long)` overload.

**Key insight:** The timer itself (countdown + StateFlow) is 20-30 lines of idiomatic Kotlin. The complexity in this phase is in the settings integration, the per-exercise duration field, and the optional foreground service — not the timer logic itself.

## Common Pitfalls

### Pitfall 1: Timer Drift from `delay(1000)`

**What goes wrong:** `delay(1000)` is not perfectly precise. Over 90 seconds, drift can accumulate to several hundred milliseconds, making the display feel slow.

**Why it happens:** Coroutine `delay` is a scheduling hint, not a hard deadline. CPU load or GC pauses can delay resumption.

**How to avoid:** Track wall-clock start time and compute `remaining = startTime + totalMs - System.currentTimeMillis()` each tick, rather than counting ticks. This corrects for drift.

**Warning signs:** Timer visually running "slow" compared to a phone clock.

### Pitfall 2: Job Leak on Multiple Timer Starts

**What goes wrong:** Starting a second timer without cancelling the first leaves two coroutines running, both updating `_timerState`. The second one "wins" for display but the first keeps running in the background.

**How to avoid:** Always `timerJob?.cancel()` before launching a new `timerJob`. This is shown explicitly in Pattern 1 above.

### Pitfall 3: POST_NOTIFICATIONS Permission Not Requested Before Background Service

**What goes wrong:** On Android 13+ (API 33+), posting notifications without the `POST_NOTIFICATIONS` runtime permission causes the notification to be silently dropped. The foreground service itself starts but shows no notification visible to the user.

**How to avoid:** Request `POST_NOTIFICATIONS` at runtime before enabling background notification mode. Use `ActivityCompat.requestPermissions` or Compose's `rememberPermissionState` (needs `accompanist-permissions` or the new Activity Result API).

**Warning signs:** Foreground service starts without crashing but user sees no notification.

### Pitfall 4: SerializableLibraryExercise Not Updated When LibraryExercise Gets restSecs

**What goes wrong:** `LibraryExercise.restSecs` is added but `SerializableLibraryExercise` is not updated. User-created exercise rest durations are silently dropped on save/load cycles.

**How to avoid:** Update both `SerializableLibraryExercise`, `toSerializable()`, and `toLibraryExercise()` extension functions together. The `restSecs` field must have `= null` as a default in the serializable class for backward compat.

### Pitfall 5: shortService ~3-Minute Timeout

**What goes wrong:** If the rest timer foreground service is started and the user doesn't complete the workout within ~3 minutes, the system calls `onTimeout()` and the service is killed.

**Why it happens:** `shortService` type is intentionally restricted to brief operations. ~3 minutes is the approximate limit (exact value undocumented but confirmed by official docs).

**How to avoid:** This is actually fine for rest timers (typical rest = 60-180 seconds). Implement `onTimeout()` to call `stopSelf()` gracefully. The in-app timer in the ViewModel continues running regardless — the foreground service is only for the background notification.

**Warning signs:** Service being killed unexpectedly during long rests (>3 min). If users regularly rest longer than 3 minutes, reconsider the foreground service approach.

## Code Examples

Verified patterns from official sources and established practice:

### Timer State Sealed Interface
```kotlin
// Fits existing sealed interface patterns in WorkoutLogUiState.kt
sealed interface TimerState {
    data object Idle : TimerState
    data class Running(val remainingSecs: Int, val totalSecs: Int) : TimerState
    data object Done : TimerState
}
```

### Drift-Corrected Countdown Coroutine
```kotlin
// Source: corrects for delay() imprecision
fun startTimer(durationSecs: Int, exerciseId: Long) {
    timerJob?.cancel()
    _activeTimerExerciseId.value = exerciseId
    timerJob = viewModelScope.launch {
        val startMs = System.currentTimeMillis()
        val endMs = startMs + durationSecs * 1_000L
        while (true) {
            val nowMs = System.currentTimeMillis()
            val remainingMs = endMs - nowMs
            if (remainingMs <= 0) break
            val remainingSecs = ((remainingMs + 999) / 1000).toInt()  // ceil
            _timerState.value = TimerState.Running(remainingSecs, durationSecs)
            delay(minOf(remainingMs, 200L))  // Update up to 5x/sec for smooth display
        }
        _timerState.value = TimerState.Done
        triggerCompletionFeedback()
        delay(2_500L)
        _timerState.value = TimerState.Idle
        _activeTimerExerciseId.value = null
    }
}
```

### Dynamic App Bar Color
```kotlin
// Source: developer.android.com/develop/ui/compose/components/app-bars
val isTimerActive = timerState is TimerState.Running || timerState is TimerState.Done
val appBarColor = if (isTimerActive) Color(0xFFBF360C) else MaterialTheme.colorScheme.surface
// 0xFFBF360C = Deep Orange 900 — warm amber signal that reads clearly in dark theme

TopAppBar(
    colors = TopAppBarDefaults.topAppBarColors(containerColor = appBarColor),
    ...
)
```

### Vibration Trigger
```kotlin
// Source: developer.android.com/reference/android/os/VibrationEffect
// minSdk = 26 — VibrationEffect always available, no version gate needed
fun triggerVibration(context: Context) {
    val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    vib.vibrate(VibrationEffect.createOneShot(400L, VibrationEffect.DEFAULT_AMPLITUDE))
}
```

### Manifest Additions
```xml
<!-- VIBRATE: install-time only, no dialog -->
<uses-permission android:name="android.permission.VIBRATE" />

<!-- For background notification path only: -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".features.workoutlog.RestTimerService"
    android:foregroundServiceType="shortService"
    android:exported="false" />
```

## State of the Art

- **Old approach:** `CountDownTimer` (android.os) — synchronous, callback-based, hard to cancel
- **Current approach:** ViewModel coroutine + `MutableStateFlow` + `delay()` — first-class Kotlin, lifecycle-safe, easy to test
- **`CountDownTimer` status:** Not deprecated but considered legacy in modern Kotlin apps

## Open Questions

1. **Foreground service timer sync with ViewModel timer**
   - What we know: Service and ViewModel both need to show the same countdown state. Common patterns use a shared `object` with a `MutableStateFlow` or a bound service.
   - What's unclear: The cleanest approach for this specific codebase (no Hilt, manual DI).
   - Recommendation: Use a singleton `object RestTimerRepository` with a `MutableStateFlow<TimerState>` that both the service and ViewModel observe. The ViewModel drives the timer; the service reads from it to update the notification.

2. **Per-exercise rest duration in the exercise library UI**
   - What we know: The context says "Per-exercise defaults configured in the exercise library (each exercise has a rest duration field)".
   - What's unclear: There is no exercise library settings screen yet. Does this phase need to add one, or just add the field and expose it only in the create-exercise flow?
   - Recommendation: Add `restSecs` to the data model and the create-exercise sheet (`ExercisePickerSheet`). A separate "exercise library management" screen is out of scope for this phase.

3. **Sound playback: RingtoneManager vs SoundPool vs MediaPlayer**
   - What we know: For a single short completion beep, all three work. `RingtoneManager` is the simplest.
   - What's unclear: Whether `RingtoneManager` respects the user's notification sound setting (it does for `TYPE_NOTIFICATION`).
   - Recommendation: Use `RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(TYPE_NOTIFICATION)).play()`. One line, respects user audio settings, no resource cleanup needed.

## Sources

### Primary (HIGH confidence)
- `developer.android.com/develop/ui/compose/components/app-bars` — TopAppBarDefaults.topAppBarColors parameter list verified
- `developer.android.com/develop/background-work/services/fgs/service-types#short-service` — shortService type, ~3 min timeout, permissions, onTimeout() requirement
- `developer.android.com/reference/android/os/VibrationEffect` — createOneShot API, API 26 minimum
- `developer.android.com/develop/ui/compose/side-effects` — LaunchedEffect / rememberCoroutineScope patterns

### Secondary (MEDIUM confidence)
- `dev.to/aniketsmk/kotlin-flow-implementing-an-android-timer-ieo` — countdown Flow pattern, downTo + delay pattern
- `developer.android.com/develop/background-work/services/fgs/timeout` — foreground service timeout behavior (shortService details deferred to service-types page)

### Tertiary (LOW confidence)
- General WebSearch results on VibrationEffect Compose integration — corroborates API 26 pattern but no single authoritative URL

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries are already in the project or part of the Android SDK at minSdk=26
- Architecture (timer in ViewModel): HIGH — well-established pattern, matches existing codebase style
- Architecture (foreground service): MEDIUM — shortService documented, but sync pattern between service and ViewModel is design work
- Pitfalls: HIGH — all verified against official documentation or direct code inspection of the codebase

**Research date:** 2026-04-13
**Valid until:** 2026-07-13 (stable APIs, unlikely to change)
