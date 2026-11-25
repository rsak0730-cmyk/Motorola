package com.universalgestures.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.universalgestures.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/** Restarts monitoring after reboot when the user enabled it. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val enabled = PreferencesRepository(appContext).monitoringEnabled.firstOrNull() ?: false
            if (enabled) {
                try {
                    GestureForegroundService.start(appContext)
                } catch (_: Exception) {
                    // Android/OEM background-start policy can refuse a boot-time FGS.
                    // Monitoring remains off until the user opens the app again.
                }
            }
        }
    }
}
