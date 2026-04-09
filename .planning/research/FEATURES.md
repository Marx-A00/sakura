# Feature Research

**Domain:** Personal iOS food/nutrition and workout tracking app (single user, org-mode output)
**Researched:** 2026-04-09
**Confidence:** MEDIUM — food/workout app feature landscape is well-documented; org-mode integration is novel (no direct comparables)

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features any credible tracking app must have. Missing these makes the product feel broken, not just incomplete.

#### Food / Nutrition

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Log food by name/search | Core mechanic — can't track without entry | LOW | For v1: manual search against local/embedded USDA or Open Food Facts data |
| Macro display: protein, carbs, fat, calories | The whole point of macro tracking | LOW | Four numbers, shown per meal and as daily totals |
| Daily macro targets | Users need a goal to track against | LOW | Static targets for v1 (e.g., 180g protein, 2400 kcal) |
| Meal groupings (breakfast, lunch, dinner, snacks) | Users think in meals, not individual foods | LOW | Org-mode naturally maps to headings per meal |
| Custom food entry (manual macros) | Whole foods, home cooking, restaurant meals | LOW | Name + P/C/F/kcal fields; critical for v1 |
| Quick add calories/macros | Rough logging when details don't matter | LOW | Enter raw numbers without a named food |
| Today's summary view | Know where you stand right now | LOW | Sum of logged macros vs. targets, remaining shown |
| View past day's log | Did I actually eat well this week? | LOW | Read org files back; history requires two-way sync |
| Edit/delete a logged entry | Mistakes happen | LOW | Modify org file entry |

#### Workout

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Log sets, reps, weight per exercise | Core mechanic | LOW | The fundamental unit of strength tracking |
| Show previous session's weights/reps | Non-negotiable for progressive overload | LOW | Auto-fill from last logged session; apps that omit this are "useless for progressive overload" per user research |
| Workout templates / routines | Nobody logs from scratch every session | LOW | User has an existing 4-day full-body split to encode |
| Exercise library | Know what exercises exist | LOW | Can start small — user's actual exercises |
| Personal records (PRs) | Automatic recognition of new bests | LOW | Track 1RM, 5RM, or set/rep PRs per exercise |
| Workout history | Review past sessions | LOW | Reading back from org files |
| Rest timer | Standard gym UX | LOW | Simple countdown; optional but expected |
| Today's planned workout | What am I doing today? | LOW | Home screen showing today's split day |

---

### Differentiators (Competitive Advantage)

Features that distinguish Origami given its specific context: single user, full data ownership, org-mode output, Emacs workflow integration.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Org-mode output (writes .org files) | Data lives in Emacs — permanent, portable, zero lock-in | MEDIUM | The core architectural differentiator; no commercial app does this |
| Two-way org sync (reads history back) | App becomes a window into your existing Emacs data | MEDIUM | Requires org-mode parser in Swift; enables history/progress views without a separate DB |
| iCloud Drive as storage | Files accessible from Emacs/Mac immediately after logging | LOW | Standard iOS API; no custom server needed for v1 |
| Unified food + workout in one app | One cohesive daily picture | MEDIUM | Most apps are food-only or workout-only; combining avoids context-switching |
| Home screen: today at a glance | Macros so far + today's workout in one view | LOW | Surfaces the two most time-sensitive data points |
| No account required | Privacy by default; no data leaves device except to your own iCloud | LOW | Consequence of the org-file architecture |
| Full data portability always | Export is the format — org files are the data | LOW | Contrast: MyFitnessPal locks export behind $20/mo premium and caps at 90 days |
| Meal templates / saved meals | Repeat meals (e.g., "usual breakfast") logged in seconds | LOW | Reduces daily friction significantly |
| Previous-session auto-fill for workouts | Weights and reps pre-populated from last time | LOW | See table stakes — this is expected but explicitly flagged as the most valuable workout feature |
| Volume tracking per session | Total weight moved (sets × reps × weight) | LOW | Simple calculation; meaningful for progressive overload decisions |
| Training split awareness | App knows it's Day 3 of a 4-day split | LOW | User has a defined split; app can suggest correct day |

---

### Anti-Features (Deliberately NOT Build)

Features that seem logical but would undermine the project's goals or add disproportionate complexity.

| Anti-Feature | Why Requested | Why Problematic | Alternative |
|--------------|---------------|-----------------|-------------|
| Barcode scanner (food) | Fast logging of packaged foods | Requires large, maintained food database; OCR/camera infrastructure; USDA/Open Food Facts API dependency; significant scope for v1 | Manual entry and custom foods cover 80% of use cases for a personal app; add barcode in v1.x once core loop works |
| AI photo logging | Frictionless meal capture | Requires ML model or cloud API; accuracy is unreliable; adds latency and cost; overkill for single-user personal app | Manual entry is fast enough with good UX; custom meals eliminate repeat friction |
| Social features (sharing, leaderboards, friends) | Motivation through community | Antithetical to personal/private design; no other users; network effects don't apply | The personal data ownership IS the social differentiator — this app is for you, not an audience |
| Adaptive calorie targets (TDEE calculation) | Automatically adjusts goals based on activity | Requires weigh-in tracking, algorithm complexity, Apple Health deep integration; v1 complexity | Static targets are fine for v1; revisit in v2 with Apple Health weight data |
| Meal planning / recipe generation | Plan the week ahead | Out of scope; planning is a separate workflow from tracking | User tracks what they ate, not plans what to eat |
| Gamification (streaks, badges, levels) | Motivation | Adds UI complexity; streaks create anxiety; doesn't match the reflective, Emacs-user mindset | Consistency is visible through org-mode history in Emacs |
| Apple Watch workout detection | Automatic workout logging | Requires WatchKit app; complex motion detection; likely wrong more than right | Manual logging from iPhone is faster and accurate for gym sets |
| Cloud food database (large, always-on API) | Large food database | Network dependency; API cost; rate limits; offline gym use broken | Embed a curated subset of USDA data locally; custom foods handle the rest |
| Notifications / reminders | "Log your lunch!" prompts | Annoying; not how power users work; adds permission complexity | User decides when to open the app |
| In-app subscription / paywall | Monetization | This is a personal app, not a product | N/A — no monetization needed |

---

## Feature Dependencies

```
[Org-mode file format]
    └──required by──> [Write food log to org]
    └──required by──> [Write workout log to org]
    └──required by──> [Read history from org]
                          └──enables──> [View past food logs]
                          └──enables──> [View workout history]
                          └──enables──> [Previous session auto-fill]
                          └──enables──> [PR tracking]

[iCloud Drive sync]
    └──required by──> [Read history from org]
    └──required by──> [Emacs round-trip]

[Food entry (manual)]
    └──required by──> [Today's food log]
    └──enhances via──> [Custom food library] (repeat meals faster)
    └──enhances via──> [Meal templates / saved meals]

[Workout templates / routines]
    └──required by──> [Today's planned workout]
    └──enables──> [Previous session auto-fill] (need a known exercise to look up)

[Training split definition]
    └──enables──> [Today's planned workout on home screen]
    └──enables──> [Correct day suggestion]

[Today's summary view]
    └──requires──> [Food entry]
    └──requires──> [Workout templates]
    └──enhances via──> [iCloud sync] (history visible)
```

### Dependency Notes

- **Org-mode file format is the foundation:** Every read/write feature depends on a stable, agreed-upon org-mode schema being defined first. This is the highest-risk architectural decision.
- **iCloud sync required for two-way history:** Without iCloud Drive access, the app can write-only. History, previous session auto-fill, and PR tracking all require reading back.
- **Previous session auto-fill requires workout templates:** The app needs to know which exercise maps to which session to look up history correctly.
- **Custom food library accelerates daily logging:** Not a blocker but a significant quality-of-life accelerant once core entry is working.

---

## MVP Definition

### Launch With (v1)

Minimum viable product: the app replaces the Apple Shortcuts hack with a cohesive, intentional interface. Validates that the org-mode round-trip works and that daily logging is fast.

- [ ] **Log food manually** (name + protein/carbs/fat/calories) — replaces Shortcuts friction
- [ ] **Log workout** (exercise, sets, reps, weight) using existing 4-day split templates — replaces Shortcuts friction
- [ ] **Write to org files** in iCloud Drive — the core architectural requirement
- [ ] **Today's home screen** — macros logged so far, today's workout — answers "where am I today?"
- [ ] **Read history from org** (for previous session auto-fill and workout history) — enables progressive overload
- [ ] **Custom food library** (save foods you eat regularly) — reduces repeat entry friction
- [ ] **Meal templates / saved meals** (e.g., "usual breakfast") — daily use accelerant
- [ ] **Daily macro targets** (static) — need a goal to track against
- [ ] **PR tracking** (automatic from workout history) — intrinsic motivation

### Add After Validation (v1.x)

Add when v1 is working and daily use patterns are clear.

- [ ] **Barcode scanner** — trigger: manual entry is the bottleneck for packaged foods
- [ ] **Richer analytics** (weekly macro averages, volume trends) — trigger: history data is accumulating and useful
- [ ] **Edit past entries** — trigger: mistakes in org files need in-app correction (vs. editing in Emacs)
- [ ] **Apple Health write** (steps, active calories) — trigger: wanting cross-app data coherence

### Future Consideration (v2+)

Defer until v1 is stable and direction is clear.

- [ ] **Local server sync** (v2 goal per project brief) — why defer: iCloud Drive covers the use case for now
- [ ] **Adaptive macro targets** (TDEE-based) — why defer: adds algorithmic complexity; static targets work
- [ ] **Apple Watch companion** — why defer: iPhone logging from gym is workable; Watch adds WatchKit scope
- [ ] **Micronutrient tracking** (vitamins, minerals beyond macros) — why defer: macro tracking is the stated v1 goal

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Manual food entry | HIGH | LOW | P1 |
| Workout log (sets/reps/weight) | HIGH | LOW | P1 |
| Write to org-mode file | HIGH | MEDIUM | P1 |
| Today's home screen summary | HIGH | LOW | P1 |
| Read org history (two-way sync) | HIGH | MEDIUM | P1 |
| Previous session auto-fill | HIGH | LOW | P1 (requires read) |
| Workout templates (4-day split) | HIGH | LOW | P1 |
| Daily macro targets | HIGH | LOW | P1 |
| Custom food library | HIGH | LOW | P1 |
| Meal templates / saved meals | MEDIUM | LOW | P1 |
| PR tracking | MEDIUM | LOW | P1 |
| Rest timer | MEDIUM | LOW | P2 |
| Barcode scanner | MEDIUM | MEDIUM | P2 |
| Weekly analytics / trends | MEDIUM | MEDIUM | P2 |
| Apple Health write | LOW | MEDIUM | P3 |
| Apple Watch companion | LOW | HIGH | P3 |
| Adaptive calorie targets | LOW | HIGH | P3 |

---

## Competitor Feature Analysis

This is a personal app, not competing for the App Store. The comparison is framed as "what do commercial apps do that informs what Origami should or shouldn't do?"

| Feature | MyFitnessPal | Hevy / Strong | Origami Approach |
|---------|--------------|---------------|------------------|
| Food database | Massive (crowdsourced, noisy) | N/A | Small local embed (USDA subset) + custom foods; prioritize accuracy over breadth |
| Workout logging | Basic, secondary feature | Core; fast, set-by-set | Core; fast, set-by-set; org-file output |
| Data export | Premium-gated, CSV, 90-day cap | CSV export (varies) | Export IS the format — org files are always accessible |
| Data ownership | Vendor-controlled | Vendor-controlled | Full — files live in iCloud Drive |
| Social features | Heavy | Hevy: social core; Strong: minimal | None — deliberate |
| Barcode scan | Yes | N/A | v1.x, not v1 |
| History / analytics | Yes (paywalled) | Yes (varying depth) | Yes — read from org files |
| Emacs / plain text integration | No | No | Yes — the entire point |
| Account required | Yes | Yes | No — iCloud Drive is the identity |
| Combined food + workout | Yes (weak workout) | No | Yes — unified daily view |

---

## Sources

- Nutrola Calorie Tracker Feature Comparison Matrix 2026: https://www.nutrola.app/en/blog/calorie-tracker-feature-comparison-matrix-2026
- Stronger Mobile: Best Workout Tracker Apps 2026: https://www.strongermobileapp.com/blog/best-workout-tracker-apps
- Setgraph: Hevy vs Strong App Comparison 2026: https://setgraph.app/ai-blog/hevy-vs-strong-app-comparison-2026
- Hevy: Best Workout Tracker App: https://www.hevyapp.com/best-workout-tracker-app/
- PRPath: Best Progressive Overload Trackers 2026: https://prpath.app/blog/best-progressive-overload-trackers-2026.html
- Foodnoms (data ownership model): https://foodnoms.com/
- Quantified Self: How to Access & Export MyFitnessPal Data: https://quantifiedself.com/blog/access-export-myfitnesspal-data/
- MyFitnessPal Data Export FAQs: https://support.myfitnesspal.com/hc/en-us/articles/360032273352-Data-Export-FAQs
- Askvora: Best Strength Training Apps 2026: https://askvora.com/blog/best-strength-training-apps-2026
- Fitbod: Best Workout Tracker Apps for 2026: https://fitbod.me/blog/best-workout-tracker-apps-for-2026/

---

*Feature research for: Personal iOS food/nutrition and workout tracking app (Origami)*
*Researched: 2026-04-09*
