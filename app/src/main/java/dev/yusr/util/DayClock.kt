package dev.yusr.util

import dev.yusr.ui.t
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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

    /** A moment as a wall clock, which is all a "takes effect at …" line ever needs. */
    fun clockAt(millis: Long): String =
        localDateTime(millis).toLocalTime().withSecond(0).withNano(0).toString()

    fun formatSeconds(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        // A running countdown is a clock rather than a sentence, and 1:05 needs no translating.
        return if (m > 0) "%d:%02d".format(m, s) else t("%ss", s)
    }
}
