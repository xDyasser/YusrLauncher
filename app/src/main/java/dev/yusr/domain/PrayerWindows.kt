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
    /**
     * The second prayer of a joined pair, prayed inside this same stop — asr behind dhuhr. It
     * names what the window is for; it does not stretch the window as far as its own adhan.
     */
    val through: Prayer? = null,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
) {
    val spansMidnight: Boolean get() = endMinuteOfDay <= startMinuteOfDay

    /** How long the phone is shut for, which is what a countdown of it is a fraction of. */
    val lengthMinutes: Int
        get() {
            val span = endMinuteOfDay - startMinuteOfDay
            return if (span > 0) span else span + PrayerWindows.MINUTES_PER_DAY
        }

    /**
     * "maghrib", or "maghrib and isha" when the two are combined.
     *
     * English, and for logs and tests rather than for a screen: it is built out of the enum's own
     * spelling, which does not change with the language the phone is being read in. Anything
     * printed to a person goes through the UI's own naming instead.
     */
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

        // A joined pair is one stop, of the same length as any other.
        //
        // This used to run from the first prayer's adhan to the second one's, plus the pause on
        // the end — three and a half hours of a closed phone in the afternoon, on the setting whose
        // whole point is that someone praying the pair together stops *once* rather than twice.
        // Combining is a permission to pray the second prayer early, at the first one's time, so
        // the pause sits where the praying does: at the first adhan, for the minutes configured.
        fun add(from: Prayer, to: Prayer?) {
            val at = timetable.minuteOfDay(from)
            val start = at - config.minutesBefore
            val end = at + config.minutesAfter
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
