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
import android.os.Handler
import android.os.HandlerThread
import android.os.VibrationEffect
import android.os.Vibrator
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.core.app.NotificationCompat
import com.universalgestures.actions.ActionExecutor
import com.universalgestures.core.GestureConfig
import com.universalgestures.core.GestureType
import com.universalgestures.core.RecognitionProfile
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
 * - Sensor registration only while monitoring; screen-off reduces the
 *   sensor rate from 50 Hz to 20 Hz to reduce steady-state work.
 * - START_STICKY recovery + best-effort boot restart when monitoring is enabled;
 *   Android and OEM process policies may still stop a service.
 * - Restricted actions check their permission at execution time and fail closed.
 */
class GestureForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var sensorHub: SensorHub
    private lateinit var sensorThread: HandlerThread
    private lateinit var sensorHandler: Handler
    @Volatile private lateinit var pipeline: RecognitionPipeline
    private lateinit var executor: ActionExecutor
    private lateinit var prefs: PreferencesRepository
    private var configs: Map<GestureType, GestureConfig> = emptyMap()
    private var vibrateOnTrigger = true
    private var soundOnTrigger = false
    private var profile = RecognitionProfile.DEFAULT
    private var globalSensitivity = 50
    private var accuracyMode = "BALANCED"
    private var toneGenerator: ToneGenerator? = null
    private var screenOn = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    restartSensorsForDisplayState()
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    restartSensorsForDisplayState()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorHub = SensorHub(this)
        sensorThread = HandlerThread("UG-Monitor-Sensors").also { it.start() }
        sensorHandler = Handler(sensorThread.looper)
        executor = ActionExecutor(this)
        prefs = PreferencesRepository(this)
        DiagnosticsHolder.store = DiagnosticsStore()
        toneGenerator = try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) } catch (_: Exception) { null }
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF).apply {
            addAction(Intent.ACTION_SCREEN_ON)
        })
        scope.launch { refreshEngine() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (intent?.action == ACTION_REFRESH_ENGINE) scope.launch { refreshEngine() }
        return START_STICKY
    }

    private suspend fun refreshEngine() {
        configs = prefs.gestureConfigs()
        vibrateOnTrigger = prefs.vibrationFeedback.first()
        soundOnTrigger = prefs.soundFeedback.first()
        profile = prefs.recognitionProfile.first()
        globalSensitivity = prefs.globalSensitivity.first()
        accuracyMode = prefs.accuracyMode.first()
        val calibration = com.universalgestures.recognition.CalibrationStore.fromJson(prefs.calibrationJson())
        pipeline = RecognitionPipeline(configs, calibration, profile = profile,
            globalSensitivity = globalSensitivity, accuracyMode = accuracyMode)
        startSensors()
    }

    private fun startSensors() {
        sensorHub.start(
            listener = ::processSample,
            samplingPeriodUs = if (screenOn) 20_000 else 50_000,
            callbackHandler = sensorHandler
        )
    }

    private fun restartSensorsForDisplayState() {
        if (::pipeline.isInitialized) startSensors()
    }

    private fun processSample(sample: com.universalgestures.core.SensorSample) {
        val result = pipeline.onSample(sample)
        val event = result.event ?: return
        DiagnosticsHolder.store.recordEvent(event)
        val config = configs[event.type]
        if (config != null && config.action.name != "NONE") {
            val actionResult = executor.execute(config.action, config.actionTarget)
            DiagnosticsHolder.lastActionMessage = actionResult.message
        }
        if (vibrateOnTrigger) vibrate()
        if (soundOnTrigger) toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 100)
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
        if (::sensorThread.isInitialized) sensorThread.quitSafely()
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
        const val ACTION_REFRESH_ENGINE = "com.universalgestures.action.REFRESH_ENGINE"
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
