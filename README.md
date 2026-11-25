# Universal Gestures

A complete, independent Android gesture-recognition platform inspired by the
interaction principles of Motorola Moto Gestures — built from scratch with its
own motion-intelligence stack (**UMI — Universal Motion Intelligence**),
contextual verification (**CMVE**) and a rejection-first precision gate (**PGI**).
Cross-manufacturer: Motorola, Samsung, Xiaomi/Redmi/POCO, OnePlus, OPPO,
Realme, Vivo, Google Pixel, Nothing and other Android devices.

> Independent implementation. Not affiliated with Motorola. No Motorola
> proprietary code, assets, branding, or undocumented APIs are used.

## What it does

Control your phone with physical movements — double chop for the flashlight,
double twist for the camera, shake for media, flip to silence, proximity
waves, tilts, pickups and your own recorded custom gestures — each assignable
to a real action (flashlight, camera, apps, media, volume, DND, ringer,
settings, shortcuts, and opt-in accessibility actions like lock screen and
screenshot).

## Architecture

| Layer | Package | Responsibility |
|---|---|---|
| Hardware intelligence | `sensors/SensorHub` | Discovers every sensor + capabilities; prefers hardware composite sensors, falls back to software fusion |
| IMU processing | `sensors/ImuProcessor` | Low-pass filtering, gravity estimation, linear-accel extraction, gyro bias calibration, discontinuity handling |
| Fusion | `sensors` + `recognition` | Direction, rotation, orientation, proximity context |
| Segmentation | `sensors/MotionSegmenter` | Complete movement sequences only — never classifies partial motion |
| Recognition | `recognition` | State machines + DTW template matching + confidence estimation |
| CMVE | `recognition/ContextVerifier` | Rejects walking/running/pocket/vehicle false triggers |
| PGI | `recognition/PrecisionGate` | 9-stage rejection-first gate; ambiguity is rejected, never guessed |
| Actions | `actions/ActionExecutor` | Supported Android APIs only; honest failure messages |
| Background | `service/GestureForegroundService` | Foreground service, screen-aware, battery-efficient |
| Compatibility | `device/DeviceCompatibilityEngine` | Runtime per-device, per-gesture capability report |
| UI | `ui` | Compose: Home, Gestures, Studio, Automation, Devices, Settings |

Recognition works fully without machine learning. No TFLite dependency.

## Gestures

Motion: Double Chop, Single Chop, Double Twist, Triple Twist, Shake,
Double/Triple Shake, Flip, Pickup, Tilt (×4), Custom Motion (DTW templates).
Proximity: Wave, Double Wave. The optional foreground camera detector reports
frame-motion activity (not hand pose or identity) with explicit permission.
Custom Air templates remain unsupported.

Screen/back-tap gestures (double/triple back tap, double-tap screen,
three-finger swipe/screenshot) are reported as **unsupported** by the
capability engine because Android does not expose those events to
third-party apps — the app says so plainly instead of faking them.

## Actions

Flashlight toggle (CameraManager), open camera, launch app, open settings,
media play/pause/next/previous (MediaSession keys), volume,
screenshot / lock screen / open notifications (opt-in accessibility),
DND (notification policy access), ringer mode (policy access where required).

## Privacy

All processing is local. No account, no cloud, no sensor-data uploads, no
hidden camera/mic. The optional Studio camera uses a foreground Camera2
preview and in-memory frame-difference motion detector only while enabled;
it does not identify hand pose or save frames. Camera permission is requested
only from that Studio control. You can delete all local data in Settings.

## Build

Requires JDK 17 + Android SDK (platform 34). No Android Studio needed:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions (`.github/workflows/android-build.yml`) runs tests and builds
the debug APK on every push, uploading it as an artifact.

## Install

1. Download `app-debug.apk` from the Actions artifact (or build locally).
2. Install on your phone (allow installs from unknown sources if prompted).
3. Open Universal Gestures → enable gestures in the Gestures tab → assign
   actions in Automation → start monitoring on Home.
4. For reliable background monitoring, exempt the app from battery
   optimisation (Home → Battery) and follow the manufacturer guidance in
   the Devices tab.

## Reports

- [COMPATIBILITY_REPORT.md](COMPATIBILITY_REPORT.md) — per-device capability matrix
- [TESTING_REPORT.md](TESTING_REPORT.md) — automated tests, targets, and what still needs physical verification

## License

Apache 2.0 — see [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
