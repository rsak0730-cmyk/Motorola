package com.universalgestures.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.universalgestures.service.GestureForegroundService

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Results are reflected on next refresh. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            UniversalGesturesTheme {
                AppShell(
                    onStartMonitoring = { startMonitoring() },
                    onStopMonitoring = { stopMonitoring() },
                    onOpenBatterySettings = { openBatterySettings() },
                    onOpenNotificationPolicySettings = { openNotificationPolicySettings() },
                    onOpenAccessibilitySettings = { openAccessibilitySettings() },
                    onRequestPermissions = { requestRuntimePermissions() }
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    /** Called only from the user's Permissions control; camera is never requested silently. */
    private fun requestRuntimePermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    private fun startMonitoring() {
        GestureForegroundService.start(this)
    }

    private fun stopMonitoring() {
        GestureForegroundService.stop(this)
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        }
    }

    private fun openNotificationPolicySettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

private enum class Tab(val label: String) {
    HOME("Home"),
    GESTURES("Gestures"),
    STUDIO("Studio"),
    AUTOMATION("Automation"),
    DEVICES("Devices"),
    SETTINGS("Settings")
}

@Composable
private fun AppShell(
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenNotificationPolicySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val viewModel: AppViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableStateOf(Tab.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                painter = painterResource(id = tabIcon(tab)),
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                Tab.HOME -> HomeScreen(
                    state = state,
                    onStartMonitoring = {
                        viewModel.setMonitoringEnabled(true)
                        onStartMonitoring()
                    },
                    onStopMonitoring = {
                        viewModel.setMonitoringEnabled(false)
                        onStopMonitoring()
                    },
                    onOpenBatterySettings = onOpenBatterySettings,
                    onRequestPermissions = onRequestPermissions
                )
                Tab.GESTURES -> GesturesScreen(
                    state = state,
                    onUpdateConfig = viewModel::updateConfig
                )
                Tab.STUDIO -> StudioScreen(
                    state = state,
                    onUpdateConfig = viewModel::updateConfig,
                    onSetProfile = viewModel::setRecognitionProfile,
                    onAddCalibrationExample = viewModel::addCalibrationExample
                )
                Tab.AUTOMATION -> AutomationScreen(
                    state = state,
                    onSetAction = viewModel::setAction,
                    onOpenNotificationPolicySettings = onOpenNotificationPolicySettings,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings
                )
                Tab.DEVICES -> DevicesScreen(state = state)
                Tab.SETTINGS -> SettingsScreen(
                    state = state,
                    onSetVibration = viewModel::setVibrationFeedback,
                    onSetSound = viewModel::setSoundFeedback,
                    onSetSensitivity = viewModel::setGlobalSensitivity,
                    onSetAccuracy = viewModel::setAccuracyMode,
                    onSetProfile = viewModel::setRecognitionProfile,
                    onClearAllData = viewModel::clearAllData,
                    onImportConfig = viewModel::importConfig,
                    onExportConfig = { viewModel.exportConfig() },
                    onOpenBatterySettings = onOpenBatterySettings,
                    onOpenNotificationPolicySettings = onOpenNotificationPolicySettings,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onRequestPermissions = onRequestPermissions
                )
            }
        }
    }
}

private fun tabIcon(tab: Tab): Int {
    return when (tab) {
        Tab.HOME -> android.R.drawable.ic_menu_compass
        Tab.GESTURES -> android.R.drawable.ic_menu_edit
        Tab.STUDIO -> android.R.drawable.ic_menu_info_details
        Tab.AUTOMATION -> android.R.drawable.ic_menu_send
        Tab.DEVICES -> android.R.drawable.ic_menu_call
        Tab.SETTINGS -> android.R.drawable.ic_menu_preferences
    }
}
