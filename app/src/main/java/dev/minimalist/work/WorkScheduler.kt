package dev.minimalist.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val PENDING_CHANGES = "pending-changes"
    private const val DIGEST = "notification-digest"
    private const val PRAYER_SYNC = "prayer-sync"
    private const val QURAN_DOWNLOAD = "quran-download"

    fun scheduleRecurring(context: Context) {
        val manager = WorkManager.getInstance(context)

        // Fifteen minutes is WorkManager's floor; the cooldown is measured in tens of minutes,
        // so a change is never more than a quarter hour late.
        manager.enqueueUniquePeriodicWork(
            PENDING_CHANGES,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PendingChangeWorker>(15, TimeUnit.MINUTES).build(),
        )

        manager.enqueueUniquePeriodicWork(
            DIGEST,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DigestWorker>(1, TimeUnit.HOURS).build(),
        )

        // Once a day is plenty: a month of times is fetched at a time, and the solver covers
        // every day the cache does not.
        manager.enqueueUniquePeriodicWork(
            PRAYER_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PrayerSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(networkRequired)
                .build(),
        )
    }

    /** Kicked off from the prayer settings screen, and once when salah enforcement is turned on. */
    fun downloadQuran(context: Context, replaceExisting: Boolean = false) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            QURAN_DOWNLOAD,
            if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<QuranDownloadWorker>()
                .setConstraints(networkRequired)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build(),
        )
    }

    /** Called when the coordinates change, so the new place's times arrive without waiting a day. */
    fun syncPrayerTimesNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$PRAYER_SYNC-now",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PrayerSyncWorker>()
                .setConstraints(networkRequired)
                .build(),
        )
    }

    private val networkRequired: Constraints
        get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
}
