package dev.minimalist.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.minimalist.container
import dev.minimalist.domain.NotificationPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Dismisses notifications from apps you have decided not to be at the beck and call of, and
 * records them for the digest instead.
 *
 * What may and may not be cancelled lives in [NotificationPolicy]; this class only translates
 * Android's flags into the facts that decision needs.
 */
class MinimalNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName == this.packageName) return

        scope.launch {
            val settings = container.settingsStore.current()
            val repository = container.repository
            val facts = factsFor(sbn, repository.snapshot(packageName).tier)
            if (!NotificationPolicy.shouldSuppress(facts, settings.suppressNotifications)) return@launch

            val title = sbn.notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            repository.recordSuppressedNotification(packageName, title)
            runCatching { cancelNotification(sbn.key) }
        }
    }

    private fun factsFor(
        sbn: StatusBarNotification,
        tier: dev.minimalist.domain.AppTier,
    ): NotificationPolicy.NotificationFacts {
        val notification = sbn.notification
        val pinnedFlags = Notification.FLAG_ONGOING_EVENT or
            Notification.FLAG_FOREGROUND_SERVICE or
            Notification.FLAG_NO_CLEAR
        return NotificationPolicy.NotificationFacts(
            tier = tier,
            category = notification.category,
            pinned = notification.flags and pinnedFlags != 0,
            clearable = sbn.isClearable,
            media = isMedia(notification),
        )
    }

    /** Carries a media session, or is drawn with the media template. Either way, playback. */
    private fun isMedia(notification: Notification): Boolean {
        val extras = notification.extras ?: return false
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return true
        return extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().contains("MediaStyle")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
