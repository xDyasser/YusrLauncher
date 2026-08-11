package dev.yusr.service

import android.app.Notification
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dev.yusr.YusrApp
import dev.yusr.R
import dev.yusr.container
import dev.yusr.domain.AppTier
import dev.yusr.domain.GateDecision
import dev.yusr.domain.RefusalReason
import dev.yusr.ui.block.BlockActivity
import dev.yusr.ui.gate.GateActivity
import dev.yusr.ui.home.HomeActivity
import dev.yusr.util.Permissions
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The part that actually holds the line.
 *
 * It watches which app is in the foreground and, when that app has not been earned, sends you
 * back to the home screen with an explanation. This is what catches the routes the launcher UI
 * cannot: recents, notifications, deep links from other apps.
 */
class GuardService : LifecycleService() {

    private val repository by lazy { container.repository }
    private val protectedPackages: Set<String> by lazy { container.catalog.protectedPackages() }

    private var trackedPackage: String? = null
    private var openSessionId: Long? = null
    private var lastForeground: String? = null

    /**
     * Whatever was in front before [lastForeground]. This is what tells a browser opened from a
     * message apart from a browser opened on purpose, and there is no other signal that does:
     * a custom tab, a web app and the browser itself all surface as the same package.
     */
    private var previousForeground: String? = null
    private var lastInterventionAt: Long = 0L
    private var lastInterventionPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        lifecycleScope.launch { watchLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        // A killed service must not leave a session open and silently accruing minutes.
        lifecycleScope.launch { repository.closeOpenSessions() }
        super.onDestroy()
    }

    private suspend fun watchLoop() {
        while (lifecycleScope.coroutineContext.isActive) {
            val monitoring = runCatching { tick() }
                .onFailure { Log.w(TAG, "Guard tick failed", it) }
                .getOrDefault(false)
            delay(if (monitoring) FAST_POLL_MILLIS else SLOW_POLL_MILLIS)
        }
    }

    /** Returns true when something worth watching closely is in the foreground. */
    private suspend fun tick(): Boolean {
        if (!Permissions.hasUsageAccess(this)) return false

        val now = System.currentTimeMillis()
        val foreground = foregroundPackage(now) ?: return trackedPackage != null

        if (foreground == packageName) {
            // We are looking at our own screens; nothing is being consumed.
            closeTracking(now)
            return false
        }

        if (foreground in protectedPackages) {
            closeTracking(now)
            return false
        }

        if (foreground != trackedPackage) {
            closeTracking(now)
            return onNewForegroundApp(foreground, now)
        }

        return onSameAppStillOpen(foreground, now)
    }

    private suspend fun onNewForegroundApp(packageName: String, now: Long): Boolean {
        if (SessionGovernor.isGrantedFor(packageName, now)) {
            beginTracking(packageName, SessionGovernor.grant?.wasBypass == true, now)
            return true
        }

        val snapshot = repository.snapshot(packageName)
        return when (val decision = repository.decide(packageName, now)) {
            is GateDecision.Allow -> {
                // Favourites and utilities still get their time recorded, just not policed.
                beginTracking(packageName, wasBypass = false, now = now)
                snapshot.tier != AppTier.FAVORITE && snapshot.tier != AppTier.ALLOWED
            }

            is GateDecision.RequireFriction -> {
                if (snapshot.openableByHandoff && handedOff(packageName)) {
                    // A link, a sign-in page, a web app that is really a browser tab. The time
                    // still counts against the app's budget; only the toll is waived.
                    beginTracking(packageName, wasBypass = false, now = now)
                } else {
                    intervene(packageName, now) { GateActivity.newIntent(this, packageName) }
                }
                true
            }

            is GateDecision.Refuse -> {
                intervene(packageName, now) {
                    BlockActivity.newIntent(this, packageName, decision.reason, decision.bypassesRemaining)
                }
                true
            }
        }
    }

    /**
     * True when [packageName] came forward because another app opened it, rather than because
     * the user went and found it.
     *
     * "Another app" means anything that is not us: reaching the browser through the launcher —
     * the favourites list, search, the gate itself — leaves our own package behind it, and that
     * still costs what it costs. This does not stop someone from opening a message and then
     * switching to the browser they were just handed; it is a deliberately cheap check, which is
     * why it is off unless a rule asks for it.
     */
    private fun handedOff(packageName: String): Boolean {
        val previous = previousForeground ?: return false
        return previous != packageName && previous != this.packageName
    }

    private suspend fun onSameAppStillOpen(packageName: String, now: Long): Boolean {
        if (SessionGovernor.isExpiredFor(packageName, now)) {
            closeTracking(now)
            SessionGovernor.clear()
            intervene(packageName, now) {
                BlockActivity.newIntent(this, packageName, RefusalReason.DAILY_MINUTES_SPENT, sessionOver = true)
            }
            return true
        }

        // Re-evaluate mid-session: a budget can run out, or a blackout can begin, while you sit
        // inside the app. A prayer window is checked whatever the app is, because unlike the
        // other limits it closes favourites too — being already inside one is exactly the case
        // that matters when the adhan arrives.
        if (SessionGovernor.grant?.packageName == packageName || repository.activePrayerWindow(now) != null) {
            val decision = repository.decide(packageName, now)
            if (decision is GateDecision.Refuse && decision.reason != RefusalReason.PERMANENTLY_BLOCKED) {
                closeTracking(now)
                SessionGovernor.clear()
                intervene(packageName, now) {
                    BlockActivity.newIntent(this, packageName, decision.reason, decision.bypassesRemaining)
                }
                return true
            }
        }
        return true
    }

    private suspend fun beginTracking(packageName: String, wasBypass: Boolean, now: Long) {
        trackedPackage = packageName
        openSessionId = repository.openSession(packageName, wasBypass, now)
    }

    private suspend fun closeTracking(now: Long) {
        if (trackedPackage != null || openSessionId != null) {
            repository.closeOpenSessions(now)
        }
        trackedPackage = null
        openSessionId = null
    }

    /**
     * Sends you home and then puts the explanation on top. Home first, so dismissing the
     * explanation cannot drop you back into the app you were just pulled out of.
     */
    private fun intervene(packageName: String, now: Long, intent: () -> Intent) {
        val isRepeat = packageName == lastInterventionPackage &&
            now - lastInterventionAt < INTERVENTION_DEBOUNCE_MILLIS
        if (isRepeat) return

        lastInterventionPackage = packageName
        lastInterventionAt = now

        runCatching {
            startActivity(homeIntent())
            startActivity(intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Log.w(TAG, "Could not show the block screen", it) }
    }

    private fun homeIntent(): Intent = Intent(this, HomeActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /**
     * The most recent app to come to the foreground. The lookback is generous because a quiet
     * minute produces no events at all, and we would rather keep the last known answer than
     * decide the screen is empty.
     */
    private fun foregroundPackage(now: Long): String? {
        val usage = getSystemService(UsageStatsManager::class.java) ?: return null
        val events: UsageEvents = usage.queryEvents(now - LOOKBACK_MILLIS, now + 1_000)
        val event = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                latest = event.packageName
            }
        }
        if (latest != null && latest != lastForeground) {
            previousForeground = lastForeground
            lastForeground = latest
        }
        return lastForeground
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, HomeActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, YusrApp.CHANNEL_GUARD)
            .setSmallIcon(R.drawable.ic_stat_minimal)
            .setContentTitle(getString(R.string.guard_notification_title))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val TAG = "GuardService"
        private const val NOTIFICATION_ID = 1

        /** While a policed app is open, react within about a second. */
        private const val FAST_POLL_MILLIS = 1_000L

        /** Otherwise idle back so the battery cost stays negligible. */
        private const val SLOW_POLL_MILLIS = 5_000L

        private const val LOOKBACK_MILLIS = 60_000L

        /** Stops a stubborn app from producing a stack of block screens. */
        private const val INTERVENTION_DEBOUNCE_MILLIS = 3_000L

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, GuardService::class.java))
            }.onFailure { Log.w(TAG, "Could not start the guard service", it) }
        }
    }
}
