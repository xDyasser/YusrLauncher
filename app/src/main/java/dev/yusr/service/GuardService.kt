package dev.yusr.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

    /** Whether [trackedPackage] was handed the phone by another app, for as long as it is in front. */
    private var trackedByHandoff: Boolean = false
    private var openSessionId: Long? = null
    private var lastForeground: String? = null

    /**
     * Whatever the usage events say was in front before [lastForeground]. This is what tells a
     * browser opened from a message apart from a browser opened on purpose, and there is no other
     * signal that does: a custom tab, a web app and the browser itself all surface as the same
     * package.
     */
    private var previousForeground: String? = null
    private var lastInterventionAt: Long = 0L
    private var lastInterventionPackage: String? = null

    /** How far back the last poll read the event stream. See [foregroundPackage]. */
    private var lastQueryAt: Long = 0L

    /**
     * Whether the phone is being used, kept as state rather than as an event so that a broadcast
     * arriving in the moment between the check and the wait cannot be missed.
     */
    private val inUse = MutableStateFlow(true)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            inUse.value = phoneInUse()
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        // Both of these are system broadcasts and nothing else may send them, so the receiver is
        // registered closed.
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        // Off the main thread: the poll reads the system's event stream, and a launcher that
        // stutters once a second is a launcher nobody keeps.
        lifecycleScope.launch(Dispatchers.Default) { watchLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        // A killed service must not leave a session open and silently accruing minutes.
        lifecycleScope.launch { repository.closeOpenSessions() }
        super.onDestroy()
    }

    private suspend fun watchLoop() {
        while (lifecycleScope.coroutineContext.isActive) {
            // Nothing can be opened on a phone nobody is holding, so there is nothing to watch.
            // This is the whole of the service's standby cost: a poll every second or five, all
            // night, kept the CPU awake for a question whose answer could not change. Now the loop
            // stops at the lock screen and starts again when the phone is unlocked.
            if (!phoneInUse()) {
                inUse.value = false
                closeTracking(System.currentTimeMillis())
                awaitUse()
                continue
            }
            val monitoring = runCatching { tick() }
                .onFailure { Log.w(TAG, "Guard tick failed", it) }
                .getOrDefault(false)
            delay(if (monitoring) FAST_POLL_MILLIS else SLOW_POLL_MILLIS)
        }
    }

    /**
     * Whether anyone is actually looking at the phone: the screen is on and the lock screen is
     * out of the way.
     *
     * The lock screen half matters as much as the screen half. Waking to a notification puts the
     * display on with the last app still nominally in front, and a guard that acted on that would
     * send you home and stack a block screen behind the lock for an app nobody had opened.
     */
    private fun phoneInUse(): Boolean {
        val power = getSystemService(PowerManager::class.java) ?: return true
        if (!power.isInteractive) return false
        val keyguard = getSystemService(KeyguardManager::class.java) ?: return true
        return !keyguard.isKeyguardLocked
    }

    /**
     * Sleeps until the phone is picked up again.
     *
     * The broadcast does the waking; the timeout is only a safety net, for the phones that deliver
     * one of these late or not at all. A minute of sleeping costs nothing — the system coalesces
     * it with everything else that wakes up while dozing — and it is a minute in which nothing can
     * be opened anyway.
     */
    private suspend fun awaitUse() {
        while (lifecycleScope.coroutineContext.isActive && !phoneInUse()) {
            withTimeoutOrNull(ASLEEP_RECHECK_MILLIS) { inUse.first { it } }
        }
        inUse.value = true
        // The event stream has moved on without us; do not judge the screen on stale events.
        lastQueryAt = 0L
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
            beginTracking(packageName, SessionGovernor.grant?.wasBypass == true, handedOff = false, now = now)
            return true
        }

        // A grant that has already run out belongs to a visit that is over. The app is coming
        // forward again and is about to be decided on afresh, so the spent grant has no say in
        // what happens next — and left lying about it has the last word anyway: the expiry check
        // in [onSameAppStillOpen] would fire on the very next tick and shut the app this decision
        // is about to allow. That is what closed a browser a second after a link had opened it,
        // on a handoff that had cost nothing and granted nothing: the session that ended it had
        // ended before the link was ever tapped.
        if (SessionGovernor.isExpiredFor(packageName, now)) SessionGovernor.clear()

        // A link, a sign-in page, a web app that is really a browser tab. Whether that is worth
        // anything is the evaluator's business — it is the one place that decides — so it is told
        // how the app arrived and answers with the whole rule, gate and budget together.
        val snapshot = repository.snapshot(packageName)
        val arrivedByHandoff = snapshot.openableByHandoff && handedOff(packageName)

        return when (val decision = repository.decide(packageName, now, arrivedByHandoff)) {
            is GateDecision.Allow -> {
                // Favourites and utilities still get their time recorded, just not policed, and
                // so does a handoff — recorded as the handoff it was, so that the minutes show up
                // on the dashboard without the browser's own allowance being what paid for them.
                beginTracking(packageName, wasBypass = false, handedOff = arrivedByHandoff, now = now)
                snapshot.tier != AppTier.FAVORITE && snapshot.tier != AppTier.ALLOWED
            }

            is GateDecision.RequireFriction -> {
                intervene(packageName, now) { GateActivity.newIntent(this, packageName) }
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

        // A spent bypass is not re-argued. The refusal it was spent on — the prayer window, the
        // blackout, the exhausted budget — is still in force a second later and will be in force
        // for the whole session, so re-deciding here closed the app on the very next tick: a few
        // seconds of the app, for one of three bypasses a week. The grant is the decision, and it
        // ends the only way it should, at [SessionGovernor.isExpiredFor] above.
        val bypassed = SessionGovernor.grant?.wasBypass == true &&
            SessionGovernor.isGrantedFor(packageName, now)
        if (bypassed) return true

        // Re-evaluate mid-session: a budget can run out, or a blackout can begin, while you sit
        // inside the app. A prayer window is checked whatever the app is, because unlike the
        // other limits it closes favourites too — being already inside one is exactly the case
        // that matters when the adhan arrives.
        if (SessionGovernor.grant?.packageName == packageName || repository.activePrayerWindow(now) != null) {
            // Asked the way it was asked when the app opened. Nothing about how it arrived has
            // changed since — it has been in front the whole time — and a visit that was waived
            // at the door must not be refused halfway through for a rule that was already waived.
            val decision = repository.decide(packageName, now, trackedByHandoff)
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

    private suspend fun beginTracking(
        packageName: String,
        wasBypass: Boolean,
        handedOff: Boolean,
        now: Long,
    ) {
        trackedPackage = packageName
        trackedByHandoff = handedOff
        openSessionId = repository.openSession(packageName, wasBypass, handedOff, now)
    }

    private suspend fun closeTracking(now: Long) {
        if (trackedPackage != null || openSessionId != null) {
            repository.closeOpenSessions(now)
        }
        trackedPackage = null
        trackedByHandoff = false
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
     * The most recent app to come to the foreground.
     *
     * A quiet minute produces no events at all, so a poll that finds none keeps the last known
     * answer rather than deciding the screen is empty. That is also what makes it safe to ask only
     * for what has happened since the last poll: silence and "nothing new" are the same thing here.
     */
    private fun foregroundPackage(now: Long): String? {
        val usage = getSystemService(UsageStatsManager::class.java) ?: return null
        // Only the events since the last poll, with an overlap so nothing falls between two
        // windows. Re-reading a whole minute of events every second was the same answer parsed
        // sixty times over, and the parsing is what the poll actually costs.
        val since = if (lastQueryAt == 0L) now - LOOKBACK_MILLIS else maxOf(
            lastQueryAt - QUERY_OVERLAP_MILLIS,
            now - LOOKBACK_MILLIS,
        )
        lastQueryAt = now
        val events: UsageEvents = usage.queryEvents(since, now + 1_000)
        val event = UsageEvents.Event()
        var latest: String? = null
        var behindLatest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            if (event.packageName == latest) continue
            behindLatest = latest
            latest = event.packageName
        }
        if (latest != null && latest != lastForeground) {
            // What came before it is read out of the events themselves rather than out of the
            // last poll. Two apps can come and go between two ticks — five seconds pass between
            // them while nothing is being policed — and the poll before could easily have caught
            // the home screen rather than the app that did the handing over. Sampling it that way
            // gated a browser opened from a message whenever the message app had been reached
            // quickly enough, and gated one every time the service had just started and had no
            // previous poll to remember.
            previousForeground = behindLatest ?: lastForeground
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

        /** Overlap between two polls' windows, so an event on the boundary is not missed. */
        private const val QUERY_OVERLAP_MILLIS = 2_000L

        /** How long the loop sleeps before checking again whether the phone has been picked up. */
        private const val ASLEEP_RECHECK_MILLIS = 60_000L

        /** Stops a stubborn app from producing a stack of block screens. */
        private const val INTERVENTION_DEBOUNCE_MILLIS = 3_000L

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, GuardService::class.java))
            }.onFailure { Log.w(TAG, "Could not start the guard service", it) }
        }
    }
}
