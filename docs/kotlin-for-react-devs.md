# Kotlin for React Devs

A crash course on Kotlin concepts you'll encounter in Sakura, explained through the lens of JavaScript/TypeScript/React.

## Variables: `val` vs `var`

```kotlin
val name = "Sakura"    // like `const` — cannot be reassigned
var count = 0          // like `let` — can be reassigned
count = 5              // ✓
name = "Cherry"        // ✗ compile error
```

**Key difference from JS**: `val` means the *reference* can't change (like `const`), but if it holds a mutable object, the object's contents can still change. Exactly like `const arr = []; arr.push(1)` in JS.

## Data Classes — Your TypeScript Interfaces

In React, you'd define a type and create objects from it:

```typescript
// TypeScript
interface FoodEntry {
  id: number;
  name: string;
  protein: number;
  calories: number;
}
const entry: FoodEntry = { id: 123, name: "Rice", protein: 4, calories: 200 };
```

In Kotlin, `data class` does the same thing — but it's a real class with built-in `equals`, `copy`, `toString`, and destructuring:

```kotlin
// From FoodEntry.kt
data class FoodEntry(
    val id: Long,
    val name: String,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val calories: Int,
    val servingSize: String?,   // nullable — explained below
    val servingUnit: String?,
    val notes: String?
)
```

[FoodEntry.kt](../app/src/main/java/com/sakura/data/food/FoodEntry.kt#L10)

The `copy()` method is something you'd do with spread in JS:

```kotlin
// Kotlin
val updated = entry.copy(calories = 250)

// JavaScript equivalent
const updated = { ...entry, calories: 250 };
```

## Nullable Types — No More `undefined` Crashes

Kotlin has null safety built into the type system. A `String` can never be null. A `String?` (with the `?`) can be null.

```kotlin
val name: String = "Rice"     // cannot be null, ever
val notes: String? = null     // allowed because of the ?

// Safe call — like optional chaining in JS
val length = notes?.length    // null if notes is null, otherwise the length

// Elvis operator — like nullish coalescing (??)
val display = notes ?: "No notes"  // "No notes" if notes is null
```

**JS equivalent**:
```javascript
const display = notes ?? "No notes";
const length = notes?.length;
```

You'll see this all over the codebase — the `?` after a type name means "this could be null, handle it."

## Sealed Interfaces — Tagged Unions

In TypeScript, you might model loading states with a discriminated union:

```typescript
type UiState =
  | { type: 'loading' }
  | { type: 'success'; data: MealGroup[] }
  | { type: 'error'; message: string };
```

Kotlin's `sealed interface` does the exact same thing, but with exhaustive `when` checks:

```kotlin
// From FoodLogUiState.kt
sealed interface FoodLogUiState {
    data object Loading : FoodLogUiState
    data class Success(
        val meals: List<MealGroup>,
        val targets: MacroTargets
    ) : FoodLogUiState
    sealed interface Error : FoodLogUiState {
        data object FolderUnavailable : Error
        data class Generic(val message: String) : Error
    }
}
```

[FoodLogUiState.kt](../app/src/main/java/com/sakura/features/foodlog/FoodLogUiState.kt#L11)

Then in composables you pattern-match on it:

```kotlin
when (val state = uiState) {
    is FoodLogUiState.Loading -> LoadingSpinner()
    is FoodLogUiState.Success -> MealList(state.meals)
    is FoodLogUiState.Error.FolderUnavailable -> ErrorBanner("Folder unavailable")
    is FoodLogUiState.Error.Generic -> ErrorBanner(state.message)
}
```

The compiler forces you to handle every case — if you add a new variant and forget to handle it, you get a compile error. TypeScript can do this too with `never`, but Kotlin enforces it more strictly.

## `object` — Singletons

An `object` in Kotlin is a singleton — one instance, globally accessible. There's no `new`, no constructor calls.

```kotlin
// From OrgParser.kt
object OrgParser {
    fun parse(content: String, mode: ParseMode): OrgFile {
        // ...
    }
}

// Usage — just call it directly, no instantiation
val result = OrgParser.parse(fileContent, ParseMode.FOOD)
```

[OrgParser.kt](../app/src/main/java/com/sakura/orgengine/OrgParser.kt#L32)

**JS equivalent**: a module that exports functions directly:
```javascript
// orgParser.js
export function parse(content, mode) { ... }

// usage
import { parse } from './orgParser';
const result = parse(fileContent, 'FOOD');
```

## Enum Classes

Like TypeScript enums, but they can have properties and methods:

```kotlin
// From ExerciseType.kt
enum class ExerciseType(val label: String) {
    BARBELL("barbell"),
    DUMBBELL("dumbbell"),
    MACHINE("machine"),
    CALISTHENICS("calisthenics"),
    TIMED("timed"),
    CARDIO("cardio"),
    STRETCH("stretch");

    companion object {
        fun fromLabel(label: String): ExerciseType =
            entries.firstOrNull { it.label == label.lowercase() } ?: BARBELL
    }
}
```

[ExerciseType.kt](../app/src/main/java/com/sakura/data/workout/ExerciseType.kt#L11)

Each enum value carries a `label` property. The `companion object` holds static-like methods (more on that below). `entries` is the list of all enum values — like `Object.values(MyEnum)` in TS.

## `when` — Supercharged Switch

`when` is Kotlin's `switch`, but more powerful:

```kotlin
// From ExerciseType.kt — extension function using when
fun ExerciseType.toCategory(): ExerciseCategory = when (this) {
    ExerciseType.BARBELL, ExerciseType.DUMBBELL, ExerciseType.MACHINE -> ExerciseCategory.WEIGHTED
    ExerciseType.CALISTHENICS -> ExerciseCategory.BODYWEIGHT
    ExerciseType.TIMED -> ExerciseCategory.TIMED
    ExerciseType.CARDIO -> ExerciseCategory.CARDIO
    ExerciseType.STRETCH -> ExerciseCategory.STRETCH
}
```

[ExerciseType.kt](../app/src/main/java/com/sakura/data/workout/ExerciseType.kt#L34)

**Key differences from JS `switch`**:
- No `break` needed — each branch is isolated
- Can be an expression (returns a value)
- Can match multiple values in one branch (`BARBELL, DUMBBELL, MACHINE ->`)
- Compiler enforces exhaustiveness when used with sealed types or enums

## Extension Functions — Adding Methods to Existing Types

This doesn't exist in JS. An extension function lets you add methods to a class you don't own:

```kotlin
// This adds a .toCategory() method to ExerciseType
fun ExerciseType.toCategory(): ExerciseCategory = when (this) { ... }

// Now you can call it like a regular method:
val category = ExerciseType.BARBELL.toCategory()  // → WEIGHTED
```

Think of it as if you could do `Array.prototype.myMethod = ...` in JS, but with compile-time safety and scoping.

## Companion Objects — Static Methods

Kotlin doesn't have `static` methods. Instead, you put shared methods in a `companion object` inside the class:

```kotlin
// From ExerciseCategory.kt
enum class ExerciseCategory(val label: String, val displayName: String) {
    WEIGHTED("weighted", "Weighted"),
    BODYWEIGHT("bodyweight", "Bodyweight"),
    // ...

    companion object {
        fun fromLabel(label: String): ExerciseCategory =
            entries.firstOrNull { it.label == label.lowercase() } ?: WEIGHTED
    }
}
```

[ExerciseCategory.kt](../app/src/main/java/com/sakura/data/workout/ExerciseCategory.kt#L17)

**JS equivalent**:
```javascript
class ExerciseCategory {
  static fromLabel(label) {
    return Object.values(ExerciseCategory).find(e => e.label === label.toLowerCase()) ?? WEIGHTED;
  }
}
```

You'll see companion objects in every ViewModel for the `factory` method — that's the standard pattern for creating ViewModels with constructor arguments.

## Coroutines & `suspend` — Async/Await

Kotlin uses coroutines for async work. A `suspend` function is like an `async` function in JS:

```kotlin
// Kotlin
suspend fun loadDay(date: LocalDate): List<MealGroup>

// JavaScript equivalent
async function loadDay(date: Date): Promise<MealGroup[]>
```

You can only call `suspend` functions from inside a coroutine scope (like how you can only `await` inside an `async` function). In ViewModels, you launch coroutines with `viewModelScope.launch`:

```kotlin
// From FoodLogViewModel.kt
fun addEntry(mealLabel: String, entry: FoodEntry) {
    viewModelScope.launch {                          // ← starts a coroutine
        foodRepo.addEntry(selectedDate, mealLabel, entry)  // ← suspend call (like await)
        reloadDay()
    }
}
```

[FoodLogViewModel.kt](../app/src/main/java/com/sakura/features/foodlog/FoodLogViewModel.kt#L262)

**React equivalent**:
```javascript
const addEntry = (mealLabel, entry) => {
  // The useEffect cleanup / AbortController equivalent is handled by viewModelScope
  (async () => {
    await foodRepo.addEntry(selectedDate, mealLabel, entry);
    reloadDay();
  })();
};
```

`viewModelScope` is automatically cancelled when the ViewModel is destroyed — like cleanup in `useEffect`.

## Flow & StateFlow — Reactive State

This is the most important concept for understanding Sakura's state management. `Flow` is Kotlin's equivalent of RxJS Observables or event streams. `StateFlow` is a special Flow that always holds a current value — like `BehaviorSubject` in RxJS, or `useState` in React.

```kotlin
// From FoodLogViewModel.kt
private val _selectedDate = MutableStateFlow(LocalDate.now())  // writable
val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()  // read-only exposure
```

[FoodLogViewModel.kt](../app/src/main/java/com/sakura/features/foodlog/FoodLogViewModel.kt#L41)

**React equivalent**:
```javascript
const [selectedDate, setSelectedDate] = useState(new Date());
// _selectedDate ≈ the setter (private, internal)
// selectedDate ≈ the getter (public, read-only)
```

In composables, you subscribe to StateFlows with `collectAsStateWithLifecycle`:

```kotlin
// In a @Composable
val date by viewModel.selectedDate.collectAsStateWithLifecycle()
// 'date' updates automatically when the Flow emits, causing recomposition
```

This is like `useSelector` in Redux or `useSyncExternalStore` — the composable re-renders whenever the value changes.

### The `combine` + `flatMapLatest` Pattern

This is Sakura's most complex reactive pattern, used for combining multiple state streams:

```kotlin
// From FoodLogViewModel.kt — combines date changes with reload triggers
val uiState: StateFlow<FoodLogUiState> = combine(_selectedDate, _reloadTrigger) { date, _ -> date }
    .flatMapLatest { date ->
        flow {
            emit(FoodLogUiState.Loading)
            val meals = foodRepo.loadDay(date)
            prefsRepo.macroTargets.collect { targets ->
                emit(FoodLogUiState.Success(meals = meals, targets = targets))
            }
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodLogUiState.Loading)
```

[FoodLogViewModel.kt](../app/src/main/java/com/sakura/features/foodlog/FoodLogViewModel.kt#L85)

**React equivalent** (roughly):
```javascript
// When selectedDate or reloadTrigger changes → re-fetch data
useEffect(() => {
  setUiState({ type: 'loading' });
  const meals = await foodRepo.loadDay(selectedDate);
  setUiState({ type: 'success', meals, targets: macroTargets });
}, [selectedDate, reloadTrigger]);
```

`flatMapLatest` cancels the previous flow when a new value arrives — like an AbortController that auto-cancels the previous fetch when you navigate to a new date.

## Lambda Syntax

Kotlin lambdas look different from JS arrow functions:

```kotlin
// Kotlin — lambda with one param (implicit `it`)
entries.filter { it.calories > 100 }

// JS equivalent
entries.filter(entry => entry.calories > 100)

// Kotlin — lambda with named param
entries.map { entry -> entry.name.uppercase() }

// JS equivalent
entries.map(entry => entry.name.toUpperCase())
```

**Trailing lambda**: If the last parameter of a function is a lambda, you can put it outside the parentheses. This is why Compose UI code looks like:

```kotlin
Column(modifier = Modifier.padding(16.dp)) {
    Text("Hello")     // this block IS the lambda parameter 'content'
    Text("World")
}
```

**JS equivalent**:
```jsx
<Column style={{ padding: 16 }}>
    <Text>Hello</Text>
    <Text>World</Text>
</Column>
```

## @Composable Functions — React Components

A `@Composable` function is exactly a React component:

```kotlin
@Composable
fun MealCard(meal: MealGroup, onDelete: (Long) -> Unit) {
    Card {
        Text(meal.label)
        meal.entries.forEach { entry ->
            Row {
                Text(entry.name)
                IconButton(onClick = { onDelete(entry.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}
```

**React equivalent**:
```jsx
function MealCard({ meal, onDelete }) {
    return (
        <Card>
            <Text>{meal.label}</Text>
            {meal.entries.map(entry => (
                <Row key={entry.id}>
                    <Text>{entry.name}</Text>
                    <IconButton onClick={() => onDelete(entry.id)}>
                        <DeleteIcon />
                    </IconButton>
                </Row>
            ))}
        </Card>
    );
}
```

Key differences:
- No JSX — Kotlin uses function calls with trailing lambdas
- `Modifier` replaces CSS/style props — `Modifier.padding(16.dp).fillMaxWidth()` is like `style={{ padding: 16, width: '100%' }}`
- `remember { }` = `useMemo(() => {}, [])`
- `LaunchedEffect(key) { }` = `useEffect(() => {}, [key])`
- `by` keyword + `mutableStateOf` = `useState`

## Type Inference

Kotlin infers types aggressively, so you'll often see:

```kotlin
val name = "Sakura"           // inferred as String
val meals = emptyList<MealGroup>()  // explicit type param needed here
val x = if (flag) 42 else 0  // inferred as Int
```

You can always add explicit types for clarity: `val name: String = "Sakura"`

## String Templates

Like JS template literals, but with `$` instead of `${}`:

```kotlin
val name = "Rice"
val msg = "Food: $name"                    // simple variable
val detail = "Calories: ${entry.calories}" // expression needs braces
```

## Collections

Kotlin collections are similar to JS arrays but with immutable-by-default semantics:

```kotlin
val items = listOf("a", "b", "c")        // immutable list (like Object.freeze([...]))
val mutable = mutableListOf("a", "b")    // mutable list (like a regular JS array)

// Same higher-order functions you know:
items.map { it.uppercase() }
items.filter { it.startsWith("a") }
items.find { it == "b" }
items.sumOf { it.length }
items.firstOrNull { it == "z" }  // returns null instead of undefined
```

`emptyList()` is like `[]`, `emptyMap()` is like `{}`.
