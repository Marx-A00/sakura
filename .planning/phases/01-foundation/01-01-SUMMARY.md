---
phase: 01
plan: 01
name: project-scaffold
subsystem: build-system
tags: [android, agp, gradle, compose, orgengine, data-models]

dependency-graph:
  requires: []
  provides:
    - android-project-scaffold
    - compose-theme-sakura
    - org-data-models
    - org-format-schema
  affects:
    - 01-02 (OrgEngine TDD — imports OrgModels and OrgSchema)
    - 01-03 (SyncBackend + onboarding — imports AppContainer, SakuraTheme)

tech-stack:
  added:
    - agp: "9.1.0"
    - compose-bom: "2025.05.00"
    - navigation-compose: "2.9.0"
    - datastore-preferences: "1.2.1"
    - lifecycle-runtime-compose: "2.9.0"
    - lifecycle-viewmodel-compose: "2.9.0"
    - kotlinx-serialization-json: "1.8.1"
    - kotlin-plugin-compose: "2.1.20"
    - kotlin-plugin-serialization: "2.1.20"
    - gradle: "9.3.1"
    - jdk: "17 (Temurin 17.0.18+8)"
    - android-sdk: "35"
    - build-tools: "36.0.0"
  patterns:
    - manual-di-via-appcontainer
    - agp-9-built-in-kotlin-no-kotlin-android-plugin
    - compose-compiler-plugin-required-kotlin-2x
    - dark-theme-only-material3

key-files:
  created:
    - settings.gradle.kts
    - build.gradle.kts
    - gradle/libs.versions.toml
    - gradle/wrapper/gradle-wrapper.properties
    - gradle/wrapper/gradle-wrapper.jar
    - gradlew
    - gradlew.bat
    - app/build.gradle.kts
    - app/src/main/AndroidManifest.xml
    - app/src/main/java/com/sakura/SakuraApplication.kt
    - app/src/main/java/com/sakura/di/AppContainer.kt
    - app/src/main/java/com/sakura/MainActivity.kt
    - app/src/main/java/com/sakura/ui/theme/Theme.kt
    - app/src/main/java/com/sakura/ui/theme/Color.kt
    - app/src/main/java/com/sakura/ui/theme/Type.kt
    - app/src/main/java/com/sakura/orgengine/OrgModels.kt
    - app/src/main/java/com/sakura/orgengine/OrgSchema.kt
    - app/src/main/res/values/themes.xml
    - app/src/main/res/values/colors.xml
    - app/src/main/res/drawable/ic_launcher_foreground.xml
    - .gitignore
  modified: []

decisions:
  - id: compose-compiler-plugin
    summary: "Add org.jetbrains.kotlin.plugin.compose — required for Kotlin 2.0+ with Compose"
    rationale: "AGP 9.1.0 bundles Kotlin 2.x. The Compose Compiler Gradle plugin is mandatory when buildFeatures { compose = true } is set with Kotlin 2.0+. This is distinct from the prohibited org.jetbrains.kotlin.android plugin."
  - id: install-jdk17
    summary: "Install JDK 17 (Temurin) to user-local directory (~/.local/jdk/) without sudo"
    rationale: "AGP 9.1.0 requires JDK 17. Only JDK 15 was available system-wide. Resolved by downloading Temurin 17.0.18+8 tar.gz and extracting to ~/.local/jdk/."
  - id: install-android-sdk
    summary: "Bootstrap Android SDK via command-line tools to ~/Library/Android/sdk/"
    rationale: "No Android SDK was present. Installed cmdline-tools, platform-tools, platforms;android-35, build-tools;36.0.0 via sdkmanager."
  - id: food-entry-notation
    summary: "Inline pipe notation chosen for food and exercise entries"
    rationale: "Format: '- Name  |P: 42g  C: 55g  F: 8g  Cal: 460|' — passes org-lint as plain text in list items, compact, human-readable in Emacs, unambiguous for regex parsing. Property drawers ruled out for Phase 1 (syntactically heavier, not required)."

metrics:
  duration: "12 minutes"
  completed: "2026-04-09"
  tasks-completed: 2
  tasks-total: 2
---

# Phase 1 Plan 01: Project Scaffold Summary

**One-liner:** AGP 9.1.0 Android scaffold with Compose, DataStore, Navigation, Locale.ENGLISH org date formatter, and complete food/workout data model + format spec.

## What Was Built

**Task 1: Android project scaffold**
Greenfield Android project with AGP 9.1.0, Gradle 9.3.1, JDK 17. All Phase 1 dependencies declared via version catalog. SakuraApplication bootstraps AppContainer (manual DI stub). MainActivity shell with SakuraTheme. Dark rose/pink theme: primary Rose400 (#FB7185) on DarkBackground (#1A1A1A).

**Task 2: OrgEngine data models and schema**
OrgModels.kt: five pure Kotlin data classes (OrgFile, OrgDateSection, OrgMealGroup, OrgFoodEntry, OrgExerciseEntry) with zero Android imports. OrgSchema.kt: complete format specification — date formatter with mandatory Locale.ENGLISH, heading format functions, entry serialization functions, and corresponding parse regexes for both food and exercise entries.

## Build Verification

```
./gradlew assembleDebug → BUILD SUCCESSFUL
APK: app/build/outputs/apk/debug/app-debug.apk
```

All Phase 1 dependencies compile: Compose BOM 2025.05.00, Navigation Compose 2.9.0, DataStore Preferences 1.2.1, Lifecycle 2.9.0, kotlinx-serialization-json 1.8.1.

## Decisions Made

**1. Compose Compiler Plugin required with Kotlin 2.0+**
The plan's anti-pattern note prohibited `org.jetbrains.kotlin.android` but AGP 9.1.0 (Kotlin 2.x built-in) also requires `org.jetbrains.kotlin.plugin.compose` when Compose is enabled. Added to libs.versions.toml and app/build.gradle.kts.

**2. JDK 17 bootstrapped without sudo**
AGP 9.1.0 requires JDK 17. Downloaded Temurin 17.0.18+8 tar.gz from Adoptium and extracted to `~/.local/jdk/`. Build invocation requires `JAVA_HOME=/Users/marcosandrade/.local/jdk/jdk-17.0.18+8/Contents/Home`.

**3. Android SDK bootstrapped**
No Android SDK was present. Installed via sdkmanager (cmdline-tools 11076708) to `~/Library/Android/sdk/`. SDK path in `local.properties` (gitignored).

**4. Food/exercise entry notation confirmed**
Inline pipe notation: `- Name  |P: 42g  C: 55g  F: 8g  Cal: 460|` and `- Exercise  |3x8  80kg|`. Chosen over property drawers for org-lint compatibility and compactness. See OrgSchema.kt for rationale inline.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added kotlin-compose plugin — required for Kotlin 2.0+ with Compose**
- Found during: Task 1 verification (first build attempt)
- Issue: Gradle failed with "Starting in Kotlin 2.0, the Compose Compiler Gradle plugin is required when compose is enabled"
- Fix: Added `kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version = "2.1.20" }` to libs.versions.toml and `alias(libs.plugins.kotlin.compose)` to app/build.gradle.kts
- Files modified: gradle/libs.versions.toml, app/build.gradle.kts
- Commit: 05b82fc

**2. [Rule 3 - Blocking] Installed JDK 17 to user-local directory**
- Found during: Task 1 setup
- Issue: Only JDK 15 available; AGP 9.1.0 requires JDK 17
- Fix: Downloaded Temurin 17.0.18+8 tar.gz from Adoptium API, extracted to ~/.local/jdk/ without sudo
- Files modified: None (environment only)
- Note: Build command requires explicit JAVA_HOME

**3. [Rule 3 - Blocking] Bootstrapped Android SDK**
- Found during: Task 1 first build
- Issue: No Android SDK installed; build failed with "SDK location not found"
- Fix: Downloaded cmdline-tools, installed platform-tools/platforms;android-35/build-tools;36.0.0 via sdkmanager with `yes |` for license acceptance. Created local.properties (gitignored)
- Files modified: .gitignore (added Android-specific entries), local.properties (gitignored)

**4. [Rule 3 - Blocking] Added Android-specific .gitignore entries**
- Found during: Task 1 completion
- Issue: .gitignore only contained web project patterns; .gradle/, build/, local.properties, *.hprof would be committed
- Fix: Rewrote .gitignore with Android/Gradle patterns
- Files modified: .gitignore
- Commit: 05b82fc

## Next Phase Readiness

**Plan 02 (OrgEngine TDD) can proceed when:**
- Org file schema validated by hand in Emacs with `M-x org-lint` (mandatory gate from research)
- The inline pipe notation (`|P: 42g  C: 55g  F: 8g  Cal: 460|`) must be written manually in Emacs and linted before OrgParser/OrgWriter implementation

**Build environment notes for future plans:**
- Always set `JAVA_HOME=/Users/marcosandrade/.local/jdk/jdk-17.0.18+8/Contents/Home` when running gradlew
- Android SDK at `~/Library/Android/sdk/`; `local.properties` is gitignored (set sdk.dir manually on new machines)
- Samsung One UI Auto Blocker must be disabled before first ADB install (from STATE.md blockers)
