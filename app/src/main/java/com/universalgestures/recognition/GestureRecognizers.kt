package com.universalgestures.recognition

import com.universalgestures.core.GestureType
import com.universalgestures.core.MotionSegment
import com.universalgestures.core.SensorSample
import com.universalgestures.core.Vec3
import com.universalgestures.sensors.MotionSegmenter
import kotlin.math.abs

/**
 * Multi-stage state-machine recognizers (UMI layer E).
 *
 * Each recognizer consumes fused [SensorSample]s and emits a candidate
 * [Candidate] with a confidence, or null. Recognition is deliberately
 * conservative: ambiguous or incomplete evidence yields no candidate and
 * the Precision Gate (PGI) rejects it downstream.
 */
data class Candidate(
    val type: GestureType,
    val confidence: Float,
    val detail: String = "",
    val detectedAtMs: Long = 0L,
    val sequenceId: Long = 0L
)

interface GestureRecognizer {
    fun onSample(sample: SensorSample): Candidate?
    fun reset()
}

/** Shared peak-counting machinery for chop / shake style gestures. */
abstract class PeakCounterRecognizer(
    private val gesture: GestureType,
    private val requiredPeaks: Int,
    private val peakThreshold: Float,
    private val windowMs: Long,
    private val minPeakGapMs: Long = 180L,
    private val useGyro: Boolean = false
) : GestureRecognizer {

    private val peakTimes = mutableListOf<Long>()
    private var lastPeakMs = -1L
    private var aboveThreshold = false

    override fun onSample(sample: SensorSample): Candidate? {
        val now = sample.timestampMs
        val magnitude = if (useGyro) sample.gyro.magnitude else sample.linearAccel.magnitude
        if (magnitude > peakThreshold) {
            if (!aboveThreshold && now - lastPeakMs >= minPeakGapMs) {
                peakTimes.add(now)
                lastPeakMs = now
                // Drop peaks outside the window.
                while (peakTimes.isNotEmpty() && now - peakTimes.first() > windowMs) {
                    peakTimes.removeAt(0)
                }
                if (peakTimes.size >= requiredPeaks) {
                    val span = now - peakTimes.first()
                    val confidence = (1f - (span.toFloat() / windowMs) * 0.4f).coerceIn(0.55f, 0.98f)
                    peakTimes.clear()
                    return Candidate(gesture, confidence, "peaks=$requiredPeaks span=${span}ms")
                }
            }
            aboveThreshold = true
        } else if (magnitude < peakThreshold * 0.5f) {
            aboveThreshold = false
        }
        // Expire stale peaks even without new peaks.
        while (peakTimes.isNotEmpty() && now - peakTimes.first() > windowMs) {
            peakTimes.removeAt(0)
        }
        return null
    }

    override fun reset() {
        peakTimes.clear()
        lastPeakMs = -1L
        aboveThreshold = false
    }
}

class ChopRecognizer(
    gesture: GestureType,
    peaks: Int,
    sensitivity: Int,
    windowMs: Long = 1200L
) : PeakCounterRecognizer(
    gesture = gesture,
    requiredPeaks = peaks,
    peakThreshold = thresholdFor(sensitivity, base = 14f),
    windowMs = windowMs
) {
    companion object {
        fun thresholdFor(sensitivity: Int, base: Float): Float {
            // sensitivity 0..100 -> threshold base*1.4 .. base*0.6
            val s = sensitivity.coerceIn(0, 100) / 100f
            return base * (1.4f - 0.8f * s)
        }
    }
}

class ShakeRecognizer(
    gesture: GestureType,
    oscillations: Int,
    sensitivity: Int,
    windowMs: Long = 1500L
) : PeakCounterRecognizer(
    gesture = gesture,
    requiredPeaks = oscillations,
    peakThreshold = ChopRecognizer.thresholdFor(sensitivity, base = 9f),
    windowMs = windowMs,
    minPeakGapMs = 120L
)

/**
 * Twist recognizer: counts sign flips of the dominant gyro axis
 * (wrist rotation). Requires alternating direction, which ordinary
 * handling rarely produces at high angular velocity.
 */
class TwistRecognizer(
    private val gesture: GestureType,
    private val requiredTwists: Int,
    sensitivity: Int,
    private val windowMs: Long = 1600L
) : GestureRecognizer {

    private val threshold = ChopRecognizer.thresholdFor(sensitivity, base = 6.5f)
    private val twistTimes = mutableListOf<Long>()
    private var lastDirection = 0
    private var lastTwistMs = -1L
    private var aboveThreshold = false

    override fun onSample(sample: SensorSample): Candidate? {
        val now = sample.timestampMs
        val gyro = sample.gyro
        val axis = gyro.dominantAxis()
        val value = gyro.component(axis)
        val magnitude = gyro.magnitude
        if (magnitude > threshold) {
            if (!aboveThreshold) {
                val direction = if (value > 0) 1 else -1
                if (direction != lastDirection && now - lastTwistMs >= 200L) {
                    twistTimes.add(now)
                    lastTwistMs = now
                    lastDirection = direction
                    while (twistTimes.isNotEmpty() && now - twistTimes.first() > windowMs) {
                        twistTimes.removeAt(0)
                    }
                    if (twistTimes.size >= requiredTwists) {
                        val span = now - twistTimes.first()
                        val confidence = (1f - (span.toFloat() / windowMs) * 0.35f).coerceIn(0.55f, 0.97f)
                        twistTimes.clear()
                        return Candidate(gesture, confidence, "twists=$requiredTwists span=${span}ms")
                    }
                }
            }
            aboveThreshold = true
        } else if (magnitude < threshold * 0.4f) {
            aboveThreshold = false
        }
        while (twistTimes.isNotEmpty() && now - twistTimes.first() > windowMs) {
            twistTimes.removeAt(0)
        }
        return null
    }

    override fun reset() {
        twistTimes.clear()
        lastDirection = 0
        lastTwistMs = -1L
        aboveThreshold = false
    }
}

/** Face-down flip: gravity z transitions to < -0.85g and holds. */
class FlipRecognizer : GestureRecognizer {
    private var faceDownSinceMs = -1L
    private var wasUpright = false

    override fun onSample(sample: SensorSample): Candidate? {
        val now = sample.timestampMs
        val g = sample.gravity.normalized()
        if (g.z > -0.3f) wasUpright = true
        if (wasUpright && g.z < -0.85f) {
            if (faceDownSinceMs < 0) faceDownSinceMs = now
            if (now - faceDownSinceMs >= 350L) {
                faceDownSinceMs = -1L
                wasUpright = false
                return Candidate(GestureType.FLIP, 0.92f, "faceDownHold=350ms")
            }
        } else {
            faceDownSinceMs = -1L
        }
        return null
    }

    override fun reset() {
        faceDownSinceMs = -1L
        wasUpright = false
    }
}

/** Pickup: hardware pickup event, or accel rise + tilt-toward-user heuristic. */
class PickupRecognizer : GestureRecognizer {
    private var baselineMs = -1L
    private var baselineGravity = Vec3()
    private var riseStartMs = -1L

    override fun onSample(sample: SensorSample): Candidate? {
        if (sample.pickupDetected) {
            return Candidate(GestureType.PICKUP, 0.95f, "hardwarePickupSensor")
        }
        val now = sample.timestampMs
        if (baselineMs < 0) {
            baselineMs = now
            baselineGravity = sample.gravity
            return null
        }
        // Heuristic: device was flat (gravity mostly -z) and now tilts up
        // with upward linear acceleration.
        val wasFlat = abs(baselineGravity.normalized().z) > 0.8f
        val current = sample.gravity.normalized()
        val nowTilted = abs(current.z) < 0.72f || abs(current.y) > 0.68f
        val rising = sample.linearAccel.z > 1.5f || sample.linearAccel.magnitude > 2.5f
        if (wasFlat && nowTilted && rising) {
            if (riseStartMs < 0) riseStartMs = now
            if (now - riseStartMs >= 120L) {
                riseStartMs = -1L
                baselineMs = now
                baselineGravity = sample.gravity
                return Candidate(GestureType.PICKUP, 0.72f, "heuristicPickup")
            }
        } else {
            riseStartMs = -1L
            // Slowly track baseline while idle.
            if (sample.linearAccel.magnitude < 0.5f) {
                baselineGravity = baselineGravity * 0.95f + sample.gravity * 0.05f
            }
        }
        return null
    }

    override fun reset() {
        baselineMs = -1L
        riseStartMs = -1L
    }
}

/** Sustained tilt in one of four directions. */
class TiltRecognizer(
    private val gesture: GestureType,
    sensitivity: Int
) : GestureRecognizer {

    private val threshold = 0.45f + (50 - sensitivity.coerceIn(0, 100)) * 0.002f
    private var holdStartMs = -1L

    override fun onSample(sample: SensorSample): Candidate? {
        val now = sample.timestampMs
        val g = sample.gravity.normalized()
        val matched = when (gesture) {
            GestureType.TILT_LEFT -> g.x < -threshold
            GestureType.TILT_RIGHT -> g.x > threshold
            GestureType.TILT_FORWARD -> g.y > threshold
            GestureType.TILT_BACKWARD -> g.y < -threshold
            else -> false
        }
        if (matched) {
            if (holdStartMs < 0) holdStartMs = now
            if (now - holdStartMs >= 450L) {
                holdStartMs = -1L
                return Candidate(gesture, 0.85f, "tiltHold=450ms")
            }
        } else {
            holdStartMs = -1L
        }
        return null
    }

    override fun reset() {
        holdStartMs = -1L
    }
}

/** Proximity wave: near/far transitions (hand passing over the sensor). */
class ProximityWaveRecognizer(
    private val gesture: GestureType,
    private val requiredWaves: Int,
    private val windowMs: Long = 2500L
) : GestureRecognizer {

    private var lastNear: Boolean? = null
    private var waveTimes = mutableListOf<Long>()
    private var lastTransitionMs = -1L

    override fun onSample(sample: SensorSample): Candidate? {
        val proximity = sample.proximityCm ?: return null
        // Binary near/far using a 3 cm decision boundary (typical max range
        // is 5 cm; values below the boundary mean "covered").
        val near = proximity < 3f
        val prev = lastNear
        if (prev != null && prev != near) {
            val now = sample.timestampMs
            if (now - lastTransitionMs >= 150L) {
                lastTransitionMs = now
                waveTimes.add(now)
                while (waveTimes.isNotEmpty() && now - waveTimes.first() > windowMs) {
                    waveTimes.removeAt(0)
                }
                if (waveTimes.size >= requiredWaves * 2) {
                    waveTimes.clear()
                    return Candidate(gesture, 0.8f, "proximityTransitions=${requiredWaves * 2}")
                }
            }
        }
        lastNear = near
        return null
    }

    override fun reset() {
        lastNear = null
        waveTimes.clear()
        lastTransitionMs = -1L
    }
}

/** Custom motion gesture: DTW against user-recorded templates. */
class CustomMotionRecognizer(
    private val templates: List<List<Vec3>>,
    private val maxDistance: Float = 0.35f
) : GestureRecognizer {

    private val segmenter = MotionSegmenter()
    private val buffer = mutableListOf<SensorSample>()

    override fun onSample(sample: SensorSample): Candidate? {
        buffer.add(sample)
        val segment = segmenter.onSample(sample)
        if (segment == null) return null
        if (!segment.complete || templates.isEmpty()) return null
        val features = DtwMatcher.featureSequence(segment.samples)
        var best = Float.MAX_VALUE
        for (t in templates) {
            best = minOf(best, DtwMatcher.distance(features, t))
        }
        return if (best <= maxDistance) {
            Candidate(GestureType.CUSTOM_MOTION, (1f - best).coerceIn(0.5f, 0.97f), "dtw=$best")
        } else {
            null
        }
    }

    override fun reset() {
        segmenter.reset()
        buffer.clear()
    }
}

/** Segment-level validation helpers shared by the pipeline. */
object SegmentValidators {
    fun isChopLike(segment: MotionSegment): Boolean =
        segment.complete && segment.peakLinearAccel > 8f && segment.durationMs in 120L..1500L

    fun isShakeLike(segment: MotionSegment): Boolean =
        segment.complete && segment.directionChanges >= 2 && segment.peakLinearAccel > 5f

    fun isTwistLike(segment: MotionSegment): Boolean =
        segment.complete && segment.peakGyro > 4f && segment.durationMs in 200L..2500L

    fun dominantSignFlips(segment: MotionSegment, axis: Int): Int {
        var flips = 0
        var prev = 0
        for (s in segment.samples) {
            val v = s.gyro.component(axis)
            val sign = when {
                v > 1f -> 1
                v < -1f -> -1
                else -> 0
            }
            if (sign != 0 && prev != 0 && sign != prev) flips++
            if (sign != 0) prev = sign
        }
        return flips
    }
}
