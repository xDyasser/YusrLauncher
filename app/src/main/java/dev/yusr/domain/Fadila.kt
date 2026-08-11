package dev.yusr.domain

/**
 * When the preferred part of each prayer's window closes.
 *
 * Every prayer has a window it remains valid in and a shorter opening portion — the *waqt
 * al-faḍīla* — in which praying it is better. A launcher that pauses the phone for salah can say
 * something more useful than "ʿasr is in two hours": it can say that dhuhr's preferred time runs
 * out in forty minutes, which is the number that actually changes behaviour.
 *
 * Each end below is a named boundary computed from the sun, not an interval picked to look
 * plausible:
 *
 * | prayer  | the faḍīla ends at                    | computed as                        |
 * | ------- | ------------------------------------- | ---------------------------------- |
 * | fajr    | *isfār*, when the sky brightens       | halfway from fajr to sunrise       |
 * | dhuhr   | a gnomon's shadow grown by its length | the standard (one-shadow) asr time |
 * | ʿasr    | before the sun yellows                | halfway from ʿasr to maghrib       |
 * | maghrib | the red *shafaq* leaving the sky      | the sun 17° below the horizon      |
 * | ʿishāʾ  | *shar'ī* midnight                     | halfway from maghrib to next fajr  |
 *
 * The schools do not agree on all five, and two of them (isfār, the yellowing) are descriptions
 * of the sky rather than angles, so those are honest approximations and are shown as such. This
 * is a nudge towards praying early, not a verdict — which is why nothing in the enforcement layer
 * reads any of it. It goes on a screen and no further.
 */
object Fadila {

    /** The angle the red twilight is taken to disappear at, which is the common figure for it. */
    const val SHAFAQ_ANGLE = 17.0

    /**
     * The end of each prayer's preferred time, as minutes past local midnight.
     *
     * [shafaqMinuteOfDay] is the moment the sun reaches [SHAFAQ_ANGLE] — the caller has the solver
     * and can compute it; passing it in keeps this function pure arithmetic over a timetable.
     * A null there simply leaves maghrib out, which is what a polar summer deserves.
     *
     * ʿishāʾ's end can land after midnight, and is returned as a minute past *that* midnight
     * (so 01:12 comes back as 72, not 1512). Everything printing it has a date to hand.
     */
    fun ends(
        timetable: PrayerTimetable,
        standardAsrMinuteOfDay: Int,
        shafaqMinuteOfDay: Int?,
    ): Map<Prayer, Int> {
        val fajr = timetable.minuteOfDay(Prayer.FAJR)
        val sunrise = timetable.minuteOfDay(Prayer.SUNRISE)
        val dhuhr = timetable.minuteOfDay(Prayer.DHUHR)
        val asr = timetable.minuteOfDay(Prayer.ASR)
        val maghrib = timetable.minuteOfDay(Prayer.MAGHRIB)

        val ends = mutableMapOf<Prayer, Int>()

        if (sunrise > fajr) ends[Prayer.FAJR] = midpoint(fajr, sunrise)

        // The one-shadow asr is inside the dhuhr window under every school; under the Hanafi
        // reading, where asr itself starts at two shadows, it is well inside it.
        if (standardAsrMinuteOfDay > dhuhr) ends[Prayer.DHUHR] = standardAsrMinuteOfDay

        if (maghrib > asr) ends[Prayer.ASR] = midpoint(asr, maghrib)

        if (shafaqMinuteOfDay != null && shafaqMinuteOfDay > maghrib) {
            ends[Prayer.MAGHRIB] = shafaqMinuteOfDay
        }

        // Night wraps midnight, so fajr is taken from tomorrow.
        val night = fajr + MINUTES_PER_DAY - maghrib
        if (night > 0) ends[Prayer.ISHA] = (maghrib + night / 2) % MINUTES_PER_DAY

        return ends
    }

    /**
     * How long is left of [prayer]'s preferred time at [minuteOfDay], or null when the prayer has
     * no faḍīla end today, when its window has not opened yet, or when the preferred part is
     * already over.
     *
     * ʿishāʾ is the awkward one: its end is usually the small hours of the next day, so a plain
     * subtraction would report a prayer that ran out twenty-two hours ago. An end that sits before
     * the prayer's own start is therefore read as tomorrow's clock.
     */
    fun remaining(
        ends: Map<Prayer, Int>,
        timetable: PrayerTimetable,
        prayer: Prayer,
        minuteOfDay: Int,
    ): Int? {
        val end = ends[prayer] ?: return null
        val start = timetable.minuteOfDay(prayer)
        val wrapped = if (end < start) end + MINUTES_PER_DAY else end
        val now = if (minuteOfDay < start && wrapped > MINUTES_PER_DAY) {
            minuteOfDay + MINUTES_PER_DAY
        } else {
            minuteOfDay
        }
        if (now < start) return null
        val left = wrapped - now
        return if (left > 0) left else null
    }

    private fun midpoint(from: Int, to: Int): Int = from + (to - from) / 2

    private const val MINUTES_PER_DAY = 24 * 60
}
