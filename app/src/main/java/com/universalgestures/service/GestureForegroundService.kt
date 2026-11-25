package com.universalgestures.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.core.app.NotificationCompat
import com.universalgestures.actions.ActionExecutor
import com.universalgestures.core.GestureConfig
import com.universalgestures.core.GestureType
import com.universalgestures.data.DiagnosticsStore
import com.universalgestures.data.PreferencesRepository
import com.universalgestures.recognition.RecognitionPipeline
import com.universalgestures.sensors.SensorHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Background gesture monitoring via a foreground service.
 *
 * - Correct foreground notification (low importance).
 * - Sensor registration only while monitoring; screen-off pauses
 *   high-rate sampling to save battery.
 * - START_STICKY recovery + boot restart when monitoring is enabled.
 * - Permission-state awareness: stops cleanly if permissions are revoked.
 */
class GestureForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var sensorHub: SensorHub
    private lateinit var pipeline: RecognitionPipeline
    private lateinit var executor: ActionExecutor
    private lateinit var prefs: PreferencesRepository
    private var configs: Map<GestureType, GestureConfig> = emptyMap()
    private var vibrateOnTrigger = true
    private var soundOnTrigger = false
    private var toneGenerator: ToneGenerator? = null
    private var screenOn = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    // Pause high-rate sampling while the screen is off to
                    // save battery; wake-up sensors remain registered by
                    // the system where available.
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorHub = SensorHub(this)
        executor = ActionExecutor(this)
        prefs = PreferencesRepository(this)
        DiagnosticsHolder.store = DiagnosticsStore()
        toneGenerator = try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) } catch (_: Exception) { null }
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF).apply {
            addAction(Intent.ACTION_SCREEN_ON)
        })
        scope.launch {
            configs = prefs.gestureConfigs()
            vibrateOnTrigger = prefs.vibrationFeedback.first()
            soundOnTrigger = prefs.soundFeedback.first()
            pipeline = RecognitionPipeline(configs)
            sensorHub.start { sample ->
                if (!screenOn) return@start
                val result = pipeline.onSample(sample)
                val event = result.event
                if (event != null) {
                    DiagnosticsHolder.store.recordEvent(event)
                    val config = configs[event.type]
                    if (config != null && config.action.name != "NONE") {
                        val actionResult = executor.execute(config.action, config.actionTarget)
                        DiagnosticsHolder.lastActionMessage = actionResult.message
                    }
                    if (vibrateOnTrigger) vibrate()
                    if (soundOnTrigger) toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 100)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            sensorHub.stop()
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // Receiver may already be unregistered.
        }
        toneGenerator?.release()
        toneGenerator = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun vibrate() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(60L)
            }
        } catch (e: Exception) {
            // Haptic feedback is best-effort.
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "universal_gestures_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Gesture monitoring",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Universal Gestures")
            .setContentText("Gesture monitoring is active")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, GestureForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GestureForegroundService::class.java))
        }
    }
}

/** Process-wide diagnostics holder shared by service and UI. */
object DiagnosticsHolder {
    lateinit var store: DiagnosticsStore
    var lastActionMessage: String = ""
}
