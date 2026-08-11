package dev.yusr.domain

import java.time.LocalDateTime

/**
 * A recurring window during which only favourites and utilities open.
 *
 * Times are minutes past local midnight. A window whose end is at or before its start spans
 * midnight ("22:00 to 07:00"), and in that case [daysOfWeek] refers to the day it *starts* on.
 */
data class BlackoutWindowSpec(
    val id: Long = 0,
    val label: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    /** java.time.DayOfWeek values, 1 = Monday .. 7 = Sunday. */
    val daysOfWeek: Set<Int>,
    val enabled: Boolean = true,
) {
    val spansMidnight: Boolean get() = endMinuteOfDay <= startMinuteOfDay
}

object BlackoutSchedule {

    const val MINUTES_PER_DAY: Int = 24 * 60

    /** Is [window] active at [minuteOfDay] on ISO weekday [dayOfWeek] (1..7)? */
    fun isActive(window: BlackoutWindowSpec, dayOfWeek: Int, minuteOfDay: Int): Boolean {
        if (!window.enabled) return false
        if (window.daysOfWeek.isEmpty()) return false

        return if (!window.spansMidnight) {
            window.daysOfWeek.contains(dayOfWeek) &&
                minuteOfDay >= window.startMinuteOfDay &&
                minuteOfDay < window.endMinuteOfDay
        } else {
            // Either we are in the evening tail of a window that started today...
            val startedToday = window.daysOfWeek.contains(dayOfWeek) &&
                minuteOfDay >= window.startMinuteOfDay
            // ...or in the morning head of one that started yesterday.
            val startedYesterday = window.daysOfWeek.contains(previousDay(dayOfWeek)) &&
                minuteOfDay < window.endMinuteOfDay
            startedToday || startedYesterday
        }
    }

    fun isActive(window: BlackoutWindowSpec, at: LocalDateTime): Boolean =
        isActive(window, at.dayOfWeek.value, at.hour * 60 + at.minute)

    /** The first active window, or null when nothing is in force. */
    fun activeWindow(windows: List<BlackoutWindowSpec>, at: LocalDateTime): BlackoutWindowSpec? =
        windows.firstOrNull { isActive(it, at) }

    fun anyActive(windows: List<BlackoutWindowSpec>, at: LocalDateTime): Boolean =
        activeWindow(windows, at) != null

    /** Minutes until [window] stops being active, for showing "opens again in ..." copy. */
    fun minutesUntilEnd(window: BlackoutWindowSpec, dayOfWeek: Int, minuteOfDay: Int): Int? {
        if (!isActive(window, dayOfWeek, minuteOfDay)) return null
        val end = window.endMinuteOfDay
        return if (minuteOfDay < end) end - minuteOfDay else MINUTES_PER_DAY - minuteOfDay + end
    }

    private fun previousDay(dayOfWeek: Int): Int = if (dayOfWeek == 1) 7 else dayOfWeek - 1

    fun formatMinuteOfDay(minuteOfDay: Int): String {
        val h = (minuteOfDay / 60) % 24
        val m = minuteOfDay % 60
        return "%02d:%02d".format(h, m)
    }

    /** Bit 0 = Monday .. bit 6 = Sunday, so a window fits in one Room column. */
    fun daysToMask(days: Set<Int>): Int = days.fold(0) { acc, d -> acc or (1 shl (d - 1)) }

    fun maskToDays(mask: Int): Set<Int> = (1..7).filter { mask and (1 shl (it - 1)) != 0 }.toSet()
}
