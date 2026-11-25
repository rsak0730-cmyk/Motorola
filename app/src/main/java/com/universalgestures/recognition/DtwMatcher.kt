package com.universalgestures.recognition

import com.universalgestures.core.Vec3
import kotlin.math.abs
import kotlin.math.min

/**
 * Dynamic Time Warping with a Sakoe-Chiba band, used for custom-gesture
 * templates and calibration comparison. Returns a normalised distance in
 * [0, 1] where 0 is an exact match.
 */
object DtwMatcher {

    fun distance(a: List<Vec3>, b: List<Vec3>, band: Int = 8): Float {
        if (a.isEmpty() || b.isEmpty()) return 1f
        val n = a.size
        val m = b.size
        val w = maxOf(band, abs(n - m))
        val cost = Array(n) { FloatArray(m) { Float.MAX_VALUE } }
        cost[0][0] = localCost(a[0], b[0])
        for (i in 1 until n) cost[i][0] = cost[i - 1][0] + localCost(a[i], b[0])
        for (j in 1 until m) cost[0][j] = cost[0][j - 1] + localCost(a[0], b[j])
        for (i in 1 until n) {
            val jStart = maxOf(1, i - w)
            val jEnd = min(m - 1, i + w)
            for (j in jStart..jEnd) {
                val best = minOf(cost[i - 1][j], cost[i][j - 1], cost[i - 1][j - 1])
                cost[i][j] = best + localCost(a[i], b[j])
            }
        }
        val raw = cost[n - 1][m - 1]
        if (raw == Float.MAX_VALUE) return 1f
        // Normalise by path length and clamp.
        return (raw / (n + m)).coerceIn(0f, 1f)
    }

    private fun localCost(a: Vec3, b: Vec3): Float {
        val d = (a - b).magnitude
        return d / (1f + d)
    }

    /** Extract a resampled feature sequence (linear accel + gyro) from raw samples. */
    fun featureSequence(
        samples: List<com.universalgestures.core.SensorSample>,
        targetLength: Int = 24
    ): List<Vec3> {
        if (samples.isEmpty()) return emptyList()
        if (samples.size <= targetLength) {
            return samples.map { it.linearAccel + it.gyro * 0.5f }
        }
        val step = samples.size.toFloat() / targetLength
        val out = ArrayList<Vec3>(targetLength)
        for (i in 0 until targetLength) {
            val idx = (i * step).toInt().coerceIn(0, samples.size - 1)
            val s = samples[idx]
            out.add(s.linearAccel + s.gyro * 0.5f)
        }
        return out
    }
}
