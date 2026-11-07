package dev.yusr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RamadanTest {

    /** fajr 04:30, sunrise 06:00, dhuhr 12:15, ʿaṣr 15:45, maghrib 18:40, ʿishāʾ 20:00. */
    private val timetable = PrayerTimetable(
        mapOf(
            Prayer.FAJR to 270,
            Prayer.SUNRISE to 360,
            Prayer.DHUHR to 735,
            Prayer.ASR to 945,
            Prayer.MAGHRIB to 1120,
            Prayer.ISHA to 1200,
        ),
    )

    @Test
    fun `imsak is ten minutes before fajr`() {
        assertEquals(260, Ramadan.imsak(timetable))
    }

    @Test
    fun `in the small hours imsak is next`() {
        val countdown = Ramadan.countdown(timetable, minuteOfDay = 200)
        assertEquals(Ramadan.Next.IMSAK, countdown.next)
        assertEquals(60, countdown.minutesAway)
    }

    @Test
    fun `from imsak to maghrib iftar is next`() {
        assertEquals(Ramadan.Next.IFTAR, Ramadan.countdown(timetable, 260).next)
        val noon = Ramadan.countdown(timetable, 720)
        assertEquals(Ramadan.Next.IFTAR, noon.next)
        assertEquals(1120, noon.minuteOfDay)
        assertEquals(400, noon.minutesAway)
    }

    @Test
    fun `after maghrib tomorrow's imsak is next, across midnight`() {
        val evening = Ramadan.countdown(timetable, 1300)
        assertEquals(Ramadan.Next.IMSAK, evening.next)
        assertEquals(260, evening.minuteOfDay)
        assertEquals(260 + 1440 - 1300, evening.minutesAway)
    }

    @Test
    fun `the countdown is always ahead, never at zero`() {
        (0 until 1440).forEach { minute ->
            assertTrue("$minute", Ramadan.countdown(timetable, minute).minutesAway > 0)
        }
    }

    @Test
    fun `a fajr just after midnight puts imsak the evening before`() {
        val polar = timetable.copy(minutes = timetable.minutes + (Prayer.FAJR to 5))
        assertEquals(1435, Ramadan.imsak(polar))
    }
}
