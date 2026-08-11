package dev.minimalist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FadilaTest {

    // A summer day in England, to the minute: fajr 04:41, sunrise 06:20, dhuhr 13:12,
    // asr 16:58, maghrib 20:24, isha 21:52.
    private val timetable = PrayerTimetable(
        mapOf(
            Prayer.FAJR to 281,
            Prayer.SUNRISE to 380,
            Prayer.DHUHR to 792,
            Prayer.ASR to 1018,
            Prayer.MAGHRIB to 1224,
            Prayer.ISHA to 1312,
        ),
    )

    // A one-shadow asr at 15:00 and the red twilight gone at 21:52.
    private val ends = Fadila.ends(timetable, standardAsrMinuteOfDay = 900, shafaqMinuteOfDay = 1312)

    @Test
    fun `fajr's preferred time runs to isfar, halfway to sunrise`() {
        assertEquals(330, ends.getValue(Prayer.FAJR)) // 05:30
    }

    @Test
    fun `dhuhr's preferred time ends when the shadow has grown by one length`() {
        assertEquals(900, ends.getValue(Prayer.DHUHR)) // 15:00
    }

    @Test
    fun `asr's preferred time ends halfway to maghrib, before the sun yellows`() {
        assertEquals(1121, ends.getValue(Prayer.ASR)) // 18:41
    }

    @Test
    fun `maghrib's preferred time ends with the red twilight`() {
        assertEquals(1312, ends.getValue(Prayer.MAGHRIB)) // 21:52
    }

    @Test
    fun `isha's preferred time ends at midnight reckoned from the night, not the clock`() {
        // The night runs 20:24 to 04:41, so its middle is 00:32 — not 00:00, and not 12 hours
        // after noon either.
        assertEquals(32, ends.getValue(Prayer.ISHA))
    }

    @Test
    fun `a prayer whose window has not opened has no time left in it`() {
        assertNull(Fadila.remaining(ends, timetable, Prayer.ASR, minuteOfDay = 900))
    }

    @Test
    fun `a preferred time already over reports nothing rather than a negative`() {
        assertNull(Fadila.remaining(ends, timetable, Prayer.FAJR, minuteOfDay = 858))
    }

    @Test
    fun `mid-afternoon there are forty-two minutes of dhuhr's preferred time left`() {
        assertEquals(42, Fadila.remaining(ends, timetable, Prayer.DHUHR, minuteOfDay = 858))
    }

    @Test
    fun `isha's preferred time survives the crossing into the next day`() {
        // Before midnight, counting down towards 00:32.
        assertEquals(142, Fadila.remaining(ends, timetable, Prayer.ISHA, minuteOfDay = 1330))
        // And after it: 00:10 is twenty-two minutes short of the end, not a day and a half past.
        assertEquals(22, Fadila.remaining(ends, timetable, Prayer.ISHA, minuteOfDay = 10))
    }

    @Test
    fun `a polar day with no twilight simply leaves maghrib out`() {
        val polar = Fadila.ends(timetable, standardAsrMinuteOfDay = 900, shafaqMinuteOfDay = null)
        assertNull(polar[Prayer.MAGHRIB])
        assertNull(Fadila.remaining(polar, timetable, Prayer.MAGHRIB, minuteOfDay = 1250))
    }

    @Test
    fun `a one-shadow asr before dhuhr is nonsense and is dropped rather than shown`() {
        val broken = Fadila.ends(timetable, standardAsrMinuteOfDay = 700, shafaqMinuteOfDay = 1312)
        assertNull(broken[Prayer.DHUHR])
    }
}
