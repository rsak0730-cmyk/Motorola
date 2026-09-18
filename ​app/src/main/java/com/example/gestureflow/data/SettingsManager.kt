package com.example.gestureflow.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "gesture_settings")

class SettingsManager(private val context: Context) {
    companion object {
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        
        // Gestures enabled
        val CHOP_ENABLED = booleanPreferencesKey("chop_enabled")
        val TWIST_ENABLED = booleanPreferencesKey("twist_enabled")
        val SHAKE_ENABLED = booleanPreferencesKey("shake_enabled")
        
        // Sensitivities (0-100)
        val CHOP_SENSITIVITY = intPreferencesKey("chop_sensitivity")
        val TWIST_SENSITIVITY = intPreferencesKey("twist_sensitivity")
        val SHAKE_SENSITIVITY = intPreferencesKey("shake_sensitivity")
    }

    val isServiceEnabled: Flow<Boolean> = context.dataStore.data.map { it[SERVICE_ENABLED] ?: false }
    val isChopEnabled: Flow<Boolean> = context.dataStore.data.map { it[CHOP_ENABLED] ?: true }
    val chopSensitivity: Flow<Int> = context.dataStore.data.map { it[CHOP_SENSITIVITY] ?: 50 }
    
    val isTwistEnabled: Flow<Boolean> = context.dataStore.data.map { it[TWIST_ENABLED] ?: true }
    val isShakeEnabled: Flow<Boolean> = context.dataStore.data.map { it[SHAKE_ENABLED] ?: false }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SERVICE_ENABLED] = enabled }
    }
    
    suspend fun setChopEnabled(enabled: Boolean) {
        context.dataStore.edit { it[CHOP_ENABLED] = enabled }
    }

    suspend fun setChopSensitivity(value: Int) {
        context.dataStore.edit { it[CHOP_SENSITIVITY] = value }
    }
}

