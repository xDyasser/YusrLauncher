package dev.yusr.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.yusr.service.GuardService
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
            }
        }
    }
}
