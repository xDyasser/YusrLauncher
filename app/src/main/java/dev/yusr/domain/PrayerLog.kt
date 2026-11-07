package dev.yusr.domain

/**
 * Which of the day's prayers have been prayed, and which have run out.
 *
 * The app already stops the phone for each prayer; what it never asked was whether the prayer
 * was then prayed. That is the one fact about salah the phone cannot work out from the sun, so
 * it is the one thing worth writing down — a tap per prayer — and from it follows the number
 * that matters more than any screen-time total in the app: how many are owed as qaḍāʾ.
 *
 * Each prayer is in exactly one of four states. It has not come in yet; its time is in and it has
 * not been marked; it has been marked; or its time ran out unmarked, which is a prayer owed. The
 * boundary where "open" becomes "owed" is the end of the prayer's time, not of its preferred
 * part — this is a ledger, not a nudge — and the ends are the ones the schools give:
 *
 * | prayer         | time runs out at                                             |
 * | -------------- | ------------------------------------------------------------ |
 * | fajr           | sunrise                                                      |
 * | dhuhr and ʿaṣr | maghrib                                                      |
 * | maghrib        | ʿishāʾ under the Sunnī schools; sharʿī midnight under the Shīʿī |
 * | ʿishāʾ         | fajr under the Sunnī schools; sharʿī midnight under the Shīʿī |
 *
 * The Sunnī ends are the generous reading — dhuhr is held open until maghrib rather than until
 * ʿaṣr — because a ledger that called a prayer late by the strict reading of one school would be
 * wrong for the next. Nothing here feeds the gate or the budgets. It is a record, and a count.
 */
object PrayerLog {

    /** The five that are owed. Sunrise bounds one of them and is not itself one. */
    val OWED: List<Prayer> = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

    enum class State {
        /** Not yet in. */
        AHEAD,

        /** In, and not marked. The one that asks for a tap. */
        OPEN,

        /** Marked as prayed. */
        PRAYED,

        /** Ran out unmarked. Owed. */
        MISSED,
        ;

        /** Whether a tap on it means anything: a prayer can be marked once its time is in. */
        val markable: Boolean get() = this != AHEAD
    }

    /**
     * When each prayer's time runs out, as minutes past local midnight. The night ends wrap — a
     * maghrib that runs out at 00:12 comes back as 12, the same convention [Night] uses — and
     * [state] reads an end before its start as the next day's clock.
     */
    fun ends(timetable: PrayerTimetable, night: NightTimes, branch: Madhab.Branch): Map<Prayer, Int> {
        val maghrib = timetable.minuteOfDay(Prayer.MAGHRIB)
        val nightEnd = if (branch == Madhab.Branch.SHIA) {
            night.midnightMinuteOfDay
        } else {
            timetable.minuteOfDay(Prayer.FAJR)
        }
        return mapOf(
            Prayer.FAJR to timetable.minuteOfDay(Prayer.SUNRISE),
            Prayer.DHUHR to maghrib,
            Prayer.ASR to maghrib,
            Prayer.MAGHRIB to if (branch == Madhab.Branch.SHIA) nightEnd else timetable.minuteOfDay(Prayer.ISHA),
            Prayer.ISHA to nightEnd,
        )
    }

    /**
     * One prayer's state, from where its time runs — [start] to [end], minutes of the day it
     * belongs to — and when it is being asked about: [daysAgo] days after that day, at
     * [minuteOfDay]. An end at or before its start is the next day's clock, which is how the
     * night prayers come back from [ends].
     */
    fun state(start: Int, end: Int, prayed: Boolean, daysAgo: Int, minuteOfDay: Int): State {
        if (prayed) return State.PRAYED
        val wraps = end <= start
        return when {
            daysAgo < 0 -> State.AHEAD
            daysAgo == 0 -> when {
                minuteOfDay < start -> State.AHEAD
                wraps -> State.OPEN
                minuteOfDay < end -> State.OPEN
                else -> State.MISSED
            }
            // The small hours after a day: its ʿishāʾ may still be open.
            daysAgo == 1 && wraps && minuteOfDay < end -> State.OPEN
            else -> State.MISSED
        }
    }

    /**
     * The state of each of the five on one day.
     *
     * [entries] decides where each prayer's time begins: on a joined line the second prayer
     * opens with the first, because joining is exactly the permission to pray it then. A prayer
     * on no line — there is none; the entries always cover the five — would open at its own time.
     */
    fun day(
        timetable: PrayerTimetable,
        ends: Map<Prayer, Int>,
        entries: List<PrayerEntry>,
        prayed: Set<Prayer>,
        daysAgo: Int,
        minuteOfDay: Int,
    ): Map<Prayer, State> = OWED.associateWith { prayer ->
        val start = entries.firstOrNull { it.covers(prayer) }?.minuteOfDay ?: timetable.minuteOfDay(prayer)
        state(
            start = start,
            end = ends.getValue(prayer),
            prayed = prayer in prayed,
            daysAgo = daysAgo,
            minuteOfDay = minuteOfDay,
        )
    }

    /** How many of [states] are owed. */
    fun missed(states: Map<Prayer, State>): Int = states.values.count { it == State.MISSED }

    /** How many of [states] are prayed. */
    fun prayed(states: Map<Prayer, State>): Int = states.values.count { it == State.PRAYED }

    /**
     * The prayer a tap on the home screen should be about: the earliest one whose time is in
     * and which has not been marked, or null when there is nothing to ask. Open before missed,
     * because the open one can still be prayed on time and the missed one can wait for the
     * ledger.
     */
    fun outstanding(states: Map<Prayer, State>): Prayer? =
        OWED.firstOrNull { states[it] == State.OPEN } ?: OWED.firstOrNull { states[it] == State.MISSED }

    /**
     * What a tap on a timetable line does to the prayers on it. If every markable prayer on the
     * line is prayed, the tap unmarks them — a mis-tap must be undoable from the same place —
     * and otherwise it marks the ones that can be marked. Returns the prayers to mark, and
     * whether to mark them.
     */
    fun toggle(entry: PrayerEntry, states: Map<Prayer, State>): Pair<List<Prayer>, Boolean> {
        val onLine = OWED.filter { entry.covers(it) && states[it]?.markable == true }
        val allPrayed = onLine.isNotEmpty() && onLine.all { states[it] == State.PRAYED }
        return onLine to !allPrayed
    }
}
