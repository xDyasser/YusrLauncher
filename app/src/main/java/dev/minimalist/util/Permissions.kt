package dev.minimalist.util

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import dev.minimalist.service.NavAccessibilityService

/**
 * The special accesses this app needs. All of them are granted by the user in system Settings,
 * none can be requested with a runtime dialog, so the setup checklist links straight to each.
 */
object Permissions {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Draw-over-other-apps. Beyond drawing, this is what exempts the guard service from the
     * background activity launch restrictions, so the block screen can appear over an app.
     */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun notificationListenerEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        return enabled.split(':').any { it.substringBefore('/') == context.packageName }
    }

    /**
     * Whether the navigation strip's accessibility service has been switched on in Settings. The
     * stored list writes components both ways — "pkg/pkg.Class" and "pkg/.Class" — so it is
     * parsed rather than string-matched.
     */
    fun navServiceEnabled(context: Context): Boolean {
        val target = ComponentName(context, NavAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == target }
    }

    /**
     * Whether the system has been told to stop putting this app to sleep.
     *
     * This is the one item on the checklist the app cannot ask for with a dialog and cannot see
     * the whole of: an OEM's own killer — Xiaomi's autostart, the recents lock — is not visible to
     * any API, so a phone can pass this and still stop the guard service overnight. It answers the
     * part Android does know about, which is the part that was being reported wrong.
     */
    fun batteryUnrestricted(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(false)
    }

    fun accessibilitySettings(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun isDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, 0)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun usageAccessSettings(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun overlaySettings(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun notificationListenerSettings(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /**
     * The one-tap dialog where it is offered, and the whole list where it is not. Some OEMs have
     * removed the direct request; falling back to the list is worse but is still a way through.
     */
    fun batteryOptimizationSettings(context: Context): Intent {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        return if (direct.resolveActivity(context.packageManager) != null) {
            direct
        } else {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
    }

    fun homeSettings(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)

    fun appDetailsSettings(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
