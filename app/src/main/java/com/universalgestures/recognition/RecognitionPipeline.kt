package com.universalgestures.recognition

import com.universalgestures.core.GestureConfig
import com.universalgestures.core.GestureType
import com.universalgestures.core.RecognitionProfile
import com.universalgestures.core.RecognitionResult
import com.universalgestures.core.SensorSample
import com.universalgestures.core.Vec3
import com.universalgestures.sensors.MotionSegmenter

/** Complete-sequence UMI pipeline: classify -> context -> precision gate -> action eligibility. */
class RecognitionPipeline(
    private val configs: Map<GestureType, GestureConfig>,
    private val calibration: CalibrationStore = CalibrationStore(),
    private val customTemplates: Map<GestureType, List<List<Vec3>>> = emptyMap(),
    private val profile: RecognitionProfile = RecognitionProfile.DEFAULT,
    private val globalSensitivity: Int = 50,
    private val accuracyMode: String = "BALANCED"
) {
    private val context = ContextVerifier()
    private val gate = PrecisionGate(minConfidence = confidenceFloor(accuracyMode, profile))
    private val segmenter = MotionSegmenter()
    private val featureWindow = ArrayDeque<SensorSample>()
    private val pending = mutableMapOf<GestureType, Pair<Candidate, Long>>()
    private val recognizers: List<GestureRecognizer> = buildRecognizers()

    init { calibration.activeProfile = profile }

    private fun effectiveSensitivity(config: GestureConfig): Int {
        val base = ((config.sensitivity.coerceIn(0, 100) * 0.65f) + (globalSensitivity.coerceIn(0, 100) * 0.35f)).toInt()
        return when (profile) {
            RecognitionProfile.HIGH_PRECISION -> (base - 15).coerceIn(0, 100)
            RecognitionProfile.HIGH_SENSITIVITY -> (base + 15).coerceIn(0, 100)
            else -> base
        }
    }

    private fun timing(type: GestureType, fallback: Long): Long =
        configs[type]?.timingMs?.toLong()?.coerceIn(250L, 5000L) ?: fallback

    private fun buildRecognizers(): List<GestureRecognizer> = buildList<GestureRecognizer> {
        fun addIfEnabled(type: GestureType, factory: (GestureConfig) -> GestureRecognizer?) {
            val config = configs[type] ?: return
            if (config.enabled) factory(config.copy(sensitivity = effectiveSensitivity(config)))?.let { add(it) }
        }
        addIfEnabled(GestureType.DOUBLE_CHOP) { ChopRecognizer(GestureType.DOUBLE_CHOP, 2, it.sensitivity, timing(it.type, 1200L)) }
        addIfEnabled(GestureType.SINGLE_CHOP) { ChopRecognizer(GestureType.SINGLE_CHOP, 1, it.sensitivity, timing(it.type, 900L)) }
        addIfEnabled(GestureType.DOUBLE_TWIST) { TwistRecognizer(GestureType.DOUBLE_TWIST, 2, it.sensitivity, timing(it.type, 1600L)) }
        addIfEnabled(GestureType.TRIPLE_TWIST) { TwistRecognizer(GestureType.TRIPLE_TWIST, 3, it.sensitivity, timing(it.type, 2000L)) }
        addIfEnabled(GestureType.SHAKE) { ShakeRecognizer(GestureType.SHAKE, 2, it.sensitivity, timing(it.type, 1500L)) }
        addIfEnabled(GestureType.DOUBLE_SHAKE) { ShakeRecognizer(GestureType.DOUBLE_SHAKE, 4, it.sensitivity, timing(it.type, 1800L)) }
        addIfEnabled(GestureType.TRIPLE_SHAKE) { ShakeRecognizer(GestureType.TRIPLE_SHAKE, 6, it.sensitivity, timing(it.type, 2200L)) }
        addIfEnabled(GestureType.FLIP) { FlipRecognizer() }
        addIfEnabled(GestureType.PICKUP) { PickupRecognizer() }
        addIfEnabled(GestureType.TILT_LEFT) { TiltRecognizer(GestureType.TILT_LEFT, it.sensitivity) }
        addIfEnabled(GestureType.TILT_RIGHT) { TiltRecognizer(GestureType.TILT_RIGHT, it.sensitivity) }
        addIfEnabled(GestureType.TILT_FORWARD) { TiltRecognizer(GestureType.TILT_FORWARD, it.sensitivity) }
        addIfEnabled(GestureType.TILT_BACKWARD) { TiltRecognizer(GestureType.TILT_BACKWARD, it.sensitivity) }
        addIfEnabled(GestureType.PROXIMITY_WAVE) { ProximityWaveRecognizer(GestureType.PROXIMITY_WAVE, 1, timing(it.type, 2500L)) }
        addIfEnabled(GestureType.DOUBLE_PROXIMITY_WAVE) { ProximityWaveRecognizer(GestureType.DOUBLE_PROXIMITY_WAVE, 2, timing(it.type, 3000L)) }
        addIfEnabled(GestureType.CUSTOM_MOTION) {
            val examples = calibration.profileFor(GestureType.CUSTOM_MOTION).positives
            val templates = (customTemplates[GestureType.CUSTOM_MOTION].orEmpty() + examples).take(10)
            if (templates.isNotEmpty()) CustomMotionRecognizer(templates, calibration.thresholdFor(GestureType.CUSTOM_MOTION))
            else null
        }
    }

    fun onSample(sample: SensorSample): RecognitionResult {
        context.onSample(sample)
        featureWindow.addLast(sample)
        while (featureWindow.size > 160) featureWindow.removeFirst()

        val completedSegment = segmenter.onSample(sample)
        val candidates = recognizers.mapNotNull { it.onSample(sample) }
        val now = sample.timestampMs
        // Retain only current-sequence motion candidates; classify multiple candidates together.
        candidates.forEach { candidate ->
            val previous = pending[candidate.type]?.first
            if (previous == null || candidate.confidence > previous.confidence) pending[candidate.type] = candidate to now
        }
        pending.entries.removeAll { now - it.value.second > 4000L }

        val proximityCandidates = pending.values.map { it.first }.filter { it.type.isProximityGesture() }
        val motionCandidates = pending.values.map { it.first }.filterNot { it.type.isProximityGesture() }
        val ready = when {
            completedSegment != null && completedSegment.complete -> motionCandidates + proximityCandidates
            completedSegment != null -> emptyList()
            !segmenter.isSegmenting -> proximityCandidates
            else -> emptyList()
        }
        if (ready.isEmpty()) {
            if (completedSegment != null) pending.clear()
            return RecognitionResult()
        }

        val features = DtwMatcher.featureSequence(featureWindow.toList(), targetLength = 32)
        val adjusted = ready.mapNotNull { candidate ->
            val value = calibration.adjustedConfidence(candidate.type, features, candidate.confidence)
            if (value <= 0f) null else candidate.copy(confidence = value)
        }
        pending.clear()
        val eligible = adjusted.filter { configs[it.type]?.action != null && configs[it.type]?.action?.name != "NONE" }
        if (eligible.isEmpty()) return RecognitionResult(
            rejectionReason = com.universalgestures.core.RejectionReason.ACTION_INELIGIBLE,
            detail = "recognized candidate has no configured action"
        )
        val contextAllows = eligible.all { context.allows(it.type, sample, now) }
        val sensorQualityOk = sample.accel.magnitude > 0.1f || sample.gyro.magnitude > 0.01f || sample.proximityCm != null
        return gate.evaluate(
            candidates = eligible,
            sample = sample,
            contextAllows = contextAllows,
            segmentComplete = completedSegment?.complete ?: eligible.all { it.type.isProximityGesture() },
            sensorQualityOk = sensorQualityOk,
            actionEligible = true,
            cooldownFor = { type -> configs[type]?.cooldownMs?.toLong() ?: 1200L }
        )
    }

    fun reset() {
        recognizers.forEach { it.reset() }
        context.reset()
        gate.reset()
        segmenter.reset()
        pending.clear()
        featureWindow.clear()
    }

    private fun GestureType.isProximityGesture() =
        this == GestureType.PROXIMITY_WAVE || this == GestureType.DOUBLE_PROXIMITY_WAVE

    private fun confidenceFloor(mode: String, profile: RecognitionProfile): Float = when {
        profile == RecognitionProfile.HIGH_PRECISION || mode == "HIGH_PRECISION" -> 0.78f
        profile == RecognitionProfile.HIGH_SENSITIVITY || mode == "HIGH_SENSITIVITY" -> 0.58f
        else -> 0.66f
    }

}
