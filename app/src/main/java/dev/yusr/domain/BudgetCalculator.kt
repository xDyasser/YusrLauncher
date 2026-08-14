package dev.yusr.domain

/** One stretch of time an app was in the foreground. `endMillis == null` means it still is. */
data class SessionRecord(
    val packageName: String,
    val startMillis: Long,
    val endMillis: Long? = null,
    val wasBypass: Boolean = false,
    /** Another app opened this one. Counted on the dashboard, not against the daily budget. */
    val wasHandoff: Boolean = false,
)

object BudgetCalculator {

    /**
     * What has been spent on [packageName] between [dayStartMillis] and [nowMillis].
     *
     * Sessions are clamped to the window, so an app left open across midnight is charged to
     * both days for the part that actually falls in each — the previous day's overrun does not
     * eat today's budget, and today's does not get a free pass.
     *
     * Handed-off sessions are left out altogether. A cap on a browser is a cap on browsing, and
     * the links, sign-in pages and web apps that pass through it were never that: charging them
     * spent the day's opens on apps the user had not opened, and then refused the browser for
     * having been busy on someone else's behalf. [totalMinutes] still counts them, because the
     * dashboard is about where the day went and the day did go there.
     */
    fun usageFor(
        sessions: List<SessionRecord>,
        packageName: String,
        dayStartMillis: Long,
        nowMillis: Long,
    ): AppUsageToday {
        var millisUsed = 0L
        var opens = 0

        for (session in sessions) {
            if (session.packageName != packageName) continue
            if (session.wasHandoff) continue

            val end = session.endMillis ?: nowMillis
            val overlapStart = maxOf(session.startMillis, dayStartMillis)
            val overlapEnd = minOf(end, nowMillis)
            if (overlapEnd <= overlapStart) continue

            millisUsed += overlapEnd - overlapStart
            // An "open" is counted on the day the session actually began, so a session carried
            // over midnight does not spend an open from the new day.
            if (session.startMillis in dayStartMillis..nowMillis) opens++
        }

        return AppUsageToday(
            opens = opens,
            minutesUsed = (millisUsed / 60_000L).toInt(),
        )
    }

    /**
     * The part of [packageName]'s day that another app is responsible for.
     *
     * The phone counts a link, a sign-in page and a web app as time in the browser, because that
     * is what they are — but the launcher's budget never charged them, for the reasons set out in
     * [dev.yusr.domain.AppRuleSnapshot.openableByHandoff]. So the phone's number is the truth
     * about the day, and this is what comes off it before the number is held against a cap.
     */
    fun handedOffUsage(
        sessions: List<SessionRecord>,
        packageName: String,
        dayStartMillis: Long,
        nowMillis: Long,
    ): ForegroundUsage {
        var millis = 0L
        var opens = 0
        for (session in sessions) {
            if (session.packageName != packageName || !session.wasHandoff) continue
            val end = session.endMillis ?: nowMillis
            val overlapStart = maxOf(session.startMillis, dayStartMillis)
            val overlapEnd = minOf(end, nowMillis)
            if (overlapEnd > overlapStart) millis += overlapEnd - overlapStart
            if (session.startMillis in dayStartMillis..nowMillis) opens++
        }
        return ForegroundUsage(millis = millis, opens = opens)
    }

    /** Same window, but totalled over every app — the shame counter on the dashboard. */
    fun totalMinutes(
        sessions: List<SessionRecord>,
        dayStartMillis: Long,
        nowMillis: Long,
    ): Int {
        var millis = 0L
        for (session in sessions) {
            val end = session.endMillis ?: nowMillis
            val overlapStart = maxOf(session.startMillis, dayStartMillis)
            val overlapEnd = minOf(end, nowMillis)
            if (overlapEnd > overlapStart) millis += overlapEnd - overlapStart
        }
        return (millis / 60_000L).toInt()
    }

    /** Per-app minutes, biggest offender first. */
    fun minutesByPackage(
        sessions: List<SessionRecord>,
        dayStartMillis: Long,
        nowMillis: Long,
    ): List<Pair<String, Int>> = sessions
        .groupBy { it.packageName }
        .mapValues { (_, group) -> totalMinutes(group, dayStartMillis, nowMillis) }
        .filterValues { it > 0 }
        .toList()
        .sortedByDescending { it.second }

    /** Whether a granted session has run out. */
    fun isSessionExpired(grantedAtMillis: Long, sessionMinutes: Int, nowMillis: Long): Boolean =
        nowMillis >= grantedAtMillis + sessionMinutes * 60_000L

    fun secondsLeftInSession(grantedAtMillis: Long, sessionMinutes: Int, nowMillis: Long): Long =
        ((grantedAtMillis + sessionMinutes * 60_000L - nowMillis) / 1000L).coerceAtLeast(0L)
}
