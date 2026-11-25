package com.universalgestures.recognition

import com.universalgestures.core.GestureType

sealed interface ArbitrationDecision {
    data object NoCandidate : ArbitrationDecision
    data object Deferred : ArbitrationDecision
    data class Winner(val candidate: Candidate) : ArbitrationDecision
    data class Ambiguous(val reason: String) : ArbitrationDecision
}

/** One authority for overlapping gesture families and deferred shorter matches. */
class GestureArbitrator {
    private data class DeferredCandidate(val candidate: Candidate, val deadlineMs: Long)
    private var deferred: DeferredCandidate? = null

    fun offer(
        candidates: List<Candidate>,
        enabledAndActionable: Set<GestureType>,
        timingWindowsMs: Map<GestureType, Long>,
        nowMs: Long
    ): ArbitrationDecision {
        if (candidates.isEmpty()) return ArbitrationDecision.NoCandidate
        val grouped = candidates.groupBy { family(it.type) }
        if (grouped.size > 1) {
            deferred = null
            return ArbitrationDecision.Ambiguous("candidate set spans ${grouped.keys}")
        }
        val familyCandidates = grouped.values.single()
        val ranked = familyCandidates.maxByOrNull { specificity(it.type) } ?: return ArbitrationDecision.NoCandidate
        val deferredNow = deferred
        if (deferredNow != null && family(deferredNow.candidate.type) == family(ranked.type)) {
            if (specificity(ranked.type) > specificity(deferredNow.candidate.type)) {
                deferred = null
                return ArbitrationDecision.Winner(ranked)
            }
        }
        val longerAlternatives = longer(ranked.type).filter { it in enabledAndActionable }
        if (longerAlternatives.isNotEmpty()) {
            val delay = longerAlternatives.maxOf { timingWindowsMs[it] ?: 900L }.coerceIn(300L, 2500L)
            val candidateTime = ranked.detectedAtMs.takeIf { it > 0L } ?: nowMs
            deferred = DeferredCandidate(ranked, candidateTime + delay)
            return ArbitrationDecision.Deferred
        }
        deferred = null
        return ArbitrationDecision.Winner(ranked)
    }

    /** Release a short pattern only after its configured longer-pattern window closes. */
    fun poll(nowMs: Long, motionInProgress: Boolean): ArbitrationDecision {
        val current = deferred ?: return ArbitrationDecision.NoCandidate
        if (motionInProgress || nowMs < current.deadlineMs) return ArbitrationDecision.Deferred
        deferred = null
        return ArbitrationDecision.Winner(current.candidate)
    }

    fun reset() { deferred = null }

    private fun family(type: GestureType): String = when (type) {
        GestureType.SINGLE_CHOP, GestureType.DOUBLE_CHOP -> "chop"
        GestureType.DOUBLE_TWIST, GestureType.TRIPLE_TWIST -> "twist"
        GestureType.SHAKE, GestureType.DOUBLE_SHAKE, GestureType.TRIPLE_SHAKE -> "shake"
        else -> type.name
    }

    private fun specificity(type: GestureType): Int = when (type) {
        GestureType.SINGLE_CHOP, GestureType.SHAKE, GestureType.DOUBLE_TWIST -> 1
        GestureType.DOUBLE_CHOP, GestureType.DOUBLE_SHAKE, GestureType.TRIPLE_TWIST -> 2
        GestureType.TRIPLE_SHAKE -> 3
        else -> 1
    }

    private fun longer(type: GestureType): List<GestureType> = when (type) {
        GestureType.SINGLE_CHOP -> listOf(GestureType.DOUBLE_CHOP)
        GestureType.SHAKE -> listOf(GestureType.DOUBLE_SHAKE, GestureType.TRIPLE_SHAKE)
        GestureType.DOUBLE_SHAKE -> listOf(GestureType.TRIPLE_SHAKE)
        GestureType.DOUBLE_TWIST -> listOf(GestureType.TRIPLE_TWIST)
        else -> emptyList()
    }
}
