package dev.yusr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class FastingCalendarTest {

    private val sunni = Madhab.Branch.SUNNI
    private val shia = Madhab.Branch.SHIA

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

    private fun kind(date: LocalDate, branch: Madhab.Branch) = FastingCalendar.classify(date, 0, branch).kind

    // ---- what both branches agree on --------------------------------------------------------

    @Test
    fun `every day of Ramadan is a fast for everyone`() {
        listOf(sunni, shia).forEach { branch ->
            val day = FastingCalendar.classify(gregorianFor(hijriDay = 12, hijriMonth = 9), 0, branch)
            assertEquals(FastingCalendar.Kind.RAMADAN, day.kind)
            assertTrue(day.isFast)
        }
    }

    @Test
    fun `the thirteenth to the fifteenth are the white days`() {
        listOf(sunni, shia).forEach { branch ->
            listOf(13, 14, 15).forEach { dayOfMonth ->
                assertEquals(
                    "$dayOfMonth rajab · $branch",
                    FastingCalendar.Kind.AYYAM_AL_BID,
                    kind(gregorianFor(dayOfMonth, hijriMonth = 7), branch),
                )
            }
            // The twelfth and the sixteenth are not, whatever weekday they land on.
            assertTrue(kind(gregorianFor(12, hijriMonth = 7), branch) != FastingCalendar.Kind.AYYAM_AL_BID)
        }
    }

    @Test
    fun `a white day carries the Hijri date it was read from`() {
        val day = FastingCalendar.classify(gregorianFor(14, hijriMonth = 7))
        assertEquals(14, day.hijri?.day)
        assertEquals(7, day.hijri?.month)
    }

    @Test
    fun `Arafah is a fast in both schools`() {
        assertEquals(FastingCalendar.Kind.ARAFAH, kind(gregorianFor(9, 12), sunni))
        assertEquals(FastingCalendar.Kind.ARAFAH, kind(gregorianFor(9, 12), shia))
    }

    @Test
    fun `fasting is never suggested on either Eid`() {
        listOf(sunni, shia).forEach { branch ->
            val fitr = FastingCalendar.classify(gregorianFor(1, 10), 0, branch)
            val adha = FastingCalendar.classify(gregorianFor(10, 12), 0, branch)
            assertEquals(FastingCalendar.Kind.EID_AL_FITR, fitr.kind)
            assertEquals(FastingCalendar.Kind.EID_AL_ADHA, adha.kind)
            assertFalse(fitr.isFast)
            assertFalse(adha.isFast)
            assertTrue(fitr.kind.forbidden && adha.kind.forbidden)
        }
    }

    // ---- the Sunnī reckoning -----------------------------------------------------------------

    @Test
    fun `Ashura is a fast under the Sunni schools`() {
        val day = FastingCalendar.classify(gregorianFor(10, 1), 0, sunni)
        assertEquals(FastingCalendar.Kind.ASHURA, day.kind)
        assertTrue(day.isFast)
    }

    @Test
    fun `the days of tashriq are forbidden under the Sunni schools`() {
        listOf(11, 12, 13).forEach { dayOfMonth ->
            val day = FastingCalendar.classify(gregorianFor(dayOfMonth, 12), 0, sunni)
            assertEquals(FastingCalendar.Kind.TASHRIQ, day.kind)
            assertFalse(day.isFast)
        }
    }

    @Test
    fun `the thirteenth of Dhu al-Hijjah is a forbidden day, not a white one, for a Sunni`() {
        // The white days and the days of tashrīq overlap, and the prohibition is the one that
        // matters — this is the case the ordering in classify() exists for.
        assertEquals(FastingCalendar.Kind.TASHRIQ, kind(gregorianFor(13, 12), sunni))
    }

    @Test
    fun `Mondays and Thursdays are fasts when nothing more particular applies`() {
        // 5 January 2026 is a Monday; 8 January a Thursday. Neither is in Ramaḍān or the white days.
        assertEquals(FastingCalendar.Kind.MONDAY, kind(LocalDate.of(2026, 1, 5), sunni))
        assertEquals(FastingCalendar.Kind.THURSDAY, kind(LocalDate.of(2026, 1, 8), sunni))
        assertEquals(FastingCalendar.Kind.NONE, kind(LocalDate.of(2026, 1, 7), sunni))
    }

    @Test
    fun `the Sunni calendar knows none of the Shia named days`() {
        assertTrue(kind(gregorianFor(27, 7), sunni) in setOf(FastingCalendar.Kind.NONE, FastingCalendar.Kind.MONDAY, FastingCalendar.Kind.THURSDAY))
        assertEquals(FastingCalendar.Kind.AYYAM_AL_BID, kind(gregorianFor(15, 8), sunni))
        assertTrue(kind(gregorianFor(18, 12), sunni) in setOf(FastingCalendar.Kind.NONE, FastingCalendar.Kind.MONDAY, FastingCalendar.Kind.THURSDAY))
    }

    // ---- the Shīʿī reckoning -----------------------------------------------------------------

    @Test
    fun `Ashura is imsak and not a fast under the Shia schools`() {
        val day = FastingCalendar.classify(gregorianFor(10, 1), 0, shia)
        assertEquals(FastingCalendar.Kind.ASHURA_IMSAK, day.kind)
        assertFalse(day.isFast)
        // Not forbidden either: it is shown, it is simply not offered as a fast to mark.
        assertFalse(day.kind.forbidden)
    }

    @Test
    fun `Monday is never a fast under the Shia schools`() {
        // Every Monday of 2026 that is not Ramaḍān, a white day or a named day comes back ordinary.
        var date = LocalDate.of(2026, 1, 5)
        repeat(52) {
            val day = FastingCalendar.classify(date, 0, shia)
            assertTrue(date.toString(), day.kind != FastingCalendar.Kind.MONDAY)
            date = date.plusWeeks(1)
        }
    }

    @Test
    fun `the Shia named days are named`() {
        assertEquals(FastingCalendar.Kind.MABATH, kind(gregorianFor(27, 7), shia))
        assertEquals(FastingCalendar.Kind.MID_SHABAN, kind(gregorianFor(15, 8), shia))
        assertEquals(FastingCalendar.Kind.DAHW_AL_ARD, kind(gregorianFor(25, 11), shia))
        assertEquals(FastingCalendar.Kind.GHADIR, kind(gregorianFor(18, 12), shia))
        assertEquals(FastingCalendar.Kind.MUBAHALA, kind(gregorianFor(24, 12), shia))
    }

    @Test
    fun `the fifteenth of Shaban is itself before it is a white day`() {
        assertEquals(FastingCalendar.Kind.MID_SHABAN, kind(gregorianFor(15, 8), shia))
        assertEquals(FastingCalendar.Kind.AYYAM_AL_BID, kind(gregorianFor(14, 8), shia))
    }

    @Test
    fun `the days of tashriq are ordinary for a Shia who is not at Mina`() {
        assertFalse(kind(gregorianFor(11, 12), shia).forbidden)
        assertFalse(kind(gregorianFor(12, 12), shia).forbidden)
        // And the thirteenth is the white day it is.
        assertEquals(FastingCalendar.Kind.AYYAM_AL_BID, kind(gregorianFor(13, 12), shia))
    }

    @Test
    fun `the first and last Thursday and the middle Wednesday are the Shia monthly fasts`() {
        // Walk a whole Hijri month and collect what it says. Jumādā al-Ūlā, because it has no
        // named day that could land on a Thursday and swallow it — Rajab's 27th could.
        val first = gregorianFor(1, 5)
        val days = FastingCalendar.upcoming(first, 30, 0, shia).filter { it.hijri?.month == 5 }

        val firstThursdays = days.filter { it.kind == FastingCalendar.Kind.FIRST_THURSDAY }
        val lastThursdays = days.filter { it.kind == FastingCalendar.Kind.LAST_THURSDAY }
        val wednesdays = days.filter { it.kind == FastingCalendar.Kind.MIDDLE_WEDNESDAY }

        assertEquals(1, firstThursdays.size)
        assertEquals(1, lastThursdays.size)
        assertEquals(1, wednesdays.size)

        assertEquals(DayOfWeek.THURSDAY, firstThursdays.single().date.dayOfWeek)
        assertTrue(firstThursdays.single().hijri!!.day <= 7)

        assertEquals(DayOfWeek.THURSDAY, lastThursdays.single().date.dayOfWeek)
        // No Thursday of the month after it.
        assertTrue(days.none { it.date.dayOfWeek == DayOfWeek.THURSDAY && it.date > lastThursdays.single().date })

        assertEquals(DayOfWeek.WEDNESDAY, wednesdays.single().date.dayOfWeek)
        assertTrue(wednesdays.single().hijri!!.day in 11..17)

        // And no Thursday in between is a fast on its own account.
        val middleThursdays = days.filter {
            it.date.dayOfWeek == DayOfWeek.THURSDAY && it.hijri!!.day in 8..15 &&
                it.kind !in setOf(FastingCalendar.Kind.AYYAM_AL_BID, FastingCalendar.Kind.LAST_THURSDAY)
        }
        middleThursdays.forEach { assertEquals(it.date.toString(), FastingCalendar.Kind.NONE, it.kind) }
    }

    @Test
    fun `a month with a Thursday on the thirteenth reads it as a white day`() {
        // Where they collide the white day wins, because that is the more particular thing to
        // say — and because the first Thursday test above would otherwise count two.
        val collision = (0 until 400).asSequence()
            .map { LocalDate.of(2026, 1, 1).plusDays(it.toLong()) }
            .first { date ->
                val hijri = Hijri.of(date)
                hijri != null && hijri.day in 13..15 && date.dayOfWeek == DayOfWeek.THURSDAY && hijri.month !in setOf(8, 9, 12)
            }
        assertEquals(FastingCalendar.Kind.AYYAM_AL_BID, kind(collision, shia))
    }

    // ---- the two helpers ---------------------------------------------------------------------

    @Test
    fun `there is always a fast within the next fortnight for a Sunni`() {
        // Mondays and Thursdays alone guarantee it, so the screen never has nothing to point at.
        var date = LocalDate.of(2026, 1, 1)
        repeat(60) {
            assertNotNull(date.toString(), FastingCalendar.next(date, withinDays = 14, branch = sunni))
            date = date.plusDays(1)
        }
    }

    @Test
    fun `there is always a fast within three weeks for a Shia`() {
        // The white days and the three monthly ones leave no gap longer than that between them.
        var date = LocalDate.of(2026, 1, 1)
        repeat(400) {
            assertNotNull(date.toString(), FastingCalendar.next(date, withinDays = 21, branch = shia))
            date = date.plusDays(1)
        }
    }

    @Test
    fun `next skips the days that are not fasts`() {
        // Ask from the day before ʿĀshūrāʾ as a Shia: the imsāk day is not a fast and is passed over.
        val eve = gregorianFor(9, 1)
        val next = FastingCalendar.next(eve, withinDays = 40, branch = shia)
        assertNotNull(next)
        assertTrue(next!!.kind != FastingCalendar.Kind.ASHURA_IMSAK)
        assertNull(FastingCalendar.next(eve, withinDays = 0, branch = shia))
    }

    @Test
    fun `a fortnight of days comes back a fortnight long and in order`() {
        val days = FastingCalendar.upcoming(LocalDate.of(2026, 3, 1), days = 14)
        assertEquals(14, days.size)
        assertEquals(LocalDate.of(2026, 3, 1), days.first().date)
        assertEquals(LocalDate.of(2026, 3, 14), days.last().date)
    }

    @Test
    fun `a stored kind name from an earlier version still resolves`() {
        assertEquals(FastingCalendar.Kind.MONDAY, FastingCalendar.Kind.named("MONDAY"))
        assertEquals(FastingCalendar.Kind.AYYAM_AL_BID, FastingCalendar.Kind.named("AYYAM_AL_BID"))
        assertNull(FastingCalendar.Kind.named("FORBIDDEN"))
    }
}
