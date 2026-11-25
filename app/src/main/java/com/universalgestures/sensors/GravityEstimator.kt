package com.universalgestures.sensors

/** Time-constant low-pass gravity estimator. Input/output units are m/s². */
class GravityEstimator(private val timeConstant: Double = 0.8) {
    private var gx = 0.0
    private var gy = 0.0
    private var gz = 0.0
    private var initialized = false

    fun update(ax: Float, ay: Float, az: Float, dt: Double): DoubleArray {
        if (!ax.isFinite() || !ay.isFinite() || !az.isFinite()) return doubleArrayOf(gx, gy, gz)
        if (!initialized) {
            gx = ax.toDouble(); gy = ay.toDouble(); gz = az.toDouble()
            initialized = true
        }
        val safeDt = dt.coerceIn(0.001, 0.1)
        val alpha = safeDt / (timeConstant + safeDt)
        gx += alpha * (ax - gx)
        gy += alpha * (ay - gy)
        gz += alpha * (az - gz)
        return doubleArrayOf(gx, gy, gz)
    }

    fun reset() {
        initialized = false
        gx = 0.0; gy = 0.0; gz = 0.0
    }
}
