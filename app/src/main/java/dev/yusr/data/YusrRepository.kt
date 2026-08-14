package dev.yusr.data

import android.content.Context
import dev.yusr.data.db.AppRuleEntity
import dev.yusr.data.db.BlackoutWindowEntity
import dev.yusr.data.db.YusrDatabase
import dev.yusr.data.db.NotificationDigestEntity
import dev.yusr.data.db.OverrideKind
import dev.yusr.data.db.OverrideLogEntity
import dev.yusr.data.db.PendingChangeEntity
import dev.yusr.data.db.PendingChangeKind
import dev.yusr.data.db.UsageSessionEntity
import dev.yusr.data.prayer.PrayerRepository
import dev.yusr.data.settings.AppSettings
import dev.yusr.data.settings.SettingsStore
import dev.yusr.data.usage.PhoneUsage
import dev.yusr.domain.AppRuleSnapshot
import dev.yusr.domain.AppTier
import dev.yusr.domain.AppUsageToday
import dev.yusr.domain.BlackoutSchedule
import dev.yusr.domain.BlackoutWindowSpec
import dev.yusr.domain.BudgetCalculator
import dev.yusr.domain.ForegroundSnapshot
import dev.yusr.domain.GateDecision
import dev.yusr.domain.GateEvaluator
import dev.yusr.domain.PrayerWindow
import dev.yusr.domain.SessionRecord
import dev.yusr.util.DayClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * The one door between the UI/services and the stored rules. Every "can I open this?" question
 * goes through [decide] so the answer is identical wherever it is asked.
 */
class YusrRepository(
    context: Context,
    private val settingsStore: SettingsStore,
    private val catalog: AppCatalog,
    private val prayer: PrayerRepository,
) {
    private val phoneUsage = PhoneUsage(context)
    private val db = YusrDatabase.get(context)
    private val rules = db.appRuleDao()
    private val sessions = db.usageSessionDao()
    private val overrides = db.overrideLogDao()
    private val digest = db.notificationDigestDao()
    private val blackouts = db.blackoutWindowDao()
    private val pending = db.pendingChangeDao()

    val settings: Flow<AppSettings> = settingsStore.settings
    val favorites: Flow<List<AppRuleEntity>> = rules.observeFavorites()
    val allRules: Flow<List<AppRuleEntity>> = rules.observeAll()
    val blackoutWindows: Flow<List<BlackoutWindowEntity>> = blackouts.observeAll()
    val pendingChanges: Flow<List<PendingChangeEntity>> = pending.observeAll()
    val recentOverrides: Flow<List<OverrideLogEntity>> = overrides.observeRecent()

    // ---- rules -------------------------------------------------------------------------

    /** Fills the rule table on first run, and adds newly installed apps as GATED afterwards. */
    suspend fun syncCatalog(force: Boolean = false) {
        val seeded = settingsStore.current().catalogSeeded
        if (!seeded || force) {
            rules.upsertAll(catalog.seedRules())
            settingsStore.setCatalogSeeded(true)
            // Seeding already marks the browsers, so the backfill below has nothing to do.
            settingsStore.setHandoffSeeded(true)
            return
        }
        // Newly installed apps take the default for their kind, without disturbing existing
        // decisions.
        val browsers = catalog.browserPackages()
        val known = rules.getAll().map { it.packageName }.toSet()
        val fresh = catalog.installedApps()
            .filterNot { it.packageName in known }
            .map {
                AppRuleEntity(
                    packageName = it.packageName,
                    label = it.label,
                    tier = catalog.defaultTierFor(it.packageName),
                    openableByHandoff = it.packageName in browsers,
                )
            }
        if (fresh.isNotEmpty()) rules.insertIfAbsent(fresh)
        backfillHandoff(browsers)
    }

    /**
     * A rule table written before the handoff exemption existed has every browser marked
     * "gated even for a link", which is the state that made web-backed apps unusable. Done once,
     * and only for browsers — anything the user has since decided for is left alone.
     */
    private suspend fun backfillHandoff(browsers: Set<String>) {
        if (settingsStore.current().handoffSeeded) return
        rules.getAll()
            .filter { it.packageName in browsers && !it.openableByHandoff }
            .forEach { rules.upsert(it.copy(openableByHandoff = true)) }
        settingsStore.setHandoffSeeded(true)
    }

    suspend fun rule(packageName: String): AppRuleEntity? = rules.get(packageName)

    suspend fun snapshot(packageName: String): AppRuleSnapshot {
        val stored = rules.get(packageName)
        return stored?.toSnapshot() ?: AppRuleSnapshot(
            packageName = packageName,
            label = catalog.labelFor(packageName),
            // Something installed since the last sync: judge it the way seeding would have.
            tier = catalog.defaultTierFor(packageName),
        )
    }

    suspend fun upsertRule(rule: AppRuleEntity) = rules.upsert(rule)

    suspend fun blockedPackages(): List<String> = rules.blockedPackages()

    /**
     * Writes a tier straight through, without asking whether it is a loosening. Only
     * [dev.yusr.data.RuleMutator] and the pending-change worker may call this.
     */
    suspend fun setTierNow(packageName: String, tier: AppTier) {
        val existing = rules.get(packageName) ?: return
        val order = when {
            tier != AppTier.FAVORITE -> null
            existing.favoriteOrder != null -> existing.favoriteOrder
            else -> (rules.maxFavoriteOrder() ?: -1) + 1
        }
        rules.upsert(existing.copy(tier = tier, favoriteOrder = order))
    }

    /**
     * Writes the home screen's order, one favourite per position, in the order given.
     *
     * Rearranging the names is not a rule change — nothing opens that did not open before — so it
     * takes effect at once rather than waiting out the cooldown. Positions are renumbered from
     * zero on every save, which also repairs the duplicate zeroes an older pending tier change
     * could leave behind.
     *
     * Packages that are no longer favourites are skipped rather than given an order: the list is
     * read from a flow, and a rearrangement racing a tier change must not put a gated app back on
     * the home screen.
     */
    suspend fun reorderFavorites(packageNames: List<String>) {
        val favorites = rules.getAll()
            .filter { it.favoriteOrder != null }
            .map { it.packageName }
            .toSet()
        packageNames
            .filter { it in favorites }
            .forEachIndexed { index, packageName -> rules.setFavoriteOrder(packageName, index) }
    }

    // ---- the decision ------------------------------------------------------------------

    /** The question this whole app exists to answer. */
    suspend fun decide(
        packageName: String,
        now: Long = System.currentTimeMillis(),
        /** Only the guard service knows this, and only it passes it. Every other door is a tap. */
        handedOff: Boolean = false,
    ): GateDecision {
        val settings = settingsStore.current()
        val rule = snapshot(packageName)
        val usage = usageToday(packageName, now)
        val inBlackout = BlackoutSchedule.anyActive(blackoutSpecs(), DayClock.localDateTime(now))
        return GateEvaluator.evaluate(
            rule = rule,
            usage = usage,
            policy = settings.policy,
            inBlackout = inBlackout,
            bypassesRemaining = bypassesRemaining(settings, now),
            inPrayerWindow = prayer.activeWindow(now) != null,
            handedOff = handedOff,
        )
    }

    /** The prayer window in force, for the screens that have to name it. */
    suspend fun activePrayerWindow(now: Long = System.currentTimeMillis()): PrayerWindow? =
        prayer.activeWindow(now)

    /** Lets another app hand off to this one without the gate. Browsers, in practice. */
    suspend fun setOpenableByHandoffNow(packageName: String, openable: Boolean) {
        val existing = rules.get(packageName) ?: return
        rules.upsert(existing.copy(openableByHandoff = openable))
    }

    /** Marks an app as one that still opens during salah — a mushaf, adhkar, the dialer. */
    suspend fun setPrayerExemptNow(packageName: String, exempt: Boolean) {
        val existing = rules.get(packageName) ?: return
        rules.upsert(existing.copy(prayerExempt = exempt))
    }

    /**
     * What has been spent on an app today, as the phone counts it.
     *
     * The launcher used to count this itself, by holding a stretch of time open from the moment an
     * app came forward until another one did. That over-charged in the one case that happens every
     * night — an app still in front when the screen goes off is not being used — and under-charged
     * whenever the guard service had been killed. Both are gone: the numbers now come from the
     * system's own usage events, which is where the phone's screen-time screen gets them, minus
     * the handoffs the budget was never meant to charge for.
     *
     * With usage access refused there is nothing to read, and the launcher's own record — all it
     * ever had — is used instead.
     */
    suspend fun usageToday(packageName: String, now: Long = System.currentTimeMillis()): AppUsageToday {
        val dayStart = DayClock.dayStart(now)
        val phone = withContext(Dispatchers.IO) { phoneUsage.snapshot(dayStart, now) }
            ?: return BudgetCalculator.usageFor(sessionRecords(dayStart), packageName, dayStart, now)

        val counted = phone.usageFor(packageName, now)
        val waived = BudgetCalculator.handedOffUsage(sessionRecords(dayStart), packageName, dayStart, now)
        return AppUsageToday(
            opens = (counted.opens - waived.opens).coerceAtLeast(0),
            minutesUsed = ((counted.millis - waived.millis).coerceAtLeast(0L) / 60_000L).toInt(),
        )
    }

    /** The whole day as the phone has it, for the dashboard. Null when usage access is refused. */
    suspend fun phoneUsageToday(now: Long = System.currentTimeMillis()): ForegroundSnapshot? =
        withContext(Dispatchers.IO) { phoneUsage.today(now) }

    /** Total foreground time over a longer span, off the system's daily buckets. */
    suspend fun phoneMillisSince(since: Long, now: Long = System.currentTimeMillis()): Long? =
        withContext(Dispatchers.IO) { phoneUsage.totalMillisSince(since, now) }

    suspend fun sessionRecords(since: Long): List<SessionRecord> =
        sessions.since(since).map {
            SessionRecord(it.packageName, it.startMillis, it.endMillis, it.wasBypass, it.wasHandoff)
        }

    fun observeSessionsSince(since: Long): Flow<List<UsageSessionEntity>> = sessions.observeSince(since)

    suspend fun blackoutSpecs(): List<BlackoutWindowSpec> = blackouts.getAll().map { it.toSpec() }

    suspend fun activeBlackout(now: Long = System.currentTimeMillis()): BlackoutWindowSpec? =
        BlackoutSchedule.activeWindow(blackoutSpecs(), DayClock.localDateTime(now))

    // ---- sessions ----------------------------------------------------------------------

    suspend fun openSession(
        packageName: String,
        wasBypass: Boolean,
        wasHandoff: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): Long {
        sessions.closeAllOpen(now)
        return sessions.insert(
            UsageSessionEntity(
                packageName = packageName,
                startMillis = now,
                wasBypass = wasBypass,
                wasHandoff = wasHandoff,
            ),
        )
    }

    suspend fun closeOpenSessions(now: Long = System.currentTimeMillis()) = sessions.closeAllOpen(now)

    suspend fun currentOpenSession(): UsageSessionEntity? = sessions.openSession()

    // ---- overrides and bypasses --------------------------------------------------------

    suspend fun recordGatePass(packageName: String, reason: String, now: Long = System.currentTimeMillis()) {
        overrides.insert(OverrideLogEntity(packageName = packageName, timestamp = now, reason = reason, kind = OverrideKind.GATE_PASSED))
    }

    suspend fun recordBypass(packageName: String, reason: String, now: Long = System.currentTimeMillis()) {
        overrides.insert(OverrideLogEntity(packageName = packageName, timestamp = now, reason = reason, kind = OverrideKind.EMERGENCY_BYPASS))
    }

    suspend fun bypassesRemaining(settings: AppSettings, now: Long): Int {
        val used = overrides.bypassCountSince(DayClock.weekAgo(now))
        return (settings.bypassesPerWeek - used).coerceAtLeast(0)
    }

    suspend fun bypassesRemaining(now: Long = System.currentTimeMillis()): Int =
        bypassesRemaining(settingsStore.current(), now)

    // ---- notifications -----------------------------------------------------------------

    suspend fun recordSuppressedNotification(packageName: String, title: String?, now: Long = System.currentTimeMillis()) {
        digest.insert(NotificationDigestEntity(packageName = packageName, title = title, timestamp = now))
    }

    suspend fun undeliveredDigest(): List<NotificationDigestEntity> = digest.undelivered()

    suspend fun markDigestDelivered() = digest.markAllDelivered()

    fun observeDigestSince(since: Long): Flow<List<NotificationDigestEntity>> = digest.observeSince(since)

    // ---- blackouts ---------------------------------------------------------------------

    suspend fun upsertBlackout(window: BlackoutWindowEntity): Long = blackouts.upsert(window)

    suspend fun deleteBlackout(id: Long) = blackouts.deleteById(id)

    // ---- pending changes ---------------------------------------------------------------

    suspend fun enqueuePendingChange(
        kind: PendingChangeKind,
        targetKey: String,
        newValue: String,
        description: String,
        now: Long = System.currentTimeMillis(),
    ): PendingChangeEntity {
        val cooldown = settingsStore.current().cooldownMinutes
        val change = PendingChangeEntity(
            kind = kind,
            targetKey = targetKey,
            newValue = newValue,
            description = description,
            requestedAtMillis = now,
            applyAtMillis = now + cooldown * 60_000L,
        )
        val id = pending.insert(change)
        return change.copy(id = id)
    }

    suspend fun cancelPendingChange(id: Long) = pending.deleteById(id)

    suspend fun duePendingChanges(now: Long = System.currentTimeMillis()): List<PendingChangeEntity> =
        pending.due(now)

    suspend fun earliestPendingApplyAt(): Long? = pending.earliestApplyAt()

    /** Applies a change that has served its cooldown. Called only by the worker. */
    suspend fun applyPendingChange(change: PendingChangeEntity) {
        when (change.kind) {
            PendingChangeKind.SET_TIER -> rules.get(change.targetKey)?.let { existing ->
                val tier = AppTier.valueOf(change.newValue)
                rules.upsert(
                    existing.copy(
                        tier = tier,
                        favoriteOrder = if (tier == AppTier.FAVORITE) existing.favoriteOrder ?: 0 else null,
                    ),
                )
            }

            PendingChangeKind.SET_DAILY_MINUTES -> rules.get(change.targetKey)?.let {
                rules.upsert(it.copy(dailyMinutes = change.newValue.toIntOrNull()))
            }

            PendingChangeKind.SET_DAILY_OPENS -> rules.get(change.targetKey)?.let {
                rules.upsert(it.copy(dailyOpens = change.newValue.toIntOrNull()))
            }

            PendingChangeKind.SET_SESSION_MINUTES -> rules.get(change.targetKey)?.let {
                rules.upsert(it.copy(sessionMinutes = change.newValue.toIntOrNull()))
            }

            PendingChangeKind.SET_DEFAULT_SESSION_MINUTES ->
                settingsStore.setDefaultSessionMinutes(change.newValue.toInt())

            PendingChangeKind.SET_BLACKOUT_ENABLED -> blackouts.get(change.targetKey.toLong())?.let {
                blackouts.upsert(it.copy(enabled = change.newValue.toBoolean()))
            }

            PendingChangeKind.DELETE_BLACKOUT -> blackouts.deleteById(change.targetKey.toLong())

            PendingChangeKind.SET_BASE_DELAY -> settingsStore.setBaseDelay(change.newValue.toInt())
            PendingChangeKind.SET_ESCALATION -> settingsStore.setEscalation(change.newValue.toInt())
            PendingChangeKind.SET_MIN_REASON_LENGTH -> settingsStore.setMinReasonLength(change.newValue.toInt())
            PendingChangeKind.SET_COOLDOWN_MINUTES -> settingsStore.setCooldownMinutes(change.newValue.toInt())
            PendingChangeKind.SET_BYPASSES_PER_WEEK -> settingsStore.setBypassesPerWeek(change.newValue.toInt())

            PendingChangeKind.SET_PRAYER_ENABLED -> settingsStore.setPrayerEnabled(change.newValue.toBoolean())

            // Stored as "before,after" so one cooldown covers one edit of the window.
            PendingChangeKind.SET_PRAYER_WINDOW_MINUTES -> {
                val (before, after) = change.newValue.split(",").map { it.trim().toInt() }
                settingsStore.setWindowMinutes(before, after)
            }

            PendingChangeKind.SET_PRAYER_EXEMPT ->
                setPrayerExemptNow(change.targetKey, change.newValue.toBoolean())

            PendingChangeKind.SET_OPENABLE_BY_HANDOFF ->
                setOpenableByHandoffNow(change.targetKey, change.newValue.toBoolean())
        }
        pending.deleteById(change.id)
    }

    // ---- housekeeping ------------------------------------------------------------------

    suspend fun prune(now: Long = System.currentTimeMillis()) {
        val cutoff = now - RETENTION_DAYS * 24L * 60 * 60 * 1000
        sessions.pruneBefore(cutoff)
        overrides.pruneBefore(cutoff)
        digest.pruneBefore(cutoff)
    }

    companion object {
        private const val RETENTION_DAYS = 60
    }
}

fun AppRuleEntity.toSnapshot(): AppRuleSnapshot = AppRuleSnapshot(
    packageName = packageName,
    label = label,
    tier = tier,
    budget = dev.yusr.domain.AppBudget(dailyMinutes = dailyMinutes, dailyOpens = dailyOpens),
    sessionMinutes = sessionMinutes,
    prayerExempt = prayerExempt,
    openableByHandoff = openableByHandoff,
)

fun BlackoutWindowEntity.toSpec(): BlackoutWindowSpec = BlackoutWindowSpec(
    id = id,
    label = label,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    daysOfWeek = BlackoutSchedule.maskToDays(daysMask),
    enabled = enabled,
)
