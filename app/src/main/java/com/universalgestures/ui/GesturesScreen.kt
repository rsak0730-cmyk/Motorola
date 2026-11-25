package com.universalgestures.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
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
import com.universalgestures.core.ActionType
import com.universalgestures.core.CapabilityStatus
import com.universalgestures.core.GestureConfig
import com.universalgestures.core.GestureType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GesturesScreen(
    state: UiState,
    onUpdateConfig: (GestureConfig) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Gestures", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Every gesture shows its real capability on this device. Unsupported gestures explain why.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        items(GestureType.values().toList()) { type ->
            val config = state.configs[type] ?: GestureConfig(type)
            val capability = state.capabilities.firstOrNull { it.type == type }
            GestureCard(
                config = config,
                capabilityStatus = capability?.status,
                capabilityReason = capability?.reason ?: "",
                onUpdateConfig = onUpdateConfig
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GestureCard(
    config: GestureConfig,
    capabilityStatus: CapabilityStatus?,
    capabilityReason: String,
    onUpdateConfig: (GestureConfig) -> Unit
) {
    var actionExpanded by remember { mutableStateOf(false) }
    var targetText by remember(config.actionTarget) { mutableStateOf(config.actionTarget) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(config.type.name, style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = config.enabled,
                    enabled = capabilityStatus != CapabilityStatus.UNSUPPORTED,
                    onCheckedChange = { onUpdateConfig(config.copy(enabled = it)) }
                )
            }
            Text(
                "Status: ${capabilityStatus?.name ?: "UNKNOWN"}",
                style = MaterialTheme.typography.bodySmall
            )
            if (capabilityReason.isNotEmpty()) {
                Text(capabilityReason, style = MaterialTheme.typography.bodySmall)
            }
            Text("Action: ${config.action.name}")
            ExposedDropdownMenuBox(
                expanded = actionExpanded,
                onExpandedChange = { actionExpanded = !actionExpanded }
            ) {
                TextField(
                    value = config.action.name,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = actionExpanded,
                    onDismissRequest = { actionExpanded = false }
                ) {
                    ActionType.values().forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.name) },
                            onClick = {
                                onUpdateConfig(config.copy(action = action))
                                actionExpanded = false
                            }
                        )
                    }
                }
            }
            if (config.action == ActionType.OPEN_APP ||
                config.action == ActionType.OPEN_SHORTCUT ||
                config.action == ActionType.CUSTOM_SHORTCUT
            ) {
                TextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text("Package name / shortcut ID") },
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.TextButton(
                    onClick = { onUpdateConfig(config.copy(actionTarget = targetText.trim())) }
                ) { Text("Save target") }
            }
            Text("Sensitivity: ${config.sensitivity}")
            Slider(
                value = config.sensitivity.toFloat(),
                onValueChange = { onUpdateConfig(config.copy(sensitivity = it.toInt())) },
                valueRange = 0f..100f
            )
            Text("Timing window: ${config.timingMs} ms")
            Slider(
                value = config.timingMs.toFloat(),
                onValueChange = { onUpdateConfig(config.copy(timingMs = it.toInt())) },
                valueRange = 300f..3000f
            )
            Text(
                "Calibration: ${if (config.calibrated) "Personalised" else "Default profile"}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
