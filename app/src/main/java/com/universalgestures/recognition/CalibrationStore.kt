package com.universalgestures.recognition

import com.universalgestures.core.GestureType
import com.universalgestures.core.RecognitionProfile
import com.universalgestures.core.Vec3
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.sqrt

/** Locally persistable positive/negative motion fingerprints. */
data class CalibrationProfileData(
    val gesture: GestureType,
    val positives: List<List<Vec3>> = emptyList(),
    val negatives: List<List<Vec3>> = emptyList(),
    val threshold: Float = 0.35f,
    val calibrated: Boolean = false
)

class CalibrationStore private constructor(private val profiles: MutableMap<GestureType, CalibrationProfileData>) {
    constructor() : this(mutableMapOf())

    var activeProfile: RecognitionProfile = RecognitionProfile.DEFAULT

    fun profileFor(gesture: GestureType): CalibrationProfileData =
        profiles[gesture] ?: CalibrationProfileData(gesture)

    fun allProfiles(): Map<GestureType, CalibrationProfileData> = profiles.toMap()

    fun addPositive(gesture: GestureType, features: List<Vec3>) {
        if (features.isEmpty()) return
        val current = profileFor(gesture)
        profiles[gesture] = current.copy(positives = (current.positives + listOf(features)).takeLast(10))
        recompute(gesture)
    }

    fun addNegative(gesture: GestureType, features: List<Vec3>) {
        if (features.isEmpty()) return
        val current = profileFor(gesture)
        profiles[gesture] = current.copy(negatives = (current.negatives + listOf(features)).takeLast(10))
        recompute(gesture)
    }

    fun isCalibrated(gesture: GestureType): Boolean = profileFor(gesture).calibrated

    fun thresholdFor(gesture: GestureType): Float {
        val personal = profileFor(gesture).threshold
        val raw = when (activeProfile) {
            RecognitionProfile.HIGH_PRECISION -> minOf(0.28f, personal)
            RecognitionProfile.HIGH_SENSITIVITY -> maxOf(0.45f, personal)
            RecognitionProfile.PERSONALIZED, RecognitionProfile.CUSTOM -> personal
            RecognitionProfile.DEFAULT -> 0.35f
        }
        return raw.coerceIn(0.22f, 0.55f)
    }

    /** Returns a 0..1 confidence penalty; a closer negative than positive is rejected. */
    fun adjustedConfidence(gesture: GestureType, features: List<Vec3>, confidence: Float): Float {
        val p = profileFor(gesture)
        if (p.positives.isEmpty() || features.isEmpty()) return confidence
        val pos = p.positives.minOf { DtwMatcher.distance(features, it) }
        val neg = p.negatives.minOfOrNull { DtwMatcher.distance(features, it) }
        if (pos > thresholdFor(gesture)) return 0f
        if (neg != null && neg <= pos) return 0f
        val margin = if (neg == null) 1f else ((neg - pos) / (neg + 0.05f)).coerceIn(0f, 1f)
        return (confidence * (0.65f + margin * 0.35f)).coerceIn(0f, 1f)
    }

    fun clear(gesture: GestureType) { profiles.remove(gesture) }
    fun clearAll() { profiles.clear() }

    fun toJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        val all = JSONArray()
        profiles.values.forEach { p ->
            val obj = JSONObject()
            obj.put("gesture", p.gesture.name)
            obj.put("threshold", p.threshold.toDouble())
            obj.put("calibrated", p.calibrated)
            obj.put("positives", examplesJson(p.positives))
            obj.put("negatives", examplesJson(p.negatives))
            all.put(obj)
        }
        root.put("profiles", all)
        return root.toString()
    }

    private fun recompute(gesture: GestureType) {
        val p = profileFor(gesture)
        if (p.positives.isEmpty()) return
        val distances = p.positives.map { DtwMatcher.distance(it, p.positives.first()) }
        val mean = distances.average().toFloat()
        val variance = distances.map { (it - mean) * (it - mean) }.average().toFloat()
        val threshold = (mean + 1.5f * sqrt(variance) + 0.1f).coerceIn(0.22f, 0.55f)
        profiles[gesture] = p.copy(threshold = threshold, calibrated = p.positives.size >= 2)
    }

    private fun examplesJson(examples: List<List<Vec3>>): JSONArray = JSONArray().also { outer ->
        examples.forEach { sequence ->
            val points = JSONArray()
            sequence.take(128).forEach { v -> points.put(JSONArray().put(v.x.toDouble()).put(v.y.toDouble()).put(v.z.toDouble())) }
            outer.put(points)
        }
    }

    fun meanMagnitude(features: List<Vec3>): Float =
        if (features.isEmpty()) 0f else features.map { abs(it.magnitude) }.average().toFloat()

    companion object {
        fun fromJson(json: String): CalibrationStore {
            val result = CalibrationStore()
            try {
                val array = JSONObject(json).optJSONArray("profiles") ?: return result
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val type = runCatching { GestureType.valueOf(obj.getString("gesture")) }.getOrNull() ?: continue
                    val positives = readExamples(obj.optJSONArray("positives"))
                    val negatives = readExamples(obj.optJSONArray("negatives"))
                    val fallback = CalibrationProfileData(type)
                    result.profiles[type] = fallback.copy(
                        positives = positives.takeLast(10),
                        negatives = negatives.takeLast(10),
                        threshold = obj.optDouble("threshold", 0.35).toFloat().coerceIn(0.22f, 0.55f),
                        calibrated = obj.optBoolean("calibrated", positives.size >= 2)
                    )
                }
            } catch (_: Exception) {
                // Corrupt calibration is safely ignored; general recognizers remain available.
            }
            return result
        }

        private fun readExamples(array: JSONArray?): List<List<Vec3>> {
            if (array == null) return emptyList()
            return buildList {
                for (i in 0 until minOf(array.length(), 10)) {
                    val points = array.optJSONArray(i) ?: continue
                    val sequence = buildList {
                        for (j in 0 until minOf(points.length(), 128)) {
                            val point = points.optJSONArray(j) ?: continue
                            if (point.length() >= 3) add(Vec3(point.optDouble(0).toFloat(), point.optDouble(1).toFloat(), point.optDouble(2).toFloat()))
                        }
                    }
                    if (sequence.isNotEmpty()) add(sequence)
                }
            }
        }
    }
}
