package dev.yusr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ---- the Shīʿī reckoning -----------------------------------------------------------------

    /**
     * The same day under the Jaʿfarī rules. Both aqdām are counted from zawāl at 13:12: two feet
     * of shadow at 14:05, four at 14:58 — well before the one-shadow moment at 15:00 that a
     * timetable prints ʿaṣr at, which is exactly why ʿaṣr's preferred time is measured from zawāl
     * rather than from that entry.
     */
    private val shia = Fadila.ends(
        timetable = timetable,
        standardAsrMinuteOfDay = 900,
        shafaqMinuteOfDay = 1312,
        branch = Madhab.Branch.SHIA,
        qadamayniMinuteOfDay = 845,
        aqdamAsrMinuteOfDay = 898,
    )

    private val shiaStarts = Fadila.starts(timetable, Madhab.Branch.SHIA)

    @Test
    fun `asr's preferred time ends at four aqdam, not halfway to maghrib`() {
        assertEquals(898, shia.getValue(Prayer.ASR))
        // The Sunnī half-way figure, 18:41, is not what a Shīʿī reader is shown.
        assertEquals(1121, ends.getValue(Prayer.ASR))
    }

    @Test
    fun `dhuhr's preferred time moves with it, so the pair stays in order`() {
        assertEquals(845, shia.getValue(Prayer.DHUHR))
        assertTrue(shia.getValue(Prayer.DHUHR) < shia.getValue(Prayer.ASR))
    }

    @Test
    fun `isha's preferred time ends a third of the way through the night`() {
        // The night runs 20:24 to 04:41 — 497 minutes — so its first third closes at 23:09,
        // rather than at the 00:32 midnight that ends the prayer's time altogether.
        assertEquals(1389, shia.getValue(Prayer.ISHA))
        assertEquals(32, ends.getValue(Prayer.ISHA))
    }

    /**
     * The bug this replaced in miniature: measured from ʿaṣr's own 16:58 entry, a preferred time
     * ending at 14:58 is a window that closed before it opened, and the plain reading of it was a
     * day and a half of preferred time left.
     */
    @Test
    fun `asr's preferred time is counted from zawal, not from the timetable's asr`() {
        // Half an hour after zawāl there are still fifty-three minutes of it.
        assertEquals(53, Fadila.remaining(shia, timetable, Prayer.ASR, 845, shiaStarts))
        // And once the four aqdām have passed, nothing.
        assertNull(Fadila.remaining(shia, timetable, Prayer.ASR, 900, shiaStarts))
        // Before zawāl the window has not opened.
        assertNull(Fadila.remaining(shia, timetable, Prayer.ASR, 700, shiaStarts))
    }

    @Test
    fun `a day the aqdam shadow never falls leaves the pair out rather than borrowing the other rule`() {
        val polar = Fadila.ends(
            timetable = timetable,
            standardAsrMinuteOfDay = 900,
            shafaqMinuteOfDay = 1312,
            branch = Madhab.Branch.SHIA,
            qadamayniMinuteOfDay = null,
            aqdamAsrMinuteOfDay = null,
        )
        assertNull(polar[Prayer.DHUHR])
        assertNull(polar[Prayer.ASR])
    }

    /** Everything not named above is the same boundary under both, and is computed once. */
    @Test
    fun `the other preferred times do not move with the school`() {
        listOf(Prayer.FAJR, Prayer.MAGHRIB).forEach { prayer ->
            assertEquals(prayer.name, ends[prayer], shia[prayer])
        }
    }

    /** Nothing is redirected under the Sunnī schools: every faḍīla starts at its own prayer. */
    @Test
    fun `the sunni reckoning needs no starts of its own`() {
        assertTrue(Fadila.starts(timetable, Madhab.Branch.SUNNI).isEmpty())
    }
}
