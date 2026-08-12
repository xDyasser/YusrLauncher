package dev.yusr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GateEvaluatorTest {

    private val policy = GatePolicy(
        baseDelaySeconds = 15,
        escalationSecondsPerOpen = 15,
        maxDelaySeconds = 300,
        minReasonLength = 15,
        defaultSessionMinutes = 5,
    )

    private fun rule(
        tier: AppTier = AppTier.GATED,
        minutes: Int? = null,
        opens: Int? = null,
        sessionMinutes: Int? = null,
        prayerExempt: Boolean = false,
        openableByHandoff: Boolean = false,
    ) = AppRuleSnapshot(
        packageName = "com.example.app",
        label = "Example",
        tier = tier,
        budget = AppBudget(dailyMinutes = minutes, dailyOpens = opens),
        sessionMinutes = sessionMinutes,
        prayerExempt = prayerExempt,
        openableByHandoff = openableByHandoff,
    )

    private fun evaluate(
        rule: AppRuleSnapshot,
        usage: AppUsageToday = AppUsageToday(),
        inBlackout: Boolean = false,
        bypasses: Int = 3,
        inPrayerWindow: Boolean = false,
        handedOff: Boolean = false,
    ) = GateEvaluator.evaluate(rule, usage, policy, inBlackout, bypasses, inPrayerWindow, handedOff)

    @Test
    fun `favourites open without friction`() {
        assertTrue(evaluate(rule(tier = AppTier.FAVORITE)) is GateDecision.Allow)
    }

    // ---- salah ---------------------------------------------------------------------------

    /** The one rule that outranks being a favourite. A blackout does not do this; salah does. */
    @Test
    fun `a prayer window closes even favourites`() {
        val decision = evaluate(rule(tier = AppTier.FAVORITE), inPrayerWindow = true)
        assertTrue(decision is GateDecision.Refuse)
        assertEquals(RefusalReason.PRAYER, (decision as GateDecision.Refuse).reason)
    }

    @Test
    fun `a prayer window closes allowed utilities too`() {
        val decision = evaluate(rule(tier = AppTier.ALLOWED), inPrayerWindow = true)
        assertEquals(RefusalReason.PRAYER, (decision as GateDecision.Refuse).reason)
    }

    @Test
    fun `an exempt app opens during a prayer window`() {
        val decision = evaluate(rule(tier = AppTier.FAVORITE, prayerExempt = true), inPrayerWindow = true)
        assertTrue(decision is GateDecision.Allow)
    }

    /** Exempt from salah is not exempt from being blocked outright. */
    @Test
    fun `a blocked app stays blocked during a prayer window even if exempt`() {
        val decision = evaluate(rule(tier = AppTier.BLOCKED, prayerExempt = true), inPrayerWindow = true)
        assertEquals(RefusalReason.PERMANENTLY_BLOCKED, (decision as GateDecision.Refuse).reason)
    }

    @Test
    fun `a gated app is refused for the prayer, not sent to the countdown`() {
        val decision = evaluate(rule(tier = AppTier.GATED), inPrayerWindow = true)
        assertEquals(RefusalReason.PRAYER, (decision as GateDecision.Refuse).reason)
    }

    @Test
    fun `the bypass count survives a prayer refusal`() {
        val decision = evaluate(rule(tier = AppTier.GATED), inPrayerWindow = true, bypasses = 2)
        assertEquals(2, (decision as GateDecision.Refuse).bypassesRemaining)
    }

    @Test
    fun `nothing changes when no prayer window is in force`() {
        assertTrue(evaluate(rule(tier = AppTier.FAVORITE), inPrayerWindow = false) is GateDecision.Allow)
    }

    @Test
    fun `favourites still open during a blackout`() {
        val decision = evaluate(rule(tier = AppTier.FAVORITE), inBlackout = true)
        assertTrue(decision is GateDecision.Allow)
    }

    @Test
    fun `blocked apps refuse even outside a blackout`() {
        val decision = evaluate(rule(tier = AppTier.BLOCKED))
        assertEquals(RefusalReason.PERMANENTLY_BLOCKED, (decision as GateDecision.Refuse).reason)
    }

    @Test
    fun `a gated app is refused during a blackout`() {
        val decision = evaluate(rule(), inBlackout = true)
        assertEquals(RefusalReason.BLACKOUT, (decision as GateDecision.Refuse).reason)
    }

    @Test
    fun `the countdown grows with every open already taken today`() {
        val first = evaluate(rule(), AppUsageToday(opens = 0)) as GateDecision.RequireFriction
        val fourth = evaluate(rule(), AppUsageToday(opens = 3)) as GateDecision.RequireFriction

        assertEquals(15, first.delaySeconds)
        assertEquals(15 + 15 * 3, fourth.delaySeconds)
    }

    @Test
    fun `the countdown is capped so it stays a delay and not a lockout`() {
        val decision = evaluate(rule(), AppUsageToday(opens = 100)) as GateDecision.RequireFriction
        assertEquals(policy.maxDelaySeconds, decision.delaySeconds)
    }

    @Test
    fun `a spent open budget refuses outright`() {
        val decision = evaluate(rule(opens = 3), AppUsageToday(opens = 3))
        assertEquals(RefusalReason.DAILY_OPENS_SPENT, (decision as GateDecision.Refuse).reason)
    }

    @Test
    fun `a spent minute budget refuses outright`() {
        val decision = evaluate(rule(minutes = 20), AppUsageToday(opens = 1, minutesUsed = 20))
        assertEquals(RefusalReason.DAILY_MINUTES_SPENT, (decision as GateDecision.Refuse).reason)
    }

    @Test
    fun `the open budget is checked before the minute budget`() {
        val decision = evaluate(rule(minutes = 20, opens = 2), AppUsageToday(opens = 2, minutesUsed = 20))
        assertEquals(RefusalReason.DAILY_OPENS_SPENT, (decision as GateDecision.Refuse).reason)
    }

    @Test
    fun `a session never runs past what is left of the daily budget`() {
        val decision = evaluate(rule(minutes = 20), AppUsageToday(opens = 1, minutesUsed = 18))
            as GateDecision.RequireFriction
        assertEquals(2, decision.sessionMinutes)
    }

    @Test
    fun `a per-app session length overrides the default`() {
        val decision = evaluate(rule(sessionMinutes = 2)) as GateDecision.RequireFriction
        assertEquals(2, decision.sessionMinutes)
    }

    @Test
    fun `a refusal reports how many bypasses are left`() {
        val decision = evaluate(rule(opens = 1), AppUsageToday(opens = 1), bypasses = 2)
        assertEquals(2, (decision as GateDecision.Refuse).bypassesRemaining)
    }

    // ---- the handoff ---------------------------------------------------------------------

    private val browser = rule(opens = 3, minutes = 20, openableByHandoff = true)

    @Test
    fun `a handed-off app skips the countdown`() {
        assertTrue(evaluate(browser, handedOff = true) is GateDecision.Allow)
    }

    /**
     * The bug this is here for: every web app on the phone is the browser wearing a coat, and
     * charging their opens to it spent a three-open budget by lunchtime. What the cap was for was
     * browsing, and the browser had not been opened once.
     */
    @Test
    fun `a spent budget does not refuse a link, a sign-in page or a web app`() {
        assertTrue(evaluate(browser, AppUsageToday(opens = 3), handedOff = true) is GateDecision.Allow)
        assertTrue(
            evaluate(browser, AppUsageToday(opens = 1, minutesUsed = 40), handedOff = true)
                is GateDecision.Allow,
        )
    }

    /** Going to it yourself costs everything it costs. Only the handoff is free. */
    @Test
    fun `the same app opened from the launcher still meets its spent budget`() {
        val decision = evaluate(browser, AppUsageToday(opens = 3), handedOff = false)
        assertEquals(RefusalReason.DAILY_OPENS_SPENT, (decision as GateDecision.Refuse).reason)
    }

    @Test
    fun `an app that was never marked for it is handed off to no effect`() {
        val decision = evaluate(rule(opens = 1), AppUsageToday(opens = 1), handedOff = true)
        assertEquals(RefusalReason.DAILY_OPENS_SPENT, (decision as GateDecision.Refuse).reason)
    }

    /** Salah and a blackout are about the clock, not about the app, and a link is not exempt. */
    @Test
    fun `a handoff does not open anything during salah, a blackout, or an outright block`() {
        assertEquals(
            RefusalReason.PRAYER,
            (evaluate(browser, inPrayerWindow = true, handedOff = true) as GateDecision.Refuse).reason,
        )
        assertEquals(
            RefusalReason.BLACKOUT,
            (evaluate(browser, inBlackout = true, handedOff = true) as GateDecision.Refuse).reason,
        )
        assertEquals(
            RefusalReason.PERMANENTLY_BLOCKED,
            (
                evaluate(rule(tier = AppTier.BLOCKED, openableByHandoff = true), handedOff = true)
                    as GateDecision.Refuse
                ).reason,
        )
    }
}
