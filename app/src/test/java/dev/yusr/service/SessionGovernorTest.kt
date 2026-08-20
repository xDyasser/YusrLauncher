package dev.yusr.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionGovernorTest {

    private val start = 1_000_000L
    private val minute = 60_000L

    @Before
    fun reset() = SessionGovernor.clear()

    @Test
    fun `a grant runs for the minutes it was given`() {
        SessionGovernor.grant("com.example", sessionMinutes = 5, wasBypass = false, now = start)

        assertTrue(SessionGovernor.isGrantedFor("com.example", start + 4 * minute))
        assertTrue(SessionGovernor.isExpiredFor("com.example", start + 5 * minute))
    }

    @Test
    fun `time with the screen off is not charged to the session`() {
        SessionGovernor.grant("com.example", sessionMinutes = 5, wasBypass = false, now = start)

        SessionGovernor.pause(start + minute)
        val wake = start + 60 * minute
        SessionGovernor.resume(wake)

        // One minute was used before the phone was put down; four are still owed.
        assertTrue(SessionGovernor.isGrantedFor("com.example", wake + 3 * minute))
        assertEquals(4 * 60L, SessionGovernor.secondsLeft(wake))
        assertTrue(SessionGovernor.isExpiredFor("com.example", wake + 4 * minute))
    }

    @Test
    fun `a paused session is read at the moment it was paused`() {
        SessionGovernor.grant("com.example", sessionMinutes = 5, wasBypass = false, now = start)
        SessionGovernor.pause(start + minute)

        assertTrue(SessionGovernor.isGrantedFor("com.example", start + 90 * minute))
        assertFalse(SessionGovernor.isExpiredFor("com.example", start + 90 * minute))
    }

    @Test
    fun `pausing twice does not hand back the first sleep twice`() {
        SessionGovernor.grant("com.example", sessionMinutes = 5, wasBypass = false, now = start)

        SessionGovernor.pause(start + minute)
        SessionGovernor.pause(start + 30 * minute)
        SessionGovernor.resume(start + 61 * minute)

        assertEquals(4 * 60L, SessionGovernor.secondsLeft(start + 61 * minute))
    }

    @Test
    fun `resuming without a pause changes nothing`() {
        SessionGovernor.grant("com.example", sessionMinutes = 5, wasBypass = false, now = start)
        SessionGovernor.resume(start + 3 * minute)

        assertEquals(2 * 60L, SessionGovernor.secondsLeft(start + 3 * minute))
    }

    @Test
    fun `a new grant starts unpaused`() {
        SessionGovernor.grant("com.example", sessionMinutes = 5, wasBypass = false, now = start)
        SessionGovernor.pause(start + minute)
        SessionGovernor.grant("com.other", sessionMinutes = 5, wasBypass = false, now = start + 10 * minute)

        assertTrue(SessionGovernor.isExpiredFor("com.other", start + 15 * minute))
    }
}
