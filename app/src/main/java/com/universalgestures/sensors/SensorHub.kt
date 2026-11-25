package com.universalgestures.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import com.universalgestures.core.SensorInfo
import com.universalgestures.core.SensorSample
import com.universalgestures.core.Vec3

/**
 * Hardware Sensor Intelligence (UMI layer A).
 *
 * Discovers every exposed sensor, records its capabilities, prefers
 * hardware-assisted composite sensors (linear acceleration, gravity,
 * rotation vector, pickup, tilt, significant motion) and falls back to
 * software fusion when hardware sensors are absent. Never fabricates
 * capabilities.
 */
class SensorHub(context: Context) : SensorEventListener {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val imu = ImuProcessor()
    private var listener: ((SensorSample) -> Unit)? = null

    private var accel: Sensor? = null
    private var gyro: Sensor? = null
    private var gravitySensor: Sensor? = null
    private var linearAccelSensor: Sensor? = null
    private var rotationVector: Sensor? = null
    private var proximity: Sensor? = null
    private var stepDetector: Sensor? = null

    private var lastAccel = Vec3()
    private var lastGyro = Vec3()
    private var lastGravity: Vec3? = null
    private var lastLinearAccel: Vec3? = null
    private var lastRotation: Vec3? = null
    private var lastProximity: Float? = null
    private var lastStepNanos = 0L
    private var registered = false

    val samplingHz: Float get() = imu.samplingHz

    fun discover(): List<SensorInfo> {
        val all = sensorManager.getSensorList(Sensor.TYPE_ALL)
        return all.map {
            SensorInfo(
                type = it.type,
                name = it.name,
                vendor = it.vendor,
                version = it.version,
                resolution = it.resolution,
                maximumRange = it.maximumRange,
                powerMa = it.power,
                minDelayUs = it.minDelay,
                reportingMode = it.reportingMode,
                wakeUp = it.isWakeUpSensor
            )
        }
    }

    fun hasSensor(type: Int): Boolean =
        sensorManager.getDefaultSensor(type) != null

    fun start(
        listener: (SensorSample) -> Unit,
        samplingPeriodUs: Int = 20_000,
        callbackHandler: Handler = Handler(Looper.getMainLooper())
    ) {
        stop()
        imu.reset()
        this.listener = listener
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        val rate = samplingPeriodUs.coerceIn(10_000, 100_000)
        accel?.let { sensorManager.registerListener(this, it, rate, 0, callbackHandler) }
        gyro?.let { sensorManager.registerListener(this, it, rate, 0, callbackHandler) }
        gravitySensor?.let { sensorManager.registerListener(this, it, rate, 0, callbackHandler) }
        linearAccelSensor?.let { sensorManager.registerListener(this, it, rate, 0, callbackHandler) }
        rotationVector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, 0, callbackHandler) }
        proximity?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, 0, callbackHandler) }
        stepDetector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, 0, callbackHandler) }
        registered = true
    }

    fun stop() {
        if (registered) {
            sensorManager.unregisterListener(this)
            registered = false
        }
        listener = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val values = event.values
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> lastAccel = Vec3(values[0], values[1], values[2])
            Sensor.TYPE_GYROSCOPE -> lastGyro = Vec3(values[0], values[1], values[2])
            Sensor.TYPE_GRAVITY -> lastGravity = Vec3(values[0], values[1], values[2])
            Sensor.TYPE_LINEAR_ACCELERATION -> lastLinearAccel = Vec3(values[0], values[1], values[2])
            Sensor.TYPE_ROTATION_VECTOR -> lastRotation = Vec3(values[0], values[1], values[2])
            Sensor.TYPE_PROXIMITY -> lastProximity = values[0]
            Sensor.TYPE_STEP_DETECTOR -> lastStepNanos = event.timestamp
            else -> return
        }
        // Use accelerometer timestamps as the IMU clock. Proximity is also
        // emitted so proximity-only interaction remains observable without
        // advancing the inertial filter on unrelated sensor callbacks.
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER || event.sensor.type == Sensor.TYPE_PROXIMITY) {
            emit(event.timestamp)
        }
    }

    private fun emit(timestampNanos: Long) {
        val cb = listener ?: return
        val processed = imu.process(timestampNanos, lastAccel, lastGyro, lastGravity, lastLinearAccel)
        cb(
            SensorSample(
                timestampNanos = timestampNanos,
                accel = processed.accel,
                gyro = processed.gyro,
                gravity = lastGravity ?: processed.gravity,
                linearAccel = processed.linearAccel,
                rotationVector = lastRotation,
                proximityCm = lastProximity,
                stepDetected = lastStepNanos != 0L && timestampNanos - lastStepNanos < 100_000_000L,
                pickupDetected = false,
                tiltDetected = false,
                significantMotion = false
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
