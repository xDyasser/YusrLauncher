package dev.minimalist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerListingTest {

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
    fun `uncombined, it is the five prayers at their five times`() {
        val entries = timetable.entries()
        assertEquals(5, entries.size)
        assertEquals(Prayer.entries.filter { it.isPrayer }, entries.map { it.prayer })
        entries.forEach { assertNull(it.with) }
        entries.forEach { assertNull(it.secondMinuteOfDay) }
        assertEquals(15 * 60 + 30, entries[2].minuteOfDay)
    }

    @Test
    fun `combined, it is three lines timed by the first of each pair`() {
        val entries = timetable.entries(combineDhuhrAsr = true, combineMaghribIsha = true)
        assertEquals(3, entries.size)
        assertEquals(listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.MAGHRIB), entries.map { it.prayer })
        assertEquals(listOf(null, Prayer.ASR, Prayer.ISHA), entries.map { it.with })
        assertEquals(12 * 60 + 15, entries[1].minuteOfDay)
        assertEquals(18 * 60, entries[2].minuteOfDay)
    }

    @Test
    fun `a joined line still knows when the second prayer enters on its own`() {
        val entries = timetable.entries(combineDhuhrAsr = true, combineMaghribIsha = true)
        assertEquals(15 * 60 + 30, entries[1].secondMinuteOfDay)
        assertEquals(19 * 60 + 30, entries[2].secondMinuteOfDay)
    }

    @Test
    fun `one pair may be joined without the other`() {
        val entries = timetable.entries(combineDhuhrAsr = true)
        assertEquals(4, entries.size)
        assertEquals(
            listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.MAGHRIB, Prayer.ISHA),
            entries.map { it.prayer },
        )
    }

    @Test
    fun `a joined line answers to either prayer on it`() {
        val dhuhrAsr = timetable.entries(combineDhuhrAsr = true)[1]
        assertTrue(dhuhrAsr.covers(Prayer.DHUHR))
        assertTrue(dhuhrAsr.covers(Prayer.ASR))
        assertTrue(!dhuhrAsr.covers(Prayer.MAGHRIB))
    }

    @Test
    fun `the lines match the windows the phone actually stops for`() {
        // The screen and the enforcement layer read the same two flags, so a day shown as three
        // prayers is a day the launcher stops three times. This is the whole point of the change.
        listOf(false, true).forEach { combined ->
            val entries = timetable.entries(combined, combined)
            val windows = PrayerWindows.windowsFor(
                timetable,
                PrayerWindowConfig(combineDhuhrAsr = combined, combineMaghribIsha = combined),
            )
            assertEquals(entries.size, windows.size)
            assertEquals(entries.map { it.prayer }, windows.map { it.prayer })
            assertEquals(entries.map { it.with }, windows.map { it.through })
        }
    }
}
