package com.dronewatch.app.data.remoteid

import com.dronewatch.app.domain.model.DroneDetection
import com.dronewatch.app.domain.model.RemoteIdMessageType
import com.dronewatch.app.domain.model.TransportType

/**
 * Interface for parsing ASTM F3411 / OpenDroneID Remote ID payloads.
 * Implementations must never crash on invalid data – return null or partial results.
 */
interface RemoteIdDecoder {
    /**
     * Attempt to decode a raw advertisement payload.
     * @param data raw bytes from BLE advertisement or Wi-Fi
     * @param rssi signal strength in dBm
     * @param transport how the data was received
     * @param timestamp receive time
     * @return parsed detection or null if not a valid Remote ID message
     */
    fun decode(
        data: ByteArray,
        rssi: Int,
        transport: TransportType,
        timestamp: Long
    ): PartialRemoteIdMessage?
}

/**
 * Intermediate parse result that can be merged into a full DroneDetection.
 */
data class PartialRemoteIdMessage(
    val uaId: String?,
    val uaIdType: String?,
    val latitude: Double?,
    val longitude: Double?,
    val altitudeMsl: Double?,
    val altitudeAgl: Double?,
    val speedHorizontal: Double?,
    val speedVertical: Double?,
    val heading: Double?,
    val operatorLatitude: Double?,
    val operatorLongitude: Double?,
    val operatorAltitude: Double?,
    val description: String?,
    val messageType: RemoteIdMessageType,
    val transport: TransportType,
    val rssi: Int,
    val timestamp: Long,
    val rawHex: String
)
