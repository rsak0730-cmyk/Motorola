package com.universalgestures.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.universalgestures.core.ActionType
import com.universalgestures.core.GestureConfig
import com.universalgestures.core.GestureType
import com.universalgestures.core.RecognitionProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "universal_gestures")

/**
 * Persistent configuration via DataStore. Gesture configs are stored as
 * JSON so they can be exported/imported without personal data.
 */
class PreferencesRepository(private val context: Context) {

    private val keyConfigs = stringPreferencesKey("gesture_configs_json")
    private val keyMonitoring = booleanPreferencesKey("monitoring_enabled")
    private val keyVibration = booleanPreferencesKey("vibration_feedback")
    private val keySound = booleanPreferencesKey("sound_feedback")
    private val keyProfile = stringPreferencesKey("recognition_profile")
    private val keySensitivity = intPreferencesKey("global_sensitivity")
    private val keyAccuracy = stringPreferencesKey("accuracy_mode")
    private val keyCalibration = stringPreferencesKey("calibration_profiles_json")

    val monitoringEnabled: Flow<Boolean> = context.dataStore.data.map { it[keyMonitoring] ?: false }
    val vibrationFeedback: Flow<Boolean> = context.dataStore.data.map { it[keyVibration] ?: true }
    val soundFeedback: Flow<Boolean> = context.dataStore.data.map { it[keySound] ?: false }
    val globalSensitivity: Flow<Int> = context.dataStore.data.map { it[keySensitivity] ?: 50 }
    val accuracyMode: Flow<String> = context.dataStore.data.map { it[keyAccuracy] ?: "BALANCED" }
    val recognitionProfile: Flow<RecognitionProfile> = context.dataStore.data.map {
        RecognitionProfile.values().firstOrNull { p -> p.name == it[keyProfile] } ?: RecognitionProfile.DEFAULT
    }

    suspend fun gestureConfigs(): Map<GestureType, GestureConfig> {
        val json = context.dataStore.data.first()[keyConfigs] ?: return defaultConfigs()
        return parseConfigs(json)
    }

    suspend fun saveGestureConfigs(configs: Map<GestureType, GestureConfig>) {
        context.dataStore.edit { it[keyConfigs] = serializeConfigs(configs) }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { it[keyMonitoring] = enabled }
    }

    suspend fun setVibrationFeedback(enabled: Boolean) {
        context.dataStore.edit { it[keyVibration] = enabled }
    }

    suspend fun setSoundFeedback(enabled: Boolean) {
        context.dataStore.edit { it[keySound] = enabled }
    }

    suspend fun setGlobalSensitivity(value: Int) {
        context.dataStore.edit { it[keySensitivity] = value.coerceIn(0, 100) }
    }

    suspend fun setAccuracyMode(mode: String) {
        context.dataStore.edit { it[keyAccuracy] = mode }
    }

    suspend fun setRecognitionProfile(profile: RecognitionProfile) {
        context.dataStore.edit { it[keyProfile] = profile.name }
    }

    suspend fun calibrationJson(): String = context.dataStore.data.first()[keyCalibration] ?: "{}"

    suspend fun saveCalibrationJson(json: String) {
        context.dataStore.edit { it[keyCalibration] = json }
    }

    suspend fun addCalibrationExample(
        gesture: GestureType,
        positive: Boolean,
        features: List<com.universalgestures.core.Vec3>,
        motionFeatures: List<com.universalgestures.recognition.MotionFeature> = emptyList()
    ): String {
        var updated = "{}"
        context.dataStore.edit { prefs ->
            val store = com.universalgestures.recognition.CalibrationStore.fromJson(prefs[keyCalibration] ?: "{}")
            if (positive) {
                store.addPositive(gesture, features)
                store.addMotionPositive(gesture, motionFeatures)
            } else {
                store.addNegative(gesture, features)
                store.addMotionNegative(gesture, motionFeatures)
            }
            updated = store.toJson()
            prefs[keyCalibration] = updated
        }
        return updated
    }

    /** Delete all stored configuration and calibration data. */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    /** Export configuration as JSON (no personal data). */
    suspend fun exportConfig(): String = serializeConfigs(gestureConfigs())

    suspend fun importConfig(json: String) {
        val parsed = parseConfigs(json)
        saveGestureConfigs(parsed)
    }

    companion object {
        fun defaultConfigs(): Map<GestureType, GestureConfig> {
            val defaults = mutableMapOf<GestureType, GestureConfig>()
            for (type in GestureType.values()) {
                defaults[type] = GestureConfig(type = type)
            }
            // Sensible out-of-the-box defaults (disabled until user opts in).
            defaults[GestureType.DOUBLE_CHOP] = GestureConfig(
                GestureType.DOUBLE_CHOP, enabled = false,
                action = ActionType.FLASHLIGHT_TOGGLE, sensitivity = 50, timingMs = 1200
            )
            defaults[GestureType.DOUBLE_TWIST] = GestureConfig(
                GestureType.DOUBLE_TWIST, enabled = false,
                action = ActionType.OPEN_CAMERA, sensitivity = 50, timingMs = 1600
            )
            defaults[GestureType.SHAKE] = GestureConfig(
                GestureType.SHAKE, enabled = false,
                action = ActionType.MEDIA_PLAY_PAUSE, sensitivity = 50, timingMs = 1500
            )
            return defaults
        }

        fun serializeConfigs(configs: Map<GestureType, GestureConfig>): String {
            val array = JSONArray()
            for ((_, config) in configs) {
                val obj = JSONObject()
                obj.put("type", config.type.name)
                obj.put("enabled", config.enabled)
                obj.put("action", config.action.name)
                obj.put("actionTarget", config.actionTarget)
                obj.put("sensitivity", config.sensitivity)
                obj.put("timingMs", config.timingMs)
                obj.put("cooldownMs", config.cooldownMs)
                obj.put("calibrated", config.calibrated)
                array.put(obj)
            }
            return array.toString()
        }

        fun parseConfigs(json: String): Map<GestureType, GestureConfig> {
            val result = defaultConfigs().toMutableMap()
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val type = GestureType.valueOf(obj.getString("type"))
                    val action = ActionType.valueOf(obj.getString("action"))
                    result[type] = GestureConfig(
                        type = type,
                        enabled = obj.optBoolean("enabled", false),
                        action = action,
                        actionTarget = obj.optString("actionTarget", ""),
                        sensitivity = obj.optInt("sensitivity", 50),
                        timingMs = obj.optInt("timingMs", 900).coerceIn(250, 5000),
                        cooldownMs = obj.optInt("cooldownMs", 1200).coerceIn(250, 10000),
                        calibrated = obj.optBoolean("calibrated", false)
                    )
                }
            } catch (e: Exception) {
                // Corrupt config falls back to defaults.
            }
            return result
        }
    }
}
