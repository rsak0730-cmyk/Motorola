package com.universalgestures.recognition

import com.universalgestures.core.MotionSegment
import com.universalgestures.core.SensorSample
import com.universalgestures.core.GestureType

/** Conservative rejection of common non-gesture segments; no positive label can bypass it. */
enum class NegativeMotionKind { NONE, POCKET_COVERED, WALKING_RHYTHM, VEHICLE_VIBRATION, TABLE_IMPACT, INVALID_SEQUENCE }

data class NegativeMotionResult(val kind: NegativeMotionKind, val reason: String) {
    val rejected: Boolean get() = kind != NegativeMotionKind.NONE
}

class NegativeMotionClassifier {
    fun classify(segment: MotionSegment, samples: List<SensorSample>, stepsPerMinute: Float, gesture: GestureType? = null): NegativeMotionResult {
        if (!segment.complete || segment.samples.size < 4) return NegativeMotionResult(NegativeMotionKind.INVALID_SEQUENCE, "incomplete segment")
        if (samples.any { (it.proximityCm ?: Float.MAX_VALUE) < 1.2f }) {
            return NegativeMotionResult(NegativeMotionKind.POCKET_COVERED, "proximity indicates covered device")
        }
        if (stepsPerMinute >= 110f && segment.directionChanges < 3 &&
            gesture in setOf(GestureType.SINGLE_CHOP, GestureType.SHAKE, GestureType.PICKUP)
        ) {
            return NegativeMotionResult(NegativeMotionKind.WALKING_RHYTHM, "gait cadence plus low sequence complexity")
        }
        val active = segment.samples.filter { it.linearAccel.magnitude > 1.5f || it.gyro.magnitude > 0.5f }
        val activeDuration = if (active.size < 2) 0L else active.last().timestampMs - active.first().timestampMs
        // Reject only the extreme single-frame impact signature. Wider intentional
        // chops remain eligible for context, arbitration and personalized matching.
        if (segment.peakLinearAccel > 24f && activeDuration <= 40L && segment.peakGyro < 0.8f && segment.directionChanges == 0) {
            return NegativeMotionResult(NegativeMotionKind.TABLE_IMPACT, "impulsive acceleration without a rotation/sequence profile")
        }
        val energy = active.map { it.linearAccel.magnitude }
        if (energy.size >= 30) {
            val mean = energy.average()
            val variance = energy.map { (it - mean) * (it - mean) }.average()
            if (mean > 1.8 && kotlin.math.sqrt(variance) / mean.coerceAtLeast(0.01) < 0.18) {
                return NegativeMotionResult(NegativeMotionKind.VEHICLE_VIBRATION, "sustained low-variance vibration")
            }
        }
        return NegativeMotionResult(NegativeMotionKind.NONE, "no negative signature matched")
    }
}
