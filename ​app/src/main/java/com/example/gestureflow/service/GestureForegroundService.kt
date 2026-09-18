package com.example.gestureflow.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.gestureflow.MainActivity
import com.example.gestureflow.actions.ActionExecutor
import com.example.gestureflow.data.SettingsManager
import com.example.gestureflow.gesture.DoubleChopDetector
import com.example.gestureflow.gesture.GestureListener
import com.example.gestureflow.gesture.TwistDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class GestureForegroundService : Service(), GestureListener {

    private val CHANNEL_ID = "GestureFlowServiceChannel"
    private lateinit var sensorManager: SensorManager
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var settingsManager: SettingsManager
    
    private lateinit var chopDetector: DoubleChopDetector
    private lateinit var twistDetector: TwistDetector

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        actionExecutor = ActionExecutor(this)
        settingsManager = SettingsManager(this)

        chopDetector = DoubleChopDetector(sensorManager, this)
        twistDetector = TwistDetector(sensorManager, this)

        createNotificationChannel()
        startForeground(1, createNotification())

        observeSettings()
    }

    private fun observeSettings() {
        serviceScope.launch {
            combine(
                settingsManager.isChopEnabled,
                settingsManager.chopSensitivity
            ) { enabled, sensitivity -> Pair(enabled, sensitivity) }
            .collect { (enabled, sens) ->
                chopDetector.updateSettings(enabled, sens)
            }
        }
        serviceScope.launch {
            settingsManager.isTwistEnabled.collect { enabled ->
                twistDetector.updateSettings(enabled, 50)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE") {
            serviceScope.launch { settingsManager.setServiceEnabled(false) }
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onGestureDetected(gestureName: String) {
        // Map gesture to action (In a full app, this mapping comes from DataStore)
        when (gestureName) {
            "DOUBLE_CHOP" -> actionExecutor.executeAction("TOGGLE_FLASHLIGHT")
            "TWIST" -> actionExecutor.executeAction("OPEN_CAMERA")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        chopDetector.updateSettings(false)
        twistDetector.updateSettings(false)
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, GestureForegroundService::class.java).apply { action = "STOP_SERVICE" }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GestureFlow is Active")
            .setContentText("Listening for motion gestures in the background.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Gesture Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

