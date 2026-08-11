package dev.minimalist.domain

/**
 * The two moments the night is divided at.
 *
 * Neither is a prayer, and that is exactly why they are worth printing. *Sharʿī* midnight is the
 * outer edge of ʿishāʾ — the hour after which the prayer is late whatever the clock on the wall
 * says — and the last third is when qiyām al-layl is prayed. Both are read off the sun rather than
 * off midnight: the night runs from maghrib to the next fajr, and 00:00 has nothing to do with it.
 *
 * The Shia schools lean on both harder than most — ʿishāʾ combined with maghrib is prayed early
 * and its window closes at midnight, and the night prayer is a fixture rather than an extra — but
 * neither boundary is anybody's in particular, so both are shown to everyone.
 */
data class NightTimes(
    /** Halfway from maghrib to fajr: the end of ʿishāʾ. Minutes past local midnight. */
    val midnightMinuteOfDay: Int,
    /** Two thirds of the way through, where qiyām al-layl begins. */
    val lastThirdMinuteOfDay: Int,
    /** How long the night is, which is what makes the two above worth anything at high latitude. */
    val lengthMinutes: Int,
)

object Night {

    const val MINUTES_PER_DAY: Int = 24 * 60

    /**
     * The night around [timetable]'s evening: from tonight's maghrib to tomorrow's fajr, taken as
     * today's, which differ by a couple of minutes at most and by nothing that matters here.
     *
     * Both results are minutes past the midnight of whichever day they land in, so a last third
     * beginning at 02:14 comes back as 134 rather than as 1574. Everything printing them has a
     * clock and no date, which is the same convention [Fadila] uses for ʿishāʾ.
     */
    fun of(timetable: PrayerTimetable): NightTimes {
        val maghrib = timetable.minuteOfDay(Prayer.MAGHRIB)
        val fajr = timetable.minuteOfDay(Prayer.FAJR)
        val length = wrap(fajr - maghrib)
        return NightTimes(
            midnightMinuteOfDay = wrap(maghrib + length / 2),
            lastThirdMinuteOfDay = wrap(maghrib + 2 * length / 3),
            lengthMinutes = length,
        )
    }

    private fun wrap(minute: Int): Int = ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
}
