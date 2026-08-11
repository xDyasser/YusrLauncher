package dev.yusr.util

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import dev.yusr.service.NavAccessibilityService

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

    /**
     * Whether the phone is driven by gestures or by the three buttons along the bottom.
     *
     * Not a permission, and nothing here can set it — but it belongs with these because it is the
     * same shape of thing: a system setting the launcher depends on and can only point at. Every
     * screen in this app is a list you swipe through, and the three buttons take a strip of that
     * list and put it under a bar the launcher never uses.
     *
     * `navigation_mode` is 0 for the buttons, 1 for the two-button arrangement Android 9 shipped,
     * and 2 for gestures. It is not in the public API and an OEM is free never to write it, so
     * anything absent or unrecognised comes back null — "cannot tell" — and the checklist stays
     * quiet rather than telling someone their phone is set up wrong on a guess.
     */
    fun gestureNavigation(context: Context): Boolean? {
        val mode = runCatching {
            Settings.Secure.getInt(context.contentResolver, NAVIGATION_MODE)
        }.getOrNull()
        return when (mode) {
            GESTURES -> true
            THREE_BUTTON, TWO_BUTTON -> false
            else -> null
        }
    }

    /**
     * Where that is changed. AOSP has a screen for it, but the action is not public API and an OEM
     * may have moved it — so an unresolved intent falls back to the top of Settings, and the line
     * that opens it names the path in words for exactly that case.
     */
    fun navigationModeSettings(context: Context): Intent {
        val direct = Intent(GESTURE_NAVIGATION_SETTINGS)
        return if (direct.resolveActivity(context.packageManager) != null) {
            direct
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
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

    private const val NAVIGATION_MODE = "navigation_mode"
    private const val THREE_BUTTON = 0
    private const val TWO_BUTTON = 1
    private const val GESTURES = 2
    private const val GESTURE_NAVIGATION_SETTINGS = "com.android.settings.GESTURE_NAVIGATION_SETTINGS"
}
