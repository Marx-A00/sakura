# Phase 7: Rest Timer - Context

**Gathered:** 2026-04-13
**Status:** Ready for planning

<domain>
## Phase Boundary

Implement a workout rest timer between sets (WORK-07). User can start a configurable countdown, see it on the exercise card, get notified on completion, and adjust duration per exercise or globally. Timer does not block set logging.

</domain>

<decisions>
## Implementation Decisions

### Timer trigger & lifecycle
- Settings toggle: auto-start after logging a set OR manual tap to start (user chooses in settings)
- Master on/off toggle in settings — when off, no timer UI appears at all
- One global timer — persists across exercise switches, only one active at a time
- Logging a new set while timer is running resets and restarts the timer (if auto-start enabled) or dismisses it (if manual)
- User can tap to dismiss/skip the timer at any time mid-countdown

### Visual presentation
- Countdown text (e.g. "1:30") displayed inline on the exercise card, in the header area next to the exercise name
- Simple text — no circular progress ring or animation
- Top app bar changes to a warm amber/orange color while timer is active, signaling "resting" state globally
- App bar returns to normal color when timer completes or is dismissed

### Completion notification
- Settings option for notification type: vibration, sound, both, or none
- Background notification toggle in settings (off by default) — when enabled, uses a foreground service to notify even when app is backgrounded
- On completion: countdown text changes to "Done" for 2-3 seconds, then disappears; app bar returns to normal color

### Duration configuration
- Per-exercise rest duration field in the exercise library + global fallback default
- Global default: 90 seconds
- In-workout adjustment: tap the timer to open a menu with quick-adjust buttons (±15s/±30s) and a free-form time input
- In-workout adjustments are one-time overrides only — saved defaults stay unchanged
- Per-exercise defaults configured in the exercise library (each exercise has a rest duration field)

### Claude's Discretion
- Quick-adjust button increments (±15s vs ±30s or both)
- Timer menu/popup design details
- Foreground service notification channel configuration
- "Done" text exact display duration within 2-3 second range
- Amber/orange exact color value

</decisions>

<specifics>
## Specific Ideas

- Timer should feel lightweight — simple text countdown on the card, not a big modal or overlay
- The warm amber app bar color change is a passive global signal, not an intrusive alert
- Tap-to-adjust menu bridges quick-adjust convenience with free-form precision

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 07-rest-timer*
*Context gathered: 2026-04-13*
