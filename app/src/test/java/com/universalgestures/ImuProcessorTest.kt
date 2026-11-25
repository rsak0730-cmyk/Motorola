package com.universalgestures

import com.universalgestures.core.Vec3
import com.universalgestures.sensors.ImuProcessor
import com.universalgestures.sensors.LowPassFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ImuProcessorTest {

    @Test
    fun gravityEstimationConvergesToRestingAcceleration() {
        val imu = ImuProcessor()
        var lastLinear = Vec3()
        // Resting device: accel = (0, 0, 9.81), no rotation.
        for (i in 0 until 300) {
            val result = imu.process(
                timestampNanos = i * 20_000_000L,
                accel = Vec3(0f, 0f, 9.81f),
                gyro = Vec3()
            )
            lastLinear = result.linearAccel
        }
        // After convergence, linear acceleration should be near zero.
        assertTrue("linear accel magnitude ${lastLinear.magnitude}", lastLinear.magnitude < 1.0f)
    }

    @Test
    fun discontinuityResetsFilters() {
        val imu = ImuProcessor()
        imu.process(0L, Vec3(0f, 0f, 9.81f), Vec3())
        val before = imu.resetCount
        // 1 second gap -> discontinuity.
        val result = imu.process(1_000_000_000L, Vec3(0f, 0f, 9.81f), Vec3())
        assertTrue(result.wasReset)
        assertEquals(before + 1, imu.resetCount)
    }

    @Test
    fun samplingRateTracked() {
        val imu = ImuProcessor()
        for (i in 0 until 50) {
            imu.process(i * 20_000_000L, Vec3(0f, 0f, 9.81f), Vec3())
        }
        // 20 ms period -> ~50 Hz.
        assertTrue("hz=${imu.samplingHz}", imu.samplingHz in 40f..60f)
    }

    @Test
    fun lowPassFilterSmooths() {
        val filter = LowPassFilter(0.2f)
        var value = 0f
        repeat(100) { value = filter.update(10f) }
        assertTrue(abs(value - 10f) < 0.5f)
    }

    @Test
    fun orientationFromGravityFlatDevice() {
        val imu = ImuProcessor()
        val (pitch, roll) = imu.orientationFromGravity(Vec3(0f, 0f, 9.81f))
        assertTrue(abs(pitch) < 0.01f)
        assertTrue(abs(roll) < 0.01f)
    }
}
