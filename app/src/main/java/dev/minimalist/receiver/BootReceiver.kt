package dev.minimalist.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.minimalist.service.GuardService
import dev.minimalist.work.WorkScheduler

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
