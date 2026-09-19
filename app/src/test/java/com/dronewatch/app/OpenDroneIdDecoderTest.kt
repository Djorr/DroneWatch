package com.dronewatch.app

import com.dronewatch.app.data.remoteid.OpenDroneIdDecoder
import com.dronewatch.app.domain.model.RemoteIdMessageType
import com.dronewatch.app.domain.model.TransportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OpenDroneIdDecoderTest {

    private val decoder = OpenDroneIdDecoder()

    @Test
    fun emptyPayload_returnsNull() {
        val result = decoder.decode(ByteArray(0), -60, TransportType.BLUETOOTH_LEGACY, System.currentTimeMillis())
        assertNull(result)
    }

    @Test
    fun invalidPayload_returnsNull() {
        val result = decoder.decode(byteArrayOf(0xFF.toByte(), 0x00), -60, TransportType.BLUETOOTH_LEGACY, System.currentTimeMillis())
        assertNull(result)
    }

    @Test
    fun basicId_message_parses() {
        // Minimal Basic ID message: type 0, serial number type, ID "TESTDRONE123"
        val payload = ByteArray(25)
        payload[0] = 0x00 // Basic ID, protocol 0
        payload[1] = 0x10 // ID type = Serial Number (1)
        val id = "TESTDRONE123".toByteArray(Charsets.US_ASCII)
        System.arraycopy(id, 0, payload, 2, id.size)

        val result = decoder.decode(payload, -55, TransportType.BLUETOOTH_LEGACY, System.currentTimeMillis())
        assertNotNull(result)
        assertEquals(RemoteIdMessageType.BASIC_ID, result!!.messageType)
        assertEquals("TESTDRONE123", result.uaId)
    }

    @Test
    fun location_message_parsesCoordinates() {
        val payload = ByteArray(25)
        payload[0] = 0x01 // Location
        payload[1] = 0x00 // status
        // Direction 90 degrees (little endian)
        payload[2] = 90
        payload[3] = 0
        // Speed horizontal 40 * 0.25 = 10 m/s
        payload[4] = 40
        payload[5] = 0
        // Latitude 52.370216 * 1e7 ≈ 523702160
        val lat = 523702160
        payload[6] = (lat and 0xFF).toByte()
        payload[7] = ((lat shr 8) and 0xFF).toByte()
        payload[8] = ((lat shr 16) and 0xFF).toByte()
        payload[9] = ((lat shr 24) and 0xFF).toByte()
        // Longitude 4.895168 * 1e7 ≈ 48951680
        val lon = 48951680
        payload[10] = (lon and 0xFF).toByte()
        payload[11] = ((lon shr 8) and 0xFF).toByte()
        payload[12] = ((lon shr 16) and 0xFF).toByte()
        payload[13] = ((lon shr 24) and 0xFF).toByte()

        val result = decoder.decode(payload, -60, TransportType.BLUETOOTH_LEGACY, System.currentTimeMillis())
        assertNotNull(result)
        assertEquals(RemoteIdMessageType.LOCATION, result!!.messageType)
        assertNotNull(result.latitude)
        assertNotNull(result.longitude)
        assertEquals(52.37, result.latitude!!, 0.01)
        assertEquals(4.895, result.longitude!!, 0.01)
        assertEquals(10.0, result.speedHorizontal!!, 0.1)
        assertEquals(90.0, result.heading!!, 0.1)
    }
}
