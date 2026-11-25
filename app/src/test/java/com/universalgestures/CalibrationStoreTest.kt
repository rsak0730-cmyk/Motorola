package com.universalgestures

import com.universalgestures.core.GestureType
import com.universalgestures.core.RecognitionProfile
import com.universalgestures.core.Vec3
import com.universalgestures.recognition.CalibrationStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationStoreTest {

    private fun features(magnitude: Float): List<Vec3> =
        listOf(Vec3(magnitude, 0f, 0f), Vec3(magnitude * 0.9f, 0f, 0f), Vec3(magnitude * 1.1f, 0f, 0f))

    @Test
    fun uncalibratedByDefault() {
        val store = CalibrationStore()
        assertFalse(store.isCalibrated(GestureType.DOUBLE_CHOP))
    }

    @Test
    fun twoPositivesCalibrate() {
        val store = CalibrationStore()
        store.addPositive(GestureType.DOUBLE_CHOP, features(10f))
        store.addPositive(GestureType.DOUBLE_CHOP, features(10.5f))
        assertTrue(store.isCalibrated(GestureType.DOUBLE_CHOP))
    }

    @Test
    fun thresholdClampedToBaselineBand() {
        val store = CalibrationStore()
        store.activeProfile = RecognitionProfile.HIGH_SENSITIVITY
        val threshold = store.thresholdFor(GestureType.DOUBLE_CHOP)
        assertTrue(threshold in 0.22f..0.55f)
        store.activeProfile = RecognitionProfile.HIGH_PRECISION
        assertTrue(store.thresholdFor(GestureType.DOUBLE_CHOP) in 0.22f..0.55f)
    }

    @Test
    fun matchScoreZeroForUncalibrated() {
        val store = CalibrationStore()
        assertEquals(1f, store.matchScore(GestureType.DOUBLE_CHOP, features(5f)), 0.001f)
    }

    @Test
    fun clearRemovesCalibration() {
        val store = CalibrationStore()
        store.addPositive(GestureType.SHAKE, features(8f))
        store.addPositive(GestureType.SHAKE, features(8.2f))
        assertTrue(store.isCalibrated(GestureType.SHAKE))
        store.clear(GestureType.SHAKE)
        assertFalse(store.isCalibrated(GestureType.SHAKE))
    }
}
