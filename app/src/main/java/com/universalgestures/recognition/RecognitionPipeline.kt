package com.universalgestures.recognition

import com.universalgestures.core.GestureConfig
import com.universalgestures.core.GestureType
import com.universalgestures.core.RecognitionResult
import com.universalgestures.core.SensorSample
import com.universalgestures.sensors.MotionSegmenter

/**
 * End-to-end recognition pipeline (UMI layer E/F/G):
 * sample -> recognizers -> CMVE context -> PGI gate.
 */
class RecognitionPipeline(
    private val configs: Map<GestureType, GestureConfig>,
    private val calibration: CalibrationStore = CalibrationStore(),
    private val customTemplates: Map<GestureType, List<List<com.universalgestures.core.Vec3>>> = emptyMap()
) {
    private val context = ContextVerifier()
    private val gate = PrecisionGate()
    private val segmenter = MotionSegmenter()
    private val recognizers: List<GestureRecognizer> = buildRecognizers(configs, calibration, customTemplates)

    private fun buildRecognizers(
        configs: Map<GestureType, GestureConfig>,
        calibration: CalibrationStore,
        templates: Map<GestureType, List<List<com.universalgestures.core.Vec3>>>
    ): List<GestureRecognizer> {
        val list = mutableListOf<GestureRecognizer>()
        fun cfg(t: GestureType) = configs[t]
        if (cfg(GestureType.DOUBLE_CHOP)?.enabled == true)
            list.add(ChopRecognizer(GestureType.DOUBLE_CHOP, 2, cfg(GestureType.DOUBLE_CHOP)!!.sensitivity))
        if (cfg(GestureType.SINGLE_CHOP)?.enabled == true)
            list.add(ChopRecognizer(GestureType.SINGLE_CHOP, 1, cfg(GestureType.SINGLE_CHOP)!!.sensitivity))
        if (cfg(GestureType.DOUBLE_TWIST)?.enabled == true)
            list.add(TwistRecognizer(GestureType.DOUBLE_TWIST, 2, cfg(GestureType.DOUBLE_TWIST)!!.sensitivity))
        if (cfg(GestureType.TRIPLE_TWIST)?.enabled == true)
            list.add(TwistRecognizer(GestureType.TRIPLE_TWIST, 3, cfg(GestureType.TRIPLE_TWIST)!!.sensitivity))
        if (cfg(GestureType.SHAKE)?.enabled == true)
            list.add(ShakeRecognizer(GestureType.SHAKE, 2, cfg(GestureType.SHAKE)!!.sensitivity))
        if (cfg(GestureType.DOUBLE_SHAKE)?.enabled == true)
            list.add(ShakeRecognizer(GestureType.DOUBLE_SHAKE, 4, cfg(GestureType.DOUBLE_SHAKE)!!.sensitivity))
        if (cfg(GestureType.TRIPLE_SHAKE)?.enabled == true)
            list.add(ShakeRecognizer(GestureType.TRIPLE_SHAKE, 6, cfg(GestureType.TRIPLE_SHAKE)!!.sensitivity))
        if (cfg(GestureType.FLIP)?.enabled == true) list.add(FlipRecognizer())
        if (cfg(GestureType.PICKUP)?.enabled == true) list.add(PickupRecognizer())
        if (cfg(GestureType.TILT_LEFT)?.enabled == true)
            list.add(TiltRecognizer(GestureType.TILT_LEFT, cfg(GestureType.TILT_LEFT)!!.sensitivity))
        if (cfg(GestureType.TILT_RIGHT)?.enabled == true)
            list.add(TiltRecognizer(GestureType.TILT_RIGHT, cfg(GestureType.TILT_RIGHT)!!.sensitivity))
        if (cfg(GestureType.TILT_FORWARD)?.enabled == true)
            list.add(TiltRecognizer(GestureType.TILT_FORWARD, cfg(GestureType.TILT_FORWARD)!!.sensitivity))
        if (cfg(GestureType.TILT_BACKWARD)?.enabled == true)
            list.add(TiltRecognizer(GestureType.TILT_BACKWARD, cfg(GestureType.TILT_BACKWARD)!!.sensitivity))
        if (cfg(GestureType.PROXIMITY_WAVE)?.enabled == true)
            list.add(ProximityWaveRecognizer(GestureType.PROXIMITY_WAVE, 1))
        if (cfg(GestureType.DOUBLE_PROXIMITY_WAVE)?.enabled == true)
            list.add(ProximityWaveRecognizer(GestureType.DOUBLE_PROXIMITY_WAVE, 2))
        if (cfg(GestureType.CUSTOM_MOTION)?.enabled == true) {
            val t = templates[GestureType.CUSTOM_MOTION] ?: emptyList()
            if (t.isNotEmpty()) list.add(CustomMotionRecognizer(t, calibration.thresholdFor(GestureType.CUSTOM_MOTION)))
        }
        return list
    }

    fun onSample(sample: SensorSample): RecognitionResult {
        context.onSample(sample)
        val segment = segmenter.onSample(sample)
        val candidates = recognizers.mapNotNull { it.onSample(sample) }
        val now = sample.timestampMs
        val contextAllows = candidates.all { context.allows(it.type, sample, now) }
        val actionEligible = candidates.any { (configs[it.type]?.action?.name ?: "NONE") != "NONE" }
        val sensorQualityOk = sample.accel.magnitude > 0.1f || sample.gyro.magnitude > 0.01f ||
            sample.proximityCm != null
        return gate.evaluate(
            candidates = candidates,
            sample = sample,
            contextAllows = contextAllows,
            segmentComplete = segment?.complete ?: true,
            sensorQualityOk = sensorQualityOk,
            actionEligible = actionEligible
        )
    }

    fun reset() {
        recognizers.forEach { it.reset() }
        context.reset()
        gate.reset()
        segmenter.reset()
    }
}
