# GestureFlow

GestureFlow is a Motorola-style Android gesture control app for supported Android devices.

## Features
- Double chop flashlight toggle
- Twist gesture actions
- Shake actions
- Flip face-up/face-down actions
- Pickup detection
- Proximity sensor actions where available
- App launcher picker
- Media controls
- Vibration
- URL launch
- Background detection via foreground service
- Boot-start option
- Calibration and testing screens
- DataStore-backed persistence

## Android requirements
- Android 8.0+ (minSdk 26)
- Android 13+ may require notification permission for the foreground service notification
- Some actions require camera, vibration, notifications, or special device support

## Build
```bash
./gradlew assembleDebug
./gradlew assembleRelease
