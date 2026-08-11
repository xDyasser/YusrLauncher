package dev.minimalist.domain

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestScheduleTest {

    private val morning = 9
    private val evening = 18

    @Test
    fun `before the morning slot the last slot is yesterday evening`() {
        val now = LocalDateTime.of(2026, 8, 9, 7, 30)
        assertEquals(
            LocalDateTime.of(2026, 8, 8, 18, 0),
            DigestSchedule.mostRecentSlot(now, morning, evening),
        )
    }

    @Test
    fun `between the slots the last slot is this morning`() {
        val now = LocalDateTime.of(2026, 8, 9, 13, 0)
        assertEquals(
            LocalDateTime.of(2026, 8, 9, 9, 0),
            DigestSchedule.mostRecentSlot(now, morning, evening),
        )
    }

    @Test
    fun `after the evening slot the last slot is this evening`() {
        val now = LocalDateTime.of(2026, 8, 9, 23, 59)
        assertEquals(
            LocalDateTime.of(2026, 8, 9, 18, 0),
            DigestSchedule.mostRecentSlot(now, morning, evening),
        )
    }

    @Test
    fun `a job running late still delivers for the slot it missed`() {
        val now = LocalDateTime.of(2026, 8, 9, 9, 47)
        val lastDelivered = LocalDateTime.of(2026, 8, 8, 18, 0)
        assertTrue(DigestSchedule.shouldDeliver(now, lastDelivered, morning, evening))
    }

    @Test
    fun `a slot is not delivered twice`() {
        val now = LocalDateTime.of(2026, 8, 9, 11, 0)
        val lastDelivered = LocalDateTime.of(2026, 8, 9, 9, 2)
        assertFalse(DigestSchedule.shouldDeliver(now, lastDelivered, morning, evening))
    }

    @Test
    fun `the first ever run delivers`() {
        val now = LocalDateTime.of(2026, 8, 9, 11, 0)
        assertTrue(DigestSchedule.shouldDeliver(now, null, morning, evening))
    }
}
