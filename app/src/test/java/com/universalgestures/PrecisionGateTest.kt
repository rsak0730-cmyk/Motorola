package com.universalgestures

import com.universalgestures.core.GestureType
import com.universalgestures.core.RejectionReason
import com.universalgestures.core.SensorSample
import com.universalgestures.core.Vec3
import com.universalgestures.recognition.Candidate
import com.universalgestures.recognition.ContextVerifier
import com.universalgestures.recognition.PrecisionGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PrecisionGateTest {

    private fun sample(ms: Long, proximity: Float? = 5f): SensorSample = SensorSample(
        timestampNanos = ms * 1_000_000L,
        accel = Vec3(0f, 0f, 9.81f),
        gyro = Vec3(),
        gravity = Vec3(0f, 0f, 9.81f),
        linearAccel = Vec3(),
        proximityCm = proximity
    )

    @Test
    fun strongCandidateAccepted() {
        val gate = PrecisionGate()
        val result = gate.evaluate(
            candidates = listOf(Candidate(GestureType.DOUBLE_CHOP, 0.9f)),
            sample = sample(1000L),
            contextAllows = true,
            segmentComplete = true,
            sensorQualityOk = true,
            actionEligible = true
        )
        assertNotNull(result.event)
        assertEquals(GestureType.DOUBLE_CHOP, result.event!!.type)
    }

    @Test
    fun ambiguousCandidatesRejected() {
        val gate = PrecisionGate()
        val result = gate.evaluate(
            candidates = listOf(
                Candidate(GestureType.DOUBLE_CHOP, 0.9f),
                Candidate(GestureType.SHAKE, 0.85f)
            ),
            sample = sample(1000L),
            contextAllows = true,
            segmentComplete = true,
            sensorQualityOk = true,
            actionEligible = true
        )
        assertNull(result.event)
        assertEquals(RejectionReason.AMBIGUOUS, result.rejectionReason)
    }

    @Test
    fun duplicateSuppressed() {
        val gate = PrecisionGate()
        val first = gate.evaluate(
            listOf(Candidate(GestureType.DOUBLE_CHOP, 0.9f)),
            sample(1000L), true, true, true, true
        )
        assertNotNull(first.event)
        val second = gate.evaluate(
            listOf(Candidate(GestureType.DOUBLE_CHOP, 0.9f)),
            sample(1100L), true, true, true, true
        )
        assertNull(second.event)
        assertEquals(RejectionReason.DUPLICATE, second.rejectionReason)
    }

    @Test
    fun lowConfidenceRejected() {
        val gate = PrecisionGate()
        val result = gate.evaluate(
            listOf(Candidate(GestureType.SHAKE, 0.3f)),
            sample(1000L), true, true, true, true
        )
        assertNull(result.event)
        assertEquals(RejectionReason.LOW_CONFIDENCE, result.rejectionReason)
    }

    @Test
    fun contextRejectionHonoured() {
        val gate = PrecisionGate()
        val result = gate.evaluate(
            listOf(Candidate(GestureType.DOUBLE_CHOP, 0.9f)),
            sample(1000L), contextAllows = false, segmentComplete = true,
            sensorQualityOk = true, actionEligible = true
        )
        assertNull(result.event)
        assertEquals(RejectionReason.CONTEXT_REJECTED, result.rejectionReason)
    }

    @Test
    fun pocketContextRejectsMotionGestures() {
        val context = ContextVerifier()
        val pocketSample = sample(1000L, proximity = 0.5f)
        context.onSample(pocketSample)
        assertEquals(false, context.allows(GestureType.DOUBLE_CHOP, pocketSample, 1000L))
        // Proximity gestures are exempt (they need the sensor).
        assertEquals(true, context.allows(GestureType.PROXIMITY_WAVE, pocketSample, 1000L))
    }

    @Test
    fun walkingContextRejectsChop() {
        val context = ContextVerifier()
        // Simulate 8 steps in 4 seconds (~120 spm).
        for (i in 0 until 8) {
            val s = SensorSample(
                timestampNanos = (i * 500L) * 1_000_000L,
                accel = Vec3(0f, 0f, 9.81f),
                gyro = Vec3(),
                gravity = Vec3(0f, 0f, 9.81f),
                linearAccel = Vec3(),
                proximityCm = 5f,
                stepDetected = true
            )
            context.onSample(s)
        }
        val now = sample(4000L)
        assertEquals(false, context.allows(GestureType.DOUBLE_CHOP, now, 4000L))
    }
}
