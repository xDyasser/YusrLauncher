package dev.yusr.data

import dev.yusr.data.db.BlackoutWindowEntity
import dev.yusr.data.db.PendingChangeKind
import dev.yusr.data.settings.SettingsStore
import dev.yusr.domain.AppTier
import dev.yusr.util.DayClock

/** What happened when the user asked for a change. */
sealed interface MutationResult {
    data object AppliedNow : MutationResult

    /** Queued: it weakens the rules, so it serves the cooldown first. */
    data class Deferred(val applyAtMillis: Long) : MutationResult
}

/**
 * Every settings change passes through here.
 *
 * The asymmetry is the point: making the rules stricter happens the moment you ask, making them
 * looser waits out the cooldown. A decision made calmly is easy to keep and hard to undo.
 *
 * The exception is setup. Until the rules are locked in, everything applies immediately — you
 * cannot be asked to sort out sixty apps thirty minutes at a time.
 */
class RuleMutator(
    private val repository: YusrRepository,
    private val settingsStore: SettingsStore,
) {

    /** True while the app is still being configured, when nothing has to wait. */
    private suspend fun unlocked(): Boolean = !settingsStore.current().rulesLocked

    suspend fun setTier(packageName: String, tier: AppTier): MutationResult {
        val existing = repository.rule(packageName) ?: return MutationResult.AppliedNow
        return if (unlocked() || severity(tier) >= severity(existing.tier)) {
            repository.setTierNow(packageName, tier)
            MutationResult.AppliedNow
        } else {
            defer(
                PendingChangeKind.SET_TIER,
                packageName,
                tier.name,
                "${existing.label} → ${tier.name.lowercase()}",
            )
        }
    }

    suspend fun setDailyMinutes(packageName: String, minutes: Int?): MutationResult {
        val existing = repository.rule(packageName) ?: return MutationResult.AppliedNow
        return if (unlocked() || isTighterCap(minutes, existing.dailyMinutes)) {
            repository.upsertRule(existing.copy(dailyMinutes = minutes))
            MutationResult.AppliedNow
        } else {
            defer(
                PendingChangeKind.SET_DAILY_MINUTES,
                packageName,
                minutes?.toString().orEmpty(),
                "${existing.label} daily limit → ${minutes?.let { DayClock.formatMinutes(it) } ?: "none"}",
            )
        }
    }

    suspend fun setDailyOpens(packageName: String, opens: Int?): MutationResult {
        val existing = repository.rule(packageName) ?: return MutationResult.AppliedNow
        return if (unlocked() || isTighterCap(opens, existing.dailyOpens)) {
            repository.upsertRule(existing.copy(dailyOpens = opens))
            MutationResult.AppliedNow
        } else {
            defer(
                PendingChangeKind.SET_DAILY_OPENS,
                packageName,
                opens?.toString().orEmpty(),
                "${existing.label} daily opens → ${opens ?: "unlimited"}",
            )
        }
    }

    suspend fun setBaseDelay(seconds: Int): MutationResult {
        val current = settingsStore.current().policy.baseDelaySeconds
        return if (unlocked() || seconds >= current) {
            settingsStore.setBaseDelay(seconds)
            MutationResult.AppliedNow
        } else {
            defer(PendingChangeKind.SET_BASE_DELAY, "", seconds.toString(), "base wait → ${seconds}s")
        }
    }

    suspend fun setEscalation(seconds: Int): MutationResult {
        val current = settingsStore.current().policy.escalationSecondsPerOpen
        return if (unlocked() || seconds >= current) {
            settingsStore.setEscalation(seconds)
            MutationResult.AppliedNow
        } else {
            defer(PendingChangeKind.SET_ESCALATION, "", seconds.toString(), "escalation → ${seconds}s per open")
        }
    }

    suspend fun setMinReasonLength(chars: Int): MutationResult {
        val current = settingsStore.current().policy.minReasonLength
        return if (unlocked() || chars >= current) {
            settingsStore.setMinReasonLength(chars)
            MutationResult.AppliedNow
        } else {
            defer(PendingChangeKind.SET_MIN_REASON_LENGTH, "", chars.toString(), "reason length → $chars")
        }
    }

    suspend fun setDefaultSessionMinutes(minutes: Int): MutationResult {
        val current = settingsStore.current().policy.defaultSessionMinutes
        return if (unlocked() || minutes <= current) {
            settingsStore.setDefaultSessionMinutes(minutes)
            MutationResult.AppliedNow
        } else {
            defer(
                PendingChangeKind.SET_DEFAULT_SESSION_MINUTES,
                "",
                minutes.toString(),
                "session length → ${DayClock.formatMinutes(minutes)}",
            )
        }
    }

    suspend fun setCooldownMinutes(minutes: Int): MutationResult {
        val current = settingsStore.current().cooldownMinutes
        return if (unlocked() || minutes >= current) {
            settingsStore.setCooldownMinutes(minutes)
            MutationResult.AppliedNow
        } else {
            // Shortening the cooldown must itself serve the current, longer cooldown.
            defer(PendingChangeKind.SET_COOLDOWN_MINUTES, "", minutes.toString(), "cooldown → ${minutes}m")
        }
    }

    suspend fun setBypassesPerWeek(count: Int): MutationResult {
        val current = settingsStore.current().bypassesPerWeek
        return if (unlocked() || count <= current) {
            settingsStore.setBypassesPerWeek(count)
            MutationResult.AppliedNow
        } else {
            defer(PendingChangeKind.SET_BYPASSES_PER_WEEK, "", count.toString(), "bypasses → $count per week")
        }
    }

    /** Adding or enabling a blackout is a tightening, so it takes hold immediately. */
    suspend fun upsertBlackout(window: BlackoutWindowEntity): MutationResult {
        repository.upsertBlackout(window)
        return MutationResult.AppliedNow
    }

    suspend fun setBlackoutEnabled(id: Long, enabled: Boolean, label: String): MutationResult {
        return if (unlocked() || enabled) {
            repository.blackoutSpecs().firstOrNull { it.id == id }?.let {
                repository.upsertBlackout(
                    BlackoutWindowEntity(
                        id = it.id,
                        label = it.label,
                        startMinuteOfDay = it.startMinuteOfDay,
                        endMinuteOfDay = it.endMinuteOfDay,
                        daysMask = dev.yusr.domain.BlackoutSchedule.daysToMask(it.daysOfWeek),
                        enabled = enabled,
                    ),
                )
            }
            MutationResult.AppliedNow
        } else {
            defer(PendingChangeKind.SET_BLACKOUT_ENABLED, id.toString(), "false", "turn off “$label”")
        }
    }

    suspend fun deleteBlackout(id: Long, label: String): MutationResult {
        if (unlocked()) {
            repository.deleteBlackout(id)
            return MutationResult.AppliedNow
        }
        return defer(PendingChangeKind.DELETE_BLACKOUT, id.toString(), "", "delete “$label”")
    }

    // ---- salah -------------------------------------------------------------------------

    /** Turning salah enforcement on is instant; turning it off waits, like every loosening. */
    suspend fun setPrayerEnabled(enabled: Boolean): MutationResult {
        return if (unlocked() || enabled) {
            settingsStore.setPrayerEnabled(enabled)
            MutationResult.AppliedNow
        } else {
            defer(PendingChangeKind.SET_PRAYER_ENABLED, "", "false", "stop pausing for salah")
        }
    }

    /** A longer pause for salah is a tightening; a shorter one is not. */
    suspend fun setPrayerWindowMinutes(before: Int, after: Int): MutationResult {
        val current = settingsStore.current().prayer
        val longer = before >= current.windowBeforeMinutes && after >= current.windowAfterMinutes
        return if (unlocked() || longer) {
            settingsStore.setWindowMinutes(before, after)
            MutationResult.AppliedNow
        } else {
            defer(
                PendingChangeKind.SET_PRAYER_WINDOW_MINUTES,
                "",
                "$before,$after",
                "salah pause → ${before}m before, ${after}m after",
            )
        }
    }

    /**
     * Exempting an app from the prayer window is a hole in the wall, so it waits. Removing an
     * exemption closes one, so it does not.
     */
    suspend fun setPrayerExempt(packageName: String, exempt: Boolean): MutationResult {
        val existing = repository.rule(packageName) ?: return MutationResult.AppliedNow
        return if (unlocked() || !exempt) {
            repository.setPrayerExemptNow(packageName, exempt)
            MutationResult.AppliedNow
        } else {
            defer(
                PendingChangeKind.SET_PRAYER_EXEMPT,
                packageName,
                "true",
                "${existing.label} opens during salah",
            )
        }
    }

    /**
     * Letting another app hand off to a gated one is a hole in the wall, so opening it waits.
     * Closing it does not.
     */
    suspend fun setOpenableByHandoff(packageName: String, openable: Boolean): MutationResult {
        val existing = repository.rule(packageName) ?: return MutationResult.AppliedNow
        return if (unlocked() || !openable) {
            repository.setOpenableByHandoffNow(packageName, openable)
            MutationResult.AppliedNow
        } else {
            defer(
                PendingChangeKind.SET_OPENABLE_BY_HANDOFF,
                packageName,
                "true",
                "${existing.label} opens when another app sends you to it",
            )
        }
    }

    private suspend fun defer(
        kind: PendingChangeKind,
        targetKey: String,
        newValue: String,
        description: String,
    ): MutationResult {
        val change = repository.enqueuePendingChange(kind, targetKey, newValue, description)
        return MutationResult.Deferred(change.applyAtMillis)
    }

    private companion object {
        /** Higher is stricter. */
        fun severity(tier: AppTier): Int = when (tier) {
            AppTier.FAVORITE -> 0
            AppTier.ALLOWED -> 1
            AppTier.GATED -> 2
            AppTier.BLOCKED -> 3
        }

        /** A cap is tighter when it exists and is no larger than what it replaces. */
        fun isTighterCap(next: Int?, current: Int?): Boolean = when {
            next == null -> false
            current == null -> true
            else -> next <= current
        }
    }
}
