# Contributing to Universal Gestures

## Architecture

The codebase is layered under `com.universalgestures`:

- `core` — pure-Kotlin models (no Android imports; unit-testable on JVM)
- `sensors` — hardware discovery (SensorHub), IMU processing, fusion, segmentation
- `recognition` — state-machine recognizers, DTW, CMVE context verification, PGI precision gate, calibration
- `actions` — action engine (flashlight, camera, media, DND, ringer, shortcuts)
- `service` — foreground service + boot recovery
- `device` — runtime compatibility engine
- `data` — DataStore preferences + diagnostics
- `accessibility` — opt-in accessibility actions only
- `ui` — Jetpack Compose screens + ViewModel

## Rules

1. Keep `core`, `sensors/ImuProcessor`, `sensors/MotionSegmenter` and
   `recognition` free of `android.*` imports so they stay JVM-testable.
2. Never fabricate capabilities: if a sensor or API is unavailable, the
   capability engine must say so with a reason.
3. Every visible UI control must work. No decorative buttons.
4. Restricted actions (lock screen, screenshot, notifications, DND, ringer)
   require explicit user consent via system settings. Never bypass.
5. All motion processing stays on-device. No uploads, no accounts.

## Building and testing

```bash
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew assembleDebug       # debug APK -> app/build/outputs/apk/debug/app-debug.apk
```

CI (`.github/workflows/android-build.yml`) runs both on every push.

## Tests

Add synthetic-stream tests for any new recognizer in
`app/src/test/java/com/universalgestures/`. Test both positive gestures
and negative examples (walking, pocket, idle).
