package dev.yusr.util

import dev.yusr.ui.t
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Local-time boundaries, kept in one place so "today" means the same thing everywhere. */
object DayClock {

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun localDateTime(millis: Long, zone: ZoneId = zone()): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    fun dayStart(millis: Long, zone: ZoneId = zone()): Long =
        localDateTime(millis, zone).truncatedTo(ChronoUnit.DAYS)
            .atZone(zone).toInstant().toEpochMilli()

    /** Rolling seven days, which is what the bypass allowance is measured against. */
    fun weekAgo(millis: Long): Long = millis - 7L * 24 * 60 * 60 * 1000

    /**
     * A span as a person would say it: "45m", "2h", "2h 30m" — and in Arabic the same numbers
     * with the same shape, "45 د", "2 س", "2 س 30 د". The h and the m are English words cut to
     * one letter, so they are translated like any other word rather than left standing.
     */
    fun formatMinutes(minutes: Int): String = when {
        minutes < 60 -> t("%sm", minutes)
        minutes % 60 == 0 -> t("%sh", minutes / 60)
        else -> t("%sh %sm", minutes / 60, minutes % 60)
    }

    /**
     * A minute of the day as a wall clock: "07:05", "23:40". Out-of-range minutes wrap, so a
     * midnight that has crossed into tomorrow still reads as a time.
     *
     * The one implementation of this in the app, and pinned to [Locale.ROOT] on purpose. `%d`
     * follows the default locale and `%s` does not: under Arabic the first gives Arabic-Indic
     * digits while every number the app passes through [t] stays as it is, and the two would end
     * up in the same row — a prayer time in one numeral system beside its offset in the other.
     * The interface uses one set of digits throughout; only the home screen's own clock and date,
     * which are typography rather than data, are set in the language's numerals.
     */
    fun clock(minuteOfDay: Int): String {
        val wrapped = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return String.format(Locale.ROOT, "%02d:%02d", wrapped / 60, wrapped % 60)
    }

    /** A moment as a wall clock, which is all a "takes effect at …" line ever needs. */
    fun clockAt(millis: Long): String = localDateTime(millis).toLocalTime()
        .let { clock(it.hour * 60 + it.minute) }

    fun formatSeconds(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        // A running countdown is a clock rather than a sentence, and 1:05 needs no translating —
        // but it does need the digits the rest of the countdown screen is using, hence [clock]'s
        // locale rather than the default one.
        return if (m > 0) String.format(Locale.ROOT, "%d:%02d", m, s) else t("%ss", s)
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
