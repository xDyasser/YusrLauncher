package dev.yusr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerWindowsTest {

    private fun at(hour: Int, minute: Int) = hour * 60 + minute

    /** A plausible day, so the windows under test are the ones a person would actually see. */
    private val timetable = PrayerTimetable(
        mapOf(
            Prayer.FAJR to at(4, 10),
            Prayer.SUNRISE to at(5, 38),
            Prayer.DHUHR to at(12, 22),
            Prayer.ASR to at(15, 41),
            Prayer.MAGHRIB to at(19, 4),
            Prayer.ISHA to at(20, 34),
        ),
    )

    @Test
    fun `five prayers give five windows`() {
        val windows = PrayerWindows.windowsFor(timetable, PrayerWindowConfig.DEFAULT)
        assertEquals(5, windows.size)
        assertEquals(listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA), windows.map { it.prayer })
    }

    @Test
    fun `combining gives three windows, not five`() {
        val windows = PrayerWindows.windowsFor(
            timetable,
            PrayerWindowConfig(combineDhuhrAsr = true, combineMaghribIsha = true),
        )

        assertEquals(3, windows.size)
        val dhuhr = windows[1]
        assertEquals(Prayer.DHUHR, dhuhr.prayer)
        assertEquals(Prayer.ASR, dhuhr.through)
        // One span from dhuhr through the end of asr, rather than two with a gap between.
        assertEquals(at(12, 22), dhuhr.startMinuteOfDay)
        assertEquals(at(16, 1), dhuhr.endMinuteOfDay)
        assertEquals("dhuhr and asr", dhuhr.label)
    }

    @Test
    fun `a window covers the minutes after the adhan`() {
        val windows = PrayerWindows.windowsFor(timetable, PrayerWindowConfig(minutesAfter = 20))
        val maghrib = windows.first { it.prayer == Prayer.MAGHRIB }

        assertFalse(PrayerWindows.isActive(maghrib, at(19, 3)))
        assertTrue(PrayerWindows.isActive(maghrib, at(19, 4)))
        assertTrue(PrayerWindows.isActive(maghrib, at(19, 23)))
        // The end is exclusive, so the phone comes back exactly on time.
        assertFalse(PrayerWindows.isActive(maghrib, at(19, 24)))
    }

    @Test
    fun `minutes before the adhan are covered too`() {
        val windows = PrayerWindows.windowsFor(timetable, PrayerWindowConfig(minutesBefore = 10))
        val fajr = windows.first { it.prayer == Prayer.FAJR }

        assertTrue(PrayerWindows.isActive(fajr, at(4, 0)))
        assertFalse(PrayerWindows.isActive(fajr, at(3, 59)))
    }

    @Test
    fun `a window running past midnight is active on both sides of it`() {
        val late = PrayerTimetable(timetable.minutes + (Prayer.ISHA to at(23, 50)))
        val windows = PrayerWindows.windowsFor(late, PrayerWindowConfig(minutesAfter = 30))
        val isha = windows.first { it.prayer == Prayer.ISHA }

        assertTrue(isha.spansMidnight)
        assertTrue(PrayerWindows.isActive(isha, at(23, 55)))
        assertTrue(PrayerWindows.isActive(isha, at(0, 10)))
        assertFalse(PrayerWindows.isActive(isha, at(0, 20)))
    }

    @Test
    fun `minutes until the end are reported across midnight`() {
        val late = PrayerTimetable(timetable.minutes + (Prayer.ISHA to at(23, 50)))
        val isha = PrayerWindows.windowsFor(late, PrayerWindowConfig(minutesAfter = 30))
            .first { it.prayer == Prayer.ISHA }

        assertEquals(25, PrayerWindows.minutesUntilEnd(isha, at(23, 55)))
        assertEquals(10, PrayerWindows.minutesUntilEnd(isha, at(0, 10)))
    }

    @Test
    fun `nothing is active between the windows`() {
        val windows = PrayerWindows.windowsFor(timetable, PrayerWindowConfig.DEFAULT)
        assertNull(PrayerWindows.activeWindow(windows, at(10, 0)))
        assertNull(PrayerWindows.minutesUntilEnd(windows.first(), at(10, 0)))
    }

    @Test
    fun `the active window is found by the time of day`() {
        val windows = PrayerWindows.windowsFor(timetable, PrayerWindowConfig.DEFAULT)
        assertEquals(Prayer.ASR, PrayerWindows.activeWindow(windows, at(15, 45))?.prayer)
    }

    @Test
    fun `the next prayer is the one after the current time`() {
        val next = PrayerTimes.next(timetable, at(13, 0))
        assertEquals(Prayer.ASR, next.prayer)
        assertEquals(161, next.minutesAway)
        assertFalse(next.tomorrow)
    }

    @Test
    fun `after isha the next prayer is tomorrow's fajr`() {
        val next = PrayerTimes.next(timetable, at(22, 0))
        assertEquals(Prayer.FAJR, next.prayer)
        assertTrue(next.tomorrow)
        // 2 hours to midnight, then 4h10 to fajr.
        assertEquals(2 * 60 + at(4, 10), next.minutesAway)
    }

    @Test
    fun `sunrise is never offered as the next prayer`() {
        val next = PrayerTimes.next(timetable, at(5, 0))
        assertEquals(Prayer.DHUHR, next.prayer)
    }

    @Test
    fun `the current prayer is the last one whose time has passed`() {
        assertEquals(Prayer.FAJR, PrayerTimes.current(timetable, at(5, 0)))
        assertEquals(Prayer.ASR, PrayerTimes.current(timetable, at(16, 0)))
        assertEquals(Prayer.ISHA, PrayerTimes.current(timetable, at(23, 0)))
        // Before fajr the day still belongs to last night's isha.
        assertEquals(Prayer.ISHA, PrayerTimes.current(timetable, at(2, 0)))
    }
}
