package com.dronewatch.app.ui

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dronewatch.app.data.location.LocationRepository
import com.dronewatch.app.data.repository.DroneRepositoryImpl
import com.dronewatch.app.data.repository.SettingsRepositoryImpl
import com.dronewatch.app.domain.model.AppSettings
import com.dronewatch.app.domain.model.DroneDetection
import com.dronewatch.app.domain.model.ScannerState
import com.dronewatch.app.service.RemoteIdScanService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepositoryImpl(application)
    private val droneRepo = DroneRepositoryImpl(application, settingsRepo)
    private val locationRepo = LocationRepository(application)

    val liveDrones: StateFlow<List<DroneDetection>> = droneRepo.liveDrones
    val scannerState: StateFlow<ScannerState> = droneRepo.scannerState
    val lastScanTimestamp: StateFlow<Long> = droneRepo.lastScanTimestamp
    val advertisementCount: StateFlow<Long> = droneRepo.advertisementCount
    val decodedMessageCount: StateFlow<Long> = droneRepo.decodedMessageCount
    val lastError: StateFlow<String?> = droneRepo.lastError

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val history = droneRepo.getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined UI state for home
    data class HomeUiState(
        val drones: List<DroneDetection> = emptyList(),
        val state: ScannerState = ScannerState.INITIALIZING,
        val nearestDistance: Double? = null,
        val lastScanAgeMs: Long = 0,
        val adCount: Long = 0,
        val decodedCount: Long = 0,
        val error: String? = null,
        val isDemo: Boolean = false
    )

    val homeState: StateFlow<HomeUiState> = combine(
        liveDrones, scannerState, lastScanTimestamp, advertisementCount,
        decodedMessageCount, lastError, settings
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val drones = values[0] as List<DroneDetection>
        val state = values[1] as ScannerState
        val lastTs = values[2] as Long
        val adCount = values[3] as Long
        val decoded = values[4] as Long
        val error = values[5] as String?
        val sett = values[6] as AppSettings

        val nearest = drones.firstOrNull()?.let { d ->
            // Distance already sorted by repository when location known
            null // filled in UI with location
        }
        HomeUiState(
            drones = drones,
            state = state,
            nearestDistance = null,
            lastScanAgeMs = if (lastTs > 0) System.currentTimeMillis() - lastTs else -1,
            adCount = adCount,
            decodedCount = decoded,
            error = error,
            isDemo = sett.demoModeEnabled
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    private var userLat: Double? = null
    private var userLon: Double? = null
    private val alertedDrones = mutableSetOf<String>() // currently inside geofence

    init {
        // Start continuous scanning immediately
        droneRepo.startContinuousScan()

        // Location updates
        viewModelScope.launch {
            try {
                locationRepo.locationUpdates().collect { loc ->
                    userLat = loc.latitude
                    userLon = loc.longitude
                    droneRepo.updateUserLocation(loc.latitude, loc.longitude, loc.accuracy)
                    checkAlerts()
                }
            } catch (_: SecurityException) {
                // Permission not granted yet
            }
        }

        // Start foreground service for background continuity
        startScanService()
    }

    fun getUserLocation(): Pair<Double, Double>? {
        val lat = userLat ?: return null
        val lon = userLon ?: return null
        return lat to lon
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepo.updateSettings(transform)
        }
    }

    fun clearHistory() {
        droneRepo.clearHistory()
    }

    fun exportDebugLog(): String = droneRepo.exportDebugLog()

    private fun checkAlerts() {
        val sett = settings.value
        if (!sett.alertEnabled) return
        val lat = userLat ?: return
        val lon = userLon ?: return
        val threshold = sett.alertDistanceMeters.toDouble()

        liveDrones.value.forEach { drone ->
            val dist = drone.distanceTo(lat, lon) ?: return@forEach
            val id = drone.id
            if (dist <= threshold) {
                if (id !in alertedDrones) {
                    alertedDrones.add(id)
                    showAlertNotification(dist)
                }
            } else {
                alertedDrones.remove(id)
            }
        }
    }

    private fun showAlertNotification(distanceM: Double) {
        val ctx = getApplication<Application>()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val text = "Drone gedetecteerd op ${distanceM.toInt()} m"
        val notification = NotificationCompat.Builder(ctx, "dronewatch_alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("DroneWatch")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun startScanService() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, RemoteIdScanService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Keep scanning via service; do not stop repository
    }
}
