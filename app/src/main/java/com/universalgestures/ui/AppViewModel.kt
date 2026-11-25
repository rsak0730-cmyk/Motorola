package com.universalgestures.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.universalgestures.core.ActionType
import com.universalgestures.core.DeviceProfile
import com.universalgestures.core.GestureCapability
import com.universalgestures.core.GestureConfig
import com.universalgestures.core.GestureEvent
import com.universalgestures.core.GestureType
import com.universalgestures.core.RecognitionProfile
import com.universalgestures.core.Vec3
import com.universalgestures.recognition.CalibrationStore
import com.universalgestures.data.PreferencesRepository
import com.universalgestures.device.DeviceCompatibilityEngine
import com.universalgestures.service.DiagnosticsHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val configs: Map<GestureType, GestureConfig> = PreferencesRepository.defaultConfigs(),
    val capabilities: List<GestureCapability> = emptyList(),
    val deviceProfile: DeviceProfile? = null,
    val monitoringEnabled: Boolean = false,
    val vibrationFeedback: Boolean = true,
    val soundFeedback: Boolean = false,
    val globalSensitivity: Int = 50,
    val accuracyMode: String = "BALANCED",
    val recognitionProfile: RecognitionProfile = RecognitionProfile.DEFAULT,
    val calibrationJson: String = "{}",
    val recentEvents: List<GestureEvent> = emptyList(),
    val statusMessage: String = ""
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesRepository(application)
    private val deviceEngine = DeviceCompatibilityEngine(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            prefs.monitoringEnabled.collect { enabled ->
                _state.value = _state.value.copy(monitoringEnabled = enabled)
            }
        }
        viewModelScope.launch {
            prefs.vibrationFeedback.collect { _state.value = _state.value.copy(vibrationFeedback = it) }
        }
        viewModelScope.launch {
            prefs.soundFeedback.collect { _state.value = _state.value.copy(soundFeedback = it) }
        }
        viewModelScope.launch {
            prefs.globalSensitivity.collect { _state.value = _state.value.copy(globalSensitivity = it) }
        }
        viewModelScope.launch {
            prefs.accuracyMode.collect { _state.value = _state.value.copy(accuracyMode = it) }
        }
        viewModelScope.launch {
            prefs.recognitionProfile.collect { _state.value = _state.value.copy(recognitionProfile = it) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val configs = prefs.gestureConfigs()
            val calibrationJson = prefs.calibrationJson()
            val capabilities = deviceEngine.evaluateGestures()
            val profile = deviceEngine.collectProfile()
            val events = try {
                DiagnosticsHolder.store.recentEvents()
            } catch (e: Exception) {
                emptyList()
            }
            _state.value = _state.value.copy(
                configs = configs,
                calibrationJson = calibrationJson,
                capabilities = capabilities,
                deviceProfile = profile,
                recentEvents = events
            )
        }
    }

    fun updateConfig(updated: GestureConfig) {
        viewModelScope.launch {
            val current = _state.value.configs.toMutableMap()
            current[updated.type] = updated
            prefs.saveGestureConfigs(current)
            _state.value = _state.value.copy(configs = current)
            refreshRunningService()
        }
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setMonitoringEnabled(enabled)
            _state.value = _state.value.copy(monitoringEnabled = enabled)
        }
    }

    fun setVibrationFeedback(enabled: Boolean) {
        viewModelScope.launch { prefs.setVibrationFeedback(enabled); refreshRunningService() }
    }

    fun setSoundFeedback(enabled: Boolean) {
        viewModelScope.launch { prefs.setSoundFeedback(enabled); refreshRunningService() }
    }

    fun setGlobalSensitivity(value: Int) {
        viewModelScope.launch { prefs.setGlobalSensitivity(value); refreshRunningService() }
    }

    fun setAccuracyMode(mode: String) {
        viewModelScope.launch { prefs.setAccuracyMode(mode); refreshRunningService() }
    }

    fun setRecognitionProfile(profile: RecognitionProfile) {
        viewModelScope.launch { prefs.setRecognitionProfile(profile); refreshRunningService() }
    }

    private fun refreshRunningService() {
        if (!_state.value.monitoringEnabled) return
        try {
            val intent = Intent(getApplication(), com.universalgestures.service.GestureForegroundService::class.java)
                .setAction(com.universalgestures.service.GestureForegroundService.ACTION_REFRESH_ENGINE)
            getApplication<Application>().startService(intent)
        } catch (_: Exception) {
            // System may have stopped the service; the Home control can start it again.
        }
    }

    fun addCalibrationExample(
        type: GestureType,
        positive: Boolean,
        features: List<Vec3>,
        motionFeatures: List<com.universalgestures.recognition.MotionFeature>
    ) {
        if (features.isEmpty() && motionFeatures.isEmpty()) return
        viewModelScope.launch {
            val json = prefs.addCalibrationExample(type, positive, features, motionFeatures)
            val store = CalibrationStore.fromJson(json)
            val updated = _state.value.configs[type]?.copy(calibrated = store.isCalibrated(type))
            if (updated != null) updateConfig(updated)
            _state.value = _state.value.copy(calibrationJson = store.toJson())
        }
    }

    fun setAction(type: GestureType, action: ActionType, target: String = "") {
        val current = _state.value.configs[type] ?: return
        updateConfig(current.copy(action = action, actionTarget = target))
    }

    fun exportConfig(): String {
        return try {
            kotlinx.coroutines.runBlocking { prefs.exportConfig() }
        } catch (e: Exception) {
            "{}"
        }
    }

    fun importConfig(json: String) {
        viewModelScope.launch {
            prefs.importConfig(json)
            refresh()
            _state.value = _state.value.copy(statusMessage = "Configuration imported")
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            try { getApplication<Application>().stopService(Intent(getApplication(), com.universalgestures.service.GestureForegroundService::class.java)) } catch (_: Exception) { }
            prefs.clearAll()
            try {
                DiagnosticsHolder.store.clear()
            } catch (e: Exception) {
                // Store may not be initialised yet.
            }
            refresh()
            _state.value = _state.value.copy(monitoringEnabled = false, statusMessage = "All local configuration, calibration and diagnostics deleted; monitoring stopped")
        }
    }

    fun setStatusMessage(message: String) {
        _state.value = _state.value.copy(statusMessage = message)
    }
}
