package dev.minimalist.domain

/** One stretch of time an app was in the foreground. `endMillis == null` means it still is. */
data class SessionRecord(
    val packageName: String,
    val startMillis: Long,
    val endMillis: Long? = null,
    val wasBypass: Boolean = false,
)

object BudgetCalculator {

    /**
     * What has been spent on [packageName] between [dayStartMillis] and [nowMillis].
     *
     * Sessions are clamped to the window, so an app left open across midnight is charged to
     * both days for the part that actually falls in each — the previous day's overrun does not
     * eat today's budget, and today's does not get a free pass.
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
