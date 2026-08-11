package dev.yusr.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.yusr.container

/**
 * Applies loosening changes whose cooldown has expired, and keeps the device-owner blocklist in
 * step with the rules. Also prunes old history.
 */
class PendingChangeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = applicationContext.container.repository
        val policyManager = applicationContext.container.policyManager

        val previouslyBlocked = repository.blockedPackages()

        repository.duePendingChanges().forEach { change ->
            runCatching { repository.applyPendingChange(change) }
        }

        val blocked = repository.blockedPackages()
        if (blocked != previouslyBlocked) {
            policyManager.applyBlocklist(blocked, previouslyBlocked)
        }

        repository.prune()
        return Result.success()
    }
}
