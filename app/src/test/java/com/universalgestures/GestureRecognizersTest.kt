package com.universalgestures

import com.universalgestures.core.GestureType
import com.universalgestures.core.SensorSample
import com.universalgestures.core.Vec3
import com.universalgestures.recognition.ChopRecognizer
import com.universalgestures.recognition.FlipRecognizer
import com.universalgestures.recognition.ProximityWaveRecognizer
import com.universalgestures.recognition.ShakeRecognizer
import com.universalgestures.recognition.TiltRecognizer
import com.universalgestures.recognition.TwistRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GestureRecognizersTest {

    private fun sample(ms: Long, linear: Vec3 = Vec3(), gyro: Vec3 = Vec3(), gravity: Vec3 = Vec3(0f, 0f, 9.81f), proximity: Float? = null): SensorSample =
        SensorSample(
            timestampNanos = ms * 1_000_000L,
            accel = Vec3(0f, 0f, 9.81f),
            gyro = gyro,
            gravity = gravity,
            linearAccel = linear,
            proximityCm = proximity
        )

    /** Two sharp linear-accel peaks 400 ms apart. */
    @Test
    fun doubleChopDetected() {
        val recognizer = ChopRecognizer(GestureType.DOUBLE_CHOP, peaks = 2, sensitivity = 50)
        var candidate: com.universalgestures.recognition.Candidate? = null
        for (ms in 0L until 2000L step 20L) {
            val linear = when {
                ms in 200L..260L -> Vec3(0f, -18f, 0f)
                ms in 600L..660L -> Vec3(0f, -18f, 0f)
                else -> Vec3()
            }
            val result = recognizer.onSample(sample(ms, linear = linear))
            if (result != null) candidate = result
        }
        assertNotNull(candidate)
        assertEquals(GestureType.DOUBLE_CHOP, candidate!!.type)
    }

    @Test
    fun singlePeakDoesNotTriggerDoubleChop() {
        val recognizer = ChopRecognizer(GestureType.DOUBLE_CHOP, peaks = 2, sensitivity = 50)
        var candidate: com.universalgestures.recognition.Candidate? = null
        for (ms in 0L until 2000L step 20L) {
            val linear = if (ms in 200L..260L) Vec3(0f, -18f, 0f) else Vec3()
            val result = recognizer.onSample(sample(ms, linear = linear))
            if (result != null) candidate = result
        }
        assertNull(candidate)
    }

    /** Alternating gyro sign flips = wrist twist. */
    @Test
    fun doubleTwistDetected() {
        val recognizer = TwistRecognizer(GestureType.DOUBLE_TWIST, requiredTwists = 2, sensitivity = 50)
        var candidate: com.universalgestures.recognition.Candidate? = null
        for (ms in 0L until 2500L step 20L) {
            val gyro = when {
                ms in 200L..400L -> Vec3(0f, 0f, 8f)
                ms in 700L..900L -> Vec3(0f, 0f, -8f)
                else -> Vec3()
            }
            val result = recognizer.onSample(sample(ms, gyro = gyro))
            if (result != null) candidate = result
        }
        assertNotNull(candidate)
        assertEquals(GestureType.DOUBLE_TWIST, candidate!!.type)
    }

    /** Oscillating linear accel = shake. */
    @Test
    fun shakeDetected() {
        val recognizer = ShakeRecognizer(GestureType.SHAKE, oscillations = 2, sensitivity = 50)
        var candidate: com.universalgestures.recognition.Candidate? = null
        for (ms in 0L until 2500L step 20L) {
            val linear = when {
                ms in 200L..300L -> Vec3(12f, 0f, 0f)
                ms in 500L..600L -> Vec3(-12f, 0f, 0f)
                ms in 800L..900L -> Vec3(12f, 0f, 0f)
                else -> Vec3()
            }
            val result = recognizer.onSample(sample(ms, linear = linear))
            if (result != null) candidate = result
        }
        assertNotNull(candidate)
        assertEquals(GestureType.SHAKE, candidate!!.type)
    }

    @Test
    fun flipDetectedWhenFaceDownHeld() {
        val recognizer = FlipRecognizer()
        var candidate: com.universalgestures.recognition.Candidate? = null
        for (ms in 0L until 2000L step 20L) {
            val gravity = if (ms < 300L) Vec3(0f, 0f, 9.81f) else Vec3(0f, 0f, -9.81f)
            val result = recognizer.onSample(sample(ms, gravity = gravity))
            if (result != null) candidate = result
        }
        assertNotNull(candidate)
        assertEquals(GestureType.FLIP, candidate!!.type)
    }

    @Test
    fun tiltRightDetected() {
        val recognizer = TiltRecognizer(GestureType.TILT_RIGHT, sensitivity = 50)
        var candidate: com.universalgestures.recognition.Candidate? = null
        for (ms in 0L until 1500L step 20L) {
            val gravity = Vec3(7f, 0f, 6.8f)
            val result = recognizer.onSample(sample(ms, gravity = gravity))
            if (result != null) candidate = result
        }
        assertNotNull(candidate)
        assertEquals(GestureType.TILT_RIGHT, candidate!!.type)
    }

    @Test
    fun proximityWaveDetected() {
        val recognizer = ProximityWaveRecognizer(GestureType.PROXIMITY_WAVE, requiredWaves = 1)
        var candidate: com.universalgestures.recognition.Candidate? = null
        for (ms in 0L until 2000L step 50L) {
            val proximity = when {
                ms in 200L..400L -> 0.5f
                ms in 600L..800L -> 0.5f
                else -> 5f
            }
            val result = recognizer.onSample(sample(ms, proximity = proximity))
            if (result != null) candidate = result
        }
        assertNotNull(candidate)
        assertEquals(GestureType.PROXIMITY_WAVE, candidate!!.type)
    }

    @Test
    fun idleStreamProducesNoCandidates() {
        val recognizer = ChopRecognizer(GestureType.DOUBLE_CHOP, peaks = 2, sensitivity = 50)
        repeat(100) { i ->
            assertNull(recognizer.onSample(sample(i * 20L)))
        }
    }
}
