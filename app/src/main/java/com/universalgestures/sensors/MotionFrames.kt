package com.universalgestures.sensors

import com.universalgestures.core.SensorSample
import com.universalgestures.core.Vec3
import kotlin.math.sqrt

/** Raw timestamped Android sensor bundle before filtering. */
data class MotionSample(
    val timestampNanos: Long,
    val accelerometer: Vec3,
    val gyroscope: Vec3,
    val accelerometerTimestampNanos: Long? = timestampNanos,
    val gyroscopeTimestampNanos: Long? = null,
    val gravitySensor: Vec3? = null,
    val gravityTimestampNanos: Long? = null,
    val linearAccelerationSensor: Vec3? = null,
    val linearAccelerationTimestampNanos: Long? = null,
    val rotationVector: Vec3? = null,
    val rotationVectorTimestampNanos: Long? = null,
    val proximityCm: Float? = null,
    val stepDetected: Boolean = false
)

/** Synchronized, compensated sample consumed by segmentation and recognition. */
data class MotionFrame(
    val timestampNanos: Long,
    val acceleration: Vec3,
    val linearAcceleration: Vec3,
    val angularVelocity: Vec3,
    val gravity: Vec3,
    /** Roll, pitch, yaw in radians. */
    val orientation: Vec3,
    val proximityCm: Float?,
    val stepDetected: Boolean,
    val quality: Float
) {
    fun toSensorSample(rotationVector: Vec3? = null) = SensorSample(
        timestampNanos = timestampNanos,
        accel = acceleration,
        gyro = angularVelocity,
        gravity = gravity,
        linearAccel = linearAcceleration,
        rotationVector = rotationVector,
        orientation = orientation,
        proximityCm = proximityCm,
        stepDetected = stepDetected,
        quality = quality
    )
}

/** Timestamp validation + IMU processing boundary. */
class MotionPreprocessor(private val imu: ImuProcessor = ImuProcessor()) {
    private var lastTimestamp = -1L

    fun process(sample: MotionSample): MotionFrame? {
        if (sample.timestampNanos <= 0L || !sample.accelerometer.isFinite() || !sample.gyroscope.isFinite()) return null
        if (lastTimestamp >= 0L && sample.timestampNanos <= lastTimestamp) return null
        val dt = if (lastTimestamp < 0L) 0.02 else (sample.timestampNanos - lastTimestamp) / 1e9
        if (dt > 0.5) imu.reset()
        lastTimestamp = sample.timestampNanos
        fun fresh(sourceTimestamp: Long?, maxAgeNanos: Long): Boolean = sourceTimestamp == null ||
            kotlin.math.abs(sample.timestampNanos - sourceTimestamp) <= maxAgeNanos
        val accelFresh = fresh(sample.accelerometerTimestampNanos, 100_000_000L)
        if (!accelFresh) return null
        val gyroFresh = fresh(sample.gyroscopeTimestampNanos, 120_000_000L)
        val gravityFresh = fresh(sample.gravityTimestampNanos, 300_000_000L)
        val linearFresh = fresh(sample.linearAccelerationTimestampNanos, 120_000_000L)
        val rotationFresh = fresh(sample.rotationVectorTimestampNanos, 300_000_000L)
        val gyro = if (gyroFresh) sample.gyroscope else Vec3()
        val hardwareGravity = sample.gravitySensor?.takeIf { gravityFresh && it.isFinite() }
        val hardwareLinear = sample.linearAccelerationSensor?.takeIf { linearFresh && it.isFinite() }
        val processed = imu.process(sample.timestampNanos, sample.accelerometer, gyro, hardwareGravity, hardwareLinear)
        val gravity = hardwareGravity ?: processed.gravity
        val (pitch, roll) = imu.orientationFromGravity(gravity)
        val orientation = sample.rotationVector?.takeIf { rotationFresh && it.isFinite() }
            ?.let(SensorFusionEngine::orientationFromRotationVector) ?: Vec3(roll, pitch, 0f)
        val baseQuality = when {
            dt > 0.5 || dt < 0.001 -> 0f
            !processed.linearAccel.isFinite() || !processed.gyro.isFinite() -> 0f
            else -> (1f - (dt / 0.1).toFloat() * 0.2f).coerceIn(0.5f, 1f)
        }
        val quality = if (sample.gyroscopeTimestampNanos != null && !gyroFresh) minOf(baseQuality, 0.4f) else baseQuality
        return MotionFrame(
            sample.timestampNanos,
            processed.accel,
            processed.linearAccel,
            processed.gyro,
            gravity,
            orientation,
            sample.proximityCm,
            sample.stepDetected,
            quality
        )
    }

    fun reset() { lastTimestamp = -1L; imu.reset() }
}

/** Fusion uses Android's rotation-vector estimate when present, otherwise gravity-derived roll/pitch. */
object SensorFusionEngine {
    fun orientationFromRotationVector(vector: Vec3): Vec3 {
        val x = vector.x.toDouble(); val y = vector.y.toDouble(); val z = vector.z.toDouble()
        val w = sqrt((1.0 - x * x - y * y - z * z).coerceAtLeast(0.0))
        val roll = kotlin.math.atan2(2.0 * (w * x + y * z), 1.0 - 2.0 * (x * x + y * y))
        val pitch = kotlin.math.asin((2.0 * (w * y - z * x)).coerceIn(-1.0, 1.0))
        val yaw = kotlin.math.atan2(2.0 * (w * z + x * y), 1.0 - 2.0 * (y * y + z * z))
        return Vec3(roll.toFloat(), pitch.toFloat(), yaw.toFloat())
    }
}

private fun Vec3.isFinite() = x.isFinite() && y.isFinite() && z.isFinite()
