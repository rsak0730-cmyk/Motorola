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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class GestureForegroundService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var isFlashOn = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
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
            .setContentTitle("GestureFlow Customizer Active")
            .setContentText("Listening for custom gestures...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val acceleration = sqrt((x * x + y * y + z * z).toDouble()) - 9.81

            // Simulated trigger mapping for demonstration of custom actions
            if (acceleration > 7.5) {
                triggerActionForGesture("chop")
            } else if (acceleration < -6.5) {
                triggerActionForGesture("twist")
            }
        }
    }

    private fun triggerActionForGesture(gestureType: String) {
        val prefs = getSharedPreferences("GesturePrefs", Context.MODE_PRIVATE)
        val actionIndex = if (gestureType == "chop") {
            prefs.getInt("chop_action", 0)
        } else {
            prefs.getInt("twist_action", 1)
        }

        handler.post {
            when (actionIndex) {
                0 -> toggleFlashlight()
                1 -> showToast("Gesture detected: $gestureType triggered!")
                else -> { /* Do nothing */ }
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

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
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
