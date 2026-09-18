package com.example.gestureflow

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gestureflow.data.SettingsManager
import com.example.gestureflow.service.GestureForegroundService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle denied permissions gracefully in a full flow.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request base permissions
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GestureAppScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureAppScreen() {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()

    val isServiceEnabled by settingsManager.isServiceEnabled.collectAsState(initial = false)
    val isChopEnabled by settingsManager.isChopEnabled.collectAsState(initial = true)
    val isTwistEnabled by settingsManager.isTwistEnabled.collectAsState(initial = true)
    val chopSensitivity by settingsManager.chopSensitivity.collectAsState(initial = 50)

    // Side effect to manage service state
    LaunchedEffect(isServiceEnabled) {
        val intent = Intent(context, GestureForegroundService::class.java)
        if (isServiceEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("GestureFlow", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Master Toggle
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isServiceEnabled) MaterialTheme.colorScheme.primaryContainer 
                                         else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Engine Status", style = MaterialTheme.typography.titleMedium)
                            Text(if (isServiceEnabled) "Listening in background" else "Disabled", 
                                 style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(
                            checked = isServiceEnabled,
                            onCheckedChange = { scope.launch { settingsManager.setServiceEnabled(it) } }
                        )
                    }
                }
            }

            // Battery Optimization Warning
            item {
                OutlinedButton(
                    onClick = { 
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Exclude from Battery Optimization (Recommended)")
                }
            }

            // Double Chop
            item {
                GestureCard(
                    title = "Double Chop",
                    description = "Chop phone twice to toggle flashlight.",
                    enabled = isChopEnabled,
                    onEnabledChange = { scope.launch { settingsManager.setChopEnabled(it) } },
                    sensitivity = chopSensitivity,
                    onSensitivityChange = { scope.launch { settingsManager.setChopSensitivity(it.toInt()) } }
                )
            }

            // Twist
            item {
                GestureCard(
                    title = "Quick Twist",
                    description = "Twist wrist twice to open camera.",
                    enabled = isTwistEnabled,
                    onEnabledChange = { /* Bind to datastore setter */ },
                    sensitivity = 50,
                    onSensitivityChange = { }
                )
            }
        }
    }
}

@Composable
fun GestureCard(
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    sensitivity: Int,
    onSensitivityChange: (Float) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(description, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (enabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Sensitivity", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = sensitivity.toFloat(),
                    onValueChange = onSensitivityChange,
                    valueRange = 0f..100f
                )
            }
        }
    }
}

