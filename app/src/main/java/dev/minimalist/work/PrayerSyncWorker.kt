package dev.minimalist.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.minimalist.container
import dev.minimalist.data.net.PrayerSync

/**
 * Refreshes the cached timetable. Failing is fine and common — the solver answers either way —
 * so this never retries aggressively and never surfaces an error.
 */
class PrayerSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.container
        val settings = container.settingsStore.current().prayer
        if (!settings.syncOverNetwork || !settings.configured) return Result.success()

        runCatching { PrayerSync(container.prayerRepository).sync(settings) }
        return Result.success()
    }
}

/**
 * Fetches the Qur'an once. Retries on failure, because unlike the timetable there is no local
 * computation that can stand in for the text — only the twenty-odd bundled ayat.
 */
class QuranDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.container
        val downloaded = runCatching {
            dev.minimalist.data.net.QuranDownloader(container.quran).download()
        }.getOrDefault(false)

        return if (downloaded) {
            container.settingsStore.setQuranSyncedAt(System.currentTimeMillis())
            Result.success()
        } else {
            Result.retry()
        }
    }
}
