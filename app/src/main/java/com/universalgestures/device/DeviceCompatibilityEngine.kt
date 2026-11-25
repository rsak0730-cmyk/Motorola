package com.universalgestures.device

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.PowerManager
import com.universalgestures.core.CapabilityStatus
import com.universalgestures.core.DeviceProfile
import com.universalgestures.core.GestureCapability
import com.universalgestures.core.GestureType

/**
 * Device Compatibility Engine.
 *
 * Detects the actual hardware, sensors, Android version and background
 * restrictions at runtime and evaluates every gesture honestly:
 * fully supported / limited / needs permission / needs alternative /
 * unsupported — with a human-readable reason.
 */
class DeviceCompatibilityEngine(private val context: Context) {

    fun collectProfile(): DeviceProfile {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensorCount = sensorManager.getSensorList(Sensor.TYPE_ALL).size
        val hasFlash = try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.cameraIdList.any { id ->
                cm.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_AVAILABLE) == true
            }
        } catch (e: Exception) {
            false
        }
        val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val exempt = try {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) {
            false
        }
        return DeviceProfile(
            manufacturer = Build.MANUFACTURER ?: "Unknown",
            model = Build.MODEL ?: "Unknown",
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            apiLevel = Build.VERSION.SDK_INT,
            sensorCount = sensorCount,
            hasFlash = hasFlash,
            hasCamera = hasCamera,
            batteryOptimisationExempt = exempt,
            manufacturerGuidance = guidanceFor(Build.MANUFACTURER ?: "")
        )
    }

    fun evaluateGestures(): List<GestureCapability> {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        fun has(type: Int) = sensorManager.getDefaultSensor(type) != null
        val hasAccel = has(Sensor.TYPE_ACCELEROMETER)
        val hasGyro = has(Sensor.TYPE_GYROSCOPE)
        val hasLinear = has(Sensor.TYPE_LINEAR_ACCELERATION)
        val hasGravity = has(Sensor.TYPE_GRAVITY)
        val hasProximity = has(Sensor.TYPE_PROXIMITY)
        val hasPickup = has(Sensor.TYPE_PICKUP_GESTURE)
        val hasTilt = has(Sensor.TYPE_TILT_DETECTOR)
        val profile = collectProfile()

        fun motion(status: CapabilityStatus, reason: String) = status to reason

        val results = mutableListOf<GestureCapability>()
        fun add(type: GestureType, status: CapabilityStatus, reason: String) {
            results.add(GestureCapability(type, status, reason))
        }
        fun motionAdd(type: GestureType, capability: Pair<CapabilityStatus, String>) {
            add(type, capability.first, capability.second)
        }

        // Motion gestures need an accelerometer; gyro improves twist.
        val accelReason = if (hasAccel) "Accelerometer available" else "No accelerometer on this device"
        val accelStatus = if (hasAccel) CapabilityStatus.FULLY_SUPPORTED else CapabilityStatus.UNSUPPORTED

        motionAdd(GestureType.DOUBLE_CHOP, motion(
            if (hasAccel) CapabilityStatus.FULLY_SUPPORTED else CapabilityStatus.UNSUPPORTED,
            if (hasAccel) "Uses accelerometer + linear acceleration${if (hasLinear) " (hardware)" else " (software)"}"
            else accelReason))
        motionAdd(GestureType.SINGLE_CHOP, motion(
            if (hasAccel) CapabilityStatus.FULLY_SUPPORTED else CapabilityStatus.UNSUPPORTED,
            if (hasAccel) "Single chop detected from linear acceleration peaks" else accelReason))
        motionAdd(GestureType.DOUBLE_TWIST, motion(
            when {
                hasGyro -> CapabilityStatus.FULLY_SUPPORTED
                hasAccel -> CapabilityStatus.SUPPORTED_WITH_LIMITATIONS
                else -> CapabilityStatus.UNSUPPORTED
            },
            when {
                hasGyro -> "Gyroscope available for wrist-twist detection"
                hasAccel -> "No gyroscope; twist falls back to accelerometer rotation estimate (less reliable)"
                else -> "No gyroscope or accelerometer on this device"
            }))
        motionAdd(GestureType.TRIPLE_TWIST, motion(
            when {
                hasGyro -> CapabilityStatus.FULLY_SUPPORTED
                hasAccel -> CapabilityStatus.SUPPORTED_WITH_LIMITATIONS
                else -> CapabilityStatus.UNSUPPORTED
            },
            when {
                hasGyro -> "Gyroscope available for triple-twist detection"
                hasAccel -> "No gyroscope; accelerometer fallback only"
                else -> "No gyroscope or accelerometer on this device"
            }))
        motionAdd(GestureType.SHAKE, motion(accelStatus, if (hasAccel) "Shake detected from linear acceleration oscillation" else accelReason))
        motionAdd(GestureType.DOUBLE_SHAKE, motion(accelStatus, if (hasAccel) "Double shake counted from oscillation peaks" else accelReason))
        motionAdd(GestureType.TRIPLE_SHAKE, motion(accelStatus, if (hasAccel) "Triple shake counted from oscillation peaks" else accelReason))
        motionAdd(GestureType.FLIP, motion(
            when {
                hasGravity -> CapabilityStatus.FULLY_SUPPORTED
                hasAccel -> CapabilityStatus.SUPPORTED_WITH_LIMITATIONS
                else -> CapabilityStatus.UNSUPPORTED
            },
            when {
                hasGravity -> "Gravity sensor available for face-down detection"
                hasAccel -> "No gravity sensor; software gravity estimate used"
                else -> "No accelerometer on this device"
            }))
        motionAdd(GestureType.PICKUP, motion(
            when {
                hasPickup -> CapabilityStatus.FULLY_SUPPORTED
                hasAccel -> CapabilityStatus.SUPPORTED_WITH_LIMITATIONS
                else -> CapabilityStatus.UNSUPPORTED
            },
            when {
                hasPickup -> "Hardware pickup gesture sensor available"
                hasAccel -> "No pickup sensor; heuristic (rise + tilt) used instead"
                else -> "No accelerometer on this device"
            }))
        val tiltStatus = when {
            hasTilt -> CapabilityStatus.FULLY_SUPPORTED
            hasGravity || hasAccel -> CapabilityStatus.SUPPORTED_WITH_LIMITATIONS
            else -> CapabilityStatus.UNSUPPORTED
        }
        val tiltReason = when {
            hasTilt -> "Tilt detector available"
            hasGravity -> "Gravity sensor used for tilt detection"
            hasAccel -> "Software gravity estimate used for tilt detection"
            else -> "No accelerometer on this device"
        }
        add(GestureType.TILT_LEFT, tiltStatus, tiltReason)
        add(GestureType.TILT_RIGHT, tiltStatus, tiltReason)
        add(GestureType.TILT_FORWARD, tiltStatus, tiltReason)
        add(GestureType.TILT_BACKWARD, tiltStatus, tiltReason)
        motionAdd(GestureType.CUSTOM_MOTION, motion(
            if (hasAccel) CapabilityStatus.FULLY_SUPPORTED else CapabilityStatus.UNSUPPORTED,
            if (hasAccel) "Record your own motion and match it with DTW templates" else accelReason))

        // Screen / back-tap gestures: Android does not expose these events
        // to third-party apps. Reported honestly as unsupported.
        val screenReason = "Android does not expose back-tap or multi-finger screen gestures to third-party apps. This requires OEM system integration (e.g. Motorola / Pixel system settings)."
        add(GestureType.DOUBLE_BACK_TAP, CapabilityStatus.UNSUPPORTED, screenReason)
        add(GestureType.TRIPLE_BACK_TAP, CapabilityStatus.UNSUPPORTED, screenReason)
        add(GestureType.DOUBLE_TAP_SCREEN, CapabilityStatus.UNSUPPORTED, screenReason)
        add(GestureType.THREE_FINGER_SWIPE, CapabilityStatus.UNSUPPORTED, screenReason)
        add(GestureType.THREE_FINGER_SCREENSHOT, CapabilityStatus.UNSUPPORTED, screenReason)
        add(GestureType.CUSTOM_SCREEN, CapabilityStatus.UNSUPPORTED, screenReason)

        // Proximity gestures.
        motionAdd(GestureType.PROXIMITY_WAVE, motion(
            if (hasProximity) CapabilityStatus.FULLY_SUPPORTED else CapabilityStatus.UNSUPPORTED,
            if (hasProximity) "Proximity sensor detects hand waves (near/far transitions). It cannot identify arbitrary hand shapes."
            else "No proximity sensor on this device"))
        motionAdd(GestureType.DOUBLE_PROXIMITY_WAVE, motion(
            if (hasProximity) CapabilityStatus.FULLY_SUPPORTED else CapabilityStatus.UNSUPPORTED,
            if (hasProximity) "Double wave counted from proximity transitions" else "No proximity sensor on this device"))

        // Do not advertise camera-based recognition until a complete,
        // foreground-only camera pipeline is implemented and validated.
        add(GestureType.HAND_MOTION, CapabilityStatus.UNSUPPORTED,
            if (profile.hasCamera) "Camera hardware exists, but foreground hand-motion recognition is not implemented in this build."
            else "No camera on this device.")
        add(GestureType.CUSTOM_AIR, CapabilityStatus.UNSUPPORTED,
            "Custom air-motion recognition is not implemented in this build; proximity reports only near/far transitions.")

        return results
    }

    fun guidanceFor(manufacturer: String): String {
        return when (manufacturer.lowercase()) {
            "samsung" -> "Samsung: allow background activity for Universal Gestures in Settings > Apps > Universal Gestures > Battery > Allow background activity. Also disable 'Optimize battery usage' for the app."
            "motorola" -> "Motorola: background monitoring works with the foreground service. If gestures stop, check Settings > Apps > Universal Gestures > Battery and set to 'Unrestricted'."
            "xiaomi", "redmi", "poco" -> "Xiaomi / Redmi / POCO: lock the app in Recents (swipe down on the app card) and set Autostart to ON in Security > Permissions > Autostart. MIUI aggressively kills background services otherwise."
            "oneplus", "oppo", "realme" -> "OnePlus / OPPO / Realme: set Battery to 'Allow background activity' and disable 'Optimize battery usage' for Universal Gestures. Also enable Autostart where available."
            "vivo" -> "Vivo: enable Background power consumption for Universal Gestures in Settings > Battery > Background power consumption, and add the app to the autostart whitelist."
            "google" -> "Google Pixel: background monitoring runs as a foreground service. No manufacturer restrictions apply; keep battery optimisation exemption for reliability."
            "nothing" -> "Nothing: background monitoring runs as a foreground service. Allow background activity in Settings > Apps > Universal Gestures > Battery."
            else -> "Background monitoring uses a foreground service. If gestures stop working, exempt the app from battery optimisation in Settings > Apps > Universal Gestures > Battery."
        }
    }
}
