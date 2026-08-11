package dev.yusr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncedTimesTest {

    private fun day(
        fajr: Int = 4 * 60,
        sunrise: Int = 5 * 60 + 30,
        dhuhr: Int = 12 * 60,
        asr: Int = 15 * 60 + 30,
        maghrib: Int = 19 * 60,
        isha: Int = 20 * 60 + 30,
    ) = mapOf(
        Prayer.FAJR to fajr,
        Prayer.SUNRISE to sunrise,
        Prayer.DHUHR to dhuhr,
        Prayer.ASR to asr,
        Prayer.MAGHRIB to maghrib,
        Prayer.ISHA to isha,
    )

    @Test
    fun `an ordinary day is left exactly as it arrived`() {
        val fetched = day()
        assertTrue(SyncedTimes.isCoherent(fetched))
        assertEquals(fetched, SyncedTimes.repair(fetched, PrayerTimetable(day(fajr = 1))))
    }

    /** The Jafari case: Aladhan reports the pairs as prayed together, not as begun together. */
    @Test
    fun `asr at dhuhr and isha at maghrib are taken from the solver instead`() {
        val fetched = day(asr = 12 * 60, isha = 19 * 60)
        assertFalse(SyncedTimes.isCoherent(fetched))

        val computed = PrayerTimetable(day(asr = 15 * 60 + 44, isha = 20 * 60 + 51))
        val repaired = SyncedTimes.repair(fetched, computed)

        assertEquals(15 * 60 + 44, repaired.getValue(Prayer.ASR))
        assertEquals(20 * 60 + 51, repaired.getValue(Prayer.ISHA))
        // Everything the fetch got right is kept, so the masjid's fajr survives.
        assertEquals(4 * 60, repaired.getValue(Prayer.FAJR))
        assertEquals(19 * 60, repaired.getValue(Prayer.MAGHRIB))
        assertTrue(SyncedTimes.isCoherent(repaired))
    }

    @Test
    fun `a day that cannot be patched is given up for the computed one`() {
        // Every fetched time collapsed onto midday: no ordering survives, and the solver's
        // answers cannot be threaded between them.
        val fetched = day(fajr = 12 * 60, sunrise = 12 * 60, dhuhr = 12 * 60, asr = 12 * 60, maghrib = 12 * 60, isha = 12 * 60)
        val computed = PrayerTimetable(day())

        assertEquals(computed.minutes, SyncedTimes.repair(fetched, computed))
    }

    @Test
    fun `a missing time is filled in rather than dropped`() {
        val fetched = day().filterKeys { it != Prayer.ASR }
        val computed = PrayerTimetable(day(asr = 15 * 60 + 10))

        val repaired = SyncedTimes.repair(fetched, computed)

        assertEquals(15 * 60 + 10, repaired.getValue(Prayer.ASR))
    }
}
