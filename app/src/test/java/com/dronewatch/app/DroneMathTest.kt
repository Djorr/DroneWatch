package com.dronewatch.app

import com.dronewatch.app.domain.model.DroneDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DroneMathTest {

    @Test
    fun haversine_samePoint_isZero() {
        val d = DroneDetection.haversineMeters(52.0, 4.0, 52.0, 4.0)
        assertEquals(0.0, d, 0.1)
    }

    @Test
    fun haversine_knownDistance() {
        // Approx 1 km north
        val d = DroneDetection.haversineMeters(52.0, 4.0, 52.009, 4.0)
        assertTrue(d in 900.0..1100.0)
    }

    @Test
    fun bearing_north() {
        val b = DroneDetection.bearingDegrees(52.0, 4.0, 52.1, 4.0)
        assertEquals(0.0, b, 1.0)
    }

    @Test
    fun bearing_east() {
        val b = DroneDetection.bearingDegrees(52.0, 4.0, 52.0, 4.1)
        assertEquals(90.0, b, 2.0)
    }
}
