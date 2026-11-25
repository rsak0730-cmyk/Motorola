package com.universalgestures.recognition

import com.universalgestures.core.GestureType
import kotlin.math.abs

/** Explicit per-gesture temporal state, timestamp-driven and resettable at gaps. */
class PerGestureStateMachine(
    val type: GestureType,
    private val expectedPulses: Int,
    private val maxGapMs: Long,
    private val minPulseMs: Long = 35L,
    private val maxPulseMs: Long = 650L
) {
    enum class State { IDLE, ACTIVE_PULSE, WAITING_GAP, COMPLETE, REJECTED }
    data class Snapshot(val state: State, val pulseCount: Int, val lastTransitionMs: Long)
    private var state = State.IDLE
    private var pulseCount = 0
    private var lastTransitionMs = -1L
    private var pulseStartMs = -1L
    private var lastSign = 0

    fun onMotion(timestampMs: Long, signedEnergy: Float, threshold: Float = 2.0f): Snapshot {
        if (timestampMs < 0 || (lastTransitionMs >= 0 && timestampMs <= lastTransitionMs)) return reject(timestampMs)
        if (state == State.COMPLETE || state == State.REJECTED) reset()
        if (state == State.WAITING_GAP && timestampMs - lastTransitionMs > maxGapMs) reset()
        val sign = when { signedEnergy > threshold -> 1; signedEnergy < -threshold -> -1; else -> 0 }
        if (state == State.IDLE || state == State.WAITING_GAP) {
            if (sign != 0) {
                state = State.ACTIVE_PULSE
                pulseStartMs = timestampMs
                lastSign = sign
            }
        } else if (state == State.ACTIVE_PULSE) {
            val duration = timestampMs - pulseStartMs
            if (sign == 0) {
                if (duration !in minPulseMs..maxPulseMs) return reject(timestampMs)
                pulseCount++
                state = if (pulseCount >= expectedPulses) State.COMPLETE else State.WAITING_GAP
            } else if (sign != lastSign) {
                if (duration !in minPulseMs..maxPulseMs) return reject(timestampMs)
                pulseCount++
                if (pulseCount >= expectedPulses) state = State.COMPLETE
                else { state = State.ACTIVE_PULSE; pulseStartMs = timestampMs; lastSign = sign }
            }
        }
        lastTransitionMs = timestampMs
        return Snapshot(state, pulseCount, timestampMs)
    }

    fun state() = Snapshot(state, pulseCount, lastTransitionMs)
    fun reset() { state = State.IDLE; pulseCount = 0; lastTransitionMs = -1L; pulseStartMs = -1L; lastSign = 0 }
    private fun reject(now: Long): Snapshot { state = State.REJECTED; lastTransitionMs = now; return Snapshot(state, pulseCount, now) }
}

/** State-machine bank makes each supported gesture's sequencing independent. */
class GestureStateMachineBank(configs: Map<GestureType, Pair<Int, Long>>) {
    private val machines = configs.mapValues { (type, spec) -> PerGestureStateMachine(type, spec.first, spec.second) }
    fun update(type: GestureType, timestampMs: Long, energy: Float): PerGestureStateMachine.Snapshot? = machines[type]?.onMotion(timestampMs, energy)
    fun snapshots() = machines.mapValues { it.value.state() }
    fun reset() = machines.values.forEach { it.reset() }
}
