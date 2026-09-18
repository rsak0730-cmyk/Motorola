# GestureFlow

A comprehensive Android gesture control system mimicking Motorola's physical gestures (Chop, Twist, Shake) using pure Android APIs.

## Features Built
1. **Double Chop:** Reliable accelerometer logic detects chop motions. Automatically manages debouncing and triggers the Camera2 Torch API (Flashlight).
2. **Quick Twist:** Gyroscope integration accurately detects wrist-twist actions. Bypasses lockscreen safely by launching `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA`.
3. **Foreground Engine:** A lightweight `ForegroundService` keeps the sensor suite running while the device screen is off, displaying a persistent, low-priority notification.
4. **DataStore Preferences:** Reactive Compose UI immediately propagates sensitivity and enable/disable states to the background service using Kotlin `StateFlow`.
5. **Battery Optimization Bypass UI:** Safe routing to Android's built-in battery optimization settings to prevent OEMs from killing the gesture service.

## Limitations Addressed (Android Restrictions)
* **Screen Off Limitations:** Some OEMs aggressively kill Sensor processing when the screen turns off. The app requests `FOREGROUND_SERVICE` and recommends Battery Exclusions.
* **Lock Screen limitations:** Standard apps cannot magically bypass secure lock screens to open *any* app. `OPEN_CAMERA` uses the standard Android Secure Camera Intent.
* **Root NOT required.**

## How to Build
1. Open project in Android Studio.
2. Let Gradle sync.
3. Build the APK using the IDE or via CLI:
   ```bash
   ./gradlew assembleDebug
   ./gradlew assembleRelease

