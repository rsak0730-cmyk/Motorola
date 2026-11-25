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
    val motionPositives: List<List<MotionFeature>> = emptyList(),
    val motionNegatives: List<List<MotionFeature>> = emptyList(),
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

    fun addMotionPositive(gesture: GestureType, features: List<MotionFeature>) {
        if (features.isEmpty()) return
        val p = profileFor(gesture)
        profiles[gesture] = p.copy(motionPositives = (p.motionPositives + listOf(features.take(128))).takeLast(10))
        recompute(gesture)
    }

    fun addMotionNegative(gesture: GestureType, features: List<MotionFeature>) {
        if (features.isEmpty()) return
        val p = profileFor(gesture)
        profiles[gesture] = p.copy(motionNegatives = (p.motionNegatives + listOf(features.take(128))).takeLast(10))
    }

    fun adjustedMotionConfidence(gesture: GestureType, features: List<MotionFeature>, confidence: Float): Float {
        val p = profileFor(gesture)
        if (p.motionPositives.isEmpty() || features.isEmpty()) return confidence
        val positiveDistance = p.motionPositives.minOf { DynamicTimeWarping.distance(features, it) }
        val negativeDistance = p.motionNegatives.minOfOrNull { DynamicTimeWarping.distance(features, it) }
        val limit = thresholdFor(gesture).coerceIn(0.12f, 0.5f)
        if (positiveDistance > limit || (negativeDistance != null && negativeDistance <= positiveDistance * 1.05f)) return 0f
        val margin = if (negativeDistance == null) 1f else
            ((negativeDistance - positiveDistance) / (negativeDistance + 0.05f)).coerceIn(0f, 1f)
        return (confidence * (0.7f + 0.3f * margin)).coerceIn(0f, 1f)
    }

    fun isCalibrated(gesture: GestureType): Boolean = profileFor(gesture).calibrated || profileFor(gesture).motionPositives.size >= 2

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

    /** Best positive-template distance; 1 means there is no usable profile. */
    fun matchScore(gesture: GestureType, features: List<Vec3>): Float {
        val templates = profileFor(gesture).positives
        if (templates.isEmpty() || features.isEmpty()) return 1f
        return templates.minOf { DtwMatcher.distance(features, it) }
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
            obj.put("motionPositives", motionExamplesJson(p.motionPositives))
            obj.put("motionNegatives", motionExamplesJson(p.motionNegatives))
            all.put(obj)
        }
        root.put("profiles", all)
        return root.toString()
    }

    private fun recompute(gesture: GestureType) {
        val p = profileFor(gesture)
        val legacyDistances = p.positives.takeIf { it.isNotEmpty() }?.map { DtwMatcher.distance(it, p.positives.first()) }.orEmpty()
        val legacyMean = legacyDistances.average().takeIf { legacyDistances.isNotEmpty() }?.toFloat() ?: 0f
        val legacyVariance = legacyDistances.map { (it - legacyMean) * (it - legacyMean) }.average()
            .takeIf { legacyDistances.isNotEmpty() }?.toFloat() ?: 0f
        val motionDistances = p.motionPositives.takeIf { it.isNotEmpty() }?.map {
            DynamicTimeWarping.distance(it, p.motionPositives.first())
        }.orEmpty()
        val motionMean = motionDistances.average().takeIf { motionDistances.isNotEmpty() }?.toFloat() ?: 0f
        val motionVariance = motionDistances.map { (it - motionMean) * (it - motionMean) }.average()
            .takeIf { motionDistances.isNotEmpty() }?.toFloat() ?: 0f
        val legacyThreshold = if (legacyDistances.isEmpty()) 0f else legacyMean + 1.5f * sqrt(legacyVariance) + 0.1f
        val motionThreshold = if (motionDistances.isEmpty()) 0f else motionMean + 1.5f * sqrt(motionVariance) + 0.12f
        val threshold = maxOf(legacyThreshold, motionThreshold).takeIf { it > 0f }?.coerceIn(0.12f, 0.55f) ?: p.threshold
        profiles[gesture] = p.copy(
            threshold = threshold,
            calibrated = p.positives.size >= 2 || p.motionPositives.size >= 2
        )
    }

    private fun examplesJson(examples: List<List<Vec3>>): JSONArray = JSONArray().also { outer ->
        examples.forEach { sequence ->
            val points = JSONArray()
            sequence.take(128).forEach { v -> points.put(JSONArray().put(v.x.toDouble()).put(v.y.toDouble()).put(v.z.toDouble())) }
            outer.put(points)
        }
    }

    private fun motionExamplesJson(examples: List<List<MotionFeature>>): JSONArray = JSONArray().also { outer ->
        examples.forEach { sequence ->
            val points = JSONArray()
            sequence.take(128).forEach { feature ->
                points.put(JSONArray().also { row -> feature.dimensions.forEach { row.put(it.toDouble()) } })
            }
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
                    val motionPositives = readMotionExamples(obj.optJSONArray("motionPositives"))
                    val motionNegatives = readMotionExamples(obj.optJSONArray("motionNegatives"))
                    val fallback = CalibrationProfileData(type)
                    result.profiles[type] = fallback.copy(
                        positives = positives.takeLast(10),
                        negatives = negatives.takeLast(10),
                        motionPositives = motionPositives.takeLast(10),
                        motionNegatives = motionNegatives.takeLast(10),
                        threshold = obj.optDouble("threshold", 0.35).toFloat().coerceIn(0.22f, 0.55f),
                        calibrated = obj.optBoolean("calibrated", positives.size >= 2)
                    )
                }
            } catch (_: Exception) {
                // Corrupt calibration is safely ignored; general recognizers remain available.
            }
            return result
        }

        private fun readMotionExamples(array: JSONArray?): List<List<MotionFeature>> {
            if (array == null) return emptyList()
            return buildList {
                for (i in 0 until minOf(array.length(), 10)) {
                    val points = array.optJSONArray(i) ?: continue
                    val sequence = buildList {
                        for (j in 0 until minOf(points.length(), 128)) {
                            val row = points.optJSONArray(j) ?: continue
                            if (row.length() == 11) {
                                val dimensions = (0 until row.length()).map { row.optDouble(it).toFloat() }
                                if (dimensions.all(Float::isFinite)) add(MotionFeature(dimensions))
                            }
                        }
                    }
                    if (sequence.isNotEmpty()) add(sequence)
                }
            }
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
