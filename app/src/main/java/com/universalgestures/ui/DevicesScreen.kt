package com.universalgestures.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.universalgestures.core.SensorInfo

/**
 * Devices: hardware capabilities, compatibility profiles and the
 * per-gesture capability report for this exact device.
 */
@Composable
fun DevicesScreen(state: UiState) {
    val context = LocalContext.current
    val sensors = remember { discoverSensors(context) }
    val profile = state.deviceProfile

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Devices", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("This device", style = MaterialTheme.typography.titleMedium)
                if (profile != null) {
                    Text("${profile.manufacturer} ${profile.model}")
                    Text("Android ${profile.androidVersion} · API ${profile.apiLevel}")
                    Text("Sensors: ${profile.sensorCount} · Flash: ${profile.hasFlash} · Camera: ${profile.hasCamera}")
                    Text("Battery optimisation exempt: ${profile.batteryOptimisationExempt}")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Manufacturer guidance", style = MaterialTheme.typography.titleMedium)
                Text(profile?.manufacturerGuidance ?: "Detecting…")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sensors (${sensors.size})", style = MaterialTheme.typography.titleMedium)
                sensors.forEach { sensor ->
                    Text(
                        "${sensorTypeName(sensor.type)} — ${sensor.name} (${sensor.vendor})",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Gesture capabilities", style = MaterialTheme.typography.titleMedium)
                state.capabilities.forEach { capability ->
                    Text(
                        "${capability.type.name}: ${capability.status.name}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(capability.reason, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun discoverSensors(context: Context): List<SensorInfo> {
    return try {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        manager.getSensorList(Sensor.TYPE_ALL).map {
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
    } catch (e: Exception) {
        emptyList()
    }
}

private fun sensorTypeName(type: Int): String = "Sensor type $type"
