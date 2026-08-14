package dev.yusr.domain

/**
 * One thing the phone recorded about what was in front of you.
 *
 * This is the system's own usage-event stream, reduced to the three kinds that decide how long an
 * app was actually looked at. Everything else the stream carries — configuration changes, standby
 * buckets, notifications — says nothing about that and is dropped before it gets here.
 */
data class ForegroundEvent(
    val packageName: String?,
    val kind: Kind,
    val timestamp: Long,
) {
    enum class Kind {
        /** An activity of this package came to the front. */
        FOREGROUND,

        /** An activity of this package left the front. */
        BACKGROUND,

        /** The screen went off, the lock screen came up, or the phone shut down. */
        IDLE,
    }
}

/** How long a package was in front, and how many separate visits that was. */
data class ForegroundUsage(
    val millis: Long = 0L,
    val opens: Int = 0,
)

/**
 * What the phone was showing, as of the moment it was asked.
 *
 * [foreground] is the package still accruing time at [takenAtMillis], which is what lets a
 * snapshot be read seconds later without asking the system again: nothing else can have changed
 * except that one app getting older. See [at].
 */
data class ForegroundSnapshot(
    val byPackage: Map<String, ForegroundUsage> = emptyMap(),
    val takenAtMillis: Long = 0L,
    val foreground: String? = null,
) {
    /**
     * The same tally, carried forward to [now] without re-reading the event stream.
     *
     * Only the app that was in front can have gained anything since, and it gains exactly the
     * elapsed time. Carrying it forward this way is what keeps the guard from parsing a day of
     * events once a second while you sit inside one app.
     */
    fun at(now: Long): Map<String, ForegroundUsage> {
        val open = foreground ?: return byPackage
        if (now <= takenAtMillis) return byPackage
        val existing = byPackage[open] ?: ForegroundUsage()
        return byPackage + (open to existing.copy(millis = existing.millis + (now - takenAtMillis)))
    }

    fun usageFor(packageName: String, now: Long): ForegroundUsage =
        at(now)[packageName] ?: ForegroundUsage()

    fun totalMillis(now: Long): Long = at(now).values.sumOf { it.millis }

    fun totalOpens(): Int = byPackage.values.sumOf { it.opens }
}

/**
 * Turns the phone's usage events into the same numbers the phone itself reports.
 *
 * This exists because counting the time ourselves got it wrong, in the one direction that
 * mattered: the launcher's own tracking opened a stretch when an app came forward and closed it
 * when another one did, so an app left in front when the screen went off was charged for the whole
 * night. The phone does not count it that way and neither does anyone reading the number. Here the
 * screen going off ends the visit, exactly as it does in the system's own accounting, and the
 * arithmetic is otherwise the system's: resumed to paused, clamped to the day.
 */
object ForegroundTally {

    /**
     * @param events the reduced event stream, in any order.
     * @param windowStart time before this is used only to know what was already in front.
     * @param nowMillis the end of the window.
     * @param interactiveNow whether the screen is on right now. When it is not, whatever was last
     *   in front stopped accruing at the last event rather than carrying on to [nowMillis].
     */
    fun tally(
        events: List<ForegroundEvent>,
        windowStart: Long,
        nowMillis: Long,
        interactiveNow: Boolean = true,
    ): ForegroundSnapshot {
        val millis = mutableMapOf<String, Long>()
        val opens = mutableMapOf<String, Int>()

        // The package accruing time right now, and since when.
        var current: String? = null
        var startedAt = 0L

        // The last package to have been in front, whether or not it still is. An open is counted
        // against this rather than against [current], so that a move between two activities of one
        // app — which the system reports as a pause and a resume — is one visit and not two.
        var last: String? = null
        var lastEventAt = windowStart

        fun close(at: Long) {
            val open = current ?: return
            val from = maxOf(startedAt, windowStart)
            val to = minOf(at, nowMillis)
            if (to > from) millis[open] = (millis[open] ?: 0L) + (to - from)
            current = null
        }

        for (event in events.sortedBy { it.timestamp }) {
            if (event.timestamp > nowMillis) break
            lastEventAt = event.timestamp
            when (event.kind) {
                ForegroundEvent.Kind.FOREGROUND -> {
                    val packageName = event.packageName ?: continue
                    if (current == packageName) continue
                    close(event.timestamp)
                    current = packageName
                    startedAt = event.timestamp
                    if (packageName != last && event.timestamp >= windowStart) {
                        opens[packageName] = (opens[packageName] ?: 0) + 1
                    }
                    last = packageName
                }

                ForegroundEvent.Kind.BACKGROUND ->
                    if (current != null && current == event.packageName) close(event.timestamp)

                // The screen went off with an app still in front. That is where the day ends for
                // that app: nobody is looking at it, and the phone does not charge it either.
                ForegroundEvent.Kind.IDLE -> close(event.timestamp)
            }
        }

        val stillOpen = current
        close(if (interactiveNow) nowMillis else lastEventAt)

        val byPackage = millis.keys.plus(opens.keys).associateWith { packageName ->
            ForegroundUsage(
                millis = millis[packageName] ?: 0L,
                opens = opens[packageName] ?: 0,
            )
        }
        return ForegroundSnapshot(
            byPackage = byPackage,
            takenAtMillis = nowMillis,
            foreground = if (interactiveNow) stillOpen else null,
        )
    }
}
