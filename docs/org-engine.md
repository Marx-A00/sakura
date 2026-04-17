# The Org-File Persistence Layer

Sakura stores all data in plain-text `.org` files instead of a database. This doc explains the file format, the parser, and the writer.

## Why .org Files?

In React, you'd typically store data in a backend database or localStorage/IndexedDB. Sakura uses Emacs Org-mode files because:

- **Human-readable** — open `food-log.org` in any text editor and see your data
- **Emacs-native** — Org-mode tables, Org-agenda, and other Emacs tools can consume the data directly
- **Syncthing-compatible** — plain text syncs cleanly across devices (SQLite databases can't be synced without corruption risk)
- **No migrations** — adding new properties doesn't break old parsers (they just skip unknown keys)

The tradeoff is no indexing, no query language, and every read parses the whole file. Fine for personal tracking, not for scale.

## The Files

Sakura manages these org files:

- **`food-log.org`** — daily food entries, organized by date and meal
- **`workout-log.org`** — daily workout sessions, organized by date, exercise, and set
- **`food-library.org`** — saved food items for quick-add
- **`meal-templates.org`** — saved meal templates for bulk-add

## File Format: food-log.org

```org
* <2026-04-09 Wed>
** Breakfast
*** Oatmeal with berries
:PROPERTIES:
:id: 1712635200000
:protein: 12
:carbs: 45
:fat: 6
:calories: 280
:serving_size: 60
:serving_unit: g
:END:
*** Coffee with milk
:PROPERTIES:
:id: 1712635200001
:protein: 2
:carbs: 3
:fat: 2
:calories: 40
:END:

** Lunch
*** Chicken and rice
:PROPERTIES:
:id: 1712661600000
:protein: 42
:carbs: 55
:fat: 8
:calories: 460
:notes: meal prep batch
:END:

* <2026-04-08 Tue>
** Dinner
*** Pizza
:PROPERTIES:
:id: 1712548800000
:protein: 20
:carbs: 60
:fat: 18
:calories: 480
:END:
```

### Heading Hierarchy (Food)

```
* <date>              ← Level 1: date heading
** Meal Label         ← Level 2: "Breakfast", "Lunch", "Dinner", or "Snacks"
*** Food Name         ← Level 3: individual food item
:PROPERTIES:          ← Property drawer (structured key-value data)
:key: value
:END:
```

The number of `*` indicates the heading level — like nested HTML headings (`<h1>`, `<h2>`, `<h3>`).

### Property Drawers

Property drawers are Org-mode's way of attaching structured data to a heading. They're always `:PROPERTIES:` → key-value pairs → `:END:`. Think of them as a JSON object embedded in a text document:

```org
*** Oatmeal
:PROPERTIES:
:id: 1712635200000
:protein: 12
:carbs: 45
:fat: 6
:calories: 280
:END:
```

Is conceptually:
```json
{
  "name": "Oatmeal",
  "id": 1712635200000,
  "protein": 12,
  "carbs": 45,
  "fat": 6,
  "calories": 280
}
```

## File Format: workout-log.org

Workouts have one more level of nesting (level 4 for individual sets):

```org
* <2026-04-09 Wed>
** Workout
:PROPERTIES:
:split_day: monday-lift
:volume: 12450
:duration_min: 62
:complete: true
:END:
*** Bench Press
:PROPERTIES:
:id: 1712635200000
:exercise_type: barbell
:category: weighted
:END:
**** Set 1
:PROPERTIES:
:reps: 5
:weight: 80
:unit: kg
:is_pr: false
:END:
**** Set 2
:PROPERTIES:
:reps: 5
:weight: 80
:unit: kg
:is_pr: false
:END:
**** Set 3
:PROPERTIES:
:reps: 4
:weight: 80
:unit: kg
:rpe: 9
:is_pr: false
:END:
*** Dead Hang
:PROPERTIES:
:id: 1712635200001
:exercise_type: calisthenics
:category: timed
:END:
**** Set 1
:PROPERTIES:
:reps: 0
:weight: 0
:unit: bw
:hold_secs: 45
:is_pr: false
:END:
```

### Heading Hierarchy (Workout)

```
* <date>              ← Level 1: date heading
** Workout            ← Level 2: session heading (with split_day, volume, duration)
*** Exercise Name     ← Level 3: exercise (with id, exercise_type, category)
**** Set N            ← Level 4: individual set (with reps, weight, unit, etc.)
```

## The Three Components

### OrgSchema — Format Specification

[OrgSchema.kt](../app/src/main/java/com/sakura/orgengine/OrgSchema.kt) is the single source of truth for the file format. Both the parser and writer reference it exclusively — no format strings are duplicated elsewhere.

It contains:
- **Date format**: `yyyy-MM-dd EEE` with `Locale.ENGLISH` (so "Thu" not "Qui" on Portuguese devices)
- **Heading regexes**: `DATE_HEADING_REGEX`, `MEAL_HEADING_REGEX`, `ITEM_HEADING_REGEX`, `SET_HEADING_REGEX`
- **Property key constants**: `PROP_PROTEIN`, `PROP_CARBS`, `PROP_ID`, etc.
- **Serialization functions**: `formatFoodEntry()`, `formatExerciseLog()`, `formatSetEntry()`, etc.

Example — the date heading regex:

```kotlin
val DATE_HEADING_REGEX = Regex("""^\* <(\d{4}-\d{2}-\d{2}) \w{3}>$""")
```

This matches `* <2026-04-09 Wed>` and captures `2026-04-09`.

Example — serializing a food entry:

```kotlin
fun formatFoodEntry(entry: OrgFoodEntry): String {
    val name = if (entry.name.isBlank()) "Unnamed" else entry.name
    val sb = StringBuilder()
    sb.append("*** $name\n")
    sb.append("$PROPERTIES_START\n")
    sb.append(":$PROP_ID: ${entry.id}\n")
    sb.append(":$PROP_PROTEIN: ${entry.protein}\n")
    sb.append(":$PROP_CARBS: ${entry.carbs}\n")
    sb.append(":$PROP_FAT: ${entry.fat}\n")
    sb.append(":$PROP_CALORIES: ${entry.calories}\n")
    entry.servingSize?.let { sb.append(":$PROP_SERVING_SIZE: $it\n") }
    entry.servingUnit?.let { sb.append(":$PROP_SERVING_UNIT: $it\n") }
    entry.notes?.let { sb.append(":$PROP_NOTES: $it\n") }
    sb.append(PROPERTIES_END)
    return sb.toString()
}
```

[OrgSchema.kt#L186](../app/src/main/java/com/sakura/orgengine/OrgSchema.kt#L186)

### OrgParser — Deserializer (Text → Models)

[OrgParser.kt](../app/src/main/java/com/sakura/orgengine/OrgParser.kt) is a `object` (singleton) that converts raw org text into an AST (Abstract Syntax Tree) of data classes.

**How it works**: It's a state machine that walks lines top-to-bottom. The current "state" tracks what heading level we're in and whether we're inside a property drawer.

```kotlin
object OrgParser {
    enum class ParseMode { FOOD, WORKOUT }

    fun parse(content: String, mode: ParseMode): OrgFile {
        if (content.isBlank()) return OrgFile(sections = emptyList())
        // State machine walks each line...
    }
}
```

**Parse flow for a food file**:

1. See `* <2026-04-09 Wed>` → match `DATE_HEADING_REGEX` → start new `OrgDateSection`
2. See `** Breakfast` → match `MEAL_HEADING_REGEX` → start new `OrgMealGroup`
3. See `*** Oatmeal` → match `ITEM_HEADING_REGEX` → remember item name
4. See `:PROPERTIES:` → enter drawer state
5. See `:protein: 12` → match `PROPERTY_REGEX` → accumulate key-value
6. See `:END:` → exit drawer state → build `OrgFoodEntry` from accumulated properties
7. Repeat until end of file

**Graceful degradation**: Unrecognized lines are silently skipped. This means:
- Extra blank lines don't cause errors
- Manual comments in the org file don't break parsing
- Future properties added by newer versions don't crash older parsers

### OrgWriter — Serializer (Models → Text)

[OrgWriter.kt](../app/src/main/java/com/sakura/orgengine/OrgWriter.kt) is also a singleton `object`. It takes `OrgFile` models and produces valid org text.

```kotlin
object OrgWriter {
    fun write(orgFile: OrgFile): String {
        if (orgFile.sections.isEmpty()) return ""
        return orgFile.sections.joinToString(separator = "\n\n") { writeSection(it) } + "\n"
    }
}
```

**Format rules** (from the docstring):
- Date sections separated by a single blank line
- Meal groups within a section separated by a single blank line
- No trailing whitespace on any line
- File ends with a single newline
- No blank line between a heading and its `:PROPERTIES:` drawer (Org-mode requirement)

## The AST (Intermediate Representation)

Between raw text and domain models sits the org AST — data classes in [OrgModels.kt](../app/src/main/java/com/sakura/orgengine/OrgModels.kt):

```
Raw text (.org file)
    │ OrgParser.parse()
    ▼
OrgFile
  └─ OrgDateSection (date, meals OR exerciseLogs)
       ├─ OrgMealGroup (label, entries)
       │    └─ OrgFoodEntry (name, protein, carbs, fat, calories, ...)
       └─ OrgExerciseLog (name, id, type, sets)
            └─ OrgSetEntry (setNumber, reps, weight, unit, ...)
    │ domain mapping (toFoodEntry(), etc.)
    ▼
Domain models (FoodEntry, MealGroup, ExerciseLog, SetLog)
```

The org models (`OrgFoodEntry`, `OrgExerciseLog`, etc.) are intentionally separate from the domain models (`FoodEntry`, `ExerciseLog`). This separation means:
- The org format can evolve independently of the domain
- Domain models don't need to know about org-specific fields (like legacy formats)
- Conversion functions (`toFoodEntry()`, `toOrgFoodEntry()`) are the only bridge

## Backward Compatibility

The workout format evolved over time:

**Legacy (flat format)** — exercises had sets/reps/weight directly on the exercise heading:
```org
*** Bench Press
:PROPERTIES:
:sets: 3
:reps: 5
:weight: 80
:unit: kg
:END:
```

**Current (per-set format)** — each set is its own level-4 heading:
```org
*** Bench Press
:PROPERTIES:
:id: 1712635200000
:exercise_type: barbell
:category: weighted
:END:
**** Set 1
:PROPERTIES:
:reps: 5
:weight: 80
:unit: kg
:END:
```

The parser handles both: if it sees `sets`/`reps`/`weight` on a level-3 heading, it creates a single-set `OrgExerciseLog` from the flat data. New entries always use the per-set format.

## Food Library & Templates

These use simpler file structures:

**food-library.org**:
```org
* Food Library
** Chicken Breast
:PROPERTIES:
:id: a1b2c3d4
:protein: 31
:carbs: 0
:fat: 3
:calories: 165
:serving_size: 100
:serving_unit: g
:END:
```

**meal-templates.org**:
```org
* Meal Templates
** Weekday Breakfast
:PROPERTIES:
:id: e5f6g7h8
:END:
*** Oatmeal
:PROPERTIES:
:protein: 5
:carbs: 27
:fat: 3
:calories: 150
:serving_size: 40
:serving_unit: g
:END:
```

Templates have an extra nesting level — the template heading (level 2) contains food items (level 3).

## Thread Safety

All file mutations in `OrgFoodRepository` and `OrgWorkoutRepository` are serialized through a `Mutex`:

```kotlin
private val fileMutex = Mutex()

override suspend fun addEntry(...): Result<Unit> {
    return fileMutex.withLock {
        // read → parse → modify → serialize → write
    }
}
```

This prevents two concurrent writes from doing a read-modify-write race. See [data-flow.md](data-flow.md#thread-safety) for more detail.
