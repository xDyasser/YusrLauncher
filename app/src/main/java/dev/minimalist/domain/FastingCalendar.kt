package dev.minimalist.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Which days are fasts, worked out from the Hijri date rather than remembered.
 *
 * The recurring sunna fasts are all calendar facts — the thirteenth to the fifteenth of every
 * Hijri month, Mondays and Thursdays, ʿĀshūrāʾ, ʿArafah — so an app that already knows today's
 * Hijri date can say what today is without being told and without a reminder to set up.
 *
 * The days fasting is *forbidden* on are listed too, and they take precedence over everything
 * else. An app that suggested a fast on Eid would be worse than an app that suggested nothing.
 */
object FastingCalendar {

    enum class Kind {
        /** The obligatory month. */
        RAMADAN,

        /** The tenth of Muḥarram. */
        ASHURA,

        /** The ninth of Dhū al-Ḥijjah, for those not on ḥajj. */
        ARAFAH,

        /** The white days: the thirteenth, fourteenth and fifteenth of any month. */
        AYYAM_AL_BID,

        /** The weekly fasts. */
        MONDAY,
        THURSDAY,

        /** The two Eids and the days of tashrīq, when fasting is not permitted. */
        FORBIDDEN,

        /** An ordinary day. */
        NONE,
    }

    data class Day(
        val date: LocalDate,
        val kind: Kind,
        /** What to print: "Ramaḍān", "Mon & Thu", "White days · 14 Rajab". */
        val label: String,
    ) {
        /** Whether the app should suggest a fast on this day at all. */
        val isFast: Boolean get() = kind != Kind.NONE && kind != Kind.FORBIDDEN
    }

    /**
     * What [date] is, given the Hijri offset the user has set.
     *
     * The order matters and is not arbitrary: forbidden beats everything, then the obligatory
     * month, then the two singular days of the year, then the monthly white days, and only then
     * the weekly ones — so the fifteenth of Rajab falling on a Monday reads as a white day, which
     * is the more particular thing to say about it.
     */
    fun classify(date: LocalDate, hijriOffsetDays: Int = 0): Day {
        val hijri = Hijri.of(date, hijriOffsetDays)
            ?: return Day(date, weekdayKind(date), weekdayLabel(date))

        forbidden(hijri)?.let { return Day(date, Kind.FORBIDDEN, it) }

        if (hijri.month == 9) return Day(date, Kind.RAMADAN, "Ramaḍān")
        if (hijri.month == 1 && hijri.day == 10) return Day(date, Kind.ASHURA, "ʿĀshūrāʾ")
        if (hijri.month == 12 && hijri.day == 9) return Day(date, Kind.ARAFAH, "ʿArafah")

        if (hijri.day in 13..15) {
            return Day(date, Kind.AYYAM_AL_BID, "White days · ${hijri.day} ${hijri.monthName}")
        }

        return Day(date, weekdayKind(date), weekdayLabel(date))
    }

    /** Every fast in the [days] beginning at [from], for the calendar strip on the screen. */
    fun upcoming(from: LocalDate, days: Int, hijriOffsetDays: Int = 0): List<Day> =
        (0 until days).map { classify(from.plusDays(it.toLong()), hijriOffsetDays) }

    /** The next day worth fasting at or after [from], or null if none falls inside [withinDays]. */
    fun next(from: LocalDate, withinDays: Int = 40, hijriOffsetDays: Int = 0): Day? =
        upcoming(from, withinDays, hijriOffsetDays).firstOrNull { it.isFast }

    private fun forbidden(hijri: Hijri.HijriDate): String? = when {
        hijri.month == 10 && hijri.day == 1 -> "ʿĪd al-Fiṭr · no fasting"
        hijri.month == 12 && hijri.day == 10 -> "ʿĪd al-Aḍḥā · no fasting"
        hijri.month == 12 && hijri.day in 11..13 -> "Days of tashrīq · no fasting"
        else -> null
    }

    private fun weekdayKind(date: LocalDate): Kind = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> Kind.MONDAY
        DayOfWeek.THURSDAY -> Kind.THURSDAY
        else -> Kind.NONE
    }

    private fun weekdayLabel(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "Monday"
        DayOfWeek.THURSDAY -> "Thursday"
        else -> ""
    }
}
