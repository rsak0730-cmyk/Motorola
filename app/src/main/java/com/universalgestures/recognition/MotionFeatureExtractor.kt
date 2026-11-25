package com.universalgestures.recognition

import com.universalgestures.core.MotionSegment
import com.universalgestures.core.SensorSample
import kotlin.math.abs
import kotlin.math.sqrt

/** One time-aligned multivariate IMU feature frame; fixed scales preserve intensity. */
data class MotionFeature(val dimensions: List<Float>) {
    init { require(dimensions.isNotEmpty() && dimensions.all(Float::isFinite)) }
}

data class MotionFeatureSummary(
    val durationMs: Long,
    val activeDurationMs: Long,
    val linearAccelPeak: Float,
    val angularVelocityPeak: Float,
    val directionChanges: Int,
    val rotationAmplitudeRad: Float,
    val movementSymmetry: Float,
    val sequenceConsistency: Float,
    val startOrientation: List<Float>,
    val endOrientation: List<Float>
)

object MotionFeatureExtractor {
    /** 11 dimensions: scaled linear acceleration xyz, angular velocity xyz,
     * acceleration/rotation magnitudes, and relative roll/pitch/yaw. */
    fun extract(samples: List<SensorSample>): List<MotionFeature> {
        if (samples.size < 4 || !monotonic(samples)) return emptyList()
        val origin = orientationOf(samples.first())
        return resample(samples, 32).map { s ->
            val o = orientationOf(s)
            MotionFeature(listOf(
                (s.linearAccel.x / 20f).coerceIn(-2f, 2f),
                (s.linearAccel.y / 20f).coerceIn(-2f, 2f),
                (s.linearAccel.z / 20f).coerceIn(-2f, 2f),
                (s.gyro.x / 8f).coerceIn(-2f, 2f),
                (s.gyro.y / 8f).coerceIn(-2f, 2f),
                (s.gyro.z / 8f).coerceIn(-2f, 2f),
                (s.linearAccel.magnitude / 24f).coerceIn(0f, 2f),
                (s.gyro.magnitude / 12f).coerceIn(0f, 2f),
                (o.x - origin.x).coerceIn(-3.2f, 3.2f),
                (o.y - origin.y).coerceIn(-3.2f, 3.2f),
                (o.z - origin.z).coerceIn(-3.2f, 3.2f)
            ))
        }
    }

    fun summarize(segment: MotionSegment): MotionFeatureSummary {
        val samples = segment.samples
        if (samples.isEmpty()) return MotionFeatureSummary(0, 0, 0f, 0f, 0, 0f, 0f, 0f, emptyList(), emptyList())
        val active = samples.filter { it.linearAccel.magnitude > 1.2f || it.gyro.magnitude > 0.5f }
        val firstOrientation = orientationOf(samples.first())
        val lastOrientation = orientationOf(samples.last())
        var changes = 0
        var lastSign = 0
        for (s in samples) {
            val axis = s.linearAccel.dominantAxis()
            val v = s.linearAccel.component(axis)
            val sign = when { v > 0.8f -> 1; v < -0.8f -> -1; else -> 0 }
            if (sign != 0 && lastSign != 0 && sign != lastSign) changes++
            if (sign != 0) lastSign = sign
        }
        val half = active.size / 2
        val firstEnergy = active.take(half).sumOf { it.linearAccel.magnitude.toDouble() }.toFloat()
        val secondEnergy = active.drop(half).sumOf { it.linearAccel.magnitude.toDouble() }.toFloat()
        val symmetry = if (maxOf(firstEnergy, secondEnergy) < 0.01f) 0f else
            (minOf(firstEnergy, secondEnergy) / maxOf(firstEnergy, secondEnergy)).coerceIn(0f, 1f)
        val average = active.map { it.linearAccel.magnitude }.average().toFloat()
        val variance = active.map { (it.linearAccel.magnitude - average) * (it.linearAccel.magnitude - average) }.average().toFloat()
        val consistency = if (average < 0.01f) 0f else (1f - sqrt(variance) / average).coerceIn(0f, 1f)
        val rotation = active.sumOf { it.gyro.magnitude.toDouble() * 0.02 }.toFloat()
        return MotionFeatureSummary(
            segment.durationMs,
            if (active.size < 2) 0 else active.last().timestampMs - active.first().timestampMs,
            samples.maxOf { it.linearAccel.magnitude },
            samples.maxOf { it.gyro.magnitude },
            changes,
            rotation,
            symmetry,
            consistency,
            listOf(firstOrientation.x, firstOrientation.y, firstOrientation.z),
            listOf(lastOrientation.x, lastOrientation.y, lastOrientation.z)
        )
    }

    private fun orientationOf(sample: SensorSample): com.universalgestures.core.Vec3 {
        sample.orientation?.let { return it }
        val g = sample.gravity.normalized()
        val pitch = kotlin.math.atan2(g.y, g.z)
        val roll = kotlin.math.atan2(-g.x, kotlin.math.sqrt(g.y * g.y + g.z * g.z))
        return com.universalgestures.core.Vec3(roll, pitch, 0f)
    }

    private fun monotonic(samples: List<SensorSample>) = samples.zipWithNext().all { (a, b) -> b.timestampNanos > a.timestampNanos }

    private fun resample(samples: List<SensorSample>, count: Int): List<SensorSample> {
        if (samples.size <= count) return samples
        val result = ArrayList<SensorSample>(count)
        for (i in 0 until count) {
            val position = i.toDouble() * (samples.size - 1) / (count - 1)
            val left = position.toInt()
            val right = minOf(left + 1, samples.lastIndex)
            val ratio = (position - left).toFloat()
            val a = samples[left]; val b = samples[right]
            fun mix(x: com.universalgestures.core.Vec3, y: com.universalgestures.core.Vec3) = x * (1f - ratio) + y * ratio
            result.add(a.copy(
                timestampNanos = (a.timestampNanos + ((b.timestampNanos - a.timestampNanos) * ratio).toLong()),
                linearAccel = mix(a.linearAccel, b.linearAccel),
                gyro = mix(a.gyro, b.gyro),
                gravity = mix(a.gravity, b.gravity),
                orientation = if (a.orientation != null && b.orientation != null) mix(a.orientation, b.orientation) else null
            ))
        }
        return result
    }
}
