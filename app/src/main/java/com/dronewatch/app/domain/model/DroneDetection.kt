package com.dronewatch.app.domain.model

/**
 * Normalized representation of a detected drone via Remote ID.
 * All optional fields are null when not present in the received messages.
 * Never invent values – use null / "Onbekend".
 */
data class DroneDetection(
    val id: String,                          // Stable unique key (prefer Remote ID UA ID)
    val droneId: String?,                    // UA ID if available
    val latitude: Double?,
    val longitude: Double?,
    val altitudeMsl: Double?,                // meters
    val altitudeAgl: Double?,                // meters above ground if available
    val speedHorizontal: Double?,            // m/s
    val speedVertical: Double?,              // m/s
    val heading: Double?,                    // degrees 0-360
    val operatorLatitude: Double?,
    val operatorLongitude: Double?,
    val operatorAltitude: Double?,
    val rssi: Int?,                          // dBm
    val transportType: TransportType,
    val timestamp: Long,                     // epoch millis of last update
    val firstSeen: Long,
    val messageTypes: Set<RemoteIdMessageType>,
    val status: DroneStatus = DroneStatus.ACTIVE,
    val description: String? = null,         // Self-ID description if present
    val classification: DroneClassification = DroneClassification.UNKNOWN,
    val isDemo: Boolean = false
) {
    fun distanceTo(userLat: Double, userLon: Double): Double? {
        val lat = latitude ?: return null
        val lon = longitude ?: return null
        return haversineMeters(userLat, userLon, lat, lon)
    }

    fun bearingFrom(userLat: Double, userLon: Double): Double? {
        val lat = latitude ?: return null
        val lon = longitude ?: return null
        return bearingDegrees(userLat, userLon, lat, lon)
    }

    companion object {
        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0 // Earth radius meters
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return r * c
        }

        fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val lat1r = Math.toRadians(lat1)
            val lat2r = Math.toRadians(lat2)
            val dLon = Math.toRadians(lon2 - lon1)
            val y = Math.sin(dLon) * Math.cos(lat2r)
            val x = Math.cos(lat1r) * Math.sin(lat2r) -
                    Math.sin(lat1r) * Math.cos(lat2r) * Math.cos(dLon)
            var brng = Math.toDegrees(Math.atan2(y, x))
            if (brng < 0) brng += 360.0
            return brng
        }
    }
}

enum class TransportType {
    BLUETOOTH_LEGACY,
    BLUETOOTH_LONG_RANGE,
    WIFI_NAN,
    WIFI_BEACON,
    UNKNOWN
}

enum class DroneStatus {
    ACTIVE,   // Recent message
    STALE,    // No message for a short period
    LOST      // No message for longer period → remove from live list
}

enum class RemoteIdMessageType {
    BASIC_ID,
    LOCATION,
    AUTHENTICATION,
    SELF_ID,
    SYSTEM,
    OPERATOR_ID,
    MESSAGE_PACK,
    UNKNOWN
}

/**
 * Classification only when a reliable external source provides evidence.
 * Default is always UNKNOWN – never claim police/government without proof.
 */
enum class DroneClassification {
    UNKNOWN,
    PRIVATE,
    COMMERCIAL,
    GOVERNMENT,
    POLICE,
    TRAFFIC,
    OTHER
}

enum class ScannerState {
    INITIALIZING,
    SCANNING,
    BLUETOOTH_DISABLED,
    PERMISSION_REQUIRED,
    GPS_UNAVAILABLE,
    ERROR,
    DEMO
}
