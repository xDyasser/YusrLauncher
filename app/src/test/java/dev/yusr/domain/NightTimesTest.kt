package dev.yusr.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NightTimesTest {

    /** Maghrib 18:00, fajr 05:00 — an eleven-hour night that wraps midnight, as they all do. */
    private val timetable = PrayerTimetable(
        mapOf(
            Prayer.FAJR to 5 * 60,
            Prayer.SUNRISE to 6 * 60 + 30,
            Prayer.DHUHR to 12 * 60 + 15,
            Prayer.ASR to 15 * 60 + 30,
            Prayer.MAGHRIB to 18 * 60,
            Prayer.ISHA to 19 * 60 + 30,
        ),
    )

    @Test
    fun `the night runs from maghrib to the next fajr`() {
        assertEquals(11 * 60, Night.of(timetable).lengthMinutes)
    }

    @Test
    fun `midnight is halfway through it, not at zero hundred hours`() {
        // 18:00 + 5 h 30 = 23:30, which is the point: shar'i midnight is not midnight.
        assertEquals(23 * 60 + 30, Night.of(timetable).midnightMinuteOfDay)
    }

    @Test
    fun `the last third begins two thirds of the way through, past midnight`() {
        // 18:00 + 7 h 20 = 01:20 the next morning, reported as minutes past that midnight.
        assertEquals(80, Night.of(timetable).lastThirdMinuteOfDay)
    }

    @Test
    fun `midnight agrees with where the isha fadila is taken to end`() {
        val fadila = Fadila.ends(timetable, standardAsrMinuteOfDay = 15 * 60 + 30, shafaqMinuteOfDay = null)
        assertEquals(fadila[Prayer.ISHA], Night.of(timetable).midnightMinuteOfDay)
    }

    @Test
    fun `a summer night that is barely a night still comes back inside the day`() {
        val short = PrayerTimetable(
            timetable.minutes + mapOf(Prayer.MAGHRIB to 22 * 60 + 30, Prayer.FAJR to 60),
        )
        val night = Night.of(short)
        assertEquals(2 * 60 + 30, night.lengthMinutes)
        assertEquals(23 * 60 + 45, night.midnightMinuteOfDay)
        assertEquals(10, night.lastThirdMinuteOfDay)
    }
}
