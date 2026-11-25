package com.universalgestures.recognition

import com.universalgestures.core.GestureType
import com.universalgestures.core.RecognitionProfile
import com.universalgestures.core.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Personal calibration: stores positive/negative example feature vectors
 * per gesture and derives per-gesture thresholds. A universal baseline
 * is always preserved — personal thresholds are clamped so a bad
 * calibration session cannot destroy general recognition quality.
 */
data class CalibrationProfile(
    val gesture: GestureType,
    val positives: List<List<Vec3>> = emptyList(),
    val negatives: List<List<Vec3>> = emptyList(),
    val threshold: Float = 0.35f,
    val calibrated: Boolean = false
)

class CalibrationStore {

    private val profiles = mutableMapOf<GestureType, CalibrationProfile>()
    var activeProfile: RecognitionProfile = RecognitionProfile.DEFAULT

    fun profileFor(gesture: GestureType): CalibrationProfile =
        profiles[gesture] ?: CalibrationProfile(gesture)

    fun addPositive(gesture: GestureType, features: List<Vec3>) {
        val current = profileFor(gesture)
        profiles[gesture] = current.copy(
            positives = (current.positives + listOf(features)).takeLast(10)
        )
        recompute(gesture)
    }

    fun addNegative(gesture: GestureType, features: List<Vec3>) {
        val current = profileFor(gesture)
        profiles[gesture] = current.copy(
            negatives = (current.negatives + listOf(features)).takeLast(10)
        )
        recompute(gesture)
    }

    fun isCalibrated(gesture: GestureType): Boolean = profileFor(gesture).calibrated

    fun thresholdFor(gesture: GestureType): Float {
        val base = when (activeProfile) {
            RecognitionProfile.HIGH_PRECISION -> 0.28f
            RecognitionProfile.HIGH_SENSITIVITY -> 0.45f
            RecognitionProfile.PERSONALIZED, RecognitionProfile.CUSTOM ->
                profileFor(gesture).threshold
            RecognitionProfile.DEFAULT -> 0.35f
        }
        // Clamp to the universal baseline band.
        return base.coerceIn(0.22f, 0.55f)
    }

    fun matchScore(gesture: GestureType, features: List<Vec3>): Float {
        val profile = profileFor(gesture)
        if (profile.positives.isEmpty()) return 1f
        var best = Float.MAX_VALUE
        for (p in profile.positives) {
            best = minOf(best, DtwMatcher.distance(features, p))
        }
        return best
    }

    fun clear(gesture: GestureType) {
        profiles.remove(gesture)
    }

    fun clearAll() {
        profiles.clear()
    }

    private fun recompute(gesture: GestureType) {
        val profile = profileFor(gesture)
        if (profile.positives.isEmpty()) return
        // Threshold = mean positive distance + 1.5 * stddev, clamped.
        val distances = profile.positives.map { DtwMatcher.distance(it, profile.positives.first()) }
        val mean = distances.average().toFloat()
        val variance = distances.map { (it - mean) * (it - mean) }.average().toFloat()
        val std = sqrt(variance)
        val threshold = (mean + 1.5f * std + 0.1f).coerceIn(0.22f, 0.55f)
        profiles[gesture] = profile.copy(threshold = threshold, calibrated = profile.positives.size >= 2)
    }

    /** Sanity helper used by tests: mean absolute feature magnitude. */
    fun meanMagnitude(features: List<Vec3>): Float {
        if (features.isEmpty()) return 0f
        return features.map { abs(it.magnitude) }.average().toFloat()
    }
}
