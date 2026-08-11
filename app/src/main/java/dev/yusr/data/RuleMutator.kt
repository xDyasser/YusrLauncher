package dev.yusr.data

import dev.yusr.data.db.BlackoutWindowEntity
import dev.yusr.data.db.PendingChangeKind
import dev.yusr.data.settings.SettingsStore
import dev.yusr.domain.AppTier
import dev.yusr.ui.t
import dev.yusr.ui.tierName
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
                t("%s → %s", existing.label, tierName(tier)),
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
                t("%s daily limit → %s", existing.label, minutes?.let { DayClock.formatMinutes(it) } ?: t("none")),
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
                t("%s daily opens → %s", existing.label, opens ?: t("unlimited")),
            )
        }
    }

    suspend fun setBaseDelay(seconds: Int): MutationResult {
        val wanted = seconds.coerceIn(0, MAX_BASE_DELAY_SECONDS)
        val current = settingsStore.current().policy.baseDelaySeconds
        return if (unlocked() || wanted >= current) {
            settingsStore.setBaseDelay(wanted)
            MutationResult.AppliedNow
        } else {
            defer(PendingChangeKind.SET_BASE_DELAY, "", wanted.toString(), t("base wait → %s", t("%ss", wanted)))
        }
    }

    suspend fun setEscalation(seconds: Int): MutationResult {
        val wanted = seconds.coerceIn(0, MAX_ESCALATION_SECONDS)
        val current = settingsStore.current().policy.escalationSecondsPerOpen
        return if (unlocked() || wanted >= current) {
            settingsStore.setEscalation(wanted)
            MutationResult.AppliedNow
        } else {
            defer(PendingChangeKind.SET_ESCALATION, "", wanted.toString(), t("escalation → %s per open", t("%ss", wanted)))
        }
    }

    suspend fun setMinReasonLength(chars: Int): MutationResult {
        val wanted = chars.coerceIn(0, MAX_REASON_LENGTH)
        val current = settingsStore.current().policy.minReasonLength
        return if (unlocked() || wanted >= current) {
            settingsStore.setMinReasonLength(wanted)
            MutationResult.AppliedNow
        } else {
            defer(PendingChangeKind.SET_MIN_REASON_LENGTH, "", wanted.toString(), t("reason length → %s", wanted))
        }
    }

    suspend fun setDefaultSessionMinutes(minutes: Int): MutationResult {
        val wanted = minutes.coerceIn(1, MAX_SESSION_MINUTES)
        val current = settingsStore.current().policy.defaultSessionMinutes
        return if (unlocked() || wanted <= current) {
            settingsStore.setDefaultSessionMinutes(wanted)
            MutationResult.AppliedNow
        } else {
            defer(
                PendingChangeKind.SET_DEFAULT_SESSION_MINUTES,
                "",
                wanted.toString(),
                t("session length → %s", DayClock.formatMinutes(wanted)),
            )
        }
    }

    suspend fun setCooldownMinutes(minutes: Int): MutationResult {
        val wanted = minutes.coerceIn(0, MAX_COOLDOWN_MINUTES)
        val current = settingsStore.current().cooldownMinutes
        return if (unlocked() || wanted >= current) {
            settingsStore.setCooldownMinutes(wanted)
            MutationResult.AppliedNow
        } else {
            // Shortening the cooldown must itself serve the current, longer cooldown.
            defer(PendingChangeKind.SET_COOLDOWN_MINUTES, "", wanted.toString(), t("cooldown → %s", DayClock.formatMinutes(wanted)))
        }
    }

    suspend fun setBypassesPerWeek(count: Int): MutationResult {
        val wanted = count.coerceIn(0, MAX_BYPASSES_PER_WEEK)
        val current = settingsStore.current().bypassesPerWeek
        return if (unlocked() || wanted <= current) {
            settingsStore.setBypassesPerWeek(wanted)
            MutationResult.AppliedNow
        } else {
            defer(PendingChangeKind.SET_BYPASSES_PER_WEEK, "", wanted.toString(), t("bypasses → %s per week", wanted))
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
            defer(PendingChangeKind.SET_BLACKOUT_ENABLED, id.toString(), "false", t("turn off “%s”", label))
        }
    }

    suspend fun deleteBlackout(id: Long, label: String): MutationResult {
        if (unlocked()) {
            repository.deleteBlackout(id)
            return MutationResult.AppliedNow
        }
        return defer(PendingChangeKind.DELETE_BLACKOUT, id.toString(), "", t("delete “%s”", label))
    }

    // ---- salah -------------------------------------------------------------------------

    /** Turning salah enforcement on is instant; turning it off waits, like every loosening. */
    suspend fun setPrayerEnabled(enabled: Boolean): MutationResult {
        return if (unlocked() || enabled) {
            settingsStore.setPrayerEnabled(enabled)
            MutationResult.AppliedNow
        } else {
            defer(PendingChangeKind.SET_PRAYER_ENABLED, "", "false", t("stop pausing for salah"))
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
                t(
                    "salah pause → %s before, %s after",
                    DayClock.formatMinutes(before),
                    DayClock.formatMinutes(after),
                ),
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
                t("%s opens during salah", existing.label),
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
                t("%s opens when another app sends you to it", existing.label),
            )
        }
    }

    /**
     * Queues the change and keeps a sentence saying what it was, which is the only thing the
     * pending-changes screen has to show. The sentence is written in the language in force when
     * the change was asked for: what is stored is prose, not a key and its arguments, and the
     * alternative — a second table mapping every kind of change back to a format string — would
     * be more machinery than the case is worth.
     *
     * What makes that trade safe is [MAX_COOLDOWN_MINUTES]: a queued change outlives its language
     * only if you switch languages while it waits, and it cannot wait longer than a day. Without
     * a ceiling on the cooldown that window would be unbounded, and the row would be as stale as
     * someone's thumb had made it.
     */
    private suspend fun defer(
        kind: PendingChangeKind,
        targetKey: String,
        newValue: String,
        description: String,
    ): MutationResult {
        val change = repository.enqueuePendingChange(kind, targetKey, newValue, description)
        return MutationResult.Deferred(change.applyAtMillis)
    }

    companion object {
        /**
         * Ceilings on the friction knobs, applied here rather than only on the screen that turns
         * them, because this is the one place every change passes through.
         *
         * They are not there to second-guess a strict setting — every one of them is far past
         * what anyone would choose on purpose. They are there because tightening is instant and
         * loosening is not, so a knob held down by a thumb writes a rule that then has to be
         * served out. [MAX_COOLDOWN_MINUTES] is the one that matters: the cooldown governs undoing
         * every other rule *including itself*, so an unbounded one is a door that locks behind you
         * — a stray press could put the whole settings screen out of reach for weeks. A day is
         * already longer than the deliberation this app is trying to buy.
         */
        const val MAX_BASE_DELAY_SECONDS: Int = 10 * 60

        const val MAX_ESCALATION_SECONDS: Int = 5 * 60

        const val MAX_REASON_LENGTH: Int = 200

        const val MAX_SESSION_MINUTES: Int = 4 * 60

        const val MAX_COOLDOWN_MINUTES: Int = 24 * 60

        const val MAX_BYPASSES_PER_WEEK: Int = 50

        /** Higher is stricter. */
        private fun severity(tier: AppTier): Int = when (tier) {
            AppTier.FAVORITE -> 0
            AppTier.ALLOWED -> 1
            AppTier.GATED -> 2
            AppTier.BLOCKED -> 3
        }

        /** A cap is tighter when it exists and is no larger than what it replaces. */
        private fun isTighterCap(next: Int?, current: Int?): Boolean = when {
            next == null -> false
            current == null -> true
            else -> next <= current
        }
    }
}
