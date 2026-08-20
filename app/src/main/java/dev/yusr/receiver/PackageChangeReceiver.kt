package dev.yusr.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.yusr.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Keeps the rule book level with the phone.
 *
 * The catalogue used to be synced only when the home screen was created, and the home screen is
 * a launcher: it is created once and then lives for weeks. Anything installed after that never
 * appeared under "Apps and limits" — and, worse, was never given a rule, so it opened on nothing
 * but the default until something else happened to trigger a sync. An install is a broadcast;
 * this listens for it.
 */
class PackageChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return
        if (packageName == context.packageName) return

        // An update arrives as a remove followed by an add. Neither is a decision about the app,
        // and forgetting its rule in between would quietly hand a gated app back its default.
        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        val removed = intent.action == Intent.ACTION_PACKAGE_REMOVED ||
            intent.action == Intent.ACTION_PACKAGE_FULLY_REMOVED
        if (removed && replacing) return

        val repository = context.applicationContext.container.repository
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runCatching {
                if (removed) repository.forgetPackage(packageName) else repository.syncCatalog()
            }.onFailure { Log.w(TAG, "Could not sync $packageName", it) }
            pending.finish()
        }
    }

    private companion object {
        const val TAG = "PackageChange"
    }
}
