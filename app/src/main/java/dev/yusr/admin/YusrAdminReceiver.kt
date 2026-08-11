package dev.yusr.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.yusr.R

class YusrAdminReceiver : DeviceAdminReceiver() {

    /** Shown when you try to turn admin off — the last chance to notice what you are doing. */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_disable_warning)

    override fun onEnabled(context: Context, intent: Intent) {
        PolicyManager(context).onAdminEnabled()
    }

    companion object {
        fun component(context: Context): ComponentName =
            ComponentName(context, YusrAdminReceiver::class.java)
    }
}
