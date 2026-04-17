# Architecture Overview

## The Layer Cake

Sakura follows a layered architecture. Here's how the layers stack, with the React equivalent on the right:

```
┌─────────────────────────────────┐
│  @Composable UI (screens)       │  ← React components (JSX)
├─────────────────────────────────┤
│  ViewModel (state + logic)      │  ← Custom hooks (useReducer + useEffect)
├─────────────────────────────────┤
│  Repository (data interface)    │  ← API service / data access layer
├─────────────────────────────────┤
│  OrgParser / OrgWriter          │  ← JSON.parse / JSON.stringify
├─────────────────────────────────┤
│  SyncBackend (file I/O)         │  ← fetch() / localStorage
├─────────────────────────────────┤
│  .org files on disk             │  ← Backend database / localStorage
└─────────────────────────────────┘
```

Data flows **down** when writing (user action → ViewModel → Repository → disk) and **up** when reading (disk → Repository → ViewModel → UI recomposes).

## Boot Sequence

When the app launches, three things happen in order:

### 1. SakuraApplication.onCreate()

[SakuraApplication.kt](../app/src/main/java/com/sakura/SakuraApplication.kt#L6) extends Android's `Application` class — think of it as your app's `index.js`. It runs once before any screen renders.

```kotlin
class SakuraApplication : Application() {
    lateinit var prefsRepo: AppPreferencesRepository

    override fun onCreate() {
        super.onCreate()
        prefsRepo = AppPreferencesRepository(this)
    }
}
```

All it does is create the preferences repository eagerly. It can't create the full DI container yet because that depends on the user's storage mode, which is stored async in DataStore.

### 2. MainActivity.onCreate()

[MainActivity.kt](../app/src/main/java/com/sakura/MainActivity.kt#L19) is the single Activity — Android's equivalent of mounting your root React component. It:

1. Reads theme mode and onboarding status from DataStore (async)
2. Waits until those values are loaded (shows blank screen to prevent flash)
3. Creates `AppContainer` with the resolved storage mode
4. Renders `AppNavHost`

```kotlin
setContent {
    val themeMode by prefsRepo.themeMode
        .collectAsStateWithLifecycle(initialValue = "DARK")

    SakuraTheme(themeMode = themeMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val onboardingDone by prefsRepo.onboardingComplete
                .collectAsStateWithLifecycle(initialValue = null)
            val storageMode by prefsRepo.storageMode
                .collectAsStateWithLifecycle(initialValue = null)

            if (onboardingDone != null) {
                val resolvedMode = storageMode ?: StorageMode.LOCAL
                val container = remember(resolvedMode) {
                    AppContainer(applicationContext, resolvedMode)
                }
                AppNavHost(navController = rememberNavController(), ...)
            }
        }
    }
}
```

**React analogy**: This is like your `App.tsx` that wraps everything in `<ThemeProvider>`, waits for an auth check, then renders `<BrowserRouter>`.

### 3. AppNavHost

[AppNavHost.kt](../app/src/main/java/com/sakura/navigation/AppNavHost.kt#L67) sets up all routes and the bottom nav bar. Covered in detail in [navigation.md](navigation.md).

## Dependency Injection — AppContainer

Instead of Hilt/Dagger (Android's popular DI frameworks, which are like heavyweight IoC containers), Sakura uses a simple manual DI container. Think of it as a single React context provider that holds all your app's services.

[AppContainer.kt](../app/src/main/java/com/sakura/di/AppContainer.kt#L24):

```kotlin
class AppContainer(context: Context, storageMode: StorageMode) {

    val prefsRepo = AppPreferencesRepository(context)

    val syncBackend: SyncBackend = when (storageMode) {
        StorageMode.LOCAL -> LocalStorageBackend(context)
        StorageMode.SYNCTHING -> SyncthingFileBackend(prefsRepo)
    }

    val orgParser = OrgParser     // singleton object
    val orgWriter = OrgWriter     // singleton object

    val foodRepository: FoodRepository = OrgFoodRepository(syncBackend)
    val workoutRepository: WorkoutRepository = OrgWorkoutRepository(syncBackend, prefsRepo)
}
```

**How dependencies flow**: `AppContainer` is created in `MainActivity`, then passed to `AppNavHost`, which injects the right dependencies into each screen's ViewModel via factory functions.

**React equivalent**:
```jsx
// This is roughly what AppContainer does, but as a React context
const AppContext = createContext();

function App() {
  const syncBackend = storageMode === 'LOCAL'
    ? new LocalBackend()
    : new SyncthingBackend();
  const foodRepo = new FoodRepository(syncBackend);

  return (
    <AppContext.Provider value={{ foodRepo, workoutRepo, prefsRepo }}>
      <Router>...</Router>
    </AppContext.Provider>
  );
}
```

## Why .org Files Instead of SQLite?

Most Android apps use Room/SQLite for persistence. Sakura uses plain-text `.org` files because:

1. **Human-readable** — you can open `food-log.org` in Emacs (or any text editor) and read/edit your data directly
2. **Syncthing-friendly** — .org files are just text, so Syncthing can sync them across devices with no special handling. SQLite databases can't be synced this way without corruption risk
3. **Emacs integration** — the .org format is native to Emacs Org-mode, so the data can be used with Org-agenda, tables, and other Emacs tools
4. **No migration headaches** — no database migrations to manage. The format is additive (new properties can be added without breaking old parsers)

The tradeoff: no indexing, no joins, no query language. Every read parses the whole file. This is fine for personal tracking (files are small), but wouldn't scale to thousands of users.

## Why Manual DI Instead of Hilt?

Hilt (built on Dagger) is the standard DI framework for Android, using annotation processing to generate injection code at compile time. Sakura skips it because:

1. **Simplicity** — the app has ~5 injectable dependencies. Hilt's annotation ceremony (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@Inject`, `@Module`, `@Provides`) would be overkill
2. **Transparency** — you can read `AppContainer.kt` and see every dependency in one place. With Hilt, you'd need to trace through annotations and generated code
3. **Build speed** — Hilt adds annotation processing to every build. Manual DI has zero build overhead

The tradeoff: if the app grows to 50+ dependencies with complex scoping, manual DI would get unwieldy. For now, one file does the job.

## MVVM Pattern

Every feature screen follows MVVM (Model-View-ViewModel):

```
┌──────────────────────┐
│  Screen (@Composable) │  ← View: renders UI from state, sends user events to ViewModel
├──────────────────────┤
│  ViewModel            │  ← Holds UI state (StateFlow), handles business logic
├──────────────────────┤
│  Repository           │  ← Model: data access interface
└──────────────────────┘
```

**React translation**:
- **Screen** = your component's JSX return
- **ViewModel** = the custom hook that manages state (`useState`, `useReducer`, `useEffect`)
- **Repository** = the API service you call from hooks

The ViewModel survives configuration changes (screen rotation, etc.) — it persists across what would be a full remount in React. Think of it as state that lives outside the component tree.

## Theme System

[Theme.kt](../app/src/main/java/com/sakura/ui/theme/Theme.kt#L44) wraps the app in Material 3 theming with sakura-inspired colors. Supports DARK, LIGHT, and SYSTEM modes.

```kotlin
@Composable
fun SakuraTheme(themeMode: String = "DARK", content: @Composable () -> Unit) {
    val useDark = when (themeMode) {
        "LIGHT" -> false
        "SYSTEM" -> isSystemInDarkTheme()
        else -> true
    }
    MaterialTheme(
        colorScheme = if (useDark) SakuraDarkColorScheme else SakuraLightColorScheme,
        typography = Typography,
        content = content
    )
}
```

**React analogy**: This is like a `<ThemeProvider theme={darkTheme}>` from styled-components or MUI. `MaterialTheme` makes the color scheme available to all descendant composables via `MaterialTheme.colorScheme.primary`, similar to `theme.palette.primary` in MUI.

Colors are defined in [Color.kt](../app/src/main/java/com/sakura/ui/theme/Color.kt) — rose/sakura pinks for primary, dark surfaces, and semantic colors like `ForestGreen` for protein and `WorkoutBlue` for workout metrics.
