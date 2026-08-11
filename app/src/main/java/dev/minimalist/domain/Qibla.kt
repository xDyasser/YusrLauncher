package dev.minimalist.domain

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The direction of the Kaʿba from anywhere on earth, and how far away it is.
 *
 * The bearing is the initial heading of the great circle — the shortest path over a sphere —
 * which is the direction every published qibla agrees on. It is emphatically not the direction
 * you would get by drawing a straight line on a flat map: from London that would point
 * south-east-by-south, where the true qibla is nearer due east-south-east, and the further from
 * the equator you are the further apart the two answers drift.
 */
object Qibla {

    /** The Kaʿba. */
    const val MAKKAH_LATITUDE = 21.4224779
    const val MAKKAH_LONGITUDE = 39.1564444

    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * The initial great-circle bearing to the Kaʿba, in degrees clockwise from true north,
     * normalised to [0, 360).
     */
    fun bearing(latitude: Double, longitude: Double): Double {
        val lat = Math.toRadians(latitude)
        val makkahLat = Math.toRadians(MAKKAH_LATITUDE)
        val deltaLon = Math.toRadians(MAKKAH_LONGITUDE - longitude)

        val y = sin(deltaLon) * cos(makkahLat)
        val x = cos(lat) * sin(makkahLat) - sin(lat) * cos(makkahLat) * cos(deltaLon)
        val degrees = Math.toDegrees(atan2(y, x))
        return (degrees % 360.0 + 360.0) % 360.0
    }

    /** Great-circle distance to the Kaʿba in kilometres, by the haversine formula. */
    fun distanceKm(latitude: Double, longitude: Double): Double {
        val lat = Math.toRadians(latitude)
        val makkahLat = Math.toRadians(MAKKAH_LATITUDE)
        val deltaLat = makkahLat - lat
        val deltaLon = Math.toRadians(MAKKAH_LONGITUDE - longitude)

        val a = sin(deltaLat / 2).let { it * it } +
            cos(lat) * cos(makkahLat) * sin(deltaLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a).coerceAtMost(1.0))
    }

    /**
     * The sixteen-point compass name for a bearing — "SE", "ESE" and so on.
     *
     * A number alone is hard to act on standing in a room; the name is what lets someone check
     * the needle against the wall they already suspect is the right one.
     */
    fun compassPoint(bearing: Double): String {
        val normalised = (bearing % 360.0 + 360.0) % 360.0
        val index = (normalised / 22.5).roundToInt() % 16
        return POINTS[index]
    }

    private val POINTS = listOf(
        "N", "NNE", "NE", "ENE",
        "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW",
        "W", "WNW", "NW", "NNW",
    )
}
