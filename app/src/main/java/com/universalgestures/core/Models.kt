package com.universalgestures.core

import kotlin.math.sqrt

/** Immutable 3-axis vector used across the motion pipeline. */
data class Vec3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
    val magnitude: Float get() = sqrt(x * x + y * y + z * z)
    val magnitudeSq: Float get() = x * x + y * y + z * z

    fun normalized(): Vec3 {
        val m = magnitude
        return if (m > 1e-6f) Vec3(x / m, y / m, z / m) else Vec3()
    }

    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun dominantAxis(): Int {
        val ax = kotlin.math.abs(x); val ay = kotlin.math.abs(y); val az = kotlin.math.abs(z)
        return when {
            ax >= ay && ax >= az -> 0
            ay >= az -> 1
            else -> 2
        }
    }

    fun component(axis: Int): Float = when (axis) {
        0 -> x; 1 -> y; else -> z
    }
}

/** One fused IMU sample. All fields are in device coordinates, SI units. */
data class SensorSample(
    val timestampNanos: Long,
    val accel: Vec3 = Vec3(),
    val gyro: Vec3 = Vec3(),
    val gravity: Vec3 = Vec3(),
    val linearAccel: Vec3 = Vec3(),
    val rotationVector: Vec3? = null,
    /** Fused roll, pitch, yaw radians when available. */
    val orientation: Vec3? = null,
    val proximityCm: Float? = null,
    val stepDetected: Boolean = false,
    val pickupDetected: Boolean = false,
    val tiltDetected: Boolean = false,
    val significantMotion: Boolean = false,
    /** Timestamp/fusion quality 0..1; zero denotes a discontinuity/untrusted frame. */
    val quality: Float = 1f
) {
    val timestampMs: Long get() = timestampNanos / 1_000_000L
}

enum class MotionState { IDLE, MOVING, PAUSED, COMPLETE }

enum class GestureType {
    DOUBLE_CHOP, SINGLE_CHOP,
    DOUBLE_TWIST, TRIPLE_TWIST,
    SHAKE, DOUBLE_SHAKE, TRIPLE_SHAKE,
    FLIP, PICKUP,
    TILT_LEFT, TILT_RIGHT, TILT_FORWARD, TILT_BACKWARD,
    CUSTOM_MOTION,
    DOUBLE_BACK_TAP, TRIPLE_BACK_TAP,
    DOUBLE_TAP_SCREEN, THREE_FINGER_SWIPE, THREE_FINGER_SCREENSHOT, CUSTOM_SCREEN,
    PROXIMITY_WAVE, DOUBLE_PROXIMITY_WAVE,
    HAND_MOTION, CUSTOM_AIR
}

enum class CapabilityStatus {
    FULLY_SUPPORTED,
    SUPPORTED_WITH_LIMITATIONS,
    REQUIRES_PERMISSION,
    REQUIRES_ALTERNATIVE,
    UNSUPPORTED
}

data class GestureCapability(
    val type: GestureType,
    val status: CapabilityStatus,
    val reason: String
)

enum class ActionType {
    NONE,
    FLASHLIGHT_TOGGLE,
    OPEN_CAMERA,
    OPEN_APP,
    OPEN_SHORTCUT,
    OPEN_SETTINGS,
    MEDIA_PLAY_PAUSE,
    MEDIA_NEXT,
    MEDIA_PREVIOUS,
    VOLUME_UP,
    VOLUME_DOWN,
    SCREENSHOT,
    LOCK_SCREEN,
    DND_TOGGLE,
    RINGER_TOGGLE,
    OPEN_NOTIFICATIONS,
    CUSTOM_SHORTCUT
}

data class GestureConfig(
    val type: GestureType,
    val enabled: Boolean = false,
    val action: ActionType = ActionType.NONE,
    val actionTarget: String = "",
    /** 0..100 — higher means more sensitive (lower thresholds). */
    val sensitivity: Int = 50,
    /** Inter-event window in ms used by multi-stage gestures. */
    val timingMs: Int = 900,
    /** Minimum delay between accepted actions for this gesture. */
    val cooldownMs: Int = 1200,
    val calibrated: Boolean = false
)

data class GestureEvent(
    val type: GestureType,
    val confidence: Float,
    val timestampMs: Long,
    val latencyMs: Long = 0L,
    val sequenceId: Long = 0L
)

enum class RejectionReason {
    NONE,
    INSUFFICIENT_DATA,
    SEGMENT_INCOMPLETE,
    PATTERN_MISMATCH,
    DIRECTION_MISMATCH,
    ROTATION_MISMATCH,
    CONTEXT_REJECTED,
    LOW_CONFIDENCE,
    AMBIGUOUS,
    DUPLICATE,
    ACTION_INELIGIBLE,
    SENSOR_QUALITY_LOW
}

data class RecognitionResult(
    val event: GestureEvent? = null,
    val rejectionReason: RejectionReason = RejectionReason.NONE,
    val detail: String = ""
)

data class MotionSegment(
    val startMs: Long,
    val endMs: Long,
    val samples: List<SensorSample>,
    val peakLinearAccel: Float,
    val peakGyro: Float,
    val dominantAxis: Int,
    val directionChanges: Int,
    val interrupted: Boolean = false
) {
    val durationMs: Long get() = endMs - startMs
    val complete: Boolean get() = !interrupted && samples.size >= 4
}

data class SensorInfo(
    val type: Int,
    val name: String,
    val vendor: String,
    val version: Int,
    val resolution: Float,
    val maximumRange: Float,
    val powerMa: Float,
    val minDelayUs: Int,
    val reportingMode: Int,
    val wakeUp: Boolean
)

data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val sensorCount: Int,
    val hasFlash: Boolean,
    val hasCamera: Boolean,
    val batteryOptimisationExempt: Boolean,
    val manufacturerGuidance: String,
    /** Runtime-reported Android build fingerprint label (Build.DISPLAY), not a guessed UI version. */
    val firmwareBuild: String = "Unknown",
    val securityPatch: String = "Unknown",
    val manufacturerUiVersion: String = "Not exposed by public Android APIs"
) {
    val isTargetSamsungA146B: Boolean
        get() = manufacturer.equals("samsung", ignoreCase = true) && model.contains("SM-A146B", ignoreCase = true)
}

data class DiagnosticSnapshot(
    val timestampMs: Long,
    val accel: Vec3,
    val gyro: Vec3,
    val gravity: Vec3,
    val linearAccel: Vec3,
    val samplingHz: Float,
    val motionState: MotionState,
    val lastGesture: GestureType?,
    val lastConfidence: Float,
    val lastRejection: RejectionReason,
    val lastLatencyMs: Long,
    val activeProfile: String
)

data class ActionResult(val success: Boolean, val message: String)

enum class RecognitionProfile { DEFAULT, PERSONALIZED, HIGH_PRECISION, HIGH_SENSITIVITY, CUSTOM }
