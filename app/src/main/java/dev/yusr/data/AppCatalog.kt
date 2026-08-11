package dev.yusr.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import dev.yusr.data.db.AppRuleEntity
import dev.yusr.domain.AppTier

data class InstalledApp(
    val packageName: String,
    val label: String,
)

/**
 * The list of launchable apps, plus the opinion the app starts with: everything is [AppTier.GATED]
 * unless it is something you would not want to fight with in an emergency.
 */
class AppCatalog(private val context: Context) {

    private val packageManager: PackageManager get() = context.packageManager

    fun installedApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)
        return resolved
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { InstalledApp(it.packageName, packageManager.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun labelFor(packageName: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    fun launchIntentFor(packageName: String): Intent? =
        packageManager.getLaunchIntentForPackage(packageName)

    /**
     * Packages the enforcement layer must never interfere with. Locking yourself out of the
     * dialer or the keyboard is not minimalism, it is a bricked phone.
     */
    fun protectedPackages(): Set<String> {
        val protectedSet = mutableSetOf(context.packageName, "android", "com.android.systemui")

        runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        }.getOrNull()?.let(protectedSet::add)

        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let(protectedSet::add)

        runCatching {
            val imm = context.getSystemService(InputMethodManager::class.java)
            imm?.enabledInputMethodList?.forEach { protectedSet.add(it.packageName) }
        }

        // Whatever this device uses for its own Settings app.
        runCatching {
            packageManager.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)
                ?.activityInfo?.packageName
        }.getOrNull()?.let(protectedSet::add)

        return protectedSet
    }

    /**
     * The tier an app gets when nobody has said otherwise.
     *
     * Anything shipped with the phone is allowed: the clock, the calculator, the file manager and
     * the camera are not what anyone loses an evening to, and making you type "Files" to open a
     * file manager is friction with nothing on the other side of it. Entertainment is judged on
     * its category, not on who signed it — a preinstalled video app is still a video app.
     */
    fun defaultTierFor(packageName: String): AppTier {
        if (packageName in protectedPackages()) return AppTier.ALLOWED
        val info = infoFor(packageName)
        return if (isSystemApp(info) && !isTimeSink(info)) AppTier.ALLOWED else AppTier.GATED
    }

    /**
     * Builds the starting rule set. Nuclear by default for everything you installed yourself; the
     * obvious time sinks are gated with a hard budget already attached.
     */
    fun seedRules(): List<AppRuleEntity> {
        val protectedSet = protectedPackages()
        val exempt = prayerExemptPackages()
        val browsers = browserPackages()
        val favorites = mutableListOf<String>()

        val rules = installedApps().map { app ->
            val info = infoFor(app.packageName)
            val tier = when {
                app.packageName in protectedSet -> AppTier.ALLOWED
                isSystemApp(info) && !isTimeSink(info) -> AppTier.ALLOWED
                else -> AppTier.GATED
            }
            val budget = if (tier == AppTier.GATED && isTimeSink(info)) {
                DEFAULT_TIME_SINK_BUDGET
            } else {
                null
            }
            AppRuleEntity(
                packageName = app.packageName,
                label = app.label,
                tier = tier,
                dailyMinutes = budget?.first,
                dailyOpens = budget?.second,
                prayerExempt = app.packageName in exempt,
                openableByHandoff = app.packageName in browsers,
            )
        }

        // Seed the home screen with the handful of things you actually need.
        for (packageName in seedFavoriteCandidates()) {
            if (favorites.size >= MAX_SEEDED_FAVORITES) break
            if (rules.any { it.packageName == packageName }) favorites.add(packageName)
        }

        return rules.map { rule ->
            val index = favorites.indexOf(rule.packageName)
            if (index >= 0) rule.copy(tier = AppTier.FAVORITE, favoriteOrder = index) else rule
        }
    }

    /**
     * What still opens while a prayer window is in force. Only the dialer to begin with — a
     * missed call during salah is a real cost, and everything else can wait twenty minutes.
     * A mushaf or an adhkar app is added by hand, because no heuristic can pick one out.
     */
    fun prayerExemptPackages(): Set<String> = buildSet {
        runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        }.getOrNull()?.let(::add)
    }

    /**
     * Everything that can open a web page.
     *
     * These are the apps a handoff exemption is for: a browser sits behind links, sign-in pages,
     * custom tabs and every "web app" that is a shortcut into one, so gating it gates a good
     * half of the phone by accident.
     */
    fun browserPackages(): Set<String> = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet()
    }.getOrDefault(emptySet())

    private fun seedFavoriteCandidates(): List<String> = buildList {
        runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        }.getOrNull()?.let(::add)
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let(::add)
        resolveFirst(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))?.let(::add)
        resolveFirst(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))?.let(::add)
        resolveFirst(Intent(Settings.ACTION_SETTINGS))?.let(::add)
    }.distinct()

    private fun resolveFirst(intent: Intent): String? = runCatching {
        packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
    }.getOrNull()

    private fun infoFor(packageName: String): ApplicationInfo? =
        runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull()

    /**
     * Preinstalled, including the ones that have since taken an update from the store — Android
     * clears FLAG_SYSTEM on those and sets FLAG_UPDATED_SYSTEM_APP instead, which is how a stock
     * Gallery or Camera ends up looking like a third-party install.
     */
    private fun isSystemApp(info: ApplicationInfo?): Boolean {
        if (info == null) return false
        val flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        return info.flags and flags != 0
    }

    private fun isTimeSink(info: ApplicationInfo?): Boolean =
        info != null && info.category in TIME_SINK_CATEGORIES

    companion object {
        const val MAX_SEEDED_FAVORITES = 5

        /** minutes to opens */
        private val DEFAULT_TIME_SINK_BUDGET = 20 to 3

        private val TIME_SINK_CATEGORIES = setOf(
            ApplicationInfo.CATEGORY_SOCIAL,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_GAME,
            ApplicationInfo.CATEGORY_NEWS,
            ApplicationInfo.CATEGORY_AUDIO,
        )
    }
}
