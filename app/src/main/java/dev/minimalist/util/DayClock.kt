package dev.minimalist.util

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

    fun formatMinutes(minutes: Int): String = when {
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }

    fun formatSeconds(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) "%d:%02d".format(m, s) else "${s}s"
    }
}
