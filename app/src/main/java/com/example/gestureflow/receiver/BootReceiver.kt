package com.example.gestureflow.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.gestureflow.service.GestureForegroundService
import com.example.gestureflow.data.SettingsManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settings = SettingsManager(context)
            // Synchronously read datastore in receiver
            val shouldStart = runBlocking { settings.isServiceEnabled.first() }
            
            if (shouldStart) {
                val serviceIntent = Intent(context, GestureForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}

