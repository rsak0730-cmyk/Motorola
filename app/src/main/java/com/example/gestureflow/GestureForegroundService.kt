package com.example.gestureflow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class GestureForegroundService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var classifier: GestureClassifier
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var isFlashOn = false

    private val accelBuffer = FloatArray(50)
    private var bufferIndex = 0

    override fun onCreate() {
        super.onCreate()
        classifier = GestureClassifier(assets)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager.cameraIdList.getOrNull(0)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        startForeground(1, createNotification())
        
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun createNotification(): Notification {
        val channelId = "gesture_service_channel"
        val channel = NotificationChannel(channelId, "Gesture Service", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("GestureFlow AI Active")
            .setContentText("Listening for gestures to toggle flashlight...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Calculate total acceleration force magnitude
            val acceleration = sqrt((x * x + y * y + z * z).toDouble()) - 9.81

            // Simple robust motion trigger for quick chop/shake gestures
            if (acceleration > 7.0) { // Sharp movement detected
                toggleFlashlight()
            }
        }
    }

    private fun toggleFlashlight() {
        cameraId?.let { id ->
            try {
                isFlashOn = !isFlashOn
                cameraManager.setTorchMode(id, isFlashOn)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        try {
            cameraId?.let { cameraManager.setTorchMode(it, false) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
