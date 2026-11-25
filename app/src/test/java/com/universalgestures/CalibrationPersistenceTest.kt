package com.universalgestures

import com.universalgestures.core.GestureType
import com.universalgestures.core.Vec3
import com.universalgestures.recognition.CalibrationStore
import com.universalgestures.recognition.MotionFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationPersistenceTest {
    private val positive = listOf(Vec3(1f, 0f, 0f), Vec3(2f, 0f, 0f), Vec3(1f, 1f, 0f))
    private val negative = listOf(Vec3(-2f, 0f, 0f), Vec3(-3f, 0f, 0f), Vec3(-2f, -1f, 0f))

    @Test
    fun examplesRoundTripThroughLocalJson() {
        val original = CalibrationStore()
        original.addPositive(GestureType.CUSTOM_MOTION, positive)
        original.addPositive(GestureType.CUSTOM_MOTION, positive.map { it * 1.02f })
        original.addNegative(GestureType.CUSTOM_MOTION, negative)
        val restored = CalibrationStore.fromJson(original.toJson())
        val result = restored.profileFor(GestureType.CUSTOM_MOTION)
        assertEquals(2, result.positives.size)
        assertEquals(1, result.negatives.size)
        assertTrue(result.calibrated)
        assertEquals(positive[0], result.positives.first()[0])
    }

    @Test
    fun multivariateTemplatesPersistAndAreCalibrated() {
        val a = MotionFeature(List(11) { if (it == 0) 0.25f else 0f })
        val b = MotionFeature(List(11) { if (it == 0) 0.3f else 0f })
        val original = CalibrationStore()
        original.addMotionPositive(GestureType.SHAKE, listOf(a, b, a))
        original.addMotionPositive(GestureType.SHAKE, listOf(a, a, b))
        original.addMotionNegative(GestureType.SHAKE, listOf(MotionFeature(List(11) { 0.8f }), MotionFeature(List(11) { 0.7f })))
        val restored = CalibrationStore.fromJson(original.toJson())
        val profile = restored.profileFor(GestureType.SHAKE)
        assertEquals(2, profile.motionPositives.size)
        assertEquals(1, profile.motionNegatives.size)
        assertTrue(restored.isCalibrated(GestureType.SHAKE))
    }

    @Test
    fun negativeExampleCanSuppressCloserNegativeCandidate() {
        val store = CalibrationStore()
        store.addPositive(GestureType.CUSTOM_MOTION, positive)
        store.addPositive(GestureType.CUSTOM_MOTION, positive.map { it * 1.01f })
        store.addNegative(GestureType.CUSTOM_MOTION, negative)
        val accepted = store.adjustedConfidence(GestureType.CUSTOM_MOTION, positive, 0.9f)
        val rejected = store.adjustedConfidence(GestureType.CUSTOM_MOTION, negative, 0.9f)
        assertTrue(accepted > 0f)
        assertEquals(0f, rejected, 0f)
    }
}
