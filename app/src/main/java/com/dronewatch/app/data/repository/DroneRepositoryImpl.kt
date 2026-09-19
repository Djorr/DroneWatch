package com.dronewatch.app.data.repository

import android.content.Context
import android.util.Log
import com.dronewatch.app.data.bluetooth.RemoteIdScanner
import com.dronewatch.app.data.database.AppDatabase
import com.dronewatch.app.data.database.DroneHistoryEntity
import com.dronewatch.app.data.remoteid.PartialRemoteIdMessage
import com.dronewatch.app.domain.model.DroneDetection
import com.dronewatch.app.domain.model.DroneStatus
import com.dronewatch.app.domain.model.RemoteIdMessageType
import com.dronewatch.app.domain.model.ScannerState
import com.dronewatch.app.domain.model.TransportType
import com.dronewatch.app.domain.repository.DroneRepository
import com.dronewatch.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Central repository that owns the live drone state machine,
 * merges Remote ID messages, manages lifecycle (ACTIVE/STALE/LOST),
 * and optionally runs a demo generator.
 */
class DroneRepositoryImpl(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val scanner: RemoteIdScanner = RemoteIdScanner(context)
) : DroneRepository {

    companion object {
        private const val TAG = "DroneRepository"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = AppDatabase.getInstance(context)
    private val historyDao = db.droneHistoryDao()

    private val _liveDrones = MutableStateFlow<List<DroneDetection>>(emptyList())
    override val liveDrones: StateFlow<List<DroneDetection>> = _liveDrones.asStateFlow()

    private val _scannerState = MutableStateFlow(ScannerState.INITIALIZING)
    override val scannerState: StateFlow<ScannerState> = _scannerState.asStateFlow()

    private val _lastScanTimestamp = MutableStateFlow(0L)
    override val lastScanTimestamp: StateFlow<Long> = _lastScanTimestamp.asStateFlow()

    private val _advertisementCount = MutableStateFlow(0L)
    override val advertisementCount: StateFlow<Long> = _advertisementCount.asStateFlow()

    private val _decodedMessageCount = MutableStateFlow(0L)
    override val decodedMessageCount: StateFlow<Long> = _decodedMessageCount.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // Internal mutable map keyed by stable drone id
    private val droneMap = mutableMapOf<String, DroneDetection>()
    private var userLat: Double? = null
    private var userLon: Double? = null

    private var scanJob: Job? = null
    private var lifecycleJob: Job? = null
    private var demoJob: Job? = null
    private var isDemo = false

    // Alert cooldown: droneId -> last alert timestamp
    private val alertCooldown = mutableMapOf<String, Long>()
    private val ALERT_COOLDOWN_MS = 60_000L

    init {
        // Observe settings for demo mode changes
        scope.launch {
            settingsRepository.settings.collect { settings ->
                if (settings.demoModeEnabled != isDemo) {
                    setDemoMode(settings.demoModeEnabled)
                }
            }
        }
    }

    override fun startContinuousScan() {
        if (scanJob?.isActive == true) return
        _scannerState.value = ScannerState.INITIALIZING

        scanJob = scope.launch {
            // Collect raw advertisements
            launch {
                scanner.rawAdvertisementEvents.collect {
                    _advertisementCount.value = _advertisementCount.value + 1
                    _lastScanTimestamp.value = System.currentTimeMillis()
                }
            }

            // Collect decoded messages
            launch {
                scanner.messages.collect { msg ->
                    _decodedMessageCount.value = _decodedMessageCount.value + 1
                    _lastScanTimestamp.value = System.currentTimeMillis()
                    mergeMessage(msg)
                }
            }

            // Lifecycle / stale management loop
            lifecycleJob = launch {
                while (isActive) {
                    val settings = settingsRepository.settings.first()
                    updateLifecycle(
                        staleMs = settings.staleTimeoutSeconds * 1000L,
                        lostMs = settings.lostTimeoutSeconds * 1000L
                    )
                    delay(1000)
                }
            }

            // Start BLE if not in demo
            if (!isDemo) {
                ensureBluetoothAndStart()
            } else {
                _scannerState.value = ScannerState.DEMO
            }
        }
    }

    private fun ensureBluetoothAndStart() {
        when {
            !scanner.isBluetoothAvailable() -> {
                _scannerState.value = ScannerState.ERROR
                _lastError.value = "Bluetooth hardware niet beschikbaar"
            }
            !scanner.isBluetoothEnabled() -> {
                _scannerState.value = ScannerState.BLUETOOTH_DISABLED
                _lastError.value = "Bluetooth staat uit"
                // Retry loop
                scope.launch {
                    while (isActive && !scanner.isBluetoothEnabled()) {
                        delay(2000)
                    }
                    if (scanner.isBluetoothEnabled() && !isDemo) {
                        scanner.start()
                        _scannerState.value = ScannerState.SCANNING
                        _lastError.value = null
                    }
                }
            }
            else -> {
                scanner.start()
                _scannerState.value = ScannerState.SCANNING
                _lastError.value = null
            }
        }
    }

    override fun stopScan() {
        scanJob?.cancel()
        lifecycleJob?.cancel()
        demoJob?.cancel()
        scanner.stop()
        _scannerState.value = ScannerState.INITIALIZING
    }

    override fun setDemoMode(enabled: Boolean) {
        isDemo = enabled
        if (enabled) {
            scanner.stop()
            _scannerState.value = ScannerState.DEMO
            startDemoGenerator()
        } else {
            demoJob?.cancel()
            droneMap.clear()
            publishLive()
            ensureBluetoothAndStart()
        }
    }

    override fun updateUserLocation(lat: Double, lon: Double, accuracy: Float?) {
        userLat = lat
        userLon = lon
        // Re-publish so UI can recalculate distances
        publishLive()
    }

    override fun getHistory() = historyDao.getRecent(500).map { list ->
        list.map { e ->
            DroneDetection(
                id = e.droneId,
                droneId = e.droneId,
                latitude = e.latitude,
                longitude = e.longitude,
                altitudeMsl = e.altitude,
                altitudeAgl = null,
                speedHorizontal = e.speed,
                speedVertical = null,
                heading = e.heading,
                operatorLatitude = null,
                operatorLongitude = null,
                operatorAltitude = null,
                rssi = e.rssi,
                transportType = try {
                    TransportType.valueOf(e.transport)
                } catch (_: Exception) {
                    TransportType.UNKNOWN
                },
                timestamp = e.timestamp,
                firstSeen = e.timestamp,
                messageTypes = emptySet(),
                status = DroneStatus.LOST,
                isDemo = e.isDemo
            )
        }
    }

    override fun clearHistory() {
        scope.launch { historyDao.clearAll() }
    }

    override fun exportDebugLog(): String {
        return buildString {
            appendLine("=== DroneWatch Debug Log ===")
            appendLine("Time: ${System.currentTimeMillis()}")
            appendLine("Scanner state: ${_scannerState.value}")
            appendLine("Bluetooth available: ${scanner.isBluetoothAvailable()}")
            appendLine("Bluetooth enabled: ${scanner.isBluetoothEnabled()}")
            appendLine("Currently scanning: ${scanner.isCurrentlyScanning()}")
            appendLine("Advertisements: ${_advertisementCount.value}")
            appendLine("Decoded messages: ${_decodedMessageCount.value}")
            appendLine("Live drones: ${_liveDrones.value.size}")
            appendLine("User location: $userLat, $userLon")
            appendLine("Last error: ${_lastError.value}")
            appendLine("--- Live drones ---")
            _liveDrones.value.forEach { d ->
                appendLine("${d.id} | ${d.droneId} | lat=${d.latitude} lon=${d.longitude} alt=${d.altitudeMsl} rssi=${d.rssi} status=${d.status}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Message merging & lifecycle
    // -------------------------------------------------------------------------

    private fun mergeMessage(msg: PartialRemoteIdMessage) {
        // Determine stable ID – prefer UA ID, otherwise generate from coordinates + time window
        val stableId = msg.uaId?.takeIf { it.isNotBlank() }
            ?: run {
                // Fallback: use approximate location key if available
                if (msg.latitude != null && msg.longitude != null) {
                    "loc_${(msg.latitude * 1e5).toInt()}_${(msg.longitude * 1e5).toInt()}"
                } else {
                    "unknown_${msg.timestamp / 10000}" // coarse time bucket
                }
            }

        val existing = droneMap[stableId]
        val now = msg.timestamp

        val merged = if (existing == null) {
            DroneDetection(
                id = stableId,
                droneId = msg.uaId,
                latitude = msg.latitude,
                longitude = msg.longitude,
                altitudeMsl = msg.altitudeMsl,
                altitudeAgl = msg.altitudeAgl,
                speedHorizontal = msg.speedHorizontal,
                speedVertical = msg.speedVertical,
                heading = msg.heading,
                operatorLatitude = msg.operatorLatitude,
                operatorLongitude = msg.operatorLongitude,
                operatorAltitude = msg.operatorAltitude,
                rssi = msg.rssi,
                transportType = msg.transport,
                timestamp = now,
                firstSeen = now,
                messageTypes = setOf(msg.messageType),
                status = DroneStatus.ACTIVE,
                description = msg.description,
                isDemo = false
            )
        } else {
            existing.copy(
                droneId = msg.uaId ?: existing.droneId,
                latitude = msg.latitude ?: existing.latitude,
                longitude = msg.longitude ?: existing.longitude,
                altitudeMsl = msg.altitudeMsl ?: existing.altitudeMsl,
                altitudeAgl = msg.altitudeAgl ?: existing.altitudeAgl,
                speedHorizontal = msg.speedHorizontal ?: existing.speedHorizontal,
                speedVertical = msg.speedVertical ?: existing.speedVertical,
                heading = msg.heading ?: existing.heading,
                operatorLatitude = msg.operatorLatitude ?: existing.operatorLatitude,
                operatorLongitude = msg.operatorLongitude ?: existing.operatorLongitude,
                operatorAltitude = msg.operatorAltitude ?: existing.operatorAltitude,
                rssi = msg.rssi,
                transportType = msg.transport,
                timestamp = now,
                messageTypes = existing.messageTypes + msg.messageType,
                status = DroneStatus.ACTIVE,
                description = msg.description ?: existing.description
            )
        }

        droneMap[stableId] = merged
        publishLive()

        // Persist a history snapshot occasionally (throttle)
        scope.launch {
            try {
                historyDao.insert(
                    DroneHistoryEntity(
                        droneId = stableId,
                        latitude = merged.latitude,
                        longitude = merged.longitude,
                        altitude = merged.altitudeMsl,
                        speed = merged.speedHorizontal,
                        heading = merged.heading,
                        rssi = merged.rssi,
                        transport = merged.transportType.name,
                        timestamp = now,
                        isDemo = false
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write history", e)
            }
        }
    }

    private fun updateLifecycle(staleMs: Long, lostMs: Long) {
        val now = System.currentTimeMillis()
        var changed = false
        val toRemove = mutableListOf<String>()

        droneMap.forEach { (id, drone) ->
            if (drone.isDemo) return@forEach
            val age = now - drone.timestamp
            val newStatus = when {
                age > lostMs -> {
                    toRemove.add(id)
                    DroneStatus.LOST
                }
                age > staleMs -> DroneStatus.STALE
                else -> DroneStatus.ACTIVE
            }
            if (newStatus != drone.status && newStatus != DroneStatus.LOST) {
                droneMap[id] = drone.copy(status = newStatus)
                changed = true
            }
        }
        toRemove.forEach { droneMap.remove(it) }
        if (changed || toRemove.isNotEmpty()) {
            publishLive()
        }
    }

    private fun publishLive() {
        val sorted = droneMap.values
            .filter { it.status != DroneStatus.LOST }
            .sortedBy { d ->
                val lat = userLat
                val lon = userLon
                if (lat != null && lon != null) {
                    d.distanceTo(lat, lon) ?: Double.MAX_VALUE
                } else {
                    Double.MAX_VALUE
                }
            }
        _liveDrones.value = sorted
    }

    // -------------------------------------------------------------------------
    // Demo mode
    // -------------------------------------------------------------------------

    private fun startDemoGenerator() {
        demoJob?.cancel()
        demoJob = scope.launch {
            val baseLat = userLat ?: 52.3702
            val baseLon = userLon ?: 4.8952

            // Create 5 virtual drones around the user
            val demos = (1..5).map { i ->
                val angle = (i * 72.0) * Math.PI / 180.0
                val distKm = 0.3 + i * 0.25
                val dLat = (distKm / 111.0) * cos(angle)
                val dLon = (distKm / (111.0 * cos(Math.toRadians(baseLat)))) * sin(angle)
                DroneDetection(
                    id = "demo_$i",
                    droneId = "DEMO-UA-00$i",
                    latitude = baseLat + dLat,
                    longitude = baseLon + dLon,
                    altitudeMsl = 40.0 + i * 25.0,
                    altitudeAgl = 40.0 + i * 25.0,
                    speedHorizontal = 2.0 + i * 1.5,
                    speedVertical = 0.0,
                    heading = (i * 50.0) % 360.0,
                    operatorLatitude = baseLat + dLat * 0.1,
                    operatorLongitude = baseLon + dLon * 0.1,
                    operatorAltitude = 0.0,
                    rssi = -55 - i * 8,
                    transportType = TransportType.BLUETOOTH_LEGACY,
                    timestamp = System.currentTimeMillis(),
                    firstSeen = System.currentTimeMillis(),
                    messageTypes = setOf(RemoteIdMessageType.BASIC_ID, RemoteIdMessageType.LOCATION),
                    status = DroneStatus.ACTIVE,
                    description = "Demo drone $i",
                    isDemo = true
                )
            }
            demos.forEach { droneMap[it.id] = it }
            publishLive()

            // Slowly move them
            while (isActive && isDemo) {
                delay(1500)
                val now = System.currentTimeMillis()
                demos.forEachIndexed { idx, _ ->
                    val id = "demo_${idx + 1}"
                    val current = droneMap[id] ?: return@forEachIndexed
                    val headingRad = Math.toRadians(current.heading ?: 0.0)
                    val step = 0.00008 // ~9 m
                    val newLat = (current.latitude ?: baseLat) + step * cos(headingRad)
                    val newLon = (current.longitude ?: baseLon) + step * sin(headingRad)
                    val newHeading = ((current.heading ?: 0.0) + Random.nextDouble(-5.0, 5.0) + 360) % 360
                    droneMap[id] = current.copy(
                        latitude = newLat,
                        longitude = newLon,
                        heading = newHeading,
                        timestamp = now,
                        rssi = (current.rssi ?: -70) + Random.nextInt(-3, 4)
                    )
                }
                publishLive()
            }
        }
    }
}
