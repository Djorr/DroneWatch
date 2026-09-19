package com.dronewatch.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dronewatch.app.domain.model.AppSettings
import com.dronewatch.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    private object Keys {
        val continuous = booleanPreferencesKey("continuous_scanning")
        val demo = booleanPreferencesKey("demo_mode")
        val alertDistance = intPreferencesKey("alert_distance")
        val alertEnabled = booleanPreferencesKey("alert_enabled")
        val staleTimeout = intPreferencesKey("stale_timeout")
        val lostTimeout = intPreferencesKey("lost_timeout")
        val batterySaver = booleanPreferencesKey("battery_saver")
        val showNotification = booleanPreferencesKey("show_notification")
    }

    override val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            continuousScanningEnabled = prefs[Keys.continuous] ?: true,
            demoModeEnabled = prefs[Keys.demo] ?: false,
            alertDistanceMeters = prefs[Keys.alertDistance] ?: 1000,
            alertEnabled = prefs[Keys.alertEnabled] ?: true,
            staleTimeoutSeconds = prefs[Keys.staleTimeout] ?: 10,
            lostTimeoutSeconds = prefs[Keys.lostTimeout] ?: 45,
            batterySaverMode = prefs[Keys.batterySaver] ?: false,
            showNotification = prefs[Keys.showNotification] ?: true
        )
    }

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = AppSettings(
                continuousScanningEnabled = prefs[Keys.continuous] ?: true,
                demoModeEnabled = prefs[Keys.demo] ?: false,
                alertDistanceMeters = prefs[Keys.alertDistance] ?: 1000,
                alertEnabled = prefs[Keys.alertEnabled] ?: true,
                staleTimeoutSeconds = prefs[Keys.staleTimeout] ?: 10,
                lostTimeoutSeconds = prefs[Keys.lostTimeout] ?: 45,
                batterySaverMode = prefs[Keys.batterySaver] ?: false,
                showNotification = prefs[Keys.showNotification] ?: true
            )
            val updated = transform(current)
            prefs[Keys.continuous] = updated.continuousScanningEnabled
            prefs[Keys.demo] = updated.demoModeEnabled
            prefs[Keys.alertDistance] = updated.alertDistanceMeters
            prefs[Keys.alertEnabled] = updated.alertEnabled
            prefs[Keys.staleTimeout] = updated.staleTimeoutSeconds
            prefs[Keys.lostTimeout] = updated.lostTimeoutSeconds
            prefs[Keys.batterySaver] = updated.batterySaverMode
            prefs[Keys.showNotification] = updated.showNotification
        }
    }
}
