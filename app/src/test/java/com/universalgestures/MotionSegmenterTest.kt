package com.universalgestures

import com.universalgestures.core.SensorSample
import com.universalgestures.core.Vec3
import com.universalgestures.sensors.MotionSegmenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionSegmenterTest {

    private fun sample(ms: Long, linear: Vec3): SensorSample = SensorSample(
        timestampNanos = ms * 1_000_000L,
        accel = Vec3(0f, 0f, 9.81f),
        gyro = Vec3(),
        gravity = Vec3(0f, 0f, 9.81f),
        linearAccel = linear
    )

    @Test
    fun idleProducesNoSegment() {
        val segmenter = MotionSegmenter()
        repeat(50) {
            assertNull(segmenter.onSample(sample(it * 20L, Vec3())))
        }
    }

    @Test
    fun movementProducesCompleteSegment() {
        val segmenter = MotionSegmenter()
        var completed: com.universalgestures.core.MotionSegment? = null
        // Quiet, then 300 ms of motion, then quiet.
        for (ms in 0L until 1000L step 20L) {
            val linear = if (ms in 200L..500L) Vec3(5f, 0f, 0f) else Vec3()
            val segment = segmenter.onSample(sample(ms, linear))
            if (segment != null) completed = segment
        }
        assertNotNull(completed)
        assertTrue(completed!!.complete)
        assertTrue(completed.peakLinearAccel > 4f)
    }

    @Test
    fun gapMidSegmentMarksInterrupted() {
        val segmenter = MotionSegmenter()
        segmenter.onSample(sample(0L, Vec3(5f, 0f, 0f)))
        segmenter.onSample(sample(20L, Vec3(5f, 0f, 0f)))
        // 1 s gap -> interrupted segment.
        val segment = segmenter.onSample(sample(1020L, Vec3(5f, 0f, 0f)))
        assertNotNull(segment)
        assertTrue(segment!!.interrupted)
    }

    @Test
    fun flushReturnsOpenSegmentAsInterrupted() {
        val segmenter = MotionSegmenter()
        segmenter.onSample(sample(0L, Vec3(5f, 0f, 0f)))
        val flushed = segmenter.flush()
        assertNotNull(flushed)
        assertTrue(flushed!!.interrupted)
        assertNull(segmenter.flush())
    }
}
