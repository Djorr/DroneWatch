package com.dronewatch.app.data.remoteid

import com.dronewatch.app.domain.model.RemoteIdMessageType
import com.dronewatch.app.domain.model.TransportType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Practical OpenDroneID / ASTM F3411 message parser.
 *
 * Supports the most common message types used in the field:
 * - Basic ID (0x0)
 * - Location / Vector (0x1)
 * - Self-ID (0x3)
 * - System (0x4)
 * - Operator ID (0x5)
 * - Message Pack (0xF)
 *
 * Based on the OpenDroneID public specification.
 * Invalid or incomplete packets return null – never crash.
 *
 * Note: Full production use should prefer the official OpenDroneID-core
 * library when available. This implementation provides a solid baseline
 * that correctly handles real-world BLE advertisements from compliant drones.
 */
class OpenDroneIdDecoder : RemoteIdDecoder {

    companion object {
        // ASTM F3411 / OpenDroneID AD type for BLE (Service Data)
        // Service UUID 0xFFFA is commonly used for OpenDroneID
        private const val ODID_SERVICE_UUID_16 = 0xFFFA

        // Message types
        private const val MSG_BASIC_ID = 0x00
        private const val MSG_LOCATION = 0x01
        private const val MSG_AUTH = 0x02
        private const val MSG_SELF_ID = 0x03
        private const val MSG_SYSTEM = 0x04
        private const val MSG_OPERATOR_ID = 0x05
        private const val MSG_MESSAGE_PACK = 0x0F

        // Protocol version typically in high nibble of first byte
        private const val PROTOCOL_VERSION_MASK = 0xF0
        private const val MESSAGE_TYPE_MASK = 0x0F
    }

    override fun decode(
        data: ByteArray,
        rssi: Int,
        transport: TransportType,
        timestamp: Long
    ): PartialRemoteIdMessage? {
        if (data.isEmpty()) return null

        return try {
            // Try to find OpenDroneID payload inside the advertisement
            val payload = extractOdIdPayload(data) ?: return null
            if (payload.size < 25) return null // Minimum message size

            parseMessage(payload, rssi, transport, timestamp)
        } catch (e: Exception) {
            // Never crash on corrupt data
            null
        }
    }

    /**
     * Extract the OpenDroneID message bytes from a BLE advertisement.
     * Handles both Service Data (0x16) and Manufacturer Specific (0xFF) formats.
     */
    private fun extractOdIdPayload(adv: ByteArray): ByteArray? {
        var i = 0
        while (i < adv.size - 1) {
            val length = adv[i].toInt() and 0xFF
            if (length == 0 || i + 1 + length > adv.size) break
            val type = adv[i + 1].toInt() and 0xFF

            when (type) {
                0x16 -> { // Service Data - 16-bit UUID
                    if (length >= 3) {
                        val uuid = ((adv[i + 3].toInt() and 0xFF) shl 8) or (adv[i + 2].toInt() and 0xFF)
                        if (uuid == ODID_SERVICE_UUID_16 || uuid == 0xFFFA) {
                            // Rest is OpenDroneID data
                            return adv.copyOfRange(i + 4, i + 1 + length)
                        }
                    }
                }
                0xFF -> { // Manufacturer Specific Data
                    // Some vendors put ODID here; try to parse as ODID if it looks valid
                    if (length >= 27) {
                        val candidate = adv.copyOfRange(i + 2, i + 1 + length)
                        // Heuristic: first byte protocol/message type looks plausible
                        val msgType = candidate[0].toInt() and MESSAGE_TYPE_MASK
                        if (msgType in 0..5 || msgType == 0x0F) {
                            return candidate
                        }
                    }
                }
                0x07 -> { // Complete list of 128-bit Service UUIDs – skip
                }
            }
            i += 1 + length
        }

        // Fallback: treat entire data as potential ODID message (for testing / direct injection)
        if (adv.size >= 25) {
            val msgType = adv[0].toInt() and MESSAGE_TYPE_MASK
            if (msgType in 0..5 || msgType == 0x0F) {
                return adv
            }
        }
        return null
    }

    private fun parseMessage(
        payload: ByteArray,
        rssi: Int,
        transport: TransportType,
        timestamp: Long
    ): PartialRemoteIdMessage? {
        val first = payload[0].toInt() and 0xFF
        val msgType = first and MESSAGE_TYPE_MASK
        // val protoVersion = (first and PROTOCOL_VERSION_MASK) shr 4

        return when (msgType) {
            MSG_BASIC_ID -> parseBasicId(payload, rssi, transport, timestamp)
            MSG_LOCATION -> parseLocation(payload, rssi, transport, timestamp)
            MSG_SELF_ID -> parseSelfId(payload, rssi, transport, timestamp)
            MSG_SYSTEM -> parseSystem(payload, rssi, transport, timestamp)
            MSG_OPERATOR_ID -> parseOperatorId(payload, rssi, transport, timestamp)
            MSG_MESSAGE_PACK -> parseMessagePack(payload, rssi, transport, timestamp)
            else -> null
        }
    }

    private fun parseBasicId(
        p: ByteArray,
        rssi: Int,
        transport: TransportType,
        timestamp: Long
    ): PartialRemoteIdMessage? {
        if (p.size < 25) return null
        // Byte 1: ID type + UA type
        val idType = (p[1].toInt() and 0xF0) shr 4
        // Bytes 2-21: UAS ID (20 bytes, null-terminated or padded)
        val idBytes = p.copyOfRange(2, 22)
        val uaId = idBytes.takeWhile { it != 0.toByte() }
            .toByteArray()
            .toString(Charsets.US_ASCII)
            .trim()
            .ifEmpty { null }

        return PartialRemoteIdMessage(
            uaId = uaId,
            uaIdType = idTypeToString(idType),
            latitude = null,
            longitude = null,
            altitudeMsl = null,
            altitudeAgl = null,
            speedHorizontal = null,
            speedVertical = null,
            heading = null,
            operatorLatitude = null,
            operatorLongitude = null,
            operatorAltitude = null,
            description = null,
            messageType = RemoteIdMessageType.BASIC_ID,
            transport = transport,
            rssi = rssi,
            timestamp = timestamp,
            rawHex = p.toHex()
        )
    }

    private fun parseLocation(
        p: ByteArray,
        rssi: Int,
        transport: TransportType,
        timestamp: Long
    ): PartialRemoteIdMessage? {
        if (p.size < 25) return null
        val status = p[1].toInt() and 0x0F
        // Direction (bytes 2-3) – 0-359 or 361 = unknown
        val directionRaw = ((p[3].toInt() and 0xFF) shl 8) or (p[2].toInt() and 0xFF)
        val heading = if (directionRaw in 0..360) directionRaw.toDouble() else null

        // Speed horizontal (byte 4) – 0.25 m/s units, 255 = unknown
        val speedHRaw = p[4].toInt() and 0xFF
        val speedH = if (speedHRaw < 255) speedHRaw * 0.25 else null

        // Speed vertical (byte 5) – 0.5 m/s units, signed, 63 = unknown
        val speedVRaw = p[5].toInt()
        val speedV = if (speedVRaw != 63 && speedVRaw != -64) speedVRaw * 0.5 else null

        // Latitude / Longitude – 32-bit signed, 10^-7 degrees
        val lat = readInt32(p, 6)?.let { it / 1e7 }
        val lon = readInt32(p, 10)?.let { it / 1e7 }

        // Pressure altitude (bytes 14-15) – 0.5 m units, offset -1000 m
        val altPressure = readUInt16(p, 14)?.let { (it * 0.5) - 1000.0 }
        // Geodetic altitude (bytes 16-17)
        val altGeodetic = readUInt16(p, 16)?.let { (it * 0.5) - 1000.0 }
        // Height (bytes 18-19) – AGL or above takeoff
        val height = readUInt16(p, 18)?.let { (it * 0.5) - 1000.0 }

        // Horizontal / Vertical accuracy, speed accuracy, timestamp etc. ignored for now

        // Sanity check coordinates
        val validLat = lat?.takeIf { it in -90.0..90.0 }
        val validLon = lon?.takeIf { it in -180.0..180.0 }

        return PartialRemoteIdMessage(
            uaId = null,
            uaIdType = null,
            latitude = validLat,
            longitude = validLon,
            altitudeMsl = altGeodetic ?: altPressure,
            altitudeAgl = height,
            speedHorizontal = speedH,
            speedVertical = speedV,
            heading = heading,
            operatorLatitude = null,
            operatorLongitude = null,
            operatorAltitude = null,
            description = null,
            messageType = RemoteIdMessageType.LOCATION,
            transport = transport,
            rssi = rssi,
            timestamp = timestamp,
            rawHex = p.toHex()
        )
    }

    private fun parseSelfId(
        p: ByteArray,
        rssi: Int,
        transport: TransportType,
        timestamp: Long
    ): PartialRemoteIdMessage? {
        if (p.size < 25) return null
        // Byte 1: description type
        val descBytes = p.copyOfRange(2, 25)
        val description = descBytes.takeWhile { it != 0.toByte() }
            .toByteArray()
            .toString(Charsets.UTF_8)
            .trim()
            .ifEmpty { null }

        return PartialRemoteIdMessage(
            uaId = null,
            uaIdType = null,
            latitude = null,
            longitude = null,
            altitudeMsl = null,
            altitudeAgl = null,
            speedHorizontal = null,
            speedVertical = null,
            heading = null,
            operatorLatitude = null,
            operatorLongitude = null,
            operatorAltitude = null,
            description = description,
            messageType = RemoteIdMessageType.SELF_ID,
            transport = transport,
            rssi = rssi,
            timestamp = timestamp,
            rawHex = p.toHex()
        )
    }

    private fun parseSystem(
        p: ByteArray,
        rssi: Int,
        transport: TransportType,
        timestamp: Long
    ): PartialRemoteIdMessage? {
        if (p.size < 25) return null
        // Operator location present in System message
        val lat = readInt32(p, 2)?.let { it / 1e7 }
        val lon = readInt32(p, 6)?.let { it / 1e7 }
        val areaCount = readUInt16(p, 10)
        val areaRadius = p.getOrNull(12)?.toInt()?.and(0xFF)
        // Operator altitude
        val opAlt = readUInt16(p, 13)?.let { (it * 0.5) - 1000.0 }

        return PartialRemoteIdMessage(
            uaId = null,
            uaIdType = null,
            latitude = null,
            longitude = null,
            altitudeMsl = null,
            altitudeAgl = null,
            speedHorizontal = null,
            speedVertical = null,
            heading = null,
            operatorLatitude = lat?.takeIf { it in -90.0..90.0 },
            operatorLongitude = lon?.takeIf { it in -180.0..180.0 },
            operatorAltitude = opAlt,
            description = null,
            messageType = RemoteIdMessageType.SYSTEM,
            transport = transport,
            rssi = rssi,
            timestamp = timestamp,
            rawHex = p.toHex()
        )
    }

    private fun parseOperatorId(
        p: ByteArray,
        rssi: Int,
        transport: TransportType,
        timestamp: Long
    ): PartialRemoteIdMessage? {
        if (p.size < 25) return null
        val opIdBytes = p.copyOfRange(2, 22)
        val opId = opIdBytes.takeWhile { it != 0.toByte() }
            .toByteArray()
            .toString(Charsets.US_ASCII)
            .trim()
            .ifEmpty { null }

        // We store operator ID in description for now (or could extend model)
        return PartialRemoteIdMessage(
            uaId = null,
            uaIdType = null,
            latitude = null,
            longitude = null,
            altitudeMsl = null,
            altitudeAgl = null,
            speedHorizontal = null,
            speedVertical = null,
            heading = null,
            operatorLatitude = null,
            operatorLongitude = null,
            operatorAltitude = null,
            description = opId?.let { "Operator: $it" },
            messageType = RemoteIdMessageType.OPERATOR_ID,
            transport = transport,
            rssi = rssi,
            timestamp = timestamp,
            rawHex = p.toHex()
        )
    }

    private fun parseMessagePack(
        p: ByteArray,
        rssi: Int,
        transport: TransportType,
        timestamp: Long
    ): PartialRemoteIdMessage? {
        // Message Pack contains multiple concatenated messages.
        // For simplicity we parse the first valid sub-message.
        if (p.size < 3) return null
        val msgSize = p[2].toInt() and 0xFF
        if (msgSize < 25 || p.size < 3 + msgSize) return null
        val firstMsg = p.copyOfRange(3, 3 + msgSize)
        return parseMessage(firstMsg, rssi, transport, timestamp)
    }

    private fun readInt32(data: ByteArray, offset: Int): Int? {
        if (offset + 4 > data.size) return null
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun readUInt16(data: ByteArray, offset: Int): Int? {
        if (offset + 2 > data.size) return null
        val v = ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        return v
    }

    private fun idTypeToString(type: Int): String = when (type) {
        0 -> "None"
        1 -> "Serial Number"
        2 -> "CAA Registration"
        3 -> "UTM Assigned"
        4 -> "Specific Session ID"
        else -> "Unknown"
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02X".format(it) }
}
