package dev.minimalist.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import dev.minimalist.domain.AppTier

@Entity(tableName = "app_rules")
data class AppRuleEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val tier: AppTier,
    val dailyMinutes: Int? = null,
    val dailyOpens: Int? = null,
    val sessionMinutes: Int? = null,
    /** Position on the home screen; null when not a favourite. */
    val favoriteOrder: Int? = null,
    /**
     * Still opens while a prayer window is in force. The dialer, a mushaf, an adhkar app.
     *
     * The default is spelled out for Room's benefit: the migration adds this column with
     * `DEFAULT 0`, and the expected schema has to say the same or the check fails at runtime.
     */
    @ColumnInfo(defaultValue = "0")
    val prayerExempt: Boolean = false,
    /**
     * Skips the gate when another app opened it — a link, a sign-in page, a custom tab. Set on
     * browsers, whose gate otherwise stands in front of every web-backed app on the phone.
     *
     * Reaching for it from the launcher still costs the full gate.
     */
    @ColumnInfo(defaultValue = "0")
    val openableByHandoff: Boolean = false,
)

@Entity(
    tableName = "usage_sessions",
    indices = [Index("packageName"), Index("startMillis")],
)
data class UsageSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startMillis: Long,
    val endMillis: Long? = null,
    val wasBypass: Boolean = false,
)

enum class OverrideKind { GATE_PASSED, EMERGENCY_BYPASS }

@Entity(tableName = "override_log", indices = [Index("timestamp")])
data class OverrideLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestamp: Long,
    val reason: String,
    val kind: OverrideKind,
)

@Entity(tableName = "notification_digest", indices = [Index("timestamp")])
data class NotificationDigestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val title: String?,
    val timestamp: Long,
    val delivered: Boolean = false,
)

@Entity(tableName = "blackout_windows")
data class BlackoutWindowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    /** Bit 0 = Monday .. bit 6 = Sunday. */
    val daysMask: Int,
    val enabled: Boolean = true,
)

/**
 * One day's prayer times as fetched from the network, in minutes past local midnight.
 *
 * This is a cache, not a source of truth: when a row is missing — no signal, a new place, a
 * date beyond what was synced — the on-device solver answers instead, and always can.
 */
@Entity(tableName = "prayer_days")
data class PrayerDayEntity(
    /** ISO date, e.g. "2026-08-09". */
    @PrimaryKey val date: String,
    val fajr: Int,
    val sunrise: Int,
    val dhuhr: Int,
    val asr: Int,
    val maghrib: Int,
    val isha: Int,
    /** Where these were fetched for, so a move invalidates them. */
    val latitude: Double,
    val longitude: Double,
    val fetchedAtMillis: Long,
)

/** One ayah. The whole Qur'an is 6,236 of these; downloaded once and then never again. */
@Entity(tableName = "quran_ayat", indices = [Index("surah")])
data class QuranAyahEntity(
    /** The ayah's number in the whole Qur'an, 1..6236. */
    @PrimaryKey val id: Int,
    val surah: Int,
    val ayah: Int,
    val surahName: String,
    val arabic: String,
    val english: String?,
)

/**
 * One day's tasbīḥ.
 *
 * Kept per day rather than as a single running number, because the count is a thing you finish
 * and start again — "33 today" is worth reading, "48,912 since March" is not.
 */
@Entity(tableName = "dhikr_days")
data class DhikrDayEntity(
    /** The local date, ISO-8601, which is how every other daily total in this app is keyed. */
    @PrimaryKey val date: String,
    val count: Int,
)

/**
 * A day marked as fasted.
 *
 * Only the days actually kept are stored. The calendar knows which days are *fasts* without
 * being told — that is arithmetic on the Hijri date — so the one thing worth writing down is
 * the answer to "did you?".
 */
@Entity(tableName = "fasting_days")
data class FastingDayEntity(
    @PrimaryKey val date: String,
    /** What kind of day it was when it was marked, so a log entry stays readable later. */
    val kind: String,
)

/**
 * A settings change that makes the rules *weaker*. It sits here until [applyAtMillis] passes,
 * so a craving cannot undo a decision made calmly. Tightening changes never come through here.
 */
@Entity(tableName = "pending_changes", indices = [Index("applyAtMillis")])
data class PendingChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: PendingChangeKind,
    /** Package name, blackout id, or "" for global policy changes. */
    val targetKey: String,
    val newValue: String,
    val description: String,
    val requestedAtMillis: Long,
    val applyAtMillis: Long,
)

enum class PendingChangeKind {
    SET_TIER,
    SET_DAILY_MINUTES,
    SET_DAILY_OPENS,
    SET_SESSION_MINUTES,
    SET_DEFAULT_SESSION_MINUTES,
    SET_BLACKOUT_ENABLED,
    DELETE_BLACKOUT,
    SET_BASE_DELAY,
    SET_ESCALATION,
    SET_MIN_REASON_LENGTH,
    SET_COOLDOWN_MINUTES,
    SET_BYPASSES_PER_WEEK,

    /** Turning salah enforcement off, or shortening its windows, both loosen the rules. */
    SET_PRAYER_ENABLED,
    SET_PRAYER_WINDOW_MINUTES,
    SET_PRAYER_EXEMPT,

    /** Letting another app open a gated one without the gate — a hole, so it waits like one. */
    SET_OPENABLE_BY_HANDOFF,
}

class Converters {
    @TypeConverter fun tierToName(tier: AppTier): String = tier.name

    @TypeConverter fun nameToTier(name: String): AppTier = AppTier.valueOf(name)

    @TypeConverter fun overrideKindToName(kind: OverrideKind): String = kind.name

    @TypeConverter fun nameToOverrideKind(name: String): OverrideKind = OverrideKind.valueOf(name)

    @TypeConverter fun pendingKindToName(kind: PendingChangeKind): String = kind.name

    @TypeConverter fun nameToPendingKind(name: String): PendingChangeKind = PendingChangeKind.valueOf(name)
}
