package dev.minimalist.data

import android.content.Context
import dev.minimalist.data.db.DhikrDayEntity
import dev.minimalist.data.db.FastingDayEntity
import dev.minimalist.data.db.MinimalistDatabase
import dev.minimalist.domain.FastingCalendar
import dev.minimalist.domain.Tasbih
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * The two things the hub keeps a record of: the day's tasbīḥ, and the days fasted.
 *
 * Both are deliberately small. Neither feeds the gate, the budgets, or anything that decides
 * whether an app may open — worship is not a currency to be spent on screen time, and wiring it
 * into the enforcement layer would make it one. They are counts on a screen, and that is all.
 */
class DevotionRepository(context: Context) {

    private val db = MinimalistDatabase.get(context)
    private val dhikr = db.dhikrDayDao()
    private val fasting = db.fastingDayDao()

    // ---- tasbīḥ -------------------------------------------------------------------------

    /** Today's count, which is the number the home screen's bottom bar shows. */
    fun observeTodayCount(today: LocalDate = LocalDate.now()): Flow<Int> =
        dhikr.observeCount(today.toString()).map { it ?: 0 }

    suspend fun countToday(today: LocalDate = LocalDate.now()): Int =
        dhikr.count(today.toString()) ?: 0

    /** One bead, and the progress that results — the caller wants both. */
    suspend fun addBead(cycle: Tasbih.Cycle, today: LocalDate = LocalDate.now()): Tasbih.Progress {
        val next = Tasbih.increment(countToday(today))
        dhikr.upsert(DhikrDayEntity(today.toString(), next))
        return Tasbih.progress(next, cycle)
    }

    suspend fun removeBead(cycle: Tasbih.Cycle, today: LocalDate = LocalDate.now()): Tasbih.Progress {
        val next = Tasbih.decrement(countToday(today))
        dhikr.upsert(DhikrDayEntity(today.toString(), next))
        return Tasbih.progress(next, cycle)
    }

    suspend fun resetToday(today: LocalDate = LocalDate.now()) {
        dhikr.upsert(DhikrDayEntity(today.toString(), 0))
    }

    /** Keeps a fortnight and forgets the rest; a tasbīḥ is not an archive. */
    suspend fun pruneDhikr(today: LocalDate = LocalDate.now()) {
        dhikr.deleteBefore(today.minusDays(14).toString())
    }

    // ---- fasting ------------------------------------------------------------------------

    fun observeFastedToday(today: LocalDate = LocalDate.now()): Flow<Boolean> =
        fasting.observeMarked(today.toString())

    /** How many days have been kept this Gregorian month, for the line under the tile. */
    fun observeFastedThisMonth(today: LocalDate = LocalDate.now()): Flow<Int> =
        fasting.observeCountSince(today.withDayOfMonth(1).toString())

    suspend fun setFasted(date: LocalDate, fasted: Boolean, hijriOffsetDays: Int = 0) {
        if (fasted) {
            val kind = FastingCalendar.classify(date, hijriOffsetDays).kind
            fasting.upsert(FastingDayEntity(date.toString(), kind.name))
        } else {
            fasting.delete(date.toString())
        }
    }

    /** Which of the last [days] were marked, for ticking the calendar strip. */
    suspend fun fastedDatesSince(from: LocalDate): Set<LocalDate> =
        fasting.since(from.toString())
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .toSet()
}
