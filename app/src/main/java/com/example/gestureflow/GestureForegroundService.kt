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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.sqrt

class GestureForegroundService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var isFlashOn = false
    private val handler = Handler(Looper.getMainLooper())
    private var lastTrigger: Long = 0

    override fun onCreate() {
        super.onCreate()
        
        // Start foreground notification immediately to avoid Android crash exceptions
        startForeground(1, createNotification())

        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }

            cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraId = cameraManager?.cameraIdList?.getOrNull(0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotification(): Notification {
        val channelId = "moto_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Moto Actions Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Moto Actions Running")
            .setContentText("Active background gesture recognizer...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val now = System.currentTimeMillis()
            if (now - lastTrigger < 1500) return

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val gForce = sqrt((x * x + y * y + z * z).toDouble()) / 9.81

            val prefs = getSharedPreferences("MotoPrefs", Context.MODE_PRIVATE)

            if (gForce > 2.7 && prefs.getInt("chop", 0) == 0) {
                lastTrigger = now
                handler.post { toggleFlashlight() }
            } else if (abs(x) > 8.5 && abs(y) < 3.0 && prefs.getInt("twist", 0) == 0) {
                lastTrigger = now
                handler.post { openCamera() }
            } else if (z < -8.5 && prefs.getInt("flip", 0) == 0) {
                lastTrigger = now
                handler.post { showToast("Phone placed face down") }
            } else if (gForce > 2.0 && abs(z) < 3.0 && prefs.getInt("shake", 0) == 0) {
                lastTrigger = now
                handler.post { openApp("com.whatsapp") }
            }
        }
    }

    private fun toggleFlashlight() {
        try {
            cameraId?.let { id ->
                isFlashOn = !isFlashOn
                cameraManager?.setTorchMode(id, isFlashOn)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openCamera() {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            showToast("Camera launch failed")
        }
    }

    private fun openApp(pkg: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                showToast("WhatsApp not installed")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            sensorManager.unregisterListener(this)
            cameraId?.let { cameraManager?.setTorchMode(it, false) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
