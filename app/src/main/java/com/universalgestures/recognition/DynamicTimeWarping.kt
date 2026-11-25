package com.universalgestures.recognition

import kotlin.math.sqrt

/** Multivariate constrained DTW with rolling rows (O(n·window) time, O(m) memory). */
object DynamicTimeWarping {
    fun distance(a: List<MotionFeature>, b: List<MotionFeature>, window: Int = 8): Float {
        if (a.size < 4 || b.size < 4) return 1f
        val dimensions = a.first().dimensions.size
        if (dimensions == 0 || a.any { it.dimensions.size != dimensions } || b.any { it.dimensions.size != dimensions }) return 1f
        val n = a.size; val m = b.size
        val band = maxOf(window, kotlin.math.abs(n - m))
        var previous = FloatArray(m + 1) { Float.POSITIVE_INFINITY }
        previous[0] = 0f
        var pathSteps = 0
        for (i in 1..n) {
            val current = FloatArray(m + 1) { Float.POSITIVE_INFINITY }
            val from = maxOf(1, i - band)
            val to = minOf(m, i + band)
            for (j in from..to) {
                val cost = localCost(a[i - 1], b[j - 1], dimensions)
                current[j] = cost + minOf(previous[j], current[j - 1], previous[j - 1])
            }
            previous = current
        }
        val total = previous[m]
        if (!total.isFinite()) return 1f
        pathSteps = n + m
        return (total / pathSteps).coerceIn(0f, 1f)
    }

    private fun localCost(a: MotionFeature, b: MotionFeature, dimensions: Int): Float {
        var sum = 0.0
        for (i in 0 until dimensions) {
            val delta = (a.dimensions[i] - b.dimensions[i]).toDouble()
            sum += delta * delta
        }
        // Bounded root-mean-square cost, stable for poorly scaled sensor ranges.
        return (sqrt(sum / dimensions) / (1.0 + sqrt(sum / dimensions))).toFloat()
    }
}
