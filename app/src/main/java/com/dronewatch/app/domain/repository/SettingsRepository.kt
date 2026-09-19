package com.dronewatch.app.domain.repository

import com.dronewatch.app.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings)
}
