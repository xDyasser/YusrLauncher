package dev.yusr.work

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.yusr.R
import dev.yusr.YusrApp
import dev.yusr.container
import dev.yusr.domain.Khums
import dev.yusr.ui.t
import dev.yusr.util.DayClock
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Checks once a day whether the khums year is a week from ending, or ends today, and says so
 * once. It does no accounting: the date is the user's, and so is the reckoning.
 */
class KhumsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = applicationContext.container.settingsStore
        val settings = store.current()
        if (!settings.khumsEnabled) return Result.success()

        val today = DayClock.localDateTime(System.currentTimeMillis()).toLocalDate()
        if (settings.khumsLastReminded == today.toString()) return Result.success()
        val reminder = Khums.reminderOn(today, settings.khumsMonth, settings.khumsDay) ?: return Result.success()

        val (title, body) = when (reminder) {
            Khums.Reminder.WEEK_BEFORE -> t("Khums · a week to go") to
                t(
                    "your khums year ends on %s. a week to do the accounts.",
                    Khums.nextOn(today, settings.khumsMonth, settings.khumsDay)
                        ?.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))
                        .orEmpty(),
                )
            Khums.Reminder.TODAY -> t("Khums · your year ends today") to
                t("what is left over from the year's earnings, after its needs, is due today.")
        }
        val notification = Notification.Builder(applicationContext, YusrApp.CHANNEL_KHUMS)
            .setSmallIcon(R.drawable.ic_stat_minimal)
            .setContentTitle(title)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
        store.setKhumsLastReminded(today.toString())
        return Result.success()
    }

    private companion object {
        const val NOTIFICATION_ID = 42
    }
}
