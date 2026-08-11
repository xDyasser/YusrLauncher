package dev.yusr.domain

/**
 * A guard on times that arrived from somewhere else.
 *
 * The reason this exists: Aladhan's Jafari (Shia) method returns asr equal to dhuhr and isha
 * equal to maghrib, because the two pairs are prayed together. That is a statement about when
 * they are *prayed*, not about when they *begin*, and a timetable that repeats a time is no
 * timetable at all — asr and isha vanish from the day, and any window built on them lands on top
 * of the window before it.
 *
 * So a fetched day is only trusted where it is coherent: the six times must be strictly
 * increasing. Anything that is not is replaced by the on-device solver's answer for that same
 * day, which always has a distinct time for each prayer. The rest of the fetched day survives,
 * which is the point — the masjid's fajr is kept even when its asr has to be worked out here.
 */
object SyncedTimes {

    /** The order the six times must appear in for the day to make sense. */
    private val ORDER = listOf(
        Prayer.FAJR,
        Prayer.SUNRISE,
        Prayer.DHUHR,
        Prayer.ASR,
        Prayer.MAGHRIB,
        Prayer.ISHA,
    )

    /**
     * True when [synced] holds all six times and each one falls strictly after the last. A day
     * that repeats, reverses, or is simply missing a prayer cannot be used as it stands.
     */
    fun isCoherent(synced: Map<Prayer, Int>): Boolean {
        val times = ORDER.map { synced[it] ?: return false }
        return times.zipWithNext().all { (earlier, later) -> later > earlier }
    }

    /**
     * [synced] with every incoherent time replaced by [computed]'s. A time is incoherent when it
     * does not fall strictly after the one before it.
     *
     * If the patched day still does not hold together — a fetched time so far out that the
     * solver's answer cannot follow it — the whole day is given up and [computed] is returned
     * instead. Half a timetable is not worth defending.
     */
    fun repair(synced: Map<Prayer, Int>, computed: PrayerTimetable): Map<Prayer, Int> {
        if (isCoherent(synced)) return synced

        val repaired = LinkedHashMap<Prayer, Int>(ORDER.size)
        var previous: Int? = null
        for (prayer in ORDER) {
            val fetched = synced[prayer]
            val floor = previous
            val kept = if (fetched != null && (floor == null || fetched > floor)) {
                fetched
            } else {
                computed.minuteOfDay(prayer)
            }
            repaired[prayer] = kept
            previous = kept
        }
        return if (isCoherent(repaired)) repaired else computed.minutes
    }
}
