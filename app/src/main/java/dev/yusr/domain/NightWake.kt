package dev.yusr.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * When to wake for ṣalāt al-layl.
 *
 * The night prayer belongs to the last third of the night, and its best time is the part of that
 * third nearest fajr. So the wake-up is anchored to one of the two and set a number of minutes
 * before it: at the last third itself, or an hour (or however long) before fajr, which is how
 * most people who keep the prayer set their alarm — long enough for wuḍūʾ, eleven rakʿāt and the
 * duʿāʾ, and over before the adhan.
 *
 * Both anchors come off the sun rather than the clock, so the alarm walks with the seasons: an
 * alarm set for 03:30 in June is an hour late by December, and one set for "an hour before fajr"
 * never is.
 */
object NightWake {

    enum class Anchor {
        /** The start of the last third of the night. */
        LAST_THIRD,

        /** Fajr itself, the end of the night. */
        FAJR,
    }

    /** The furthest before its anchor the alarm can be set: three hours is the whole of most nights' last third. */
    const val MAX_MINUTES_BEFORE: Int = 180

    /**
     * The wake-up for the night that *begins* at maghrib on [evening], as a date and time.
     *
     * [tonight] is that evening's timetable and [tomorrow] the next day's, whose fajr ends the
     * night. The last third is reckoned from tonight's maghrib to tomorrow's fajr, which is the
     * night it actually is.
     */
    fun wakeFor(
        evening: LocalDate,
        tonight: PrayerTimetable,
        tomorrow: PrayerTimetable,
        anchor: Anchor,
        minutesBefore: Int,
    ): LocalDateTime {
        val maghrib = evening.atTime(time(tonight.minuteOfDay(Prayer.MAGHRIB)))
        val fajr = evening.plusDays(1).atTime(time(tomorrow.minuteOfDay(Prayer.FAJR)))
        val nightMinutes = java.time.Duration.between(maghrib, fajr).toMinutes()
        val target = when (anchor) {
            Anchor.LAST_THIRD -> maghrib.plusMinutes(nightMinutes * 2 / 3)
            Anchor.FAJR -> fajr
        }
        // Never before maghrib, whatever the setting: an alarm for the night prayer that rings in
        // the evening is a mistake, not an early start.
        val wanted = target.minusMinutes(minutesBefore.coerceIn(0, MAX_MINUTES_BEFORE).toLong())
        return if (wanted.isBefore(maghrib)) maghrib else wanted
    }

    /**
     * The next wake-up after [now]. It is either the one for the night that began last evening
     * (if [now] is in the small hours and it has not rung yet) or the one for tonight.
     */
    fun next(
        now: LocalDateTime,
        timetableFor: (LocalDate) -> PrayerTimetable,
        anchor: Anchor,
        minutesBefore: Int,
    ): LocalDateTime {
        val today = now.toLocalDate()
        val lastNight = wakeFor(today.minusDays(1), timetableFor(today.minusDays(1)), timetableFor(today), anchor, minutesBefore)
        if (lastNight.isAfter(now)) return lastNight
        return wakeFor(today, timetableFor(today), timetableFor(today.plusDays(1)), anchor, minutesBefore)
    }

    private fun time(minuteOfDay: Int): LocalTime {
        val wrapped = Math.floorMod(minuteOfDay, 24 * 60)
        return LocalTime.of(wrapped / 60, wrapped % 60)
    }
}

/**
 * The khums year, and the reminder that it is ending.
 *
 * Khums is reckoned once a year, on a date each person fixes for themselves — usually the day
 * their earnings began, or a date they chose to make the accounting regular. The app does none
 * of the reckoning; it remembers the date and says so a week ahead, so the accounts can be done
 * before the day rather than discovered after it.
 */
object Khums {

    enum class Reminder {
        /** Seven days before the date. */
        WEEK_BEFORE,

        /** The date itself. */
        TODAY,
    }

    const val DAYS_AHEAD: Long = 7

    /**
     * Whether [today] calls for a reminder about a khums year ending each [month]/[day]. The 29th
     * of February is the 28th in a year without one, so the reminder never skips a year.
     */
    fun reminderOn(today: LocalDate, month: Int, day: Int): Reminder? {
        val due = dateIn(today.year, month, day) ?: return null
        return when {
            today == due -> Reminder.TODAY
            today == due.minusDays(DAYS_AHEAD) -> Reminder.WEEK_BEFORE
            // A date in the first week of January is warned about in the last week of December.
            today == dateIn(today.year + 1, month, day)?.minusDays(DAYS_AHEAD) -> Reminder.WEEK_BEFORE
            else -> null
        }
    }

    /** The next khums date on or after [today]. */
    fun nextOn(today: LocalDate, month: Int, day: Int): LocalDate? {
        val thisYear = dateIn(today.year, month, day) ?: return null
        return if (thisYear.isBefore(today)) dateIn(today.year + 1, month, day) else thisYear
    }

    private fun dateIn(year: Int, month: Int, day: Int): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        val length = java.time.YearMonth.of(year, month).lengthOfMonth()
        return LocalDate.of(year, month, day.coerceAtMost(length))
    }
}
