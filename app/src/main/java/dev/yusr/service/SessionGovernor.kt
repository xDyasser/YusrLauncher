package dev.yusr.service

import dev.yusr.domain.BudgetCalculator

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

    /**
     * When the phone stopped being used, while a grant was still running.
     *
     * A session is an allowance of *use*, and a phone in a pocket is not being used. Left running
     * against the wall clock, a five-minute grant was spent by a screen that went off for six —
     * you put the phone down mid-session and picked it up to a block screen, having spent the
     * open on nothing. The clock stops here and starts again at [resume].
     */
    @Volatile
    private var pausedAtMillis: Long? = null

    fun grant(packageName: String, sessionMinutes: Int, wasBypass: Boolean, now: Long = System.currentTimeMillis()) {
        pausedAtMillis = null
        grant = Grant(packageName, now, sessionMinutes, wasBypass)
    }

    fun clear() {
        grant = null
        pausedAtMillis = null
    }

    /** The phone has gone to sleep or locked: hold the session where it is. */
    fun pause(now: Long = System.currentTimeMillis()) {
        if (grant == null || pausedAtMillis != null) return
        pausedAtMillis = now
    }

    /** Picked up again: give back exactly the time that was slept through. */
    fun resume(now: Long = System.currentTimeMillis()) {
        val pausedAt = pausedAtMillis ?: return
        pausedAtMillis = null
        val current = grant ?: return
        val slept = (now - pausedAt).coerceAtLeast(0L)
        grant = current.copy(grantedAtMillis = current.grantedAtMillis + slept)
    }

    fun isGrantedFor(packageName: String, now: Long = System.currentTimeMillis()): Boolean {
        val current = grant ?: return false
        if (current.packageName != packageName) return false
        return !BudgetCalculator.isSessionExpired(current.grantedAtMillis, current.sessionMinutes, asOf(now))
    }

    fun isExpiredFor(packageName: String, now: Long = System.currentTimeMillis()): Boolean {
        val current = grant ?: return false
        if (current.packageName != packageName) return false
        return BudgetCalculator.isSessionExpired(current.grantedAtMillis, current.sessionMinutes, asOf(now))
    }

    fun secondsLeft(now: Long = System.currentTimeMillis()): Long {
        val current = grant ?: return 0
        return BudgetCalculator.secondsLeftInSession(current.grantedAtMillis, current.sessionMinutes, asOf(now))
    }

    /** A paused session is read at the moment it was paused, however long ago that was. */
    private fun asOf(now: Long): Long = pausedAtMillis ?: now
}
