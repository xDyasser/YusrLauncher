package dev.yusr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerLogTest {

    /** A plain spring day: fajr 05:00, sunrise 06:20, dhuhr 12:10, ʿaṣr 15:40, maghrib 18:30, ʿishāʾ 19:50. */
    private val timetable = PrayerTimetable(
        mapOf(
            Prayer.FAJR to 300,
            Prayer.SUNRISE to 380,
            Prayer.DHUHR to 730,
            Prayer.ASR to 940,
            Prayer.MAGHRIB to 1110,
            Prayer.ISHA to 1190,
        ),
    )
    private val night = Night.of(timetable) // midnight 23:45 (1425), last third 01:30

    private val separate = timetable.entries()
    private val joined = timetable.entries(combineDhuhrAsr = true, combineMaghribIsha = true)

    private fun states(
        minuteOfDay: Int,
        prayed: Set<Prayer> = emptySet(),
        branch: Madhab.Branch = Madhab.Branch.SUNNI,
        entries: List<PrayerEntry> = separate,
        daysAgo: Int = 0,
    ) = PrayerLog.day(timetable, PrayerLog.ends(timetable, night, branch), entries, prayed, daysAgo, minuteOfDay)

    // ---- the ends ------------------------------------------------------------------------------

    @Test
    fun `fajr runs out at sunrise and the afternoon pair at maghrib for everyone`() {
        listOf(Madhab.Branch.SUNNI, Madhab.Branch.SHIA).forEach { branch ->
            val ends = PrayerLog.ends(timetable, night, branch)
            assertEquals(380, ends[Prayer.FAJR])
            assertEquals(1110, ends[Prayer.DHUHR])
            assertEquals(1110, ends[Prayer.ASR])
        }
    }

    @Test
    fun `the night prayers run out at midnight for the Shia and at isha and fajr for the Sunni`() {
        val shia = PrayerLog.ends(timetable, night, Madhab.Branch.SHIA)
        assertEquals(night.midnightMinuteOfDay, shia[Prayer.MAGHRIB])
        assertEquals(night.midnightMinuteOfDay, shia[Prayer.ISHA])

        val sunni = PrayerLog.ends(timetable, night, Madhab.Branch.SUNNI)
        assertEquals(1190, sunni[Prayer.MAGHRIB])
        assertEquals(300, sunni[Prayer.ISHA]) // tomorrow's fajr, as a minute of that day
    }

    // ---- the states ----------------------------------------------------------------------------

    @Test
    fun `before dawn everything is ahead`() {
        val day = states(minuteOfDay = 200)
        PrayerLog.OWED.forEach { assertEquals(it.name, PrayerLog.State.AHEAD, day[it]) }
        assertNull(PrayerLog.outstanding(day))
    }

    @Test
    fun `a prayer is open from its time until its end, then missed`() {
        assertEquals(PrayerLog.State.OPEN, states(310)[Prayer.FAJR])
        assertEquals(PrayerLog.State.MISSED, states(400)[Prayer.FAJR])
        assertEquals(PrayerLog.State.OPEN, states(1000)[Prayer.DHUHR])
        assertEquals(PrayerLog.State.MISSED, states(1120)[Prayer.DHUHR])
    }

    @Test
    fun `marked is marked whatever the clock says`() {
        assertEquals(PrayerLog.State.PRAYED, states(400, prayed = setOf(Prayer.FAJR))[Prayer.FAJR])
        assertEquals(PrayerLog.State.PRAYED, states(1400, prayed = setOf(Prayer.FAJR))[Prayer.FAJR])
    }

    @Test
    fun `isha stays open across midnight into the small hours of the next day`() {
        // Sunnī: ʿishāʾ runs to fajr, so at 02:00 the next day yesterday's ʿishāʾ is still open.
        assertEquals(PrayerLog.State.OPEN, states(1200)[Prayer.ISHA])
        assertEquals(PrayerLog.State.OPEN, states(120, daysAgo = 1)[Prayer.ISHA])
        assertEquals(PrayerLog.State.MISSED, states(320, daysAgo = 1)[Prayer.ISHA])
        // And today's ʿishāʾ at 02:00 today is simply not in yet.
        assertEquals(PrayerLog.State.AHEAD, states(120)[Prayer.ISHA])
    }

    @Test
    fun `under the Shia reckoning the night pair runs out at midnight on the same clock`() {
        // Midnight here is 23:45, before the day's own midnight, so nothing wraps.
        val shia = Madhab.Branch.SHIA
        assertEquals(PrayerLog.State.OPEN, states(1300, branch = shia)[Prayer.ISHA])
        assertEquals(PrayerLog.State.MISSED, states(1430, branch = shia)[Prayer.ISHA])
        assertEquals(PrayerLog.State.MISSED, states(1430, branch = shia)[Prayer.MAGHRIB])
    }

    @Test
    fun `a past day owes whatever was not marked`() {
        val yesterday = states(minuteOfDay = 900, daysAgo = 1, prayed = setOf(Prayer.FAJR, Prayer.MAGHRIB))
        assertEquals(PrayerLog.State.PRAYED, yesterday[Prayer.FAJR])
        assertEquals(PrayerLog.State.MISSED, yesterday[Prayer.DHUHR])
        assertEquals(PrayerLog.State.MISSED, yesterday[Prayer.ASR])
        assertEquals(PrayerLog.State.PRAYED, yesterday[Prayer.MAGHRIB])
        assertEquals(PrayerLog.State.MISSED, yesterday[Prayer.ISHA])
        assertEquals(3, PrayerLog.missed(yesterday))
        assertEquals(2, PrayerLog.prayed(yesterday))
    }

    @Test
    fun `a day in the future is all ahead`() {
        val tomorrow = states(minuteOfDay = 900, daysAgo = -1)
        PrayerLog.OWED.forEach { assertEquals(PrayerLog.State.AHEAD, tomorrow[it]) }
    }

    // ---- joined lines --------------------------------------------------------------------------

    @Test
    fun `on a joined line the second prayer opens with the first`() {
        // 13:00: ʿaṣr's own time (15:40) is hours away, but joined to dhuhr it is open now.
        assertEquals(PrayerLog.State.AHEAD, states(780, entries = separate)[Prayer.ASR])
        assertEquals(PrayerLog.State.OPEN, states(780, entries = joined)[Prayer.ASR])
    }

    @Test
    fun `a tap on a line marks what is markable on it and unmarks a line that is all prayed`() {
        val line = joined.first { it.prayer == Prayer.DHUHR }
        val open = states(780, entries = joined)
        assertEquals(listOf(Prayer.DHUHR, Prayer.ASR) to true, PrayerLog.toggle(line, open))

        val half = states(780, entries = joined, prayed = setOf(Prayer.DHUHR))
        assertEquals(listOf(Prayer.DHUHR, Prayer.ASR) to true, PrayerLog.toggle(line, half))

        val done = states(780, entries = joined, prayed = setOf(Prayer.DHUHR, Prayer.ASR))
        assertEquals(listOf(Prayer.DHUHR, Prayer.ASR) to false, PrayerLog.toggle(line, done))
    }

    @Test
    fun `a tap on a line whose time has not come does nothing`() {
        val line = separate.first { it.prayer == Prayer.ASR }
        val (prayers, _) = PrayerLog.toggle(line, states(780))
        assertTrue(prayers.isEmpty())
    }

    // ---- what to ask about ---------------------------------------------------------------------

    @Test
    fun `the outstanding prayer is the earliest open one, then the earliest missed one`() {
        // 16:00, nothing marked: fajr missed, dhuhr and ʿaṣr both open (dhuhr runs to maghrib)
        // — dhuhr is asked about first, because it can still be prayed on time.
        assertEquals(Prayer.DHUHR, PrayerLog.outstanding(states(960)))
        assertEquals(Prayer.ASR, PrayerLog.outstanding(states(960, prayed = setOf(Prayer.DHUHR))))
        // Both marked: back to the ledger, and fajr is the first thing owed.
        assertEquals(Prayer.FAJR, PrayerLog.outstanding(states(960, prayed = setOf(Prayer.DHUHR, Prayer.ASR))))
        // Everything so far marked: nothing to ask until maghrib.
        val caughtUp = states(960, prayed = setOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR))
        assertNull(PrayerLog.outstanding(caughtUp))
        assertFalse(PrayerLog.State.AHEAD.markable)
    }
}
