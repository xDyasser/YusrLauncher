package dev.minimalist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlackoutScheduleTest {

    private val everyDay = setOf(1, 2, 3, 4, 5, 6, 7)
    private val weekdays = setOf(1, 2, 3, 4, 5)

    private fun window(
        start: Int,
        end: Int,
        days: Set<Int> = everyDay,
        enabled: Boolean = true,
    ) = BlackoutWindowSpec(
        label = "test",
        startMinuteOfDay = start,
        endMinuteOfDay = end,
        daysOfWeek = days,
        enabled = enabled,
    )

    @Test
    fun `a same-day window covers its own hours only`() {
        val work = window(9 * 60, 17 * 60, weekdays)

        assertTrue(BlackoutSchedule.isActive(work, dayOfWeek = 3, minuteOfDay = 12 * 60))
        assertFalse(BlackoutSchedule.isActive(work, dayOfWeek = 3, minuteOfDay = 8 * 60))
        assertFalse(BlackoutSchedule.isActive(work, dayOfWeek = 3, minuteOfDay = 18 * 60))
    }

    @Test
    fun `the end minute is exclusive so the window opens exactly on time`() {
        val work = window(9 * 60, 17 * 60)
        assertTrue(BlackoutSchedule.isActive(work, 3, 9 * 60))
        assertFalse(BlackoutSchedule.isActive(work, 3, 17 * 60))
    }

    @Test
    fun `a window is inactive on days it does not cover`() {
        val work = window(9 * 60, 17 * 60, weekdays)
        assertFalse(BlackoutSchedule.isActive(work, dayOfWeek = 6, minuteOfDay = 12 * 60))
    }

    @Test
    fun `a window spanning midnight covers the evening it starts`() {
        val sleep = window(22 * 60, 7 * 60)
        assertTrue(BlackoutSchedule.isActive(sleep, dayOfWeek = 3, minuteOfDay = 23 * 60))
    }

    @Test
    fun `a window spanning midnight covers the following morning`() {
        val sleep = window(22 * 60, 7 * 60)
        assertTrue(BlackoutSchedule.isActive(sleep, dayOfWeek = 4, minuteOfDay = 2 * 60))
        assertFalse(BlackoutSchedule.isActive(sleep, dayOfWeek = 4, minuteOfDay = 8 * 60))
    }

    @Test
    fun `a midnight-spanning weekday window belongs to the day it starts on`() {
        val sleep = window(22 * 60, 7 * 60, weekdays)

        // Friday night is covered...
        assertTrue(BlackoutSchedule.isActive(sleep, dayOfWeek = 5, minuteOfDay = 23 * 60))
        // ...and so is the Saturday morning it runs into, even though Saturday is not selected.
        assertTrue(BlackoutSchedule.isActive(sleep, dayOfWeek = 6, minuteOfDay = 3 * 60))
        // But Saturday night is not.
        assertFalse(BlackoutSchedule.isActive(sleep, dayOfWeek = 6, minuteOfDay = 23 * 60))
    }

    @Test
    fun `a midnight-spanning window starting on sunday wraps to monday`() {
        val sleep = window(22 * 60, 7 * 60, setOf(7))
        assertTrue(BlackoutSchedule.isActive(sleep, dayOfWeek = 1, minuteOfDay = 3 * 60))
    }

    @Test
    fun `a disabled window is never active`() {
        val sleep = window(22 * 60, 7 * 60, enabled = false)
        assertFalse(BlackoutSchedule.isActive(sleep, 3, 23 * 60))
    }

    @Test
    fun `a window with no days is never active`() {
        val never = window(9 * 60, 17 * 60, days = emptySet())
        assertFalse(BlackoutSchedule.isActive(never, 3, 12 * 60))
    }

    @Test
    fun `minutes until the end are reported across midnight`() {
        val sleep = window(22 * 60, 7 * 60)
        assertEquals(8 * 60, BlackoutSchedule.minutesUntilEnd(sleep, dayOfWeek = 3, minuteOfDay = 23 * 60))
        assertEquals(5 * 60, BlackoutSchedule.minutesUntilEnd(sleep, dayOfWeek = 4, minuteOfDay = 2 * 60))
    }

    @Test
    fun `minutes until the end are null when nothing is in force`() {
        val work = window(9 * 60, 17 * 60)
        assertEquals(null, BlackoutSchedule.minutesUntilEnd(work, 3, 20 * 60))
    }

    @Test
    fun `day masks survive a round trip`() {
        assertEquals(weekdays, BlackoutSchedule.maskToDays(BlackoutSchedule.daysToMask(weekdays)))
        assertEquals(everyDay, BlackoutSchedule.maskToDays(BlackoutSchedule.daysToMask(everyDay)))
        assertEquals(setOf(7), BlackoutSchedule.maskToDays(BlackoutSchedule.daysToMask(setOf(7))))
    }

    @Test
    fun `the first active window wins`() {
        val windows = listOf(window(9 * 60, 17 * 60, weekdays), window(22 * 60, 7 * 60))
        val at = java.time.LocalDateTime.of(2026, 8, 12, 23, 30) // a Wednesday
        assertEquals(22 * 60, BlackoutSchedule.activeWindow(windows, at)?.startMinuteOfDay)
    }
}
