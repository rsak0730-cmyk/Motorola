package com.universalgestures.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.universalgestures.core.CapabilityStatus

@Composable
fun HomeScreen(
    state: UiState,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Universal Gestures", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Motion control for your phone — chop, twist, shake and more.",
            style = MaterialTheme.typography.bodyMedium
        )

        // Monitoring status
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (state.monitoringEnabled) "Monitoring: Active" else "Monitoring: Off",
                    style = MaterialTheme.typography.titleMedium
                )
                val activeCount = state.configs.values.count { it.enabled }
                Text("Active gestures: $activeCount")
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.monitoringEnabled) {
                        OutlinedButton(onClick = onStopMonitoring) { Text("Stop") }
                    } else {
                        Button(onClick = onStartMonitoring) { Text("Start monitoring") }
                    }
                    OutlinedButton(onClick = onRequestPermissions) { Text("Permissions") }
                    OutlinedButton(onClick = onOpenBatterySettings) { Text("Battery") }
                }
            }
        }

        // Device compatibility summary
        val profile = state.deviceProfile
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Device compatibility", style = MaterialTheme.typography.titleMedium)
                if (profile != null) {
                    Text("${profile.manufacturer} ${profile.model}")
                    Text("Android ${profile.androidVersion} (API ${profile.apiLevel})")
                    Text("Sensors detected: ${profile.sensorCount}")
                    Text("Flash: ${if (profile.hasFlash) "Yes" else "No"} · Camera: ${if (profile.hasCamera) "Yes" else "No"}")
                    Text("Battery optimisation exempt: ${if (profile.batteryOptimisationExempt) "Yes" else "No"}")
                } else {
                    Text("Detecting device…")
                }
                val supported = state.capabilities.count {
                    it.status == CapabilityStatus.FULLY_SUPPORTED ||
                        it.status == CapabilityStatus.SUPPORTED_WITH_LIMITATIONS ||
                        it.status == CapabilityStatus.REQUIRES_PERMISSION
                }
                Text("Gestures usable on this device: $supported / ${state.capabilities.size}")
            }
        }

        // Sensor health
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sensor health", style = MaterialTheme.typography.titleMedium)
                Text("Open the Devices tab for the full sensor list and per-gesture capability report.")
            }
        }

        // Recent activations
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Recent activations", style = MaterialTheme.typography.titleMedium)
                if (state.recentEvents.isEmpty()) {
                    Text("No activations yet. Enable a gesture and try it.")
                } else {
                    state.recentEvents.takeLast(5).reversed().forEach { event ->
                        Text("${event.type.name} · confidence ${(event.confidence * 100).toInt()}%")
                    }
                }
            }
        }

        if (state.statusMessage.isNotEmpty()) {
            Text(state.statusMessage, style = MaterialTheme.typography.bodySmall)
        }
    }
}
