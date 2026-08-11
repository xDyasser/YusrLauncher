package dev.yusr.domain

/**
 * Whether a notification may be cancelled.
 *
 * This is deliberately pure and deliberately cautious. Cancelling the wrong notification is not a
 * cosmetic mistake: a media or foreground-service notification *is* the thing keeping its app
 * alive, so pulling it stops playback, or stops it ever starting. Anything pinned, unclearable,
 * or carrying a media session is left exactly where it is.
 */
object NotificationPolicy {

    /**
     * Categories that always ring through. A phone that cannot ring is a broken phone, not a
     * minimal one — and the transport/service/progress end of the list is playback and downloads,
     * which are not lures either.
     */
    val PASS_THROUGH_CATEGORIES = setOf(
        "call",
        "alarm",
        "reminder",
        "sys",
        "transport",
        "service",
        "progress",
        "navigation",
        "missed_call",
        "stopwatch",
    )

    /** Everything the decision needs, with no Android types in sight. */
    data class NotificationFacts(
        val tier: AppTier,
        val category: String?,
        /** Ongoing, a foreground service, or otherwise flagged no-clear. */
        val pinned: Boolean,
        /** Whether the user could swipe it away by hand. */
        val clearable: Boolean,
        /** Carries a media session, or is drawn with the media template. */
        val media: Boolean,
    )

    fun shouldSuppress(facts: NotificationFacts, suppressionEnabled: Boolean): Boolean {
        if (!suppressionEnabled) return false
        if (facts.tier != AppTier.GATED && facts.tier != AppTier.BLOCKED) return false
        if (facts.category in PASS_THROUGH_CATEGORIES) return false
        if (facts.pinned || !facts.clearable) return false
        if (facts.media) return false
        return true
    }
}
