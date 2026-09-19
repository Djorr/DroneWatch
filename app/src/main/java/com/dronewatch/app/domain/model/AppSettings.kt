package com.dronewatch.app.domain.model

data class AppSettings(
    val continuousScanningEnabled: Boolean = true,
    val demoModeEnabled: Boolean = false,
    val alertDistanceMeters: Int = 1000,
    val alertEnabled: Boolean = true,
    val staleTimeoutSeconds: Int = 10,
    val lostTimeoutSeconds: Int = 45,
    val batterySaverMode: Boolean = false,
    val showNotification: Boolean = true
)
