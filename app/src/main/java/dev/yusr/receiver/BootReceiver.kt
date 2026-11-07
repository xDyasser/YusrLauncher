package dev.yusr.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.yusr.service.GuardService
import dev.yusr.service.NightWakeAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import dev.yusr.work.WorkScheduler

/** Enforcement has to survive a reboot and an app update, or it is only a suggestion. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                GuardService.start(context)
                WorkScheduler.scheduleRecurring(context)
                // Alarms do not survive a reboot; the night's has to be set again.
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
    }
}
