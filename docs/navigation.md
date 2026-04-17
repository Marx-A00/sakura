# Navigation & Routing

## Overview

Sakura uses Jetpack Compose Navigation — Android's official routing library. If you know React Router, the concepts map directly:

| React Router | Compose Navigation |
|---|---|
| `<BrowserRouter>` | `NavController` |
| `<Routes>` | `NavHost` |
| `<Route path="/food" element={<FoodLog />} />` | `composable<FoodLog> { FoodLogScreen() }` |
| `"/food"` (string path) | `FoodLog` (type-safe `@Serializable object`) |
| `useNavigate()` | `navController.navigate(...)` |
| `useParams()` | Route data class properties |
| `<Link to="/food">` | `navController.navigate(FoodLog)` |

## Route Definitions

Routes are defined as `@Serializable object`s in [Routes.kt](../app/src/main/java/com/sakura/navigation/Routes.kt):

```kotlin
@Serializable object Onboarding
@Serializable object FoodLog
@Serializable object Settings
@Serializable object WorkoutLog
@Serializable object WorkoutHistory
@Serializable object Progress
@Serializable object MacroTargets
@Serializable object FoodLibrary
@Serializable object Home
```

**Why objects instead of strings?** Type safety. In React Router, you might typo `"/fod"` and not know until runtime. Here, `FoodLog` is a real Kotlin type — if you typo it, the compiler catches it immediately.

If a route needs parameters (none do currently), you'd use a `data class` instead:

```kotlin
// Hypothetical: route with a parameter
@Serializable data class FoodDetail(val entryId: Long)
// Access in composable: val args = it.toRoute<FoodDetail>(); args.entryId
```

## NavHost — The Router

[AppNavHost.kt](../app/src/main/java/com/sakura/navigation/AppNavHost.kt#L67) is where routes are mapped to screens. It's wrapped in a `Scaffold` (Material's page layout with bottom bar).

```kotlin
@Composable
fun AppNavHost(
    navController: NavHostController,
    appContainer: AppContainer,
    startWithOnboarding: Boolean
) {
    Scaffold(
        bottomBar = { SakuraBottomBar(...) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (startWithOnboarding) Onboarding else Home,
        ) {
            composable<Home> {
                val viewModel: DashboardViewModel = viewModel(
                    factory = DashboardViewModel.factory(
                        foodRepo = appContainer.foodRepository,
                        workoutRepo = appContainer.workoutRepository,
                        prefsRepo = appContainer.prefsRepo,
                        syncBackend = appContainer.syncBackend
                    )
                )
                DashboardScreen(viewModel = viewModel)
            }

            composable<FoodLog> {
                val viewModel: FoodLogViewModel = viewModel(
                    factory = FoodLogViewModel.factory(
                        foodRepo = appContainer.foodRepository,
                        prefsRepo = appContainer.prefsRepo
                    )
                )
                FoodLogScreen(viewModel = viewModel, ...)
            }
            // ... more routes
        }
    }
}
```

**React equivalent**:
```jsx
function AppNavHost({ appContainer, startWithOnboarding }) {
  return (
    <Layout bottomBar={<BottomNav />}>
      <Routes>
        <Route path="/" element={
          <Home foodRepo={appContainer.foodRepo} />
        } />
        <Route path="/food" element={
          <FoodLog foodRepo={appContainer.foodRepo} />
        } />
      </Routes>
    </Layout>
  );
}
```

### ViewModel Factory Injection

Each route creates its ViewModel with a `factory` that injects dependencies from `AppContainer`. This is the Kotlin equivalent of passing props or using context:

```kotlin
val viewModel: FoodLogViewModel = viewModel(
    factory = FoodLogViewModel.factory(
        foodRepo = appContainer.foodRepository,
        prefsRepo = appContainer.prefsRepo
    )
)
```

The `viewModel()` call uses Android's ViewModelStore — it creates the ViewModel once and reuses it across recompositions and configuration changes. Like `useMemo` but even more persistent.

## Tab Navigation

The app has 5 tabs in the bottom bar: FOOD, WORKOUT, (sakura branch), PROGRESS, SETTINGS.

Tab navigation uses `saveState`/`restoreState` to preserve each tab's state when switching:

```kotlin
fun navigateToTab(route: Any) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true      // save the tab you're leaving
        }
        launchSingleTop = true    // don't create duplicate if already on this tab
        restoreState = true       // restore the tab you're going to
    }
}
```

[AppNavHost.kt#L87](../app/src/main/java/com/sakura/navigation/AppNavHost.kt#L87)

**React analogy**: This is like having each tab be its own nested router that preserves scroll position and form state when you switch away and come back. In React you'd need something like `react-router` with hidden divs or a state persistence library.

### Which routes are tabs?

[AppNavHost.kt#L237](../app/src/main/java/com/sakura/navigation/AppNavHost.kt#L237):

```kotlin
private fun NavDestination?.isTabDestination(): Boolean {
    if (this == null) return false
    return hasRoute<Home>() || hasRoute<FoodLog>() || hasRoute<WorkoutLog>()
        || hasRoute<Progress>() || hasRoute<Settings>()
}
```

Non-tab routes (Onboarding, MacroTargets, FoodLibrary, WorkoutHistory) don't show the bottom bar.

## The Sakura Radial Button

The centerpiece of the bottom nav bar is the cherry blossom branch image. It has two interactions:

1. **Tap** → Navigate to Home
2. **Long-press + drag** → Context-aware radial menu fans out with 3 options

### Radial Context

The menu shows different options depending on which tab you're on — [RadialMenu.kt](../app/src/main/java/com/sakura/navigation/RadialMenu.kt#L78):

```kotlin
enum class RadialContext { DEFAULT, FOOD, WORKOUT }
```

The context is determined by the current route in AppNavHost:

```kotlin
val radialContext = when {
    currentDestination?.hasRoute<FoodLog>() == true -> RadialContext.FOOD
    currentDestination?.hasRoute<WorkoutLog>() == true -> RadialContext.WORKOUT
    else -> RadialContext.DEFAULT
}
```

### Radial Actions

[RadialMenu.kt#L68](../app/src/main/java/com/sakura/navigation/RadialMenu.kt#L68):

```kotlin
enum class RadialAction {
    // Default navigation actions (shown on Home, Progress, Settings)
    NAV_FOOD, NAV_EXERCISE, NAV_SETTINGS,
    // Food page actions (shown on Food Log tab)
    FOOD_ADD_ENTRY, FOOD_FROM_LIBRARY, FOOD_LIBRARY,
    // Workout page actions (shown on Workout Log tab)
    WORKOUT_ADD_EXERCISE, WORKOUT_FROM_TEMPLATE, WORKOUT_LOG_WEIGHT,
}
```

### How the Gesture Works

The radial menu uses `detectDragGesturesAfterLongPress` — a Compose pointer input that:

1. Waits for a long press (haptic feedback fires)
2. Shows a full-screen overlay with a scrim
3. Fans out 3 option circles with spring animations
4. Tracks finger drag position in real-time
5. Highlights the closest option as you drag
6. On release, fires the selected action

This is all in [RadialMenu.kt](../app/src/main/java/com/sakura/navigation/RadialMenu.kt). The gesture math uses `atan2` and trigonometry to position options in an arc above the branch and detect which option the finger is closest to.

### How Actions Are Handled

Actions are dispatched in AppNavHost's `onRadialAction` callback — [AppNavHost.kt#L104](../app/src/main/java/com/sakura/navigation/AppNavHost.kt#L104):

```kotlin
onRadialAction = { action ->
    when (action) {
        RadialAction.NAV_FOOD -> navigateToTab(FoodLog)
        RadialAction.NAV_EXERCISE -> navigateToTab(WorkoutLog)
        RadialAction.NAV_SETTINGS -> navigateToTab(Settings)
        RadialAction.FOOD_ADD_ENTRY -> addFoodEntryTrigger++
        RadialAction.FOOD_LIBRARY -> navController.navigate(FoodLibrary)
        // ... more actions
    }
}
```

The `addFoodEntryTrigger++` pattern is interesting — it's a counter that the FoodLogScreen observes. When it increments, the screen opens the add-entry bottom sheet. This is how the radial menu communicates with a specific tab's screen without tight coupling.

## Bottom Bar Layout

The bottom bar is a custom implementation (not Material's `NavigationBar`) — [AppNavHost.kt#L247](../app/src/main/java/com/sakura/navigation/AppNavHost.kt#L247):

```
┌───────────────────────────────────────────────┐
│                    🌸                          │  ← branch overflows 86dp above bar
│               (cherry blossom)                 │
├──────┬──────┬──────┬──────────┬───────────────┤
│ FOOD │WORK- │      │ PROGRESS │   SETTINGS    │  ← 64dp bar
│      │ OUT  │(gap) │          │               │
└──────┴──────┴──────┴──────────┴───────────────┘
```

The center gap (`Spacer(Modifier.weight(1f))`) leaves room for the branch image, which is positioned with `Alignment.BottomCenter` and overflows above the bar.
