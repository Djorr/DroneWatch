package com.dronewatch.app.domain.repository

import com.dronewatch.app.domain.model.DroneDetection
import com.dronewatch.app.domain.model.ScannerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DroneRepository {
    val liveDrones: StateFlow<List<DroneDetection>>
    val scannerState: StateFlow<ScannerState>
    val lastScanTimestamp: StateFlow<Long>
    val advertisementCount: StateFlow<Long>
    val decodedMessageCount: StateFlow<Long>
    val lastError: StateFlow<String?>

    fun startContinuousScan()
    fun stopScan()
    fun setDemoMode(enabled: Boolean)
    fun updateUserLocation(lat: Double, lon: Double, accuracy: Float?)
    fun getHistory(): Flow<List<DroneDetection>>
    fun clearHistory()
    fun exportDebugLog(): String
}
