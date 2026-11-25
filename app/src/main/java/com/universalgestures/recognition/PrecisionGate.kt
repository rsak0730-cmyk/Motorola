package com.universalgestures.recognition

import com.universalgestures.core.GestureEvent
import com.universalgestures.core.GestureType
import com.universalgestures.core.RecognitionResult
import com.universalgestures.core.RejectionReason
import com.universalgestures.core.SensorSample

/**
 * PGI — Precision Gesture Intelligence.
 *
 * Rejection-first architecture. A candidate must pass sensor-data
 * validation, segmentation, pattern, direction/rotation, context,
 * confidence, duplicate and eligibility checks. Ambiguity is rejected
 * rather than guessed.
 */
class PrecisionGate(
    private val minConfidence: Float = 0.6f,
    private val duplicateCooldownMs: Long = 1200L
) {
    private val lastAccepted = mutableMapOf<GestureType, Long>()
    private var lastAnyAcceptedMs = -1L

    fun evaluate(
        candidates: List<Candidate>,
        sample: SensorSample,
        contextAllows: Boolean,
        segmentComplete: Boolean,
        sensorQualityOk: Boolean,
        actionEligible: Boolean
    ): RecognitionResult {
        if (!sensorQualityOk) {
            return RecognitionResult(rejectionReason = RejectionReason.SENSOR_QUALITY_LOW, detail = "sensor quality insufficient")
        }
        if (candidates.isEmpty()) {
            return RecognitionResult(rejectionReason = RejectionReason.PATTERN_MISMATCH, detail = "no candidate")
        }
        if (!segmentComplete && candidates.any { it.type == GestureType.CUSTOM_MOTION }) {
            return RecognitionResult(rejectionReason = RejectionReason.SEGMENT_INCOMPLETE, detail = "segment incomplete")
        }
        // Ambiguity: two different gestures above threshold -> reject.
        val strong = candidates.filter { it.confidence >= minConfidence }
        val distinct = strong.map { it.type }.distinct()
        if (distinct.size > 1) {
            return RecognitionResult(rejectionReason = RejectionReason.AMBIGUOUS, detail = "ambiguous: $distinct")
        }
        val best = strong.maxByOrNull { it.confidence }
            ?: return RecognitionResult(rejectionReason = RejectionReason.LOW_CONFIDENCE, detail = "below confidence floor")
        if (!contextAllows) {
            return RecognitionResult(rejectionReason = RejectionReason.CONTEXT_REJECTED, detail = "context rejected ${best.type}")
        }
        if (!actionEligible) {
            return RecognitionResult(rejectionReason = RejectionReason.ACTION_INELIGIBLE, detail = "no action assigned")
        }
        val now = sample.timestampMs
        val last = lastAccepted[best.type] ?: -1L
        if (last >= 0 && now - last < duplicateCooldownMs) {
            return RecognitionResult(rejectionReason = RejectionReason.DUPLICATE, detail = "duplicate suppressed")
        }
        if (lastAnyAcceptedMs >= 0 && now - lastAnyAcceptedMs < 400L) {
            return RecognitionResult(rejectionReason = RejectionReason.DUPLICATE, detail = "global cooldown")
        }
        lastAccepted[best.type] = now
        lastAnyAcceptedMs = now
        return RecognitionResult(
            event = GestureEvent(best.type, best.confidence, now),
            rejectionReason = RejectionReason.NONE,
            detail = best.detail
        )
    }

    fun reset() {
        lastAccepted.clear()
        lastAnyAcceptedMs = -1L
    }
}
