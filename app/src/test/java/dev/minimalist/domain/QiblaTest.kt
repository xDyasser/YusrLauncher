package dev.minimalist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QiblaTest {

    @Test
    fun `the bearing from cities on four continents matches the published qibla`() {
        // Tolerance is half a degree, which is far finer than anyone can hold a phone.
        assertEquals(119.6, Qibla.bearing(52.6369, -1.1398), 0.5) // Leicester
        assertEquals(58.9, Qibla.bearing(40.7128, -74.0060), 0.5) // New York
        assertEquals(295.0, Qibla.bearing(-6.2088, 106.8456), 0.5) // Jakarta
        assertEquals(22.7, Qibla.bearing(-33.9249, 18.4241), 0.5) // Cape Town
    }

    @Test
    fun `the great circle is not the line drawn on a flat map`() {
        // The whole reason this is worth computing: from New York the qibla is north of east,
        // where a straight line on a Mercator map points south of east.
        assertTrue(Qibla.bearing(40.7128, -74.0060) < 90.0)
    }

    @Test
    fun `due north from Makkah points back due south`() {
        val bearing = Qibla.bearing(Qibla.MAKKAH_LATITUDE + 5.0, Qibla.MAKKAH_LONGITUDE)
        assertEquals(180.0, bearing, 0.01)
    }

    @Test
    fun `a bearing always lands inside one turn of the compass`() {
        var latitude = -80.0
        while (latitude <= 80.0) {
            var longitude = -180.0
            while (longitude < 180.0) {
                val bearing = Qibla.bearing(latitude, longitude)
                assertTrue("$latitude,$longitude gave $bearing", bearing >= 0.0 && bearing < 360.0)
                longitude += 15.0
            }
            latitude += 10.0
        }
    }

    @Test
    fun `the distance to the Kaaba is zero from the Kaaba`() {
        assertEquals(0.0, Qibla.distanceKm(Qibla.MAKKAH_LATITUDE, Qibla.MAKKAH_LONGITUDE), 0.001)
    }

    @Test
    fun `the distance matches the known great circle from a few cities`() {
        assertEquals(4876.0, Qibla.distanceKm(52.6369, -1.1398), 10.0)
        assertEquals(10258.0, Qibla.distanceKm(40.7128, -74.0060), 20.0)
        assertEquals(7987.0, Qibla.distanceKm(-6.2088, 106.8456), 20.0)
    }

    @Test
    fun `a bearing gets the compass point a person would use for it`() {
        assertEquals("N", Qibla.compassPoint(0.0))
        assertEquals("N", Qibla.compassPoint(359.0))
        assertEquals("E", Qibla.compassPoint(90.0))
        assertEquals("SE", Qibla.compassPoint(135.0))
        assertEquals("ESE", Qibla.compassPoint(119.6))
        assertEquals("W", Qibla.compassPoint(270.0))
        assertEquals("NNW", Qibla.compassPoint(340.0))
    }
}
