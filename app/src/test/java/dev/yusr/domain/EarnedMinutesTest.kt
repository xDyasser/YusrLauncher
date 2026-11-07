package dev.yusr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EarnedMinutesTest {

    // ---- what the day earns ------------------------------------------------------------------

    @Test
    fun `nothing done is nothing earned`() {
        val today = EarnedMinutes.earned(0, 0, 0, cap = 30)
        assertEquals(0, today.total)
        assertFalse(today.capped)
    }

    @Test
    fun `each source earns at its own rate`() {
        val today = EarnedMinutes.earned(prayersMarked = 2, beads = 70, ayatRead = 10, cap = 120)
        assertEquals(10, today.fromPrayer) // 2 × 5
        assertEquals(4, today.fromDhikr) // two full sets of 33, the 4 beads over earn nothing
        assertEquals(3, today.fromQuran) // 10 / 3, rounded down
        assertEquals(17, today.total)
    }

    @Test
    fun `no more than five prayers count, however many marks there are`() {
        assertEquals(25, EarnedMinutes.earned(prayersMarked = 9, beads = 0, ayatRead = 0, cap = 120).fromPrayer)
    }

    @Test
    fun `the ceiling holds the total, not the sources`() {
        val today = EarnedMinutes.earned(prayersMarked = 5, beads = 33 * 50, ayatRead = 300, cap = 30)
        assertEquals(30, today.total)
        assertTrue(today.capped)
        // The sources are reported as they came, so a screen can say where the day went.
        assertEquals(25, today.fromPrayer)
        assertEquals(100, today.fromDhikr)
    }

    @Test
    fun `a ceiling past the maximum is held to the maximum`() {
        val today = EarnedMinutes.earned(prayersMarked = 5, beads = 33 * 200, ayatRead = 0, cap = 10_000)
        assertEquals(EarnedMinutes.MAX_CAP, today.cap)
        assertEquals(EarnedMinutes.MAX_CAP, today.total)
    }

    @Test
    fun `negative inputs are read as nothing`() {
        assertEquals(0, EarnedMinutes.earned(-3, -40, -9, cap = 30).total)
    }

    // ---- what the day has spent --------------------------------------------------------------

    @Test
    fun `only minutes past an app's own cap come out of the pool`() {
        // 20 of 30 used: nothing spent. 45 of 30: 15 spent. 12 of 10: 2 spent.
        assertEquals(17, EarnedMinutes.spent(listOf(20 to 30, 45 to 30, 12 to 10)))
        assertEquals(0, EarnedMinutes.spent(emptyList()))
    }

    @Test
    fun `what is left is never negative`() {
        val earned = EarnedMinutes.earned(prayersMarked = 1, beads = 0, ayatRead = 0, cap = 30) // 5
        assertEquals(3, EarnedMinutes.left(earned, spent = 2))
        assertEquals(0, EarnedMinutes.left(earned, spent = 9))
    }

    // ---- reading at a pace -------------------------------------------------------------------

    @Test
    fun `the first ayah of the day always counts and the next waits for the pace`() {
        assertTrue(EarnedMinutes.countsAsRead(null, 1_000))
        assertFalse(EarnedMinutes.countsAsRead(1_000, 1_000 + 3_000))
        assertTrue(EarnedMinutes.countsAsRead(1_000, 1_000 + EarnedMinutes.MIN_SECONDS_PER_AYAH * 1000L))
    }

    @Test
    fun `a page takes an ayah's pace for each ayah on it`() {
        assertEquals(EarnedMinutes.MIN_SECONDS_PER_AYAH * 15, EarnedMinutes.secondsToReadPage(15))
        // A page never asks for nothing, even one with a single long ayah beginning on it.
        assertEquals(EarnedMinutes.MIN_SECONDS_PER_AYAH, EarnedMinutes.secondsToReadPage(0))
    }

    // ---- the gate ----------------------------------------------------------------------------

    private val gated = AppRuleSnapshot(
        packageName = "com.example.feed",
        label = "Feed",
        tier = AppTier.GATED,
        budget = AppBudget(dailyMinutes = 30),
    )

    private fun decide(used: Int, pool: Int, rule: AppRuleSnapshot = gated, opens: Int = 0) = GateEvaluator.evaluate(
        rule = rule,
        usage = AppUsageToday(opens = opens, minutesUsed = used),
        policy = GatePolicy.DEFAULT,
        inBlackout = false,
        bypassesRemaining = 3,
        earnedMinutesLeft = pool,
    )

    @Test
    fun `with nothing earned a spent cap refuses as it always did`() {
        val decision = decide(used = 30, pool = 0)
        assertTrue(decision is GateDecision.Refuse)
        assertEquals(RefusalReason.DAILY_MINUTES_SPENT, (decision as GateDecision.Refuse).reason)
    }

    @Test
    fun `with minutes earned a spent cap opens, for no longer than the pool`() {
        val decision = decide(used = 30, pool = 3)
        assertTrue(decision is GateDecision.RequireFriction)
        assertEquals(3, (decision as GateDecision.RequireFriction).sessionMinutes)
    }

    @Test
    fun `an app still inside its own cap may run on into the pool`() {
        // 28 of 30 used, 2 of its own left and 10 earned: the default five-minute session fits.
        val decision = decide(used = 28, pool = 10) as GateDecision.RequireFriction
        assertEquals(GatePolicy.DEFAULT.defaultSessionMinutes, decision.sessionMinutes)
        // Without the pool the same session is held to the two minutes left.
        assertEquals(2, (decide(used = 28, pool = 0) as GateDecision.RequireFriction).sessionMinutes)
    }

    @Test
    fun `the pool buys minutes and nothing else`() {
        // Opens spent: refused, earned minutes or not.
        val opens = decide(used = 0, pool = 30, rule = gated.copy(budget = AppBudget(dailyOpens = 2)), opens = 2)
        assertEquals(RefusalReason.DAILY_OPENS_SPENT, (opens as GateDecision.Refuse).reason)

        // Blocked: refused.
        val blocked = decide(used = 0, pool = 30, rule = gated.copy(tier = AppTier.BLOCKED))
        assertEquals(RefusalReason.PERMANENTLY_BLOCKED, (blocked as GateDecision.Refuse).reason)

        // Salah and a blackout: refused.
        val salah = GateEvaluator.evaluate(
            rule = gated, usage = AppUsageToday(), policy = GatePolicy.DEFAULT, inBlackout = false,
            bypassesRemaining = 3, inPrayerWindow = true, earnedMinutesLeft = 30,
        )
        assertEquals(RefusalReason.PRAYER, (salah as GateDecision.Refuse).reason)
        val blackout = GateEvaluator.evaluate(
            rule = gated, usage = AppUsageToday(), policy = GatePolicy.DEFAULT, inBlackout = true,
            bypassesRemaining = 3, earnedMinutesLeft = 30,
        )
        assertEquals(RefusalReason.BLACKOUT, (blackout as GateDecision.Refuse).reason)
    }

    @Test
    fun `the countdown is still paid in full`() {
        val decision = decide(used = 30, pool = 10, opens = 4) as GateDecision.RequireFriction
        assertEquals(GateEvaluator.delayFor(4, GatePolicy.DEFAULT), decision.delaySeconds)
    }
}
