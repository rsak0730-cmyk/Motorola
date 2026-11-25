package com.universalgestures.recognition

import com.universalgestures.core.GestureType
import com.universalgestures.core.SensorSample
import kotlin.math.sqrt

/**
 * CMVE — Contextual Motion Verification Engine.
 *
 * Distinguishes intentional gestures from walking, running, pocket
 * movement, table impacts, vehicle motion and ordinary handling.
 * Context raises the bar for acceptance; it never fabricates a gesture.
 */
class ContextVerifier {

    private val stepTimes = mutableListOf<Long>()
    private val recentEnergy = mutableListOf<Pair<Long, Float>>()
    private var lastStepRecordedMs = Long.MIN_VALUE

    fun onSample(sample: SensorSample) {
        val now = sample.timestampMs
        // SensorHub exposes a short-lived step pulse alongside other sensor
        // callbacks; deduplicate that pulse to one count per physical step.
        if (sample.stepDetected && (lastStepRecordedMs == Long.MIN_VALUE || now - lastStepRecordedMs >= 300L)) {
            stepTimes.add(now)
            lastStepRecordedMs = now
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
        if (recentEnergy.size < 40) return false
        val values = recentEnergy.map { it.second }
        val avg = values.average()
        if (avg < 1.8) return false
        val variance = values.map { (it - avg) * (it - avg) }.average()
        // Steady sustained vibration is more consistent with vehicle or
        // continuous locomotion than a short, intentional gesture burst.
        return sqrt(variance) / avg.coerceAtLeast(0.1) < 0.42
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
        val rhythmicSensitiveGestures = setOf(
            GestureType.SINGLE_CHOP, GestureType.DOUBLE_CHOP,
            GestureType.SHAKE, GestureType.DOUBLE_SHAKE, GestureType.TRIPLE_SHAKE,
            GestureType.DOUBLE_TWIST, GestureType.TRIPLE_TWIST
        )
        if (spm > 60f && gesture in rhythmicSensitiveGestures) return false
        if (gesture in rhythmicSensitiveGestures && hasSustainedPeriodicEnergy()) return false
        return true
    }

    /** Locomotion increases the evidence bar rather than disabling every gesture. */
    fun confidenceMultiplier(gesture: GestureType, nowMs: Long): Float {
        val sensitive = gesture in setOf(
            GestureType.SINGLE_CHOP, GestureType.DOUBLE_CHOP, GestureType.SHAKE,
            GestureType.DOUBLE_SHAKE, GestureType.TRIPLE_SHAKE,
            GestureType.DOUBLE_TWIST, GestureType.TRIPLE_TWIST
        )
        if (!sensitive) return 1f
        val cadence = stepsPerMinute(nowMs)
        return when {
            cadence > 150f -> 0.68f
            cadence > 60f -> 0.86f
            else -> 1f
        }
    }

    fun reset() {
        stepTimes.clear()
        recentEnergy.clear()
    }
}
