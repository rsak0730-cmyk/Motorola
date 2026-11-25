package com.universalgestures.sensors

import com.universalgestures.core.MotionSegment
import com.universalgestures.core.SensorSample
import kotlin.math.abs

/**
 * Motion segmentation (UMI layer D).
 *
 * Energy = |linearAccel| + 0.5 * |gyro|. A segment starts when energy
 * crosses [startThreshold], ends after energy stays below [endThreshold]
 * for [endQuietMs], and is flagged interrupted when a long gap or a
 * discontinuity occurs mid-segment. Gestures are never classified from
 * partial segments.
 */
class MotionSegmenter(
    private val startThreshold: Float = 1.2f,
    private val endThreshold: Float = 0.6f,
    private val endQuietMs: Long = 220L,
    private val maxSegmentMs: Long = 4000L
) {
    private val buffer = mutableListOf<SensorSample>()
    private var segmentStartMs: Long = -1L
    private var quietSinceMs: Long = -1L
    private var lastSampleMs: Long = -1L

    val isSegmenting: Boolean get() = segmentStartMs >= 0

    fun reset() {
        buffer.clear()
        segmentStartMs = -1L
        quietSinceMs = -1L
        lastSampleMs = -1L
    }

    fun energy(sample: SensorSample): Float =
        sample.linearAccel.magnitude + 0.5f * sample.gyro.magnitude

    /** Feed one sample; returns a completed segment when one closes. */
    fun onSample(sample: SensorSample): MotionSegment? {
        val now = sample.timestampMs
        val gap = if (lastSampleMs < 0) 0L else now - lastSampleMs
        lastSampleMs = now

        if (segmentStartMs < 0) {
            if (energy(sample) > startThreshold) {
                segmentStartMs = now
                buffer.clear()
                buffer.add(sample)
                quietSinceMs = -1L
            }
            return null
        }

        // Discontinuity mid-segment -> interrupted (incomplete) movement.
        if (gap > 600L) {
            val seg = buildSegment(interrupted = true)
            reset()
            return seg
        }

        buffer.add(sample)
        val e = energy(sample)
        if (e < endThreshold) {
            if (quietSinceMs < 0) quietSinceMs = now
            if (now - quietSinceMs >= endQuietMs) {
                val seg = buildSegment(interrupted = false)
                reset()
                return seg
            }
        } else {
            quietSinceMs = -1L
        }

        if (now - segmentStartMs > maxSegmentMs) {
            val seg = buildSegment(interrupted = true)
            reset()
            return seg
        }
        return null
    }

    /** Flush any open segment (e.g. on pause). Returns null when idle. */
    fun flush(): MotionSegment? {
        if (segmentStartMs < 0 || buffer.isEmpty()) {
            reset()
            return null
        }
        val seg = buildSegment(interrupted = true)
        reset()
        return seg
    }

    private fun buildSegment(interrupted: Boolean): MotionSegment {
        val samples = buffer.toList()
        var peakLin = 0f
        var peakGyro = 0f
        var axisEnergy = floatArrayOf(0f, 0f, 0f)
        var directionChanges = 0
        var prevSign = 0
        for (s in samples) {
            peakLin = maxOf(peakLin, s.linearAccel.magnitude)
            peakGyro = maxOf(peakGyro, s.gyro.magnitude)
            for (a in 0..2) axisEnergy[a] += abs(s.linearAccel.component(a))
            val dom = s.linearAccel.dominantAxis()
            val sign = when {
                s.linearAccel.component(dom) > 0.3f -> 1
                s.linearAccel.component(dom) < -0.3f -> -1
                else -> 0
            }
            if (sign != 0 && prevSign != 0 && sign != prevSign) directionChanges++
            if (sign != 0) prevSign = sign
        }
        val dominantAxis = axisEnergy.indices.maxByOrNull { axisEnergy[it] } ?: 0
        return MotionSegment(
            startMs = samples.firstOrNull()?.timestampMs ?: 0L,
            endMs = samples.lastOrNull()?.timestampMs ?: 0L,
            samples = samples,
            peakLinearAccel = peakLin,
            peakGyro = peakGyro,
            dominantAxis = dominantAxis,
            directionChanges = directionChanges,
            interrupted = interrupted
        )
    }
}
