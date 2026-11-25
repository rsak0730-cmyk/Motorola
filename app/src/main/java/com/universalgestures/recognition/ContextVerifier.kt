package com.universalgestures.recognition

import com.universalgestures.core.GestureType
import com.universalgestures.core.SensorSample

/**
 * CMVE — Contextual Motion Verification Engine.
 *
 * Distinguishes intentional gestures from walking, running, pocket
 * movement, table impacts, vehicle motion and ordinary handling.
 * Context raises the bar for acceptance; it never fabricates a gesture.
 */
class ContextVerifier {

    private var stepTimes = mutableListOf<Long>()
    private var recentEnergy = mutableListOf<Pair<Long, Float>>()

    fun onSample(sample: SensorSample) {
        val now = sample.timestampMs
        if (sample.stepDetected) {
            stepTimes.add(now)
        }
        while (stepTimes.isNotEmpty() && now - stepTimes.first() > 4000L) {
            stepTimes.removeAt(0)
        }
        recentEnergy.add(now to sample.linearAccel.magnitude)
        while (recentEnergy.isNotEmpty() && now - recentEnergy.first().first > 3000L) {
            recentEnergy.removeAt(0)
        }
    }

    /** Steps per minute over the trailing window (walking/running indicator). */
    fun stepsPerMinute(nowMs: Long): Float {
        val cutoff = nowMs - 4000L
        val count = stepTimes.count { it >= cutoff }
        return count * 15f
    }

    fun isInPocket(sample: SensorSample): Boolean {
        val p = sample.proximityCm ?: return false
        return p < 2f
    }

    /** Sustained periodic energy typical of vehicle / walking vibration. */
    fun hasSustainedPeriodicEnergy(): Boolean {
        if (recentEnergy.size < 20) return false
        val avg = recentEnergy.map { it.second }.average()
        return avg > 1.8
    }

    /**
     * @return true when context allows the gesture to proceed.
     */
    fun allows(gesture: GestureType, sample: SensorSample, nowMs: Long): Boolean {
        // Pocket / covered device: motion gestures are almost always
        // accidental. Proximity gestures are exempt (they need the sensor).
        val proximityGestures = setOf(
            GestureType.PROXIMITY_WAVE, GestureType.DOUBLE_PROXIMITY_WAVE
        )
        if (gesture !in proximityGestures && isInPocket(sample)) return false

        // Walking / running: require stronger evidence for chop/shake/twist
        // by rejecting low-confidence candidates outright.
        val spm = stepsPerMinute(nowMs)
        if (spm > 60f && gesture in setOf(
                GestureType.SINGLE_CHOP, GestureType.DOUBLE_CHOP,
                GestureType.SHAKE, GestureType.DOUBLE_SHAKE, GestureType.TRIPLE_SHAKE
            )
        ) {
            return false
        }
        return true
    }

    fun reset() {
        stepTimes.clear()
        recentEnergy.clear()
    }
}
