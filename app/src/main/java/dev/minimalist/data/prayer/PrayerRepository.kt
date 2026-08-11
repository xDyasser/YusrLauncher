package dev.minimalist.data.prayer

import android.content.Context
import dev.minimalist.data.db.MinimalistDatabase
import dev.minimalist.data.db.PrayerDayEntity
import dev.minimalist.data.settings.PrayerSettings
import dev.minimalist.data.settings.SettingsStore
import dev.minimalist.domain.AsrMethod
import dev.minimalist.domain.Fadila
import dev.minimalist.domain.Hijri
import dev.minimalist.domain.NextPrayer
import dev.minimalist.domain.Night
import dev.minimalist.domain.NightTimes
import dev.minimalist.domain.Prayer
import dev.minimalist.domain.PrayerEntry
import dev.minimalist.domain.PrayerTimes
import dev.minimalist.domain.PrayerTimetable
import dev.minimalist.domain.PrayerWindow
import dev.minimalist.domain.PrayerWindows
import dev.minimalist.domain.SyncedTimes
import dev.minimalist.domain.entries
import dev.minimalist.util.DayClock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * One consistent reading of today: the times, where the clock sits among them, and when each
 * preferred window closes. Taken in a single pass so nothing on the screen can contradict
 * anything else on it.
 */
data class PrayerToday(
    val date: LocalDate,
    val minuteOfDay: Int,
    val timetable: PrayerTimetable,
    val next: NextPrayer,
    /** The prayer whose time is in, which is the one the faḍīla countdown is about. */
    val current: Prayer,
    val fadilaEnds: Map<Prayer, Int>,
    /** Sharʿī midnight and the last third, for the screens that print the night as well as the day. */
    val night: NightTimes,
    /**
     * Today's prayers the way this user prays them — three lines rather than five where dhuhr is
     * joined to asr and maghrib to isha. Read off the same setting the enforcement windows use,
     * so the screen and the phone's behaviour cannot disagree about how many times a day it stops.
     */
    val entries: List<PrayerEntry>,
) {
    /** How long is left of the current prayer's preferred time, or null if that has passed. */
    val fadilaRemaining: Int?
        get() = Fadila.remaining(fadilaEnds, timetable, current, minuteOfDay)

    fun endOfFadila(prayer: Prayer): Int? = fadilaEnds[prayer]
}

/**
 * Today's times, and what they mean for the enforcement layer.
 *
 * Synced times are preferred when they exist and were fetched for this place; otherwise the
 * on-device solver answers. There is no third state where the app does not know the times.
 */
class PrayerRepository(
    context: Context,
    private val settingsStore: SettingsStore,
) {
    private val prayerDays = MinimalistDatabase.get(context).prayerDayDao()

    /** Null [settings] means "whatever is stored" — a default parameter cannot suspend. */
    suspend fun timetable(
        settings: PrayerSettings? = null,
        date: LocalDate = LocalDate.now(),
    ): PrayerTimetable {
        val resolved = settings ?: settingsStore.current().prayer
        return synced(resolved, date) ?: computed(resolved, date)
    }

    /** The solver's own answer, ignoring anything cached. Used by the settings screen to compare. */
    fun computed(settings: PrayerSettings, date: LocalDate = LocalDate.now()): PrayerTimetable =
        PrayerTimes.compute(
            date = date,
            latitude = settings.latitude,
            longitude = settings.longitude,
            utcOffsetMinutes = utcOffsetMinutes(date),
            config = settings.toPrayerConfig(),
        )

    private suspend fun synced(settings: PrayerSettings, date: LocalDate): PrayerTimetable? {
        if (!settings.syncOverNetwork) return null
        val row = prayerDays.get(date.toString()) ?: return null
        // Cached for somewhere else: a move invalidates the times more surely than age does.
        if (abs(row.latitude - settings.latitude) > COORDINATE_TOLERANCE) return null
        if (abs(row.longitude - settings.longitude) > COORDINATE_TOLERANCE) return null

        val fetched = mapOf(
            Prayer.FAJR to row.fajr,
            Prayer.SUNRISE to row.sunrise,
            Prayer.DHUHR to row.dhuhr,
            Prayer.ASR to row.asr,
            Prayer.MAGHRIB to row.maghrib,
            Prayer.ISHA to row.isha,
        )
        // Aladhan's Jafari method reports asr at dhuhr and isha at maghrib. Those are not start
        // times, and a day with two prayers missing from it would silently drop two windows, so
        // the solver fills the gaps. Checked on every read rather than only on fetch, because
        // rows cached before this existed are still on the phone.
        val trusted = if (SyncedTimes.isCoherent(fetched)) {
            fetched
        } else {
            SyncedTimes.repair(fetched, computed(settings, date))
        }

        val offsets = settings.offsetMinutes
        return PrayerTimetable(
            trusted.mapValues { (prayer, minute) ->
                ((minute + (offsets[prayer] ?: 0)) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY
            },
        )
    }

    /**
     * The guard service asks this on every poll, so the day's windows are worked out once and
     * kept until either the date or the settings change.
     */
    suspend fun windows(
        settings: PrayerSettings? = null,
        date: LocalDate = LocalDate.now(),
    ): List<PrayerWindow> {
        val resolved = settings ?: settingsStore.current().prayer
        val key = date to resolved
        cached?.let { (cachedKey, windows) -> if (cachedKey == key) return windows }
        val windows = PrayerWindows.windowsFor(timetable(resolved, date), resolved.toWindowConfig())
        cached = key to windows
        return windows
    }

    @Volatile
    private var cached: Pair<Pair<LocalDate, PrayerSettings>, List<PrayerWindow>>? = null

    /** Freshly synced times must not be masked by windows worked out from the old ones. */
    private fun invalidate() {
        cached = null
    }

    /**
     * The window in force right now, or null. Null when salah enforcement is off or the place is
     * unset — an unconfigured launcher must never start blocking on its own.
     */
    suspend fun activeWindow(now: Long = System.currentTimeMillis()): PrayerWindow? {
        val settings = settingsStore.current().prayer
        if (!settings.enforcing) return null
        val local = DayClock.localDateTime(now)
        return PrayerWindows.activeWindow(
            windows = windows(settings, local.toLocalDate()),
            minuteOfDay = local.hour * 60 + local.minute,
        )
    }

    suspend fun nextPrayer(now: Long = System.currentTimeMillis()): NextPrayer? {
        val settings = settingsStore.current().prayer
        if (!settings.configured) return null
        val local = DayClock.localDateTime(now)
        return PrayerTimes.next(
            timetable = timetable(settings, local.toLocalDate()),
            minuteOfDay = local.hour * 60 + local.minute,
        )
    }

    /**
     * Everything the home screen and the hub print about today, gathered in one pass.
     *
     * The alternative is five separate suspending reads that each recompute the timetable and can
     * each land in a different minute — a strip saying ʿaṣr is next above a line counting down to
     * maghrib. One snapshot, taken at one instant, cannot disagree with itself.
     */
    suspend fun today(now: Long = System.currentTimeMillis()): PrayerToday? {
        val settings = settingsStore.current().prayer
        if (!settings.configured) return null

        val local = DayClock.localDateTime(now)
        val date = local.toLocalDate()
        val minuteOfDay = local.hour * 60 + local.minute
        val timetable = timetable(settings, date)

        val fadila = if (settings.showFadila) {
            // The one-shadow asr is what closes dhuhr's preferred time whatever the user's own
            // asr rule is, so it is computed on its own terms rather than read off the timetable.
            val standardAsr = PrayerTimes.compute(
                date = date,
                latitude = settings.latitude,
                longitude = settings.longitude,
                utcOffsetMinutes = utcOffsetMinutes(date),
                config = settings.toPrayerConfig().copy(
                    asr = AsrMethod.STANDARD,
                    offsetMinutes = emptyMap(),
                ),
            ).minuteOfDay(Prayer.ASR)

            Fadila.ends(
                timetable = timetable,
                standardAsrMinuteOfDay = standardAsr,
                shafaqMinuteOfDay = PrayerTimes.eveningDepression(
                    date = date,
                    latitude = settings.latitude,
                    longitude = settings.longitude,
                    utcOffsetMinutes = utcOffsetMinutes(date),
                    angle = Fadila.SHAFAQ_ANGLE,
                ),
            )
        } else {
            emptyMap()
        }

        return PrayerToday(
            date = date,
            minuteOfDay = minuteOfDay,
            timetable = timetable,
            next = PrayerTimes.next(timetable, minuteOfDay),
            current = PrayerTimes.current(timetable, minuteOfDay),
            fadilaEnds = fadila,
            night = Night.of(timetable),
            entries = timetable.entries(
                combineDhuhrAsr = settings.combineDhuhrAsr,
                combineMaghribIsha = settings.combineMaghribIsha,
            ),
        )
    }

    /** The Islamic date for the home screen, with the user's sighting correction applied. */
    suspend fun hijriDate(now: Long = System.currentTimeMillis()): String? {
        val settings = settingsStore.current().prayer
        return Hijri.format(DayClock.localDateTime(now).toLocalDate(), settings.hijriOffsetDays)
    }

    suspend fun cacheSyncedDays(days: List<PrayerDayEntity>) {
        prayerDays.upsertAll(days)
        invalidate()
    }

    /** Called when the coordinates change, because every cached row was fetched for elsewhere. */
    suspend fun clearSyncedDays() {
        prayerDays.clear()
        invalidate()
    }

    suspend fun pruneSyncedDays(before: LocalDate = LocalDate.now().minusDays(7)) =
        prayerDays.pruneBefore(before.toString())

    private fun utcOffsetMinutes(date: LocalDate, zone: ZoneId = DayClock.zone()): Int =
        zone.rules.getOffset(date.atStartOfDay(zone).toInstant()).totalSeconds / 60

    companion object {
        /** About a kilometre — closer than that and the times do not move by a minute. */
        private const val COORDINATE_TOLERANCE = 0.01
        private const val MINUTES_PER_DAY = 24 * 60
    }
}
