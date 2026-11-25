package com.universalgestures.recognition

import com.universalgestures.core.GestureType

/** Recognition-facing facade over the locally persisted calibration profiles. */
class GestureTemplateRepository(private val calibration: CalibrationStore) {
    fun positives(type: GestureType): List<List<MotionFeature>> = calibration.profileFor(type).motionPositives
    fun negatives(type: GestureType): List<List<MotionFeature>> = calibration.profileFor(type).motionNegatives
    fun recordPositive(type: GestureType, sequence: List<MotionFeature>) = calibration.addMotionPositive(type, sequence)
    fun recordNegative(type: GestureType, sequence: List<MotionFeature>) = calibration.addMotionNegative(type, sequence)
    fun confidence(type: GestureType, sequence: List<MotionFeature>, baseline: Float) =
        calibration.adjustedMotionConfidence(type, sequence, baseline)
    fun isCalibrated(type: GestureType) = calibration.isCalibrated(type)
}
