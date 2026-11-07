package dev.yusr.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Which days are fasts, worked out from the Hijri date rather than remembered.
 *
 * The recurring fasts are all calendar facts — the thirteenth to the fifteenth of every Hijri
 * month, this weekday or that, a handful of named days in the year — so an app that already knows
 * today's Hijri date can say what today is without being told and without a reminder to set up.
 *
 * Which facts, though, depends on the school, and the two branches differ more here than
 * anywhere else in the app:
 *
 * | | Sunnī | Shīʿī |
 * | --- | --- | --- |
 * | weekly | Monday and Thursday | — |
 * | monthly | the white days | the white days, the first and last Thursday, the Wednesday after the tenth |
 * | 10 Muḥarram | a fast | *imsāk* till ʿaṣr, deliberately *without* a fasting intention |
 * | named days | ʿArafah | ʿArafah, 27 Rajab, 15 Shaʿbān, 25 Dhū al-Qaʿda, 18 and 24 Dhū al-Ḥijja |
 * | tashrīq | no fasting | no fasting only at Minā, so an ordinary day for everyone else |
 *
 * Telling a Jaʿfarī reader to fast on Monday and on ʿĀshūrāʾ — which is what this did before it
 * took a [Madhab.Branch] — is not a near miss: the Monday fast is discouraged in that school's
 * narrations and the ʿĀshūrāʾ one is close to the opposite of what it prescribes for the day.
 *
 * The days fasting is *forbidden* on are listed too, and they take precedence over everything
 * else. An app that suggested a fast on Eid would be worse than an app that suggested nothing.
 *
 * Nothing here is a string. The day comes back as a [Kind] and the Hijri date it was read from,
 * and whichever screen prints it decides the words and the language — the domain does not know
 * what language the phone is being read in and should not.
 */
object FastingCalendar {

    enum class Kind(
        /** Whether this is a day the app should suggest a fast on at all. */
        val isFast: Boolean = true,
        /** Whether fasting is *not permitted*, which is a stronger statement than "not a fast". */
        val forbidden: Boolean = false,
    ) {
        /** The obligatory month. */
        RAMADAN,

        /** The tenth of Muḥarram, as the Sunnī schools keep it: a fast. */
        ASHURA,

        /**
         * The tenth of Muḥarram, as the Shīʿī schools keep it: abstaining from food and drink
         * until the afternoon with no intention of fasting, in grief rather than in worship. Not a
         * fast, and shown so that the day is not left blank — but never offered as one to mark.
         */
        ASHURA_IMSAK(isFast = false),

        /** The ninth of Dhū al-Ḥijjah, for those not on ḥajj. */
        ARAFAH,

        /** 27 Rajab, the day of the Prophet's mission. */
        MABATH,

        /** 15 Shaʿbān. It is also a white day; this is the more particular thing to say about it. */
        MID_SHABAN,

        /** 25 Dhū al-Qaʿda, the day the earth was spread out from under the Kaʿba. */
        DAHW_AL_ARD,

        /** 18 Dhū al-Ḥijja, the day of Ghadīr. */
        GHADIR,

        /** 24 Dhū al-Ḥijja, the day of the Mubāhala. */
        MUBAHALA,

        /** The white days: the thirteenth, fourteenth and fifteenth of any month. */
        AYYAM_AL_BID,

        /** The Sunnī weekly fasts. */
        MONDAY,
        THURSDAY,

        /** The Shīʿī monthly three: the first Thursday, the last, and the Wednesday between. */
        FIRST_THURSDAY,
        LAST_THURSDAY,
        MIDDLE_WEDNESDAY,

        /** The two Eids and, for the Sunnī schools, the days of tashrīq. */
        EID_AL_FITR(isFast = false, forbidden = true),
        EID_AL_ADHA(isFast = false, forbidden = true),
        TASHRIQ(isFast = false, forbidden = true),

        /** An ordinary day. */
        NONE(isFast = false),
        ;

        companion object {
            /**
             * A kind by its stored name, for a record written by an earlier version. The names
             * that have ever been written are all still here; `FORBIDDEN`, the one that is not,
             * was never storable because a forbidden day cannot be marked as kept.
             */
            fun named(name: String): Kind? = entries.firstOrNull { it.name == name }
        }
    }

    data class Day(
        val date: LocalDate,
        val kind: Kind,
        /** The Hijri date the kind was read from, which is what a label of a white day needs. */
        val hijri: Hijri.HijriDate?,
    ) {
        /** Whether the app should suggest a fast on this day at all. */
        val isFast: Boolean get() = kind.isFast
    }

    /**
     * What [date] is, given the Hijri offset the user has set and the school they follow.
     *
     * The order matters and is not arbitrary: forbidden beats everything, then the obligatory
     * month, then the named days of the year, then the monthly days, and only then the weekly
     * ones — so the fifteenth of Rajab falling on a Monday reads as a white day, and the fifteenth
     * of Shaʿbān reads as itself rather than as a white day.
     */
    fun classify(
        date: LocalDate,
        hijriOffsetDays: Int = 0,
        branch: Madhab.Branch = Madhab.Branch.SUNNI,
    ): Day {
        val hijri = Hijri.of(date, hijriOffsetDays)
            ?: return Day(date, weekly(date, branch), null)

        forbidden(hijri, branch)?.let { return Day(date, it, hijri) }

        if (hijri.month == 9) return Day(date, Kind.RAMADAN, hijri)

        named(hijri, branch)?.let { return Day(date, it, hijri) }

        if (hijri.day in 13..15) return Day(date, Kind.AYYAM_AL_BID, hijri)

        monthly(date, hijri, hijriOffsetDays, branch)?.let { return Day(date, it, hijri) }

        return Day(date, weekly(date, branch), hijri)
    }

    /** Every day in the [days] beginning at [from], for the calendar strip on the screen. */
    fun upcoming(
        from: LocalDate,
        days: Int,
        hijriOffsetDays: Int = 0,
        branch: Madhab.Branch = Madhab.Branch.SUNNI,
    ): List<Day> = (0 until days).map { classify(from.plusDays(it.toLong()), hijriOffsetDays, branch) }

    /** The next day worth fasting at or after [from], or null if none falls inside [withinDays]. */
    fun next(
        from: LocalDate,
        withinDays: Int = 40,
        hijriOffsetDays: Int = 0,
        branch: Madhab.Branch = Madhab.Branch.SUNNI,
    ): Day? = upcoming(from, withinDays, hijriOffsetDays, branch).firstOrNull { it.isFast }

    private fun forbidden(hijri: Hijri.HijriDate, branch: Madhab.Branch): Kind? = when {
        hijri.month == 10 && hijri.day == 1 -> Kind.EID_AL_FITR
        hijri.month == 12 && hijri.day == 10 -> Kind.EID_AL_ADHA
        // The Shīʿī prohibition on the days of tashrīq is for whoever is at Minā, which is a
        // place and not a date; for everyone else they are ordinary days and the thirteenth is
        // a white one.
        hijri.month == 12 && hijri.day in 11..13 && branch == Madhab.Branch.SUNNI -> Kind.TASHRIQ
        else -> null
    }

    private fun named(hijri: Hijri.HijriDate, branch: Madhab.Branch): Kind? {
        val shia = branch == Madhab.Branch.SHIA
        return when {
            hijri.month == 1 && hijri.day == 10 -> if (shia) Kind.ASHURA_IMSAK else Kind.ASHURA
            hijri.month == 12 && hijri.day == 9 -> Kind.ARAFAH
            !shia -> null
            hijri.month == 7 && hijri.day == 27 -> Kind.MABATH
            hijri.month == 8 && hijri.day == 15 -> Kind.MID_SHABAN
            hijri.month == 11 && hijri.day == 25 -> Kind.DAHW_AL_ARD
            hijri.month == 12 && hijri.day == 18 -> Kind.GHADIR
            hijri.month == 12 && hijri.day == 24 -> Kind.MUBAHALA
            else -> null
        }
    }

    /**
     * The three days a month the Shīʿī schools keep in place of the weekly pair. "Last Thursday"
     * is the Thursday after which the month is over — read off the calendar a week ahead rather
     * than off a table of month lengths, because the calendar is the thing that knows.
     */
    private fun monthly(
        date: LocalDate,
        hijri: Hijri.HijriDate,
        hijriOffsetDays: Int,
        branch: Madhab.Branch,
    ): Kind? {
        if (branch != Madhab.Branch.SHIA) return null
        return when (date.dayOfWeek) {
            DayOfWeek.THURSDAY -> when {
                hijri.day <= 7 -> Kind.FIRST_THURSDAY
                Hijri.of(date.plusDays(7), hijriOffsetDays)?.month != hijri.month -> Kind.LAST_THURSDAY
                else -> null
            }
            DayOfWeek.WEDNESDAY -> if (hijri.day in 11..17) Kind.MIDDLE_WEDNESDAY else null
            else -> null
        }
    }

    private fun weekly(date: LocalDate, branch: Madhab.Branch): Kind {
        if (branch == Madhab.Branch.SHIA) return Kind.NONE
        return when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> Kind.MONDAY
            DayOfWeek.THURSDAY -> Kind.THURSDAY
            else -> Kind.NONE
        }
    }
}
