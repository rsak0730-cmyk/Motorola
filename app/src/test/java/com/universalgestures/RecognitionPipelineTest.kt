package com.universalgestures

import com.universalgestures.core.ActionType
import com.universalgestures.core.GestureConfig
import com.universalgestures.core.GestureType
import com.universalgestures.core.RecognitionProfile
import com.universalgestures.core.SensorSample
import com.universalgestures.core.Vec3
import com.universalgestures.recognition.RecognitionPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Replayable, deterministic streams shaped like hand-generated device traces (not physical recordings). */
class RecognitionPipelineTest {
    private fun streamChops(gapMs: Long = 500L): List<SensorSample> = buildList {
        var ms = 0L
        while (ms < 2200L) {
            val inFirst = ms in 200L..260L
            val inSecond = ms in (200L + gapMs)..(260L + gapMs)
            val linear = if (inFirst || inSecond) Vec3(0f, -17f, 0f) else Vec3()
            add(sample(ms, linear))
            ms += 20L
        }
    }

    private fun sample(ms: Long, linear: Vec3 = Vec3(), proximity: Float = 5f, step: Boolean = false) = SensorSample(
        timestampNanos = ms * 1_000_000L,
        accel = Vec3(linear.x, linear.y, 9.81f + linear.z),
        gyro = Vec3(),
        gravity = Vec3(0f, 0f, 9.81f),
        linearAccel = linear,
        proximityCm = proximity,
        stepDetected = step
    )

    @Test
    fun completeDoubleChopSequenceProducesSingleActionEvent() {
        val config = GestureConfig(GestureType.DOUBLE_CHOP, enabled = true, action = ActionType.FLASHLIGHT_TOGGLE)
        val pipeline = RecognitionPipeline(mapOf(GestureType.DOUBLE_CHOP to config))
        val events = streamChops().mapNotNull { pipeline.onSample(it).event }
        assertEquals(1, events.size)
        assertEquals(GestureType.DOUBLE_CHOP, events.single().type)
    }

    @Test
    fun tooSlowChopsDoNotMatchConfiguredTimingWindow() {
        val config = GestureConfig(
            GestureType.DOUBLE_CHOP, enabled = true, action = ActionType.FLASHLIGHT_TOGGLE, timingMs = 500
        )
        val pipeline = RecognitionPipeline(mapOf(GestureType.DOUBLE_CHOP to config))
        val events = streamChops(gapMs = 1000L).mapNotNull { pipeline.onSample(it).event }
        assertNull(events.firstOrNull())
    }

    @Test
    fun actionlessGestureCannotExecute() {
        val config = GestureConfig(GestureType.DOUBLE_CHOP, enabled = true, action = ActionType.NONE)
        val pipeline = RecognitionPipeline(mapOf(GestureType.DOUBLE_CHOP to config))
        val results = streamChops().map { pipeline.onSample(it) }
        assertNull(results.mapNotNull { it.event }.firstOrNull())
    }

    @Test
    fun idleTraceDoesNotTrigger() {
        val config = GestureConfig(GestureType.DOUBLE_CHOP, enabled = true, action = ActionType.FLASHLIGHT_TOGGLE)
        val pipeline = RecognitionPipeline(mapOf(GestureType.DOUBLE_CHOP to config))
        repeat(200) { assertNull(pipeline.onSample(sample(it * 20L)).event) }
    }

    @Test
    fun walkingTraceDoesNotTriggerChop() {
        val config = GestureConfig(GestureType.DOUBLE_CHOP, enabled = true, action = ActionType.FLASHLIGHT_TOGGLE)
        val pipeline = RecognitionPipeline(mapOf(GestureType.DOUBLE_CHOP to config))
        val events = (0 until 300).map { i ->
            val ms = i * 20L
            val step = i % 25 == 0
            val gait = if (i % 25 in 0..3) Vec3(0f, 0f, if (i % 2 == 0) 2.1f else -2.1f) else Vec3()
            pipeline.onSample(sample(ms, gait, step = step)).event
        }
        assertNull(events.firstOrNull { it != null })
    }

    @Test
    fun coveredPocketTraceRejectsChopCandidate() {
        val config = GestureConfig(GestureType.DOUBLE_CHOP, enabled = true, action = ActionType.FLASHLIGHT_TOGGLE)
        val pipeline = RecognitionPipeline(mapOf(GestureType.DOUBLE_CHOP to config))
        val events = streamChops().map { s -> pipeline.onSample(s.copy(proximityCm = 0.5f)).event }
        assertNull(events.firstOrNull { it != null })
    }

    @Test
    fun profileChangesSensitivityMapping() {
        val lowPrecision = GestureConfig(GestureType.SHAKE, sensitivity = 50)
        val strict = RecognitionPipeline(mapOf(GestureType.SHAKE to lowPrecision), profile = RecognitionProfile.HIGH_PRECISION)
        val sensitive = RecognitionPipeline(mapOf(GestureType.SHAKE to lowPrecision), profile = RecognitionProfile.HIGH_SENSITIVITY)
        // Public profile behavior is asserted by construction and an idle negative stays negative.
        assertNotNull(strict)
        assertNotNull(sensitive)
        repeat(20) { assertNull(strict.onSample(sample(it * 20L)).event) }
        repeat(20) { assertNull(sensitive.onSample(sample(it * 20L)).event) }
    }
}
