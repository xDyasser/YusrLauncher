package dev.yusr.domain

/**
 * How long the phone stops for each prayer.
 *
 * Combining matches how the Shia schools pray: dhuhr and asr together, maghrib and isha
 * together, which is two windows a day rather than four.
 */
data class PrayerWindowConfig(
    val minutesBefore: Int = 0,
    val minutesAfter: Int = 20,
    val combineDhuhrAsr: Boolean = false,
    val combineMaghribIsha: Boolean = false,
) {
    companion object {
        val DEFAULT = PrayerWindowConfig()
    }
}

/**
 * A span during which nothing opens but calls and whatever is marked prayer-exempt.
 *
 * Times are minutes past local midnight, and an end at or before the start spans midnight —
 * the same convention [BlackoutWindowSpec] uses, so the two read alike.
 */
data class PrayerWindow(
    val prayer: Prayer,
    /** Set when the window covers two prayers, e.g. dhuhr through asr. */
    val through: Prayer? = null,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
) {
    val spansMidnight: Boolean get() = endMinuteOfDay <= startMinuteOfDay

    /** "maghrib", or "maghrib and isha" when the two are combined. */
    val label: String
        get() = if (through == null) {
            prayer.name.lowercase()
        } else {
            "${prayer.name.lowercase()} and ${through.name.lowercase()}"
        }
}

object PrayerWindows {

    const val MINUTES_PER_DAY: Int = 24 * 60

    fun windowsFor(timetable: PrayerTimetable, config: PrayerWindowConfig): List<PrayerWindow> {
        val windows = mutableListOf<PrayerWindow>()

        fun add(from: Prayer, to: Prayer?) {
            val start = timetable.minuteOfDay(from) - config.minutesBefore
            val end = timetable.minuteOfDay(to ?: from) + config.minutesAfter
            windows += PrayerWindow(
                prayer = from,
                through = to,
                startMinuteOfDay = wrap(start),
                endMinuteOfDay = wrap(end),
            )
        }

        add(Prayer.FAJR, null)
        if (config.combineDhuhrAsr) {
            add(Prayer.DHUHR, Prayer.ASR)
        } else {
            add(Prayer.DHUHR, null)
            add(Prayer.ASR, null)
        }
        if (config.combineMaghribIsha) {
            add(Prayer.MAGHRIB, Prayer.ISHA)
        } else {
            add(Prayer.MAGHRIB, null)
            add(Prayer.ISHA, null)
        }
        return windows
    }

    fun isActive(window: PrayerWindow, minuteOfDay: Int): Boolean =
        if (!window.spansMidnight) {
            minuteOfDay >= window.startMinuteOfDay && minuteOfDay < window.endMinuteOfDay
        } else {
            minuteOfDay >= window.startMinuteOfDay || minuteOfDay < window.endMinuteOfDay
        }

    fun activeWindow(windows: List<PrayerWindow>, minuteOfDay: Int): PrayerWindow? =
        windows.firstOrNull { isActive(it, minuteOfDay) }

    /** Minutes until [window] lifts, for the "opens again in ..." line on the block screen. */
    fun minutesUntilEnd(window: PrayerWindow, minuteOfDay: Int): Int? {
        if (!isActive(window, minuteOfDay)) return null
        val end = window.endMinuteOfDay
        return if (minuteOfDay < end) end - minuteOfDay else MINUTES_PER_DAY - minuteOfDay + end
    }

    private fun wrap(minute: Int): Int = ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
}
