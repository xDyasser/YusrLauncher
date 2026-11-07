package dev.yusr.data.prayer

import android.content.Context
import dev.yusr.data.db.PrayerLogEntity
import dev.yusr.data.db.YusrDatabase
import dev.yusr.data.settings.SettingsStore
import dev.yusr.domain.Madhab
import dev.yusr.domain.Night
import dev.yusr.domain.Prayer
import dev.yusr.domain.PrayerLog
import dev.yusr.domain.entries
import dev.yusr.util.DayClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The record of which prayers were prayed, and the arithmetic over it.
 *
 * The rows are the marks; everything else — which of the five are open, which have run out,
 * how many are owed this week — is worked out from the timetable the rest of the app already
 * computes, so there is one source for when a prayer's time ends and this screen cannot
 * disagree with the one next to it.
 *
 * Like the tasbīḥ and the fasts, none of this feeds the gate or the budgets. It is a record of
 * what was done, and a count of what is owed, and that is all it is for.
 */
class PrayerLogRepository(
    context: Context,
    private val settingsStore: SettingsStore,
    private val prayerRepository: PrayerRepository,
) {
    private val log = YusrDatabase.get(context).prayerLogDao()

    /** The prayers marked on [date]. */
    fun observePrayed(date: LocalDate): Flow<Set<Prayer>> =
        log.observeDay(date.toString()).map { rows -> rows.mapNotNull { it.prayerOrNull() }.toSet() }

    suspend fun setPrayed(date: LocalDate, prayer: Prayer, prayed: Boolean, now: Long = System.currentTimeMillis()) {
        if (prayed) {
            log.upsert(PrayerLogEntity(date.toString(), prayer.name, now))
        } else {
            log.delete(date.toString(), prayer.name)
        }
    }

    /**
     * Today's five and where each stands, given the marks, as the domain reckons them — and
     * null before the timetable exists, because a ledger over no times is no ledger.
     */
    fun states(today: PrayerToday, prayed: Set<Prayer>, branch: Madhab.Branch): Map<Prayer, PrayerLog.State> =
        PrayerLog.day(
            timetable = today.timetable,
            ends = PrayerLog.ends(today.timetable, today.night, branch),
            entries = today.entries,
            prayed = prayed,
            daysAgo = 0,
            minuteOfDay = today.minuteOfDay,
        )

    /**
     * How many prayers are owed over the [days] ending today, today's included as far as the
     * clock has got. Each day is reckoned against its own timetable, so a week that crossed a
     * clock change or a long summer evening is still counted by the sun.
     */
    fun observeMissedSince(days: Int): Flow<Int> {
        // The query reaches a day further back than the count, so a flow made just before
        // midnight is still watching the right rows just after it.
        val from = LocalDate.now().minusDays(days.toLong())
        return log.observeSince(from.toString()).map { rows ->
            // The clock is read on every emission rather than once when the flow was made: a
            // mark at 16:00 should be counted against the day as it is at 16:00.
            val local = DayClock.localDateTime(System.currentTimeMillis())
            val today = local.toLocalDate()
            val minuteOfDay = local.hour * 60 + local.minute
            val settings = settingsStore.current().prayer
            if (!settings.configured) return@map 0
            val branch = settings.effectiveMadhab.branch
            val window = settings.toWindowConfig()
            val marked = rows.groupBy({ it.date }, { it.prayerOrNull() })
            (0 until days).sumOf { back ->
                val date = today.minusDays(back.toLong())
                val timetable = prayerRepository.timetable(settings, date)
                val states = PrayerLog.day(
                    timetable = timetable,
                    ends = PrayerLog.ends(timetable, Night.of(timetable), branch),
                    entries = timetable.entries(window.combineDhuhrAsr, window.combineMaghribIsha),
                    prayed = marked[date.toString()].orEmpty().filterNotNull().toSet(),
                    daysAgo = ChronoUnit.DAYS.between(date, today).toInt(),
                    minuteOfDay = minuteOfDay,
                )
                PrayerLog.missed(states)
            }
        }
    }

    private fun PrayerLogEntity.prayerOrNull(): Prayer? = runCatching { Prayer.valueOf(prayer) }.getOrNull()
}
