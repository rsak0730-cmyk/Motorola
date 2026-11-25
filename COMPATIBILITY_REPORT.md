# Feature Compatibility Report

Generated from the runtime capability engine (`DeviceCompatibilityEngine`).
The app re-evaluates this on every device at runtime — the table below
describes the logic, not a hardcoded device list.

## Capability levels

- **Fully supported** — hardware sensor/API present; gesture works as designed.
- **Supported with limitations** — software fallback used (e.g. no gyroscope → accelerometer rotation estimate).
- **Requires permission** — works after the user grants a permission (camera for hand motion).
- **Requires alternative implementation** — hardware sensor absent; heuristic used (e.g. pickup via rise+tilt).
- **Unsupported** — Android does not expose the capability to third-party apps; the app explains why.

## Gesture matrix (typical device with accel + gyro + proximity)

| Gesture | Typical status | Notes |
|---|---|---|
| Double Chop | Fully supported | Linear acceleration peaks |
| Single Chop | Fully supported | |
| Double Twist | Fully supported | Gyroscope sign flips |
| Triple Twist | Fully supported | |
| Shake | Fully supported | Oscillation counting |
| Double Shake | Fully supported | |
| Triple Shake | Fully supported | |
| Flip Phone | Fully supported / limited | Gravity sensor preferred; software estimate otherwise |
| Pickup Phone | Fully supported / limited | Hardware pickup sensor preferred; heuristic otherwise |
| Tilt Left/Right/Fwd/Back | Fully supported / limited | Tilt detector or gravity |
| Custom Motion | Fully supported | DTW templates recorded by user |
| Proximity Wave | Fully supported | Near/far transitions only — cannot identify hand shapes |
| Double Proximity Wave | Fully supported | |
| Hand Motion | **Unsupported in this build** | Camera hardware may exist, but no camera-recognition pipeline is implemented |
| Custom Air | **Unsupported in this build** | Proximity reports near/far only; no camera-recognition pipeline is implemented |
| Double Back Tap | **Unsupported** | Android exposes no back-tap events to third-party apps |
| Triple Back Tap | **Unsupported** | Same |
| Double Tap on Screen | **Unsupported** | Same |
| Three-Finger Swipe | **Unsupported** | Same |
| Three-Finger Screenshot | **Unsupported** | Same |
| Custom Screen | **Unsupported** | Same |

## Action matrix

| Action | Requirement |
|---|---|
| Flashlight | Flash-capable camera; no permission |
| Open camera | System camera app; no permission |
| Launch app | Target package name |
| Open shortcut | Pinned shortcut ID (API 25+) |
| Open settings | No permission |
| Media / volume | No permission |
| Screenshot | Opt-in accessibility service (API 30+ for `takeScreenshot`) |
| Lock screen | Opt-in accessibility service |
| Open notifications | Opt-in accessibility service |
| DND | Notification policy access (explicit opt-in) |
| Ringer mode | Notification policy access on modern Android |

## Manufacturer guidance (shown in-app)

- **Samsung**: allow background activity; disable battery optimisation for the app.
- **Motorola**: foreground service; set Battery to Unrestricted if gestures stop.
- **Xiaomi/Redmi/POCO**: lock app in Recents + enable Autostart (MIUI kills background services otherwise).
- **OnePlus/OPPO/Realme**: allow background activity; disable battery optimisation.
- **Vivo**: enable background power consumption + autostart whitelist.
- **Pixel / Nothing**: no manufacturer restrictions; battery exemption still recommended.

## Primary development device

Samsung Galaxy A14 5G (SM-A146B, 6 GB RAM, Android 13/14). The app adapts at
runtime: sensors, API level and restrictions are detected, never assumed.
Physical-device verification on this handset is still required — see
TESTING_REPORT.md.
