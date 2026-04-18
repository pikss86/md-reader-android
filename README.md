# MD Reader Android

Minimal Android markdown reader scaffold built with Kotlin and Jetpack Compose.

## MVP scope

This repository currently provides:

- a single-activity Android app
- Jetpack Compose UI
- light and dark theme support
- opening local markdown files through Android file picker or `ACTION_VIEW` intents
- basic markdown parsing for headings and paragraphs
- raw markdown fallback view
- GitHub Actions build workflow for generating a debug APK

The current app supports a minimal reader flow. Rich markdown rendering, better styling for lists/code blocks, and broader file-provider compatibility are still pending.

## Tech stack

- Kotlin
- Android Gradle Plugin
- Jetpack Compose Material 3

## Project layout

- `app/`: Android application module
- `.github/workflows/android-debug.yml`: CI build for debug APK
- `gradle/libs.versions.toml`: centralized dependency and plugin versions

## Build policy

This project is expected to be built on GitHub through GitHub Actions rather than locally on this host.
The repository should keep a working CI workflow for generating at least a debug APK.

## Building in GitHub Actions

The repository includes a workflow that:

1. checks out the code
2. sets up JDK 17
3. installs required Android SDK packages
4. installs Gradle in CI
5. assembles the debug APK

No local Android SDK installation was performed as part of this scaffold.

## Next steps

- improve markdown rendering for lists, links, emphasis, and code blocks
- harden file opening across more document providers and edge cases
- add tests once core behavior exists

