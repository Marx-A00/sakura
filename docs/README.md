# Sakura Developer Docs

Developer documentation for the Sakura food & workout tracking Android app. Written for a React JS developer learning Kotlin and Jetpack Compose.

## Build & Run

```bash
JAVA_HOME=/Users/marcosandrade/.local/jdk/jdk-17.0.18+8/Contents/Home ./gradlew assembleDebug
# Deploy to connected device/emulator:
JAVA_HOME=/Users/marcosandrade/.local/jdk/jdk-17.0.18+8/Contents/Home ./gradlew installDebug
```

## Where to Start

If you're new to the codebase, read these in order:

1. **[Kotlin for React Devs](kotlin-for-react-devs.md)** — language concepts mapped to JS/React equivalents. Read this first if Kotlin syntax looks alien.
2. **[Architecture Overview](architecture.md)** — how the app is structured, the layer cake from UI to disk, and why certain decisions were made.
3. **[Directory Guide](directory-guide.md)** — what every directory and key file does, with links.
4. **[Data Flow](data-flow.md)** — how data moves from user tap to .org file and back to the screen.
5. **[Navigation](navigation.md)** — routing, tabs, and the radial menu.
6. **[Org Engine](org-engine.md)** — the custom .org file parser/writer that replaces a traditional database.

## Quick Mental Model

If you're coming from React, here's the 30-second mapping:

| React Concept | Sakura/Kotlin Equivalent |
|---|---|
| Component (JSX) | `@Composable` function |
| `useState` / `useReducer` | `MutableStateFlow` in ViewModel |
| `useEffect` + fetch | `viewModelScope.launch` + `suspend` repo call |
| Context Provider | `AppContainer` (manual DI) |
| React Router | Compose Navigation (`NavHost` + `@Serializable` routes) |
| localStorage / IndexedDB | `.org` files via `SyncBackend` |
| TypeScript interface | `data class` |
| Tagged union (`type: 'loading' | 'success'`) | `sealed interface` |

All source code lives under [`app/src/main/java/com/sakura/`](../app/src/main/java/com/sakura/).
