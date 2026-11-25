package com.universalgestures

import com.universalgestures.core.GestureType
import com.universalgestures.core.SensorSample
import com.universalgestures.core.Vec3
import com.universalgestures.recognition.DynamicTimeWarping
import com.universalgestures.recognition.GestureArbitrator
import com.universalgestures.recognition.ArbitrationDecision
import com.universalgestures.recognition.Candidate
import com.universalgestures.recognition.MotionFeature
import com.universalgestures.recognition.MotionFeatureExtractor
import com.universalgestures.sensors.GravityEstimator
import com.universalgestures.sensors.MotionPreprocessor
import com.universalgestures.sensors.MotionSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedMotionPipelineTest {
    @Test fun gravityEstimatorTracksChangingGravityWithoutInvalidNumbers() {
        val estimator = GravityEstimator(timeConstant = 0.3)
        repeat(60) { estimator.update(0f, 0f, 9.81f, 0.02) }
        val result = estimator.update(0f, 9.81f, 0f, 0.02)
        assertTrue(result.all(Double::isFinite))
        assertTrue(result[1] > 0.5)
        assertTrue(result[2] > 0.0)
    }

    @Test fun preprocessorRejectsOutOfOrderFramesAndProducesOrientation() {
        val processor = MotionPreprocessor()
        val sample = MotionSample(1_000_000_000L, Vec3(0f, 0f, 9.81f), Vec3(), rotationVector = Vec3())
        assertNotNull(processor.process(sample))
        assertNull(processor.process(sample.copy(timestampNanos = sample.timestampNanos)))
        val next = processor.process(sample.copy(timestampNanos = sample.timestampNanos + 20_000_000L))
        assertNotNull(next)
        assertNotNull(next?.orientation)
    }

    @Test fun staleGyroscopeIsDownweightedInsteadOfBeingTreatedAsSynchronized() {
        val processor = MotionPreprocessor()
        val frame = processor.process(
            MotionSample(
                timestampNanos = 1_000_000_000L,
                accelerometer = Vec3(0f, 0f, 9.81f),
                gyroscope = Vec3(0f, 4f, 0f),
                gyroscopeTimestampNanos = 500_000_000L
            )
        )
        assertNotNull(frame)
        assertEquals(0f, frame?.angularVelocity?.magnitude ?: -1f, 0.0001f)
        assertTrue((frame?.quality ?: 1f) < 0.5f)
    }

    @Test fun multivariateDtwSeparatesDifferentMotionAndAcceptsIdenticalSequence() {
        val a = (0 until 12).map { i -> feature(if (i < 6) 0.2f else -0.2f, 0.1f) }
        val same = a.map { MotionFeature(it.dimensions.toList()) }
        val other = (0 until 12).map { feature(0.7f, -0.5f) }
        assertEquals(0f, DynamicTimeWarping.distance(a, same), 0.0001f)
        assertTrue(DynamicTimeWarping.distance(a, other) > 0.1f)
    }

    @Test fun extractorRequiresSufficientOrderedFrames() {
        assertTrue(MotionFeatureExtractor.extract(listOf(SensorSample(1), SensorSample(2))).isEmpty())
        val samples = (0 until 10).map { index ->
            SensorSample(
                timestampNanos = index * 20_000_000L + 1,
                accel = Vec3(0f, 0f, 9.81f),
                gravity = Vec3(0f, 0f, 9.81f),
                linearAccel = Vec3(index.toFloat(), 0f, 0f),
                gyro = Vec3(0f, index * 0.02f, 0f),
                orientation = Vec3(0f, index * 0.01f, 0f)
            )
        }
        val features = MotionFeatureExtractor.extract(samples)
        assertEquals(32, features.size)
        assertEquals(11, features.first().dimensions.size)
    }

    @Test fun arbitratorDefersSingleChopAndChoosesMoreSpecificDouble() {
        val arbiter = GestureArbitrator()
        val enabled = setOf(GestureType.SINGLE_CHOP, GestureType.DOUBLE_CHOP)
        val windows = mapOf(GestureType.DOUBLE_CHOP to 700L)
        val single = Candidate(GestureType.SINGLE_CHOP, 0.9f, detectedAtMs = 100)
        assertTrue(arbiter.offer(listOf(single), enabled, windows, 100) is ArbitrationDecision.Deferred)
        val double = Candidate(GestureType.DOUBLE_CHOP, 0.8f, detectedAtMs = 350)
        val winner = arbiter.offer(listOf(double), enabled, windows, 350)
        assertTrue(winner is ArbitrationDecision.Winner)
        assertEquals(GestureType.DOUBLE_CHOP, (winner as ArbitrationDecision.Winner).candidate.type)
    }

    private fun feature(a: Float, b: Float) = MotionFeature(List(11) { index -> if (index == 0) a else if (index == 1) b else 0f })
}
