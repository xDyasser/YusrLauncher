package dev.minimalist.domain

/**
 * How much an app costs to reach.
 *
 * The default for anything the user has not classified is [GATED]: nothing gets in for free
 * just because it was installed.
 */
enum class AppTier {
    /** On the home screen, opens instantly. */
    FAVORITE,

    /** Not on the home screen, but opens instantly once found. Phone, clock, camera. */
    ALLOWED,

    /** Must be earned: typed name, stated reason, countdown, and a bounded session. */
    GATED,

    /** Never opens. No countdown will save you. */
    BLOCKED,
}

/** The knobs that decide how expensive the gate is. All durations in seconds unless named otherwise. */
data class GatePolicy(
    val baseDelaySeconds: Int = 15,
    val escalationSecondsPerOpen: Int = 15,
    val maxDelaySeconds: Int = 300,
    val minReasonLength: Int = 15,
    val defaultSessionMinutes: Int = 5,
) {
    companion object {
        val DEFAULT = GatePolicy()
    }
}

/** Per-app hard caps. `null` means "no cap", which for a GATED app still means the countdown applies. */
data class AppBudget(
    val dailyMinutes: Int? = null,
    val dailyOpens: Int? = null,
) {
    companion object {
        val UNLIMITED = AppBudget()
    }
}

/** What the rules say about one app, flattened for the pure evaluator. */
data class AppRuleSnapshot(
    val packageName: String,
    val label: String,
    val tier: AppTier,
    val budget: AppBudget = AppBudget.UNLIMITED,
    /** Overrides [GatePolicy.defaultSessionMinutes] when set. */
    val sessionMinutes: Int? = null,
    /**
     * Opens during a prayer window. The dialer, and whatever the user marks — a mushaf, an
     * adhkar app. Everything else waits, favourites included.
     */
    val prayerExempt: Boolean = false,
    /**
     * Opens without the gate when another app handed off to it, rather than when you went
     * looking for it.
     *
     * This is what a browser needs to stay usable while it is gated. Half the apps on a phone
     * are a web view in a coat: a link from a message, a sign-in page, a "web app" that is a
     * shortcut into Chrome. Every one of those brings the browser to the foreground, and
     * demanding a typed dhikr and a countdown before a login form is friction with nothing on
     * the other side of it.
     *
     * Opening the browser *from the launcher* still costs what it costs. Only the handoff is
     * free, and only for apps marked this way.
     */
    val openableByHandoff: Boolean = false,
)

/** What has already been spent on an app today. */
data class AppUsageToday(
    val opens: Int = 0,
    val minutesUsed: Int = 0,
)

enum class RefusalReason {
    PERMANENTLY_BLOCKED,
    /** A prayer window is in force. Unlike a blackout, this one closes favourites too. */
    PRAYER,
    BLACKOUT,
    DAILY_OPENS_SPENT,
    DAILY_MINUTES_SPENT,
}

sealed interface GateDecision {
    /** Open it, no questions. Favorites and allowed utilities. */
    data class Allow(val sessionMinutes: Int?) : GateDecision

    /** Earn it. */
    data class RequireFriction(
        val delaySeconds: Int,
        val minReasonLength: Int,
        val sessionMinutes: Int,
    ) : GateDecision

    /** No. The only way past this is an emergency bypass, if any are left. */
    data class Refuse(
        val reason: RefusalReason,
        val bypassesRemaining: Int,
    ) : GateDecision
}

object GateEvaluator {

    /**
     * The single place that decides whether an app opens.
     *
     * Order matters: a permanent block beats everything, then the prayer window, then blackout
     * windows, then hard budgets, and only what survives all of them gets the countdown.
     */
    fun evaluate(
        rule: AppRuleSnapshot,
        usage: AppUsageToday,
        policy: GatePolicy,
        inBlackout: Boolean,
        bypassesRemaining: Int,
        inPrayerWindow: Boolean = false,
    ): GateDecision {
        if (rule.tier == AppTier.BLOCKED) {
            return GateDecision.Refuse(RefusalReason.PERMANENTLY_BLOCKED, bypassesRemaining)
        }
        // A prayer window is the one thing that outranks being a favourite. The phone stops for
        // salah; only calls and what you have marked exempt carry on.
        if (inPrayerWindow && !rule.prayerExempt) {
            return GateDecision.Refuse(RefusalReason.PRAYER, bypassesRemaining)
        }
        // Favourites and utilities stay reachable even during a blackout: a blackout is meant to
        // shut out distraction, not to lock you out of the phone.
        if (rule.tier == AppTier.FAVORITE || rule.tier == AppTier.ALLOWED) {
            return GateDecision.Allow(sessionMinutes = null)
        }
        if (inBlackout) {
            return GateDecision.Refuse(RefusalReason.BLACKOUT, bypassesRemaining)
        }

        val openCap = rule.budget.dailyOpens
        if (openCap != null && usage.opens >= openCap) {
            return GateDecision.Refuse(RefusalReason.DAILY_OPENS_SPENT, bypassesRemaining)
        }

        val minuteCap = rule.budget.dailyMinutes
        if (minuteCap != null && usage.minutesUsed >= minuteCap) {
            return GateDecision.Refuse(RefusalReason.DAILY_MINUTES_SPENT, bypassesRemaining)
        }

        return GateDecision.RequireFriction(
            delaySeconds = delayFor(usage.opens, policy),
            minReasonLength = policy.minReasonLength,
            sessionMinutes = sessionFor(rule, policy, minuteCap, usage),
        )
    }

    /** The countdown grows with every open you have already had today, up to the cap. */
    fun delayFor(opensToday: Int, policy: GatePolicy): Int {
        val raw = policy.baseDelaySeconds.toLong() +
            policy.escalationSecondsPerOpen.toLong() * opensToday.coerceAtLeast(0)
        return raw.coerceAtMost(policy.maxDelaySeconds.toLong()).toInt()
    }

    /** A session never runs past what is left of the daily minute budget. */
    private fun sessionFor(
        rule: AppRuleSnapshot,
        policy: GatePolicy,
        minuteCap: Int?,
        usage: AppUsageToday,
    ): Int {
        val requested = rule.sessionMinutes ?: policy.defaultSessionMinutes
        if (minuteCap == null) return requested
        val remaining = (minuteCap - usage.minutesUsed).coerceAtLeast(1)
        return minOf(requested, remaining)
    }
}
