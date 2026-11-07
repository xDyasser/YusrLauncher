package dev.yusr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class NightWakeTest {

    /** maghrib 18:00, fajr 06:00 — a twelve-hour night whose last third begins at 02:00. */
    private val day = PrayerTimetable(
        mapOf(
            Prayer.FAJR to 360,
            Prayer.SUNRISE to 450,
            Prayer.DHUHR to 720,
            Prayer.ASR to 930,
            Prayer.MAGHRIB to 1080,
            Prayer.ISHA to 1160,
        ),
    )
    private val evening = LocalDate.of(2026, 3, 1)

    @Test
    fun `the last third of a twelve-hour night begins four hours before fajr`() {
        val wake = NightWake.wakeFor(evening, day, day, NightWake.Anchor.LAST_THIRD, 0)
        assertEquals(LocalDateTime.of(2026, 3, 2, 2, 0), wake)
    }

    @Test
    fun `an hour before fajr is an hour before the next morning's fajr`() {
        val wake = NightWake.wakeFor(evening, day, day, NightWake.Anchor.FAJR, 60)
        assertEquals(LocalDateTime.of(2026, 3, 2, 5, 0), wake)
    }

    @Test
    fun `the anchor's own fajr is tomorrow's, not tonight's`() {
        val laterFajr = day.copy(minutes = day.minutes + (Prayer.FAJR to 380))
        val wake = NightWake.wakeFor(evening, day, laterFajr, NightWake.Anchor.FAJR, 0)
        assertEquals(LocalDateTime.of(2026, 3, 2, 6, 20), wake)
    }

    @Test
    fun `an alarm is never set before maghrib however far back it is asked for`() {
        // A short summer night: maghrib 21:00, fajr 03:00, last third from 01:00. Three hours
        // before it is 22:00, still after maghrib, so it stands; anything further back is held
        // at maghrib.
        val summer = day.copy(minutes = day.minutes + (Prayer.MAGHRIB to 1260) + (Prayer.FAJR to 180))
        val wake = NightWake.wakeFor(evening, summer, summer, NightWake.Anchor.LAST_THIRD, NightWake.MAX_MINUTES_BEFORE)
        assertEquals(LocalDateTime.of(2026, 3, 1, 22, 0), wake)

        val wayBack = NightWake.wakeFor(evening, summer, summer, NightWake.Anchor.LAST_THIRD, 10_000)
        assertTrue(!wayBack.isBefore(LocalDateTime.of(2026, 3, 1, 21, 0)))
    }

    @Test
    fun `in the small hours the next alarm is last night's if it has not rung`() {
        val now = LocalDateTime.of(2026, 3, 2, 1, 0)
        val next = NightWake.next(now, { day }, NightWake.Anchor.LAST_THIRD, 0)
        assertEquals(LocalDateTime.of(2026, 3, 2, 2, 0), next)
    }

    @Test
    fun `once last night's has rung the next alarm is tonight's`() {
        val now = LocalDateTime.of(2026, 3, 2, 2, 30)
        val next = NightWake.next(now, { day }, NightWake.Anchor.LAST_THIRD, 0)
        assertEquals(LocalDateTime.of(2026, 3, 3, 2, 0), next)
    }

    @Test
    fun `the next alarm is always in the future`() {
        var now = LocalDateTime.of(2026, 3, 1, 0, 0)
        repeat(48 * 4) {
            val next = NightWake.next(now, { day }, NightWake.Anchor.FAJR, 45)
            assertTrue("$now → $next", next.isAfter(now))
            now = now.plusMinutes(15)
        }
    }

    // ---- khums -------------------------------------------------------------------------------

    @Test
    fun `khums is called a week ahead and on the day`() {
        assertEquals(Khums.Reminder.WEEK_BEFORE, Khums.reminderOn(LocalDate.of(2026, 6, 8), 6, 15))
        assertEquals(Khums.Reminder.TODAY, Khums.reminderOn(LocalDate.of(2026, 6, 15), 6, 15))
        assertNull(Khums.reminderOn(LocalDate.of(2026, 6, 9), 6, 15))
    }

    @Test
    fun `a date early in January is warned about in December`() {
        assertEquals(Khums.Reminder.WEEK_BEFORE, Khums.reminderOn(LocalDate.of(2026, 12, 27), 1, 3))
    }

    @Test
    fun `the twenty-ninth of February falls on the twenty-eighth in a common year`() {
        assertEquals(Khums.Reminder.TODAY, Khums.reminderOn(LocalDate.of(2026, 2, 28), 2, 29))
        assertEquals(Khums.Reminder.TODAY, Khums.reminderOn(LocalDate.of(2028, 2, 29), 2, 29))
        assertEquals(LocalDate.of(2027, 2, 28), Khums.nextOn(LocalDate.of(2026, 3, 1), 2, 29))
    }

    @Test
    fun `the next date is this year's until it has passed`() {
        assertEquals(LocalDate.of(2026, 6, 15), Khums.nextOn(LocalDate.of(2026, 6, 15), 6, 15))
        assertEquals(LocalDate.of(2027, 6, 15), Khums.nextOn(LocalDate.of(2026, 6, 16), 6, 15))
        assertNull(Khums.nextOn(LocalDate.of(2026, 6, 16), 13, 1))
    }
}
