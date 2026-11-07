package dev.yusr.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.yusr.R
import dev.yusr.YusrApp
import dev.yusr.container
import dev.yusr.domain.NightWake
import dev.yusr.ui.night.NightWakeActivity
import dev.yusr.ui.t
import dev.yusr.util.DayClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * The alarm for ṣalāt al-layl.
 *
 * Set with [AlarmManager.setAlarmClock], which is what an alarm clock uses: it rings on time in
 * Doze, it shows in the status bar as the next alarm, and on this app's minimum Android it needs
 * no permission prompt — `USE_EXACT_ALARM` is granted at install. One alarm is ever set, the next
 * one; when it rings it sets the one after, so a change of season, place or setting is picked up
 * the next night without anything else having to notice.
 */
object NightWakeAlarm {

    /** Sets the next alarm, or cancels it when the setting is off or there is no timetable. */
    suspend fun schedule(context: Context) {
        val container = context.container
        val settings = container.settingsStore.current()
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val ring = ringIntent(context)
        if (!settings.nightWakeEnabled || !settings.prayer.configured) {
            manager.cancel(ring)
            return
        }
        if (!manager.canScheduleExactAlarms()) return

        val now = DayClock.localDateTime(System.currentTimeMillis())
        // The timetable for each of the three days the next alarm can depend on, read up front:
        // the domain wants a plain function from a date to its times.
        val days = (-1L..1L).associate { offset ->
            val date = now.toLocalDate().plusDays(offset)
            date to container.prayerRepository.timetable(settings.prayer, date)
        }
        val next = NightWake.next(
            now = now,
            timetableFor = { date -> days[date] ?: days.getValue(now.toLocalDate()) },
            anchor = settings.nightWakeAnchor,
            minutesBefore = settings.nightWakeMinutesBefore,
        )
        setAt(context, manager, next, ring)
    }

    /** Ten more minutes. Replaces the night's alarm; ringing again sets the next night's. */
    fun snooze(context: Context, minutes: Long = SNOOZE_MINUTES) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        if (!manager.canScheduleExactAlarms()) return
        val at = DayClock.localDateTime(System.currentTimeMillis()).plusMinutes(minutes)
        setAt(context, manager, at, ringIntent(context))
    }

    /** Takes the alarm notification down, which also stops its sound. */
    fun dismiss(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun setAt(context: Context, manager: AlarmManager, at: LocalDateTime, ring: PendingIntent) {
        val millis = at.atZone(DayClock.zone()).toInstant().toEpochMilli()
        val show = PendingIntent.getActivity(
            context,
            0,
            Intent(context, NightWakeActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(millis, show), ring)
    }

    private fun ringIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_RING,
        Intent(context, NightWakeReceiver::class.java).setAction(ACTION_RING),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * The alarm itself: a notification on the alarm channel, insistent so it keeps sounding
     * until it is answered, with a full-screen screen for a locked phone.
     */
    internal fun ring(context: Context) {
        val fullScreen = PendingIntent.getActivity(
            context,
            1,
            Intent(context, NightWakeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, YusrApp.CHANNEL_NIGHT)
            .setSmallIcon(R.drawable.ic_stat_minimal)
            .setContentTitle(t("The last third of the night"))
            .setContentText(t("ṣalāt al-layl · before fajr"))
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setOngoing(true)
            .build()
            .apply { flags = flags or Notification.FLAG_INSISTENT }
        context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
    }

    internal const val ACTION_RING = "dev.yusr.NIGHT_WAKE_RING"
    private const val REQUEST_RING = 41
    private const val NOTIFICATION_ID = 41
    const val SNOOZE_MINUTES = 10L
}

/** Rings, then sets the next night's alarm. */
class NightWakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NightWakeAlarm.ACTION_RING) return
        NightWakeAlarm.ring(context)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                NightWakeAlarm.schedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
