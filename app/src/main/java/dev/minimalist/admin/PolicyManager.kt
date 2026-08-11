package dev.minimalist.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import dev.minimalist.ui.home.HomeActivity

/** How much power the app actually has on this device. */
enum class PolicyTier {
    /** Nothing granted. Friction only, and the launcher can be swapped in Settings. */
    NONE,

    /** Active device admin: uninstalling requires deactivating admin first. */
    ADMIN,

    /** Device owner: the OS itself will refuse to open suspended apps. */
    OWNER,
}

/**
 * The bridge to [DevicePolicyManager]. Everything here degrades quietly — a device with no
 * admin granted still runs, it just relies on in-app friction instead of the OS.
 */
class PolicyManager(private val context: Context) {

    private val dpm: DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)

    private val admin: ComponentName = MinimalAdminReceiver.component(context)

    fun tier(): PolicyTier = when {
        dpm.isDeviceOwnerApp(context.packageName) -> PolicyTier.OWNER
        dpm.isAdminActive(admin) -> PolicyTier.ADMIN
        else -> PolicyTier.NONE
    }

    val isDeviceOwner: Boolean get() = dpm.isDeviceOwnerApp(context.packageName)

    val isAdminActive: Boolean get() = dpm.isAdminActive(admin)

    /** Intent that opens the system's "activate device admin?" screen. */
    fun addAdminIntent(explanation: String): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation)

    fun onAdminEnabled() {
        runCatching { if (isDeviceOwner) protectSelf() }
    }

    /**
     * OS-level suspension of blocked apps. Only a device owner can do this; on lesser tiers the
     * guard service does the work instead.
     */
    fun applyBlocklist(blocked: Collection<String>, previouslyBlocked: Collection<String> = emptyList()) {
        if (!isDeviceOwner) return
        val toUnsuspend = previouslyBlocked - blocked.toSet()
        runCatching {
            if (blocked.isNotEmpty()) dpm.setPackagesSuspended(admin, blocked.toTypedArray(), true)
            if (toUnsuspend.isNotEmpty()) dpm.setPackagesSuspended(admin, toUnsuspend.toTypedArray(), false)
        }.onFailure { Log.w(TAG, "Could not update suspended packages", it) }
    }

    fun isSuspended(packageName: String): Boolean = runCatching {
        isDeviceOwner && dpm.isPackageSuspended(admin, packageName)
    }.getOrDefault(false)

    /** Makes this app the home screen in a way Settings cannot casually undo. */
    fun lockAsHome() {
        if (!isDeviceOwner) return
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        runCatching {
            dpm.addPersistentPreferredActivity(
                admin,
                filter,
                ComponentName(context, HomeActivity::class.java),
            )
        }.onFailure { Log.w(TAG, "Could not pin the launcher", it) }
    }

    fun unlockHome() {
        if (!isDeviceOwner) return
        runCatching { dpm.clearPackagePersistentPreferredActivities(admin, context.packageName) }
    }

    /** Blocks uninstall, and on API 30+ stops the OS force-stopping the guard service. */
    fun protectSelf() {
        if (!isDeviceOwner) return
        runCatching { dpm.setUninstallBlocked(admin, context.packageName, true) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { dpm.setUserControlDisabledPackages(admin, listOf(context.packageName)) }
        }
    }

    /**
     * Undoes every policy and hands the device back. Deliberately all-or-nothing: this is the
     * documented exit, not a per-app loophole.
     */
    fun releaseEverything(blocked: Collection<String>) {
        if (isDeviceOwner) {
            runCatching { if (blocked.isNotEmpty()) dpm.setPackagesSuspended(admin, blocked.toTypedArray(), false) }
            runCatching { dpm.setUninstallBlocked(admin, context.packageName, false) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { dpm.setUserControlDisabledPackages(admin, emptyList()) }
            }
            unlockHome()
            runCatching { dpm.clearDeviceOwnerApp(context.packageName) }
        } else if (isAdminActive) {
            runCatching { dpm.removeActiveAdmin(admin) }
        }
    }

    companion object {
        private const val TAG = "PolicyManager"
    }
}
