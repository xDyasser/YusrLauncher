package dev.minimalist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HijriTest {

    @Test
    fun `a known date converts`() {
        val date = Hijri.of(LocalDate.of(2026, 8, 9))
        assertNotNull(date)
        assertEquals(26, date!!.day)
        assertEquals(2, date.month)
        assertEquals(1448, date.year)
        assertEquals("safar", date.monthName)
    }

    @Test
    fun `it formats the way the home screen shows it`() {
        assertEquals("26 safar 1448", Hijri.format(LocalDate.of(2026, 8, 9)))
    }

    @Test
    fun `the offset shifts the date by whole days`() {
        val plain = Hijri.of(LocalDate.of(2026, 8, 9))!!
        val ahead = Hijri.of(LocalDate.of(2026, 8, 9), offsetDays = 1)!!
        val behind = Hijri.of(LocalDate.of(2026, 8, 9), offsetDays = -1)!!

        assertEquals(plain.day + 1, ahead.day)
        assertEquals(plain.day - 1, behind.day)
    }

    @Test
    fun `an offset is the same as moving the gregorian date`() {
        assertEquals(
            Hijri.of(LocalDate.of(2026, 8, 10)),
            Hijri.of(LocalDate.of(2026, 8, 9), offsetDays = 1),
        )
    }

    @Test
    fun `ramadan is recognised`() {
        assertTrue(Hijri.isRamadan(LocalDate.of(2026, 2, 19)))
        assertFalse(Hijri.isRamadan(LocalDate.of(2026, 8, 9)))
    }

    @Test
    fun `every month of a year has a name`() {
        // Walk a full lunar year in ten-day steps; nothing should fall outside the month table.
        var date = LocalDate.of(2026, 1, 1)
        val seen = mutableSetOf<Int>()
        while (date.isBefore(LocalDate.of(2027, 1, 1))) {
            val hijri = Hijri.of(date)
            assertNotNull("no hijri date for $date", hijri)
            assertTrue(hijri!!.monthName.isNotEmpty())
            seen += hijri.month
            date = date.plusDays(10)
        }
        assertEquals(12, seen.size)
    }

    /** Out of the calendar's supported range, a missing date beats a crashing home screen. */
    @Test
    fun `a date before the calendar begins is null rather than an exception`() {
        assertNull(Hijri.of(LocalDate.of(1700, 1, 1)))
    }
}
