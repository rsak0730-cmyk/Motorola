package com.universalgestures.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.universalgestures.core.GestureConfig
import com.universalgestures.core.GestureType
import com.universalgestures.core.RecognitionProfile
import com.universalgestures.core.SensorSample
import com.universalgestures.core.Vec3
import com.universalgestures.recognition.CalibrationStore
import com.universalgestures.recognition.RecognitionPipeline
import com.universalgestures.recognition.DtwMatcher
import com.universalgestures.sensors.SensorHub
import kotlin.math.sqrt

/** On-device sensor laboratory and in-memory positive/negative calibration. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    state: UiState,
    onUpdateConfig: (GestureConfig) -> Unit,
    onSetProfile: (RecognitionProfile) -> Unit,
    onAddCalibrationExample: (GestureType, Boolean, List<Vec3>) -> Unit
) {
    val context = LocalContext.current
    var recording by remember { mutableStateOf(false) }
    var liveText by remember { mutableStateOf("Lab idle. Start recording to see live sensor data.") }
    var selectedGesture by remember { mutableStateOf(GestureType.DOUBLE_CHOP) }
    var expanded by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var cameraActive by remember { mutableStateOf(false) }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var cameraMessage by remember { mutableStateOf("Camera analysis is off.") }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        cameraActive = granted
        cameraMessage = if (granted) "Camera analysis enabled while this screen is visible." else "Camera permission denied; camera analysis remains off."
    }
    val calibration = remember(state.calibrationJson) { CalibrationStore.fromJson(state.calibrationJson) }
    val samples = remember { mutableListOf<SensorSample>() }
    val replay = remember { mutableListOf<SensorSample>() }
    val hub = remember { mutableStateOf<SensorHub?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            hub.value?.stop()
            hub.value = null
            samples.clear()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Gesture Studio", style = MaterialTheme.typography.headlineMedium)
        Text("Live sensor diagnostics and session calibration. Motion data remains on this device.")

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Recognition profile: ${state.recognitionProfile.name}", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RecognitionProfile.values().forEach { profile ->
                        OutlinedButton(onClick = { onSetProfile(profile) }) { Text(profile.name.replace('_', ' ')) }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Live sensor lab", style = MaterialTheme.typography.titleMedium)
                Text(liveText, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (!recording) {
                            samples.clear()
                            val sensorHub = SensorHub(context.applicationContext)
                            hub.value = sensorHub
                            recording = true
                            liveText = "Waiting for sensor samples…"
                            sensorHub.start { sample ->
                                samples.add(sample)
                                if (samples.size > 5000) samples.removeAt(0)
                                liveText = buildString {
                                    appendLine("Accel: ${vec(sample.accel)}")
                                    appendLine("Gyro: ${vec(sample.gyro)}")
                                    appendLine("Gravity: ${vec(sample.gravity)}")
                                    appendLine("Linear accel: ${vec(sample.linearAccel)}")
                                    appendLine("Proximity: ${sample.proximityCm?.let { "${it} cm" } ?: "not available"}")
                                    appendLine("Samples: ${samples.size} · Sensor rate: ${"%.1f".format(sensorHub.samplingHz)} Hz")
                                }
                            }
                        } else {
                            hub.value?.stop()
                            hub.value = null
                            recording = false
                            liveText += "\nRecording stopped. ${samples.size} samples retained for this session."
                        }
                    }) { Text(if (recording) "Stop recording" else "Start recording") }
                    OutlinedButton(onClick = {
                        replay.clear()
                        replay.addAll(samples)
                        liveText = if (replay.isEmpty()) "No samples to replay. Record a motion first." else
                            "Replay saved in this session: ${replay.size} samples. Record another attempt, then Compare."
                    }) { Text("Save replay") }
                    OutlinedButton(onClick = {
                        val replayPipeline = RecognitionPipeline(
                            configs = state.configs,
                            calibration = CalibrationStore.fromJson(state.calibrationJson),
                            profile = state.recognitionProfile,
                            globalSensitivity = state.globalSensitivity,
                            accuracyMode = state.accuracyMode
                        )
                        var accepted = 0
                        var lastReason = "none"
                        replay.forEach { sample ->
                            val result = replayPipeline.onSample(sample)
                            if (result.event != null) accepted++
                            if (result.rejectionReason != com.universalgestures.core.RejectionReason.NONE) lastReason = result.rejectionReason.name
                        }
                        liveText = "Replay: ${replay.size} samples · accepted events=$accepted · final rejection=$lastReason"
                    }) { Text("Run replay") }
                    OutlinedButton(onClick = { liveText = compareSamples(samples, replay) }) { Text("Compare") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Optional foreground hand-motion", style = MaterialTheme.typography.titleMedium)
                Text(cameraMessage, style = MaterialTheme.typography.bodySmall)
                Text("Only frame-to-frame movement is detected. No frames are saved. This is not hand pose recognition.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (!cameraGranted) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        else cameraActive = !cameraActive
                    }) { Text(if (cameraActive) "Stop camera" else if (cameraGranted) "Start camera" else "Grant camera permission") }
                }
                if (cameraActive && cameraGranted) {
                    ForegroundHandMotionPreview { message ->
                        cameraMessage = message
                        if (message.startsWith("Motion detected") && state.configs[GestureType.HAND_MOTION]?.enabled == true) {
                            val config = state.configs[GestureType.HAND_MOTION]
                            if (config != null && config.action != com.universalgestures.core.ActionType.NONE) {
                                val result = com.universalgestures.actions.ActionExecutor(context).execute(config.action, config.actionTarget)
                                cameraMessage = "$message Action: ${result.message}"
                            }
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Personal calibration", style = MaterialTheme.typography.titleMedium)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    TextField(
                        value = selectedGesture.name.replace('_', ' '),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        GestureType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name.replace('_', ' ')) },
                                onClick = { selectedGesture = type; expanded = false }
                            )
                        }
                    }
                }
                Text(
                    "Record a gesture attempt, then add it as a positive sample. Record ordinary handling separately as a negative sample. Calibration samples are stored locally in DataStore and remain on this device.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val features = DtwMatcher.featureSequence(samples.takeLast(120))
                        if (features.isEmpty()) note = "Record sensor data before adding an example."
                        else {
                            calibration.addPositive(selectedGesture, features)
                            onAddCalibrationExample(selectedGesture, true, features)
                            note = "Positive example saved locally (${calibration.profileFor(selectedGesture).positives.size})."
                        }
                    }) { Text("Add positive") }
                    OutlinedButton(onClick = {
                        val features = DtwMatcher.featureSequence(samples.takeLast(120))
                        if (features.isEmpty()) note = "Record sensor data before adding an example."
                        else {
                            calibration.addNegative(selectedGesture, features)
                            onAddCalibrationExample(selectedGesture, false, features)
                            note = "Negative example saved locally (${calibration.profileFor(selectedGesture).negatives.size})."
                        }
                    }) { Text("Add negative") }
                }
                Text("Positive ${calibration.profileFor(selectedGesture).positives.size} · Negative ${calibration.profileFor(selectedGesture).negatives.size}")
                if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    val profile = calibration.profileFor(selectedGesture)
                    val config = state.configs[selectedGesture]
                    if (config == null || profile.positives.isEmpty()) {
                        note = "Add at least one positive sample before saving calibration."
                    } else {
                        onUpdateConfig(config.copy(calibrated = profile.calibrated))
                        note = if (profile.calibrated) "Persistent calibration saved and applied to the live recognizer." else
                            "Baseline status saved; add at least two positive examples for a calibrated profile."
                    }
                }) { Text("Apply calibration status") }
            }
        }
    }
}

private fun vec(v: com.universalgestures.core.Vec3): String =
    "(${"%.2f".format(v.x)}, ${"%.2f".format(v.y)}, ${"%.2f".format(v.z)})"

private fun compareSamples(current: List<SensorSample>, previous: List<SensorSample>): String {
    if (current.isEmpty() || previous.isEmpty()) return "Record one attempt, load replay, record another attempt, then compare."
    val a = DtwMatcher.featureSequence(current.takeLast(240))
    val b = DtwMatcher.featureSequence(previous.takeLast(240))
    val distance = DtwMatcher.distance(a, b)
    val peakA = current.maxOfOrNull { it.linearAccel.magnitude } ?: 0f
    val peakB = previous.maxOfOrNull { it.linearAccel.magnitude } ?: 0f
    return "DTW distance ${"%.3f".format(distance)} · peak acceleration ${"%.2f".format(peakA)} / ${"%.2f".format(peakB)} m/s² · samples ${current.size} / ${previous.size}"
}
