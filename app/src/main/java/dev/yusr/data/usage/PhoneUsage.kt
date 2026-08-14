package dev.yusr.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import dev.yusr.domain.ForegroundEvent
import dev.yusr.domain.ForegroundSnapshot
import dev.yusr.domain.ForegroundTally
import dev.yusr.util.DayClock
import dev.yusr.util.Permissions

/**
 * The phone's own answer to "how long was that app open?".
 *
 * Android already keeps this, to the same standard its own screen-time screen is held to, and it
 * keeps it whether or not our service was running. Reading it instead of counting alongside it
 * fixes the two ways the launcher's own tally was wrong: an app left in front while the phone
 * slept was charged for the sleep, and anything that happened while the guard was killed —
 * overnight, on most phones that go in for that — was not charged at all.
 *
 * Nothing here needs a permission the app does not already need. Without usage access the guard
 * cannot see the foreground either, so every reader here answers null and the caller falls back
 * to what the launcher recorded itself.
 */
class PhoneUsage(private val context: Context) {

    private val lock = Any()
    private var cached: ForegroundSnapshot? = null
    private var cachedFrom: Long = 0L

    /**
     * Everything in front of you since [since], as the phone counts it.
     *
     * Null when usage access has not been granted. The result is cached for [REFRESH_MILLIS] and
     * carried forward with [ForegroundSnapshot.at] in between: the guard asks this once a second
     * while a budgeted app is open, and parsing a day of events that often is exactly the sort of
     * thing that shows up on the battery screen.
     */
    fun snapshot(since: Long, now: Long = System.currentTimeMillis()): ForegroundSnapshot? {
        synchronized(lock) {
            val current = cached
            val fresh = current != null &&
                cachedFrom == since &&
                now - current.takenAtMillis < REFRESH_MILLIS &&
                now >= current.takenAtMillis
            if (fresh) return current

            val computed = read(since, now) ?: return null
            cached = computed
            cachedFrom = since
            return computed
        }
    }

    /** Today's tally, from local midnight. The window every budget is measured against. */
    fun today(now: Long = System.currentTimeMillis()): ForegroundSnapshot? =
        snapshot(DayClock.dayStart(now), now)

    /**
     * Total foreground time between [since] and [now], read off the system's own daily buckets.
     *
     * Used for the seven-day figure, where parsing a week of raw events would cost far more than
     * the extra precision is worth. These are the numbers the phone's settings screen shows.
     */
    fun totalMillisSince(since: Long, now: Long = System.currentTimeMillis()): Long? {
        if (!Permissions.hasUsageAccess(context)) return null
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return null
        return runCatching {
            manager.queryAndAggregateUsageStats(since, now)
                .filterKeys { it != context.packageName }
                .values
                .sumOf { it.totalTimeInForeground }
        }.onFailure { Log.w(TAG, "Could not read the usage totals", it) }.getOrNull()
    }

    private fun read(since: Long, now: Long): ForegroundSnapshot? {
        if (!Permissions.hasUsageAccess(context)) return null
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return null

        // The window starts earlier than it is measured from, so that an app which came forward
        // before midnight — or before the last hour, on the shorter windows — is known to have
        // been in front rather than appearing out of nowhere at the first pause.
        val events = runCatching { manager.queryEvents(since - BACKFILL_MILLIS, now) }
            .onFailure { Log.w(TAG, "Could not read the usage events", it) }
            .getOrNull() ?: return null

        val records = mutableListOf<ForegroundEvent>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val kind = kindOf(event.eventType) ?: continue
            records += ForegroundEvent(event.packageName, kind, event.timeStamp)
        }

        val interactive = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true
        val snapshot = ForegroundTally.tally(records, since, now, interactive)

        // The launcher's own screens are not where the day went. They are what you pass through on
        // the way, and counting them would put this app at the top of its own list.
        return snapshot.copy(
            byPackage = snapshot.byPackage.filterKeys { it != context.packageName },
            foreground = snapshot.foreground?.takeIf { it != context.packageName },
        )
    }

    private fun kindOf(eventType: Int): ForegroundEvent.Kind? = when (eventType) {
        UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundEvent.Kind.FOREGROUND
        UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
            ForegroundEvent.Kind.BACKGROUND
        UsageEvents.Event.SCREEN_NON_INTERACTIVE,
        UsageEvents.Event.KEYGUARD_SHOWN,
        UsageEvents.Event.DEVICE_SHUTDOWN,
        -> ForegroundEvent.Kind.IDLE

        else -> null
    }

    companion object {
        private const val TAG = "PhoneUsage"

        /** How stale a tally may get before the events are read again. */
        private const val REFRESH_MILLIS = 15_000L

        /** Enough to catch the app that was already open when the window began. */
        private const val BACKFILL_MILLIS = 6L * 60 * 60 * 1000
    }
}
