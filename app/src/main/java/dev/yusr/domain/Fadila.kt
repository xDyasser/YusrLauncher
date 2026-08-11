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
 * Three of those are the Sunnī reckoning, and are not what a Jaʿfarī or Zaydī reader is owed:
 *
 * | prayer  | the Shīʿī faḍīla ends at              | computed as                        |
 * | ------- | ------------------------------------- | ---------------------------------- |
 * | dhuhr   | *qadamayn*, two feet of shadow        | the shadow grown by 2/7            |
 * | ʿasr    | *arbaʿat aqdām*, four feet of shadow  | the shadow grown by 4/7            |
 * | ʿishāʾ  | *thulth al-layl*, the first third     | a third of maghrib to next fajr    |
 *
 * The aqdām are the school's own measure — a shadow counted in the feet of a seven-foot man, so
 * two of them are a shadow grown by 2/7 and four by 4/7 — and both are counted from *zawāl*, not
 * each from its own prayer. That is why ẓuhr is on this list although nobody complained about it:
 * ʿaṣr's boundary at 4/7 falls *before* the one-shadow moment that a timetable prints ʿaṣr at, and
 * leaving ẓuhr at one shadow would have put the earlier prayer's preferred time hours after the
 * later one's. The two move together or neither does.
 *
 * ʿishāʾ is the plainer error: the first third of the night is where its preferred time closes,
 * and *shar'ī* midnight — which is what was printed — is where its time runs out altogether. A
 * Shīʿī reader was being shown some three hours of preferred time the school does not grant.
 *
 * Because the aqdām run from zawāl, ʿaṣr's preferred time also *begins* there under these schools
 * rather than at its own entry in the timetable, which is what [starts] is for.
 *
 * The schools do not agree on all five, and two of them (isfār, the yellowing) are descriptions
 * of the sky rather than angles, so those are honest approximations and are shown as such. This
 * is a nudge towards praying early, not a verdict — which is why nothing in the enforcement layer
 * reads any of it. It goes on a screen and no further.
 */
object Fadila {

    /**
     * The two shadows the aqdām come to: 2/7 of the gnomon closes ẓuhr's preferred time under the
     * Shīʿī schools and 4/7 closes ʿaṣr's. The caller has the solver and computes the moments;
     * these are the measures they are computed at.
     */
    const val DHUHR_AQDAM_SHADOW = 2.0 / 7.0
    const val ASR_AQDAM_SHADOW = 4.0 / 7.0

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
        branch: Madhab.Branch = Madhab.Branch.SUNNI,
        qadamayniMinuteOfDay: Int? = null,
        aqdamAsrMinuteOfDay: Int? = null,
    ): Map<Prayer, Int> {
        val fajr = timetable.minuteOfDay(Prayer.FAJR)
        val sunrise = timetable.minuteOfDay(Prayer.SUNRISE)
        val dhuhr = timetable.minuteOfDay(Prayer.DHUHR)
        val asr = timetable.minuteOfDay(Prayer.ASR)
        val maghrib = timetable.minuteOfDay(Prayer.MAGHRIB)

        val ends = mutableMapOf<Prayer, Int>()

        if (sunrise > fajr) ends[Prayer.FAJR] = midpoint(fajr, sunrise)

        val shia = branch == Madhab.Branch.SHIA
        if (shia) {
            // Both aqdām, or neither: a day the sun never casts that shadow gets no preferred
            // time printed for the pair, rather than the Sunnī figures standing in for boundaries
            // this reader does not go by.
            if (qadamayniMinuteOfDay != null && qadamayniMinuteOfDay > dhuhr) {
                ends[Prayer.DHUHR] = qadamayniMinuteOfDay
            }
            if (aqdamAsrMinuteOfDay != null && aqdamAsrMinuteOfDay > dhuhr) {
                ends[Prayer.ASR] = aqdamAsrMinuteOfDay
            }
        } else {
            // The one-shadow asr is inside the dhuhr window under every Sunnī school; under the
            // Hanafi reading, where asr itself starts at two shadows, it is well inside it.
            if (standardAsrMinuteOfDay > dhuhr) ends[Prayer.DHUHR] = standardAsrMinuteOfDay
            if (maghrib > asr) ends[Prayer.ASR] = midpoint(asr, maghrib)
        }

        if (shafaqMinuteOfDay != null && shafaqMinuteOfDay > maghrib) {
            ends[Prayer.MAGHRIB] = shafaqMinuteOfDay
        }

        // Night wraps midnight, so fajr is taken from tomorrow. A third of it for the Shīʿī
        // schools, half of it — *shar'ī* midnight — for the Sunnī ones.
        val night = fajr + MINUTES_PER_DAY - maghrib
        val share = if (shia) night / 3 else night / 2
        if (night > 0) ends[Prayer.ISHA] = (maghrib + share) % MINUTES_PER_DAY

        return ends
    }

    /**
     * Where a preferred time begins, for the prayers that do not begin at their own entry in the
     * timetable. Empty under the Sunnī schools, where every one of them does.
     *
     * Under the Shīʿī schools ẓuhr and ʿaṣr share a window that opens at *zawāl*, and the aqdām
     * that close each of their preferred times are counted from there. A timetable still prints
     * ʿaṣr at the one-shadow moment, which is the convention every published Shīʿī calendar uses —
     * but that moment is after the four aqdām, so measuring ʿaṣr's preferred time from it would
     * make it a window that ended before it opened.
     */
    fun starts(timetable: PrayerTimetable, branch: Madhab.Branch): Map<Prayer, Int> =
        if (branch == Madhab.Branch.SHIA) {
            mapOf(Prayer.ASR to timetable.minuteOfDay(Prayer.DHUHR))
        } else {
            emptyMap()
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
        starts: Map<Prayer, Int> = emptyMap(),
    ): Int? {
        val end = ends[prayer] ?: return null
        val start = starts[prayer] ?: timetable.minuteOfDay(prayer)
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
