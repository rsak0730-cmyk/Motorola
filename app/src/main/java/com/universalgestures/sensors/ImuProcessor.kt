package com.universalgestures.sensors

import com.universalgestures.core.Vec3
import kotlin.math.abs

/**
 * Independent IMU processing pipeline (UMI layer B).
 *
 * - Low-pass filtering of accelerometer / gyroscope streams.
 * - Gravity estimation via a complementary low-pass on the accelerometer
 *   (alpha ~0.08) cross-checked against the hardware gravity sensor when
 *   present, and linear-acceleration extraction as accel - gravity.
 * - Gyro bias calibration while the device is still.
 * - Sampling-rate tracking and discontinuity handling (filter reset when
 *   the inter-sample gap exceeds 500 ms, e.g. after screen-off gaps).
 *
 * Pure Kotlin — no Android dependencies — so it is fully unit-testable.
 */
class ImuProcessor {

    private var filteredAccel = Vec3()
    private var filteredGyro = Vec3()
    private var gravityEstimate = Vec3(0f, 0f, 9.81f)
    private val gravityEstimator = GravityEstimator(timeConstant = 0.8)
    private var gyroBias = Vec3()
    private var lastTimestampNanos: Long = -1L
    private var samplingHzEma = 0f
    private var stillSamples = 0

    var resetCount = 0
        private set

    val samplingHz: Float get() = samplingHzEma
    val bias: Vec3 get() = gyroBias

    fun reset() {
        filteredAccel = Vec3()
        filteredGyro = Vec3()
        gravityEstimate = Vec3(0f, 0f, 9.81f)
        gravityEstimator.reset()
        gyroBias = Vec3()
        lastTimestampNanos = -1L
        samplingHzEma = 0f
        stillSamples = 0
    }

    /**
     * @param accel raw accelerometer reading (m/s^2)
     * @param gyro raw gyroscope reading (rad/s)
     * @param hardwareGravity optional TYPE_GRAVITY reading; when present it
     *   anchors the gravity estimate (hardware-assisted fusion).
     * @param hardwareLinearAccel optional TYPE_LINEAR_ACCELERATION reading.
     * @return processed triple (filtered accel, bias-corrected filtered gyro,
     *   linear acceleration).
     */
    fun process(
        timestampNanos: Long,
        accel: Vec3,
        gyro: Vec3,
        hardwareGravity: Vec3? = null,
        hardwareLinearAccel: Vec3? = null
    ): ProcessedImu {
        val dtNanos = if (lastTimestampNanos < 0) 0L else timestampNanos - lastTimestampNanos
        if (dtNanos > 500_000_000L) {
            // Sensor discontinuity: drop stale filter state rather than
            // producing a huge artificial transient.
            resetCount++
            filteredAccel = accel
            filteredGyro = gyro
            gravityEstimator.reset()
            val estimate = gravityEstimator.update(accel.x, accel.y, accel.z, 0.02)
            gravityEstimate = Vec3(estimate[0].toFloat(), estimate[1].toFloat(), estimate[2].toFloat())
            lastTimestampNanos = timestampNanos
            return ProcessedImu(filteredAccel, filteredGyro, Vec3(), gravityEstimate, true)
        }
        if (dtNanos > 0) {
            val hz = 1e9f / dtNanos.toFloat()
            samplingHzEma = if (samplingHzEma == 0f) hz else samplingHzEma * 0.9f + hz * 0.1f
        }
        lastTimestampNanos = timestampNanos

        // Low-pass filters (first-order IIR).
        val accelAlpha = 0.35f
        filteredAccel = filteredAccel * (1f - accelAlpha) + accel * accelAlpha
        val gyroAlpha = 0.5f
        filteredGyro = filteredGyro * (1f - gyroAlpha) + gyro * gyroAlpha

        // Gravity estimation: slow low-pass on the accelerometer, anchored
        // by the hardware gravity sensor when available.
        val dtSeconds = if (dtNanos > 0L) dtNanos / 1_000_000_000.0 else 0.02
        val softwareGravity = gravityEstimator.update(accel.x, accel.y, accel.z, dtSeconds)
        gravityEstimate = if (hardwareGravity != null && hardwareGravity.x.isFinite() &&
            hardwareGravity.y.isFinite() && hardwareGravity.z.isFinite()
        ) {
            // Use the OS fused gravity sensor as the anchor, with a conservative
            // correction toward the timestamp-aware software estimate.
            val hw = hardwareGravity
            Vec3(
                hw.x * 0.8f + softwareGravity[0].toFloat() * 0.2f,
                hw.y * 0.8f + softwareGravity[1].toFloat() * 0.2f,
                hw.z * 0.8f + softwareGravity[2].toFloat() * 0.2f
            )
        } else Vec3(softwareGravity[0].toFloat(), softwareGravity[1].toFloat(), softwareGravity[2].toFloat())

        // Gyro bias calibration while still (magnitude below 0.02 rad/s).
        val gyroMag = filteredGyro.magnitude
        if (gyroMag < 0.02f && accel.magnitude in 9.0f..10.6f) {
            stillSamples++
            if (stillSamples > 20) {
                val k = 0.02f
                gyroBias = gyroBias * (1f - k) + filteredGyro * k
            }
        } else {
            stillSamples = 0
        }
        val correctedGyro = filteredGyro - gyroBias

        val linearAccel = hardwareLinearAccel ?: (filteredAccel - gravityEstimate)
        return ProcessedImu(filteredAccel, correctedGyro, linearAccel, gravityEstimate, false)
    }

    /** Complementary-filter orientation (roll/pitch) from gravity + gyro integration. */
    fun orientationFromGravity(gravity: Vec3): Pair<Float, Float> {
        val g = gravity.normalized()
        val pitch = kotlin.math.atan2(g.y, g.z)
        val roll = kotlin.math.atan2(-g.x, kotlin.math.sqrt(g.y * g.y + g.z * g.z))
        return pitch to roll
    }

    fun isStill(accel: Vec3, gyro: Vec3): Boolean =
        abs(accel.magnitude - 9.81f) < 0.4f && gyro.magnitude < 0.05f
}

data class ProcessedImu(
    val accel: Vec3,
    val gyro: Vec3,
    val linearAccel: Vec3,
    val gravity: Vec3,
    val wasReset: Boolean
)

/** First-order low-pass filter used for proximity and auxiliary streams. */
class LowPassFilter(private val alpha: Float) {
    private var value = 0f
    private var initialised = false

    fun update(sample: Float): Float {
        value = if (!initialised) {
            initialised = true
            sample
        } else {
            value * (1f - alpha) + sample * alpha
        }
        return value
    }

    fun reset() {
        initialised = false
        value = 0f
    }
}
