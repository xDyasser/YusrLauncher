package dev.minimalist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FastingCalendarTest {

    /** The Gregorian date on which a given Hijri day falls, so the tests read as intentions. */
    private fun gregorianFor(hijriDay: Int, hijriMonth: Int, year: Int = 1448): LocalDate {
        var date = LocalDate.of(2026, 1, 1)
        repeat(800) {
            val hijri = Hijri.of(date)
            if (hijri != null && hijri.day == hijriDay && hijri.month == hijriMonth && hijri.year == year) {
                return date
            }
            date = date.plusDays(1)
        }
        error("no Gregorian date found for $hijriDay/$hijriMonth/$year")
    }

    @Test
    fun `every day of Ramadan is a fast`() {
        val day = FastingCalendar.classify(gregorianFor(hijriDay = 12, hijriMonth = 9))
        assertEquals(FastingCalendar.Kind.RAMADAN, day.kind)
        assertTrue(day.isFast)
    }

    @Test
    fun `the thirteenth to the fifteenth are the white days`() {
        listOf(13, 14, 15).forEach { dayOfMonth ->
            val day = FastingCalendar.classify(gregorianFor(dayOfMonth, hijriMonth = 7))
            assertEquals("$dayOfMonth rajab", FastingCalendar.Kind.AYYAM_AL_BID, day.kind)
        }
        // The twelfth and the sixteenth are not, whatever weekday they land on.
        val twelfth = FastingCalendar.classify(gregorianFor(12, hijriMonth = 7))
        assertTrue(twelfth.kind != FastingCalendar.Kind.AYYAM_AL_BID)
    }

    @Test
    fun `Ashura and Arafah are named rather than left as ordinary days`() {
        assertEquals(FastingCalendar.Kind.ASHURA, FastingCalendar.classify(gregorianFor(10, 1)).kind)
        assertEquals(FastingCalendar.Kind.ARAFAH, FastingCalendar.classify(gregorianFor(9, 12)).kind)
    }

    @Test
    fun `fasting is never suggested on the Eids or the days of tashriq`() {
        val forbidden = listOf(
            gregorianFor(1, 10), // ʿĪd al-Fiṭr
            gregorianFor(10, 12), // ʿĪd al-Aḍḥā
            gregorianFor(11, 12),
            gregorianFor(12, 12),
            gregorianFor(13, 12),
        )
        forbidden.forEach { date ->
            val day = FastingCalendar.classify(date)
            assertEquals(date.toString(), FastingCalendar.Kind.FORBIDDEN, day.kind)
            assertFalse(date.toString(), day.isFast)
        }
    }

    @Test
    fun `the thirteenth of Dhu al-Hijjah is a forbidden day, not a white one`() {
        // The white days and the days of tashrīq overlap, and the prohibition is the one that
        // matters — this is the case the ordering in classify() exists for.
        assertEquals(FastingCalendar.Kind.FORBIDDEN, FastingCalendar.classify(gregorianFor(13, 12)).kind)
    }

    @Test
    fun `Mondays and Thursdays are fasts when nothing more particular applies`() {
        // 5 January 2026 is a Monday; 8 January a Thursday. Neither is in Ramaḍān or the white days.
        assertEquals(FastingCalendar.Kind.MONDAY, FastingCalendar.classify(LocalDate.of(2026, 1, 5)).kind)
        assertEquals(FastingCalendar.Kind.THURSDAY, FastingCalendar.classify(LocalDate.of(2026, 1, 8)).kind)
        assertEquals(FastingCalendar.Kind.NONE, FastingCalendar.classify(LocalDate.of(2026, 1, 7)).kind)
    }

    @Test
    fun `there is always a fast within the next fortnight`() {
        // Mondays and Thursdays alone guarantee it, so the screen never has nothing to point at.
        var date = LocalDate.of(2026, 1, 1)
        repeat(60) {
            val next = FastingCalendar.next(date, withinDays = 14)
            assertNotNull(date.toString(), next)
            date = date.plusDays(1)
        }
    }

    @Test
    fun `a fortnight of days comes back a fortnight long and in order`() {
        val days = FastingCalendar.upcoming(LocalDate.of(2026, 3, 1), days = 14)
        assertEquals(14, days.size)
        assertEquals(LocalDate.of(2026, 3, 1), days.first().date)
        assertEquals(LocalDate.of(2026, 3, 14), days.last().date)
    }
}
