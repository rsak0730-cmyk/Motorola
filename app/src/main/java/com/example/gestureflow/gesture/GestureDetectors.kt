package com.example.gestureflow.gesture

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

interface GestureListener {
    fun onGestureDetected(gestureName: String)
}

abstract class BaseGestureDetector(
    protected val sensorManager: SensorManager,
    private val listener: GestureListener
) : SensorEventListener {
    protected var isEnabled = false
    protected var sensitivity = 50
    
    // Sensor Fusion: Add Proximity Sensor
    private var proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    protected var isProximityCovered = false

    fun updateSettings(enabled: Boolean, sensitivity: Int = 50) {
        this.isEnabled = enabled
        this.sensitivity = sensitivity
        manageRegistration()
    }

    open fun manageRegistration() {
        if (isEnabled && proximitySensor != null) {
            // Register proximity sensor for all gestures to prevent pocket accidents
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            sensorManager.unregisterListener(this, proximitySensor)
        }
    }
    
    protected fun triggerGesture(name: String) {
        // ONLY trigger if the sensor is not covered (Not in pocket/bag)
        if (isEnabled && !isProximityCovered) {
            listener.onGestureDetected(name)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

/**
 * UPGRADED DOUBLE CHOP DETECTOR
 * Includes Sensor Fusion (Proximity) and stricter motion filtering.
 */
class DoubleChopDetector(sensorManager: SensorManager, listener: GestureListener) : 
    BaseGestureDetector(sensorManager, listener) {
    
    private var linearAccelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private var chopCount = 0
    private var lastChopTime = 0L
    private val CHOP_TIMEOUT_MS = 800L // Stricter time window
    private val COOLDOWN_MS = 1500L
    
    override fun manageRegistration() {
        super.manageRegistration() // Register proximity
        if (isEnabled && linearAccelSensor != null) {
            // SENSOR_DELAY_GAME provides faster updates for better accuracy
            sensorManager.registerListener(this, linearAccelSensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            sensorManager.unregisterListener(this, linearAccelSensor)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        // 1. Handle Proximity Data (Sensor Fusion)
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            // If the value is less than the max range (usually 5cm), something is covering the phone
            val distance = event.values[0]
            isProximityCovered = distance < (event.sensor.maximumRange ?: 5f)
            return
        }

        // 2. Handle Motion Data
        if (!isEnabled || event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return

        // Stop processing immediately if the phone is in a pocket
        if (isProximityCovered) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Dynamic threshold based on sensitivity slider
        val threshold = 22f - (sensitivity / 10f) 

        // Calculate total acceleration without gravity (using LINEAR_ACCELERATION instead of ACCELEROMETER)
        val acceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        if (acceleration > threshold) {
            val now = System.currentTimeMillis()
            
            // Debounce: Ignore chaotic bouncing within 200ms
            if (now - lastChopTime > 200) {
                if (now - lastChopTime > CHOP_TIMEOUT_MS) {
                    chopCount = 1 // Reset if too much time passed between chops
                } else {
                    chopCount++
                }
                lastChopTime = now

                if (chopCount == 2) { 
                    val timeSinceLastTrigger = now - lastTriggerTime
                    if (timeSinceLastTrigger > COOLDOWN_MS) {
                        triggerGesture("DOUBLE_CHOP")
                        lastTriggerTime = now
                        chopCount = 0
                    }
                }
            }
        }
    }
    companion object { var lastTriggerTime = 0L }
}

/**
 * UPGRADED TWIST DETECTOR
 */
class TwistDetector(sensorManager: SensorManager, listener: GestureListener) : 
    BaseGestureDetector(sensorManager, listener) {

    private var gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private var lastTwistTime = 0L
    private var twistDirection = 0 
    private var twistCount = 0
    private val COOLDOWN_MS = 1500L

    override fun manageRegistration() {
        super.manageRegistration() // Register proximity
        if (isEnabled && gyroSensor != null) {
            sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            sensorManager.unregisterListener(this, gyroSensor)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        // 1. Handle Proximity Data
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values[0]
            isProximityCovered = distance < (event.sensor.maximumRange ?: 5f)
            return
        }

        if (!isEnabled || event.sensor.type != Sensor.TYPE_GYROSCOPE) return
        if (isProximityCovered) return

        val yRotation = event.values[1] 
        val threshold = 5f - (sensitivity / 25f) 

        if (abs(yRotation) > threshold) {
            val now = System.currentTimeMillis()
            val currentDirection = if (yRotation > 0) 1 else -1

            if (now - lastTwistTime > 250) {
                if (currentDirection != twistDirection) {
                    twistCount++
                } else {
                    twistCount = 1 
                }
                
                twistDirection = currentDirection
                lastTwistTime = now

                if (twistCount >= 2) {
                    val timeSinceTrigger = now - lastTriggerTime
                    if (timeSinceTrigger > COOLDOWN_MS) {
                        triggerGesture("TWIST")
                        lastTriggerTime = now
                        twistCount = 0
                    }
                }
            }
        }
    }
    companion object { var lastTriggerTime = 0L }
}

