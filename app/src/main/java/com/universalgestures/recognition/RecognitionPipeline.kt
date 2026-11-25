package com.universalgestures.recognition

import com.universalgestures.core.GestureConfig
import com.universalgestures.core.GestureType
import com.universalgestures.core.MotionSegment
import com.universalgestures.core.RecognitionProfile
import com.universalgestures.core.RecognitionResult
import com.universalgestures.core.RejectionReason
import com.universalgestures.core.SensorSample
import com.universalgestures.core.Vec3
import com.universalgestures.sensors.MotionSegmenter

/** Timestamped, complete-sequence UMI pipeline with negative classification and centralized arbitration. */
class RecognitionPipeline(
    private val configs: Map<GestureType, GestureConfig>,
    private val calibration: CalibrationStore = CalibrationStore(),
    private val customTemplates: Map<GestureType, List<List<Vec3>>> = emptyMap(),
    private val profile: RecognitionProfile = RecognitionProfile.DEFAULT,
    private val globalSensitivity: Int = 50,
    private val accuracyMode: String = "BALANCED"
) {
    private val context = ContextVerifier()
    private val negativeClassifier = NegativeMotionClassifier()
    private val gate = PrecisionGate(minConfidence = confidenceFloor(accuracyMode, profile))
    private val segmenter = MotionSegmenter()
    private val arbitrator = GestureArbitrator()
    private val featureWindow = ArrayDeque<SensorSample>()
    private val pending = mutableMapOf<GestureType, Candidate>()
    private val templates = GestureTemplateRepository(calibration)
    private val recognizers: List<GestureRecognizer> = buildRecognizers()
    private var sequenceId = 0L
    private var deferredSegment: MotionSegment? = null
    private var deferredSequenceId = 0L

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

    private fun buildRecognizers(): List<GestureRecognizer> = buildList {
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
            val gestureTemplates = (customTemplates[GestureType.CUSTOM_MOTION].orEmpty() + examples).take(10)
            if (gestureTemplates.isNotEmpty()) CustomMotionRecognizer(gestureTemplates, calibration.thresholdFor(GestureType.CUSTOM_MOTION)) else null
        }
    }

    fun onSample(sample: SensorSample): RecognitionResult {
        if (!sample.isValid()) return RecognitionResult(rejectionReason = RejectionReason.SENSOR_QUALITY_LOW, detail = "non-finite or invalid sensor frame")
        context.onSample(sample)
        featureWindow.addLast(sample)
        while (featureWindow.size > 160) featureWindow.removeFirst()

        val completedSegment = segmenter.onSample(sample)
        val newlyDetected = recognizers.mapNotNull { recognizer ->
            recognizer.onSample(sample)?.copy(detectedAtMs = sample.timestampMs)
        }
        newlyDetected.forEach { candidate ->
            val previous = pending[candidate.type]
            if (previous == null || candidate.confidence > previous.confidence) pending[candidate.type] = candidate
        }
        if (completedSegment?.complete == false) {
            pending.clear()
            arbitrator.reset()
            deferredSegment = null
            return RecognitionResult(rejectionReason = RejectionReason.SEGMENT_INCOMPLETE, detail = "interrupted segment discarded")
        }

        var candidates = emptyList<Candidate>()
        var candidateSegment: MotionSegment? = null
        var candidateSequence = sequenceId
        if (completedSegment != null) {
            sequenceId++
            candidateSequence = sequenceId
            if (completedSegment.complete) {
                candidates = pending.values.filterNot { it.type.isProximityGesture() }
                    .map { it.copy(sequenceId = sequenceId) }
                candidateSegment = completedSegment
            }
            // A completed segment is a hard boundary. Never carry a partial match into the next one.
            pending.clear()
        } else if (!segmenter.isSegmenting) {
            candidates = pending.values.filter { it.type.isProximityGesture() }
            pending.keys.removeAll { it.isProximityGesture() }
        }

        val actionable = configs.filterValues { it.enabled && it.action.name != "NONE" }.keys
        val windows = configs.mapValues { it.value.timingMs.toLong() }
        val decision = if (candidates.isNotEmpty()) {
            arbitrator.offer(candidates, actionable, windows, sample.timestampMs)
        } else {
            arbitrator.poll(sample.timestampMs, segmenter.isSegmenting)
        }

        return when (decision) {
            ArbitrationDecision.NoCandidate -> RecognitionResult()
            ArbitrationDecision.Deferred -> {
                if (candidateSegment?.complete == true) {
                    deferredSegment = candidateSegment
                    deferredSequenceId = candidateSequence
                }
                RecognitionResult(rejectionReason = RejectionReason.SEGMENT_INCOMPLETE, detail = "shorter pattern deferred while checking for a longer gesture")
            }
            is ArbitrationDecision.Ambiguous -> {
                deferredSegment = null
                RecognitionResult(rejectionReason = RejectionReason.AMBIGUOUS, detail = decision.reason)
            }
            is ArbitrationDecision.Winner -> {
                val segment = candidateSegment ?: deferredSegment
                val eventSequence = if (candidateSegment != null) candidateSequence else deferredSequenceId
                deferredSegment = null
                evaluateWinner(decision.candidate.copy(sequenceId = eventSequence), sample, segment, eventSequence)
            }
        }
    }

    private fun evaluateWinner(
        candidate: Candidate,
        sample: SensorSample,
        segment: MotionSegment?,
        sequence: Long
    ): RecognitionResult {
        val type = candidate.type
        if (configs[type]?.enabled != true || configs[type]?.action?.name == "NONE") {
            return RecognitionResult(rejectionReason = RejectionReason.ACTION_INELIGIBLE, detail = "gesture disabled or action not assigned")
        }
        val negative = segment?.let {
            negativeClassifier.classify(it, it.samples, context.stepsPerMinute(sample.timestampMs), type)
        }
        if (negative?.rejected == true) {
            val reason = if (negative.kind == NegativeMotionKind.TABLE_IMPACT) RejectionReason.PATTERN_MISMATCH else RejectionReason.CONTEXT_REJECTED
            return RecognitionResult(rejectionReason = reason, detail = "negative classifier: ${negative.reason}")
        }
        if (!context.allows(type, sample, sample.timestampMs)) {
            return RecognitionResult(rejectionReason = RejectionReason.CONTEXT_REJECTED, detail = "CMVE rejected ${type.name}")
        }
        val sequenceFeatures = segment?.takeIf { it.complete }?.let { MotionFeatureExtractor.extract(it.samples) }.orEmpty()
        val calibrated = if (sequenceFeatures.isNotEmpty() && templates.positives(type).isNotEmpty()) {
            templates.confidence(type, sequenceFeatures, candidate.confidence)
        } else {
            val legacy = segment?.let { DtwMatcher.featureSequence(it.samples, 32) }.orEmpty()
            calibration.adjustedConfidence(type, legacy, candidate.confidence)
        }
        val confidence = calibrated * context.confidenceMultiplier(type, sample.timestampMs)
        if (confidence < gate.minimumConfidence) {
            return RecognitionResult(rejectionReason = RejectionReason.LOW_CONFIDENCE, detail = "candidate confidence after calibration/context=${"%.3f".format(confidence)}")
        }
        val qualityOk = sample.accel.magnitude > 0.1f || sample.gyro.magnitude > 0.01f || sample.proximityCm != null
        return gate.evaluate(
            candidates = listOf(candidate.copy(confidence = confidence)),
            sample = sample,
            contextAllows = true,
            segmentComplete = segment?.complete ?: type.isProximityGesture(),
            sensorQualityOk = qualityOk,
            actionEligible = true,
            cooldownFor = { configs[it]?.cooldownMs?.toLong() ?: 1200L },
            sequenceId = sequence
        )
    }

    fun reset() {
        recognizers.forEach { it.reset() }
        context.reset(); gate.reset(); segmenter.reset(); arbitrator.reset()
        pending.clear(); featureWindow.clear(); deferredSegment = null; sequenceId = 0L
    }

    private fun GestureType.isProximityGesture() = this == GestureType.PROXIMITY_WAVE || this == GestureType.DOUBLE_PROXIMITY_WAVE
    private fun SensorSample.isValid() = timestampNanos > 0L &&
        accel.x.isFinite() && accel.y.isFinite() && accel.z.isFinite() &&
        gyro.x.isFinite() && gyro.y.isFinite() && gyro.z.isFinite() &&
        gravity.x.isFinite() && gravity.y.isFinite() && gravity.z.isFinite() &&
        linearAccel.x.isFinite() && linearAccel.y.isFinite() && linearAccel.z.isFinite() &&
        (proximityCm == null || proximityCm.isFinite()) && quality.isFinite() && quality >= 0.5f

    private fun confidenceFloor(mode: String, profile: RecognitionProfile): Float = when {
        profile == RecognitionProfile.HIGH_PRECISION || mode == "HIGH_PRECISION" -> 0.78f
        profile == RecognitionProfile.HIGH_SENSITIVITY || mode == "HIGH_SENSITIVITY" -> 0.58f
        else -> 0.66f
    }
}
