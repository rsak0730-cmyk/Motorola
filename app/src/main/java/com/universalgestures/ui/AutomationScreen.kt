package com.universalgestures.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.universalgestures.core.ActionType
import com.universalgestures.core.GestureType

/**
 * Automation: assign an action to each gesture and manage the special
 * permissions that restricted actions need. Nothing here claims a
 * restricted action works without its permission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    state: UiState,
    onSetAction: (GestureType, ActionType, String) -> Unit,
    onOpenNotificationPolicySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Automation", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Assign what each gesture does. Restricted actions explain exactly what permission they need.",
                style = MaterialTheme.typography.bodyMedium
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Special permissions", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Do Not Disturb / ringer mode need notification policy access. " +
                            "Lock screen, screenshot and opening notifications need the accessibility service. " +
                            "Both require your explicit consent in system settings.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = onOpenNotificationPolicySettings) {
                        Text("Notification policy access")
                    }
                    Button(onClick = onOpenAccessibilitySettings) {
                        Text("Accessibility settings")
                    }
                }
            }
        }
        items(GestureType.values().toList()) { type ->
            val config = state.configs[type]
            var expanded by remember { mutableStateOf(false) }
            var target by remember(config?.actionTarget) { mutableStateOf(config?.actionTarget ?: "") }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(type.name, style = MaterialTheme.typography.titleMedium)
                    Text("Current action: ${config?.action?.name ?: "NONE"}")
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        TextField(
                            value = config?.action?.name ?: "NONE",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            ActionType.values().forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.name) },
                                    onClick = {
                                        onSetAction(type, action, target.trim())
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (config?.action == ActionType.OPEN_APP ||
                        config?.action == ActionType.OPEN_SHORTCUT ||
                        config?.action == ActionType.CUSTOM_SHORTCUT
                    ) {
                        TextField(
                            value = target,
                            onValueChange = { target = it },
                            label = { Text("Package name / shortcut ID") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(onClick = { onSetAction(type, config.action, target.trim()) }) {
                            Text("Save target")
                        }
                    }
                    Text(
                        actionRequirement(config?.action),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun actionRequirement(action: ActionType?): String {
    return when (action) {
        ActionType.FLASHLIGHT_TOGGLE -> "Needs a flash-capable camera. No permission required."
        ActionType.OPEN_CAMERA -> "Opens the system camera app. No permission required."
        ActionType.OPEN_APP -> "Needs the target package name (e.g. com.google.android.youtube)."
        ActionType.OPEN_SHORTCUT, ActionType.CUSTOM_SHORTCUT -> "Android does not expose a public API for third-party apps to invoke another app's pinned shortcut; use Launch application instead."
        ActionType.OPEN_SETTINGS -> "Opens system Settings. No permission required."
        ActionType.MEDIA_PLAY_PAUSE, ActionType.MEDIA_NEXT, ActionType.MEDIA_PREVIOUS -> "Uses MediaSession key events. No permission required."
        ActionType.VOLUME_UP, ActionType.VOLUME_DOWN -> "Adjusts media volume. No permission required."
        ActionType.SCREENSHOT -> "Requires the Universal Gestures accessibility service (explicit opt-in)."
        ActionType.LOCK_SCREEN -> "Requires the Universal Gestures accessibility service (explicit opt-in)."
        ActionType.OPEN_NOTIFICATIONS -> "Requires the Universal Gestures accessibility service (explicit opt-in)."
        ActionType.DND_TOGGLE -> "Requires notification policy access (explicit opt-in)."
        ActionType.RINGER_TOGGLE -> "Requires notification policy access on this Android version."
        else -> "No requirement."
    }
}
