package com.universalgestures.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.universalgestures.core.RecognitionProfile
import com.universalgestures.service.DiagnosticsHolder

/**
 * Settings: every control here is wired to a real preference or action.
 */
@Composable
fun SettingsScreen(
    state: UiState,
    onSetVibration: (Boolean) -> Unit,
    onSetSound: (Boolean) -> Unit,
    onSetSensitivity: (Int) -> Unit,
    onSetAccuracy: (String) -> Unit,
    onSetProfile: (RecognitionProfile) -> Unit,
    onClearAllData: () -> Unit,
    onImportConfig: (String) -> Unit,
    onExportConfig: () -> String,
    onOpenBatterySettings: () -> Unit,
    onOpenNotificationPolicySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    var importText by remember { mutableStateOf("") }
    var exportText by remember { mutableStateOf("") }
    var diagnosticsText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Feedback", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Vibration on trigger")
                    Switch(checked = state.vibrationFeedback, onCheckedChange = onSetVibration)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sound on trigger")
                    Switch(checked = state.soundFeedback, onCheckedChange = onSetSound)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Recognition", style = MaterialTheme.typography.titleMedium)
                Text("Global sensitivity: ${state.globalSensitivity}")
                Slider(
                    value = state.globalSensitivity.toFloat(),
                    onValueChange = { onSetSensitivity(it.toInt()) },
                    valueRange = 0f..100f
                )
                Text("Accuracy mode: ${state.accuracyMode}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("BALANCED", "HIGH_PRECISION", "HIGH_SENSITIVITY").forEach { mode ->
                        OutlinedButton(onClick = { onSetAccuracy(mode) }) { Text(mode) }
                    }
                }
                Text("Recognition profile: ${state.recognitionProfile.name}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecognitionProfile.values().forEach { profile ->
                        OutlinedButton(onClick = { onSetProfile(profile) }) { Text(profile.name) }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Permissions & battery", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Universal Gestures never enables accessibility, camera or notification policy access silently. " +
                        "Grant each one explicitly below.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = onRequestPermissions) { Text("Runtime permissions") }
                Button(onClick = onOpenBatterySettings) { Text("Battery optimisation exemption") }
                Button(onClick = onOpenNotificationPolicySettings) { Text("Notification policy access") }
                Button(onClick = onOpenAccessibilitySettings) { Text("Accessibility service") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Exports contain motion aggregates only — no personal data, no recordings.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = {
                        diagnosticsText = try {
                            DiagnosticsHolder.store.exportJson()
                        } catch (e: Exception) {
                            "Diagnostics not available yet."
                        }
                    }
                ) { Text("Export diagnostics JSON") }
                if (diagnosticsText.isNotEmpty()) {
                    Text(diagnosticsText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Configuration", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { exportText = onExportConfig() }) { Text("Export configuration") }
                if (exportText.isNotEmpty()) {
                    Text(exportText, style = MaterialTheme.typography.bodySmall)
                }
                TextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("Paste configuration JSON to import") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { onImportConfig(importText) }) { Text("Import configuration") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Privacy", style = MaterialTheme.typography.titleMedium)
                Text(
                    "All motion processing is local. No account, no cloud, no uploads. " +
                        "Camera-based gestures run only in the foreground when you activate them.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = onClearAllData) { Text("Delete all local data") }
            }
        }
    }
}
