package dev.yusr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetCalculatorTest {

    private val dayStart = 1_000_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val app = "com.example.app"
    private val other = "com.example.other"

    private fun at(minutesIn: Long) = dayStart + minutesIn * minute

    @Test
    fun `finished sessions inside the day are summed`() {
        val sessions = listOf(
            SessionRecord(app, at(10), at(25)),
            SessionRecord(app, at(60), at(70)),
        )
        val usage = BudgetCalculator.usageFor(sessions, app, dayStart, at(120))

        assertEquals(25, usage.minutesUsed)
        assertEquals(2, usage.opens)
    }

    @Test
    fun `other apps do not count toward this app's budget`() {
        val sessions = listOf(
            SessionRecord(app, at(10), at(20)),
            SessionRecord(other, at(30), at(90)),
        )
        val usage = BudgetCalculator.usageFor(sessions, app, dayStart, at(120))

        assertEquals(10, usage.minutesUsed)
        assertEquals(1, usage.opens)
    }

    @Test
    fun `a session still open is charged up to now, not beyond`() {
        val sessions = listOf(SessionRecord(app, at(10), endMillis = null))
        val usage = BudgetCalculator.usageFor(sessions, app, dayStart, at(40))

        assertEquals(30, usage.minutesUsed)
    }

    @Test
    fun `a session carried over midnight only charges today's share`() {
        // Started at 23:00 yesterday, still open at 00:30 today.
        val sessions = listOf(SessionRecord(app, dayStart - hour, at(30)))
        val usage = BudgetCalculator.usageFor(sessions, app, dayStart, at(60))

        assertEquals(30, usage.minutesUsed)
    }

    @Test
    fun `a session carried over midnight does not spend an open from the new day`() {
        val sessions = listOf(SessionRecord(app, dayStart - hour, at(30)))
        val usage = BudgetCalculator.usageFor(sessions, app, dayStart, at(60))

        assertEquals(0, usage.opens)
    }

    @Test
    fun `yesterday's finished sessions are ignored entirely`() {
        val sessions = listOf(SessionRecord(app, dayStart - 2 * hour, dayStart - hour))
        val usage = BudgetCalculator.usageFor(sessions, app, dayStart, at(60))

        assertEquals(0, usage.minutesUsed)
        assertEquals(0, usage.opens)
    }

    @Test
    fun `a zero-length session contributes nothing`() {
        val sessions = listOf(SessionRecord(app, at(10), at(10)))
        val usage = BudgetCalculator.usageFor(sessions, app, dayStart, at(60))

        assertEquals(0, usage.minutesUsed)
    }

    @Test
    fun `the total spans every app`() {
        val sessions = listOf(
            SessionRecord(app, at(0), at(15)),
            SessionRecord(other, at(20), at(35)),
        )
        assertEquals(30, BudgetCalculator.totalMinutes(sessions, dayStart, at(60)))
    }

    @Test
    fun `the per-app breakdown is ordered by time spent`() {
        val sessions = listOf(
            SessionRecord(app, at(0), at(10)),
            SessionRecord(other, at(10), at(40)),
        )
        val breakdown = BudgetCalculator.minutesByPackage(sessions, dayStart, at(60))

        assertEquals(listOf(other to 30, app to 10), breakdown)
    }

    @Test
    fun `apps with under a minute are left out of the breakdown`() {
        val sessions = listOf(SessionRecord(app, at(0), dayStart + 30_000))
        assertTrue(BudgetCalculator.minutesByPackage(sessions, dayStart, at(60)).isEmpty())
    }

    @Test
    fun `a session expires exactly when its minutes run out`() {
        val granted = at(0)
        assertFalse(BudgetCalculator.isSessionExpired(granted, sessionMinutes = 5, nowMillis = at(4)))
        assertTrue(BudgetCalculator.isSessionExpired(granted, sessionMinutes = 5, nowMillis = at(5)))
    }

    @Test
    fun `remaining session seconds never go negative`() {
        val granted = at(0)
        assertEquals(120L, BudgetCalculator.secondsLeftInSession(granted, 5, at(3)))
        assertEquals(0L, BudgetCalculator.secondsLeftInSession(granted, 5, at(9)))
    }
}
