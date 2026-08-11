package dev.minimalist.service

import dev.minimalist.domain.BudgetCalculator

/**
 * The single in-memory record of "you may use this app right now, until then".
 *
 * Deliberately not persisted: if the phone reboots or the service is killed, the grant dies with
 * it and the app has to be earned again. Losing a grant always fails toward more friction.
 */
object SessionGovernor {

    data class Grant(
        val packageName: String,
        val grantedAtMillis: Long,
        val sessionMinutes: Int,
        val wasBypass: Boolean,
    )

    @Volatile
    var grant: Grant? = null
        private set

    fun grant(packageName: String, sessionMinutes: Int, wasBypass: Boolean, now: Long = System.currentTimeMillis()) {
        grant = Grant(packageName, now, sessionMinutes, wasBypass)
    }

    fun clear() {
        grant = null
    }

    fun isGrantedFor(packageName: String, now: Long = System.currentTimeMillis()): Boolean {
        val current = grant ?: return false
        if (current.packageName != packageName) return false
        return !BudgetCalculator.isSessionExpired(current.grantedAtMillis, current.sessionMinutes, now)
    }

    fun isExpiredFor(packageName: String, now: Long = System.currentTimeMillis()): Boolean {
        val current = grant ?: return false
        if (current.packageName != packageName) return false
        return BudgetCalculator.isSessionExpired(current.grantedAtMillis, current.sessionMinutes, now)
    }

    fun secondsLeft(now: Long = System.currentTimeMillis()): Long {
        val current = grant ?: return 0
        return BudgetCalculator.secondsLeftInSession(current.grantedAtMillis, current.sessionMinutes, now)
    }
}
