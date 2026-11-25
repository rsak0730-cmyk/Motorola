package com.universalgestures.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
    private var pickup: Sensor? = null
    private var tilt: Sensor? = null
    private var significantMotion: Sensor? = null

    private var lastAccel = Vec3()
    private var lastGyro = Vec3()
    private var lastGravity: Vec3? = null
    private var lastLinearAccel: Vec3? = null
    private var lastRotation: Vec3? = null
    private var lastProximity: Float? = null
    private var lastStepNanos = 0L
    private var lastPickupNanos = 0L
    private var lastTiltNanos = 0L
    private var lastSignificantNanos = 0L
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

    fun start(listener: (SensorSample) -> Unit) {
        this.listener = listener
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        pickup = sensorManager.getDefaultSensor(Sensor.TYPE_PICKUP_GESTURE)
        tilt = sensorManager.getDefaultSensor(Sensor.TYPE_TILT_DETECTOR)
        significantMotion = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

        val rate = SensorManager.SENSOR_DELAY_GAME
        accel?.let { sensorManager.registerListener(this, it, rate) }
        gyro?.let { sensorManager.registerListener(this, it, rate) }
        gravitySensor?.let { sensorManager.registerListener(this, it, rate) }
        linearAccelSensor?.let { sensorManager.registerListener(this, it, rate) }
        rotationVector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        proximity?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        stepDetector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        pickup?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        tilt?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        significantMotion?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
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
            Sensor.TYPE_PICKUP_GESTURE -> lastPickupNanos = event.timestamp
            Sensor.TYPE_TILT_DETECTOR -> lastTiltNanos = event.timestamp
            Sensor.TYPE_SIGNIFICANT_MOTION -> lastSignificantNanos = event.timestamp
            else -> return
        }
        emit(event.timestamp)
    }

    private fun emit(timestampNanos: Long) {
        val cb = listener ?: return
        val processed = imu.process(timestampNanos, lastAccel, lastGyro, lastGravity, lastLinearAccel)
        cb(
            SensorSample(
                timestampNanos = timestampNanos,
                accel = processed.accel,
                gyro = processed.gyro,
                gravity = lastGravity ?: lastAccel,
                linearAccel = processed.linearAccel,
                rotationVector = lastRotation,
                proximityCm = lastProximity,
                stepDetected = lastStepNanos != 0L && timestampNanos - lastStepNanos < 100_000_000L,
                pickupDetected = lastPickupNanos != 0L && timestampNanos - lastPickupNanos < 100_000_000L,
                tiltDetected = lastTiltNanos != 0L && timestampNanos - lastTiltNanos < 100_000_000L,
                significantMotion = lastSignificantNanos != 0L && timestampNanos - lastSignificantNanos < 100_000_000L
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
