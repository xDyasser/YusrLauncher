package dev.yusr.domain

/**
 * The two moments a fasting day turns on, and which of them is next.
 *
 * The fast begins at fajr and ends at maghrib, and the app computes both to the minute already.
 * What it did not print is *imsāk*: the point a little before fajr at which one stops eating, kept
 * as a margin so that the last mouthful is not a guess at the edge. Every printed Ramaḍān calendar,
 * Shīʿī and Sunnī alike, sets it a fixed few minutes before fajr — ten is the common figure — and
 * that is what [IMSAK_MINUTES_BEFORE_FAJR] is. It is precaution, not a ruling: the fast itself still
 * begins at fajr, and the screens say so.
 *
 * During Ramaḍān the home screen asks this which way the day is facing — towards ifṭār, or, after
 * it, towards tomorrow's imsāk — and counts down to it. Outside Ramaḍān nothing here is read.
 */
object Ramadan {

    const val IMSAK_MINUTES_BEFORE_FAJR: Int = 10

    enum class Next {
        /** The night: eating is allowed until this. */
        IMSAK,

        /** The day: the fast runs until this. */
        IFTAR,
    }

    data class Countdown(
        val next: Next,
        /** When it falls, as a minute of the day. */
        val minuteOfDay: Int,
        /** How long until then, in minutes; always positive. */
        val minutesAway: Int,
    )

    fun imsak(timetable: PrayerTimetable): Int =
        Math.floorMod(timetable.minuteOfDay(Prayer.FAJR) - IMSAK_MINUTES_BEFORE_FAJR, MINUTES_PER_DAY)

    /**
     * Which of the two is next at [minuteOfDay], given today's timetable. Before imsāk the night
     * is still running and imsāk is next; from imsāk to maghrib the fast is in and ifṭār is next;
     * after maghrib the night has begun again and tomorrow's imsāk is next — taken from today's
     * timetable, which differs from tomorrow's by a minute or so.
     */
    fun countdown(timetable: PrayerTimetable, minuteOfDay: Int): Countdown {
        val imsak = imsak(timetable)
        val iftar = timetable.minuteOfDay(Prayer.MAGHRIB)
        return when {
            minuteOfDay < imsak -> Countdown(Next.IMSAK, imsak, imsak - minuteOfDay)
            minuteOfDay < iftar -> Countdown(Next.IFTAR, iftar, iftar - minuteOfDay)
            else -> Countdown(Next.IMSAK, imsak, imsak + MINUTES_PER_DAY - minuteOfDay)
        }
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
