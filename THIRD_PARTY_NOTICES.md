# Third-Party Notices

Universal Gestures is an independent implementation. It does not contain,
copy, or derive from Motorola proprietary code, assets, branding, or
undocumented implementation details.

## Open-source dependencies

| Dependency | License | Purpose |
|---|---|---|
| AndroidX Core (`androidx.core:core-ktx`) | Apache 2.0 | Core AndroidX utilities |
| Jetpack Compose BOM / UI / Material3 | Apache 2.0 | Declarative UI |
| Activity Compose | Apache 2.0 | Compose activity integration |
| Lifecycle Runtime / ViewModel Compose | Apache 2.0 | Lifecycle-aware UI state |
| DataStore Preferences | Apache 2.0 | Persistent configuration |
| Kotlin Coroutines | Apache 2.0 | Async / Flow |
| JUnit 4 | Eclipse Public License 1.0 | Unit tests |

All dependencies are resolved from Google Maven and Maven Central at build
time; no third-party binaries are vendored in this repository.

## Android platform APIs

The app uses only public, documented Android SDK APIs (SensorManager,
CameraManager/Camera2, MediaSession key events, AudioManager,
NotificationManager, AccessibilityService global actions, foreground
services, DataStore). No undocumented or private APIs are used as
mandatory dependencies.

## Trademarks

"Motorola" and "Moto Gestures" are trademarks of Motorola Mobility LLC.
This project is not affiliated with or endorsed by Motorola. The name
"Universal Gestures" is used to describe an independent, cross-manufacturer
gesture platform.
