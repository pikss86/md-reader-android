# MD Reader Android

Minimal Android markdown reader scaffold built with Kotlin and Jetpack Compose.

## MVP scope

This repository currently provides:

- a single-activity Android app
- Jetpack Compose UI
- light and dark theme support
- opening local markdown/text files through the Android file picker
- opening markdown-ish files from Android `ACTION_VIEW` intents such as "Open with"
- reading incoming `Uri` content through `ContentResolver`
- basic markdown rendering for headings, paragraphs, lists, and fenced code blocks
- plain-text fallback when content does not map to the supported markdown subset
- GitHub Actions build workflow for generating a debug APK

The current app supports a practical MVP reader flow. Rich markdown rendering, clickable links, inline emphasis styling, images, and broader file-provider compatibility are still pending.

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

- improve markdown rendering for links, emphasis, block quotes, and tables
- harden file opening across more document providers, MIME types, and edge cases
- add tests once core behavior exists
