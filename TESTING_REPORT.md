# Testing and Accuracy Report

## What is tested automatically (JVM unit tests, run in CI)

| Suite | Covers |
|---|---|
| `ImuProcessorTest` | Gravity convergence, discontinuity reset, sampling-rate tracking, low-pass filtering, orientation |
| `MotionSegmenterTest` | Idle (no segment), complete movement, gap → interrupted, flush |
| `DtwMatcherTest` | Identical sequences, different sequences, empty input, time-warped match |
| `GestureRecognizersTest` | Double chop (+ single-peak negative), double twist, shake, flip, tilt, proximity wave, idle negative |
| `PrecisionGateTest` | Accept, ambiguity rejection, duplicate suppression, low-confidence rejection, context rejection, pocket + walking context |
| `CalibrationStoreTest` | Calibration lifecycle, threshold clamping to baseline band, clear |
| `CalibrationPersistenceTest` | Positive/negative templates survive local JSON serialization and negative examples can reject a closer match |
| `RecognitionPipelineTest` | Complete chop sequence, timing window, action eligibility, idle, walking, pocket negatives, profiles |

Run: `./gradlew testDebugUnitTest` (also in CI on every push).

## Method

Tests use synthetic sensor streams generated in code (deterministic,
replayable): sharp accel peaks for chops, alternating gyro sign flips for
twists, oscillating linear accel for shakes, sustained face-down gravity
for flip, near/far proximity transitions for waves, step-detector bursts
for walking context, and covered-proximity samples for pocket context.
Negative examples (idle, single peak, walking, pocket) are asserted to
produce **no** gesture.

## Targets (not claimed results)

- Precision ≥ 99%, recall ≥ 98%
- Fewer than 1 false activation per 24 h in representative use
- No duplicate action execution (PGI cooldown)
- Recognition latency < 500 ms after gesture completion

These are development targets. No accuracy numbers are claimed without
physical-device measurement.

## What is NOT yet verified (honest status)

- **Physical-device testing** on the Samsung Galaxy A14 5G (SM-A146B) or
  any handset has not been performed in this environment — no device was
  available during development. On-device validation of every gesture,
  walking/running/vehicle negatives, screen on/off behaviour, battery
  drain, and manufacturer background restrictions must be done by hand
  using the in-app Gesture Studio (record → replay → compare).
- The optional camera feature is a foreground-only frame-motion activity
  detector, not hand-pose recognition; custom air templates remain
  unsupported. It needs camera permission and has not been physically
  validated on a device.
- **Screen/back-tap gestures** are unsupported by Android public APIs and
  are reported as such (see COMPATIBILITY_REPORT.md).
- CI build status is the source of truth for compilation; check the
  Actions tab for the latest run.

## How to validate on your phone

1. Install the debug APK.
2. Open Gesture Studio → Start recording → perform each gesture, then
   ordinary movements (walk, type, pick up, put down) → Replay/Compare.
3. Enable gestures one at a time in the Gestures tab; assign actions in
   Automation; test with the screen on and off.
4. Export diagnostics (Settings) after a day of use and check the false
   activation count.
