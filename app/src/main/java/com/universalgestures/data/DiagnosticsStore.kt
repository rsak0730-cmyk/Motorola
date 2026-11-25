package com.universalgestures.data

import com.universalgestures.core.DiagnosticSnapshot
import com.universalgestures.core.GestureEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * In-memory diagnostics ring buffer + JSON export.
 * Exports contain only motion aggregates and timestamps — no personal
 * data, no recordings, no identifiers.
 */
class DiagnosticsStore(private val capacity: Int = 200) {

    private val snapshots = ArrayDeque<DiagnosticSnapshot>()
    private val events = ArrayDeque<GestureEvent>()

    @Synchronized
    fun recordSnapshot(snapshot: DiagnosticSnapshot) {
        snapshots.addLast(snapshot)
        while (snapshots.size > capacity) snapshots.removeFirst()
    }

    @Synchronized
    fun recordEvent(event: GestureEvent) {
        events.addLast(event)
        while (events.size > capacity) events.removeFirst()
    }

    @Synchronized
    fun recentSnapshots(): List<DiagnosticSnapshot> = snapshots.toList()

    @Synchronized
    fun recentEvents(): List<GestureEvent> = events.toList()

    @Synchronized
    fun exportJson(): String {
        val root = JSONObject()
        root.put("exportedAtMs", System.currentTimeMillis())
        root.put("note", "Motion aggregates only. No personal data.")
        val snapArray = JSONArray()
        for (s in snapshots) {
            val o = JSONObject()
            o.put("timestampMs", s.timestampMs)
            o.put("accel", vecJson(s.accel.x, s.accel.y, s.accel.z))
            o.put("gyro", vecJson(s.gyro.x, s.gyro.y, s.gyro.z))
            o.put("gravity", vecJson(s.gravity.x, s.gravity.y, s.gravity.z))
            o.put("linearAccel", vecJson(s.linearAccel.x, s.linearAccel.y, s.linearAccel.z))
            o.put("samplingHz", s.samplingHz.toDouble())
            o.put("motionState", s.motionState.name)
            o.put("lastGesture", s.lastGesture?.name ?: "NONE")
            o.put("lastConfidence", s.lastConfidence.toDouble())
            o.put("lastRejection", s.lastRejection.name)
            o.put("lastLatencyMs", s.lastLatencyMs)
            o.put("activeProfile", s.activeProfile)
            snapArray.put(o)
        }
        root.put("snapshots", snapArray)
        val eventArray = JSONArray()
        for (e in events) {
            val o = JSONObject()
            o.put("type", e.type.name)
            o.put("confidence", e.confidence.toDouble())
            o.put("timestampMs", e.timestampMs)
            o.put("latencyMs", e.latencyMs)
            eventArray.put(o)
        }
        root.put("events", eventArray)
        return root.toString()
    }

    @Synchronized
    fun clear() {
        snapshots.clear()
        events.clear()
    }

    private fun vecJson(x: Float, y: Float, z: Float): JSONObject {
        val o = JSONObject()
        o.put("x", x.toDouble())
        o.put("y", y.toDouble())
        o.put("z", z.toDouble())
        return o
    }
}
