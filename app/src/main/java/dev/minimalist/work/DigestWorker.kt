package dev.minimalist.work

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.minimalist.MinimalistApp
import dev.minimalist.R
import dev.minimalist.container
import dev.minimalist.domain.DigestSchedule
import dev.minimalist.util.DayClock

/**
 * Delivers the held-back notifications as one summary, at the two times of day the user picked,
 * so nothing is lost but nothing interrupts either.
 */
class DigestWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settingsStore = applicationContext.container.settingsStore
        val settings = settingsStore.current()
        val repository = applicationContext.container.repository

        val now = System.currentTimeMillis()
        val lastDelivered = settings.lastDigestDeliveredAt
            .takeIf { it > 0 }
            ?.let { DayClock.localDateTime(it) }
        val due = DigestSchedule.shouldDeliver(
            now = DayClock.localDateTime(now),
            lastDeliveredAt = lastDelivered,
            morningHour = settings.digestMorningHour,
            eveningHour = settings.digestEveningHour,
        )
        if (!due) return Result.success()

        val held = repository.undeliveredDigest()
        if (held.isEmpty()) return Result.success()

        val catalog = applicationContext.container.catalog
        val byApp = held.groupBy { it.packageName }
            .map { (packageName, entries) -> "${catalog.labelFor(packageName)}: ${entries.size}" }
            .sorted()

        val notification = Notification.Builder(applicationContext, MinimalistApp.CHANNEL_DIGEST)
            .setSmallIcon(R.drawable.ic_stat_minimal)
            .setContentTitle("${held.size} notifications held back")
            .setStyle(Notification.BigTextStyle().bigText(byApp.joinToString("\n")))
            .setAutoCancel(true)
            .build()

        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(DIGEST_NOTIFICATION_ID, notification)
        repository.markDigestDelivered()
        settingsStore.setLastDigestDeliveredAt(now)

        return Result.success()
    }

    private companion object {
        const val DIGEST_NOTIFICATION_ID = 2
    }
}
