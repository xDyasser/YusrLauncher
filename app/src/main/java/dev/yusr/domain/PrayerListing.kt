package dev.yusr.domain

/**
 * A line in a printed timetable: one prayer, or two that are prayed one after the other.
 *
 * The distinction the app already made — [PrayerWindowConfig] combines dhuhr with asr and maghrib
 * with isha, so the phone stops twice in the afternoon rather than four times — stopped at the
 * enforcement layer. Every screen went on listing five prayers at five times, which told someone
 * who prays them as three that the app had not understood the answer they gave it. So the
 * combining is read off the same setting in both places, and this is where the reading lives.
 */
data class PrayerEntry(
    /** The prayer the entry is named and timed by: the first of the pair when there are two. */
    val prayer: Prayer,
    /** The prayer joined to it, or null when this line is one prayer on its own. */
    val with: Prayer?,
    /** When the pair may be prayed from, which is the first one's time. */
    val minuteOfDay: Int,
    /**
     * When the second of the pair enters on its own terms — asr's own start behind a combined
     * dhuhr. Null on an uncombined line. It is worth showing: joining is a permission to pray
     * early, not a claim that the later prayer's time has already come.
     */
    val secondMinuteOfDay: Int?,
) {
    /** Whether [prayer] is on this line at all, which is what "is this one next?" means here. */
    fun covers(other: Prayer): Boolean = other == prayer || other == with
}

/**
 * Today's prayers as they should be read, with the joined pairs on one line each.
 *
 * Sunrise is left out: it is not a prayer, and the screens that want it ask for it by name.
 */
fun PrayerTimetable.entries(
    combineDhuhrAsr: Boolean = false,
    combineMaghribIsha: Boolean = false,
): List<PrayerEntry> {
    fun entry(first: Prayer, second: Prayer?) = PrayerEntry(
        prayer = first,
        with = second,
        minuteOfDay = minuteOfDay(first),
        secondMinuteOfDay = second?.let { minuteOfDay(it) },
    )

    return buildList {
        add(entry(Prayer.FAJR, null))
        if (combineDhuhrAsr) {
            add(entry(Prayer.DHUHR, Prayer.ASR))
        } else {
            add(entry(Prayer.DHUHR, null))
            add(entry(Prayer.ASR, null))
        }
        if (combineMaghribIsha) {
            add(entry(Prayer.MAGHRIB, Prayer.ISHA))
        } else {
            add(entry(Prayer.MAGHRIB, null))
            add(entry(Prayer.ISHA, null))
        }
    }
}
