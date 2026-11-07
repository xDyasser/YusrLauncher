package dev.yusr.domain

/**
 * Screen time bought with worship, when that has been asked for.
 *
 * Off unless turned on, and turning it on is a loosening — it waits out the cooldown like every
 * other one. What it does once on is narrow: an app whose own daily minutes are spent may draw on
 * a single shared pool, and the pool is filled by the day's devotions. Nothing else moves. The
 * prayer window, the blackouts, the blocked list, the daily opens and the countdown are all
 * untouched, and an app with no minute cap has nothing to draw for.
 *
 * One pool, not a bonus per app. Ten minutes earned is ten minutes of screen, wherever it is
 * spent; a bonus added to every cap would turn ten minutes into ten times that.
 *
 * The rates are fixed here rather than offered as knobs, because a knob that set how much a
 * prayer is worth in minutes of an app is a setting nobody should have to choose:
 *
 * | devotion                                   | earns        | at most     |
 * | ------------------------------------------ | ------------ | ----------- |
 * | a prayer marked prayed                     | 5 minutes    | 25 a day    |
 * | a completed set of 33 beads                | 2 minutes    |             |
 * | 3 āyāt read, on the home screen or a page  | 1 minute     |             |
 *
 * and the whole is held under the daily ceiling the user sets ([DEFAULT_CAP] unless changed).
 * The ceiling is the real limit: without it a thumb on the tasbīḥ screen is an unlimited budget.
 *
 * Reading is only counted at a reading pace — an ayah moved on from fewer than
 * [MIN_SECONDS_PER_AYAH] seconds after the last one that counted is still moved on, and the
 * bookmark goes with it, but it earns nothing. Tapping through the Qur'an to unlock an app is the
 * one use of this the app should not reward.
 */
object EarnedMinutes {

    const val PER_PRAYER: Int = 5
    const val PER_TASBIH_SET: Int = 2
    const val BEADS_PER_SET: Int = 33
    const val AYAT_PER_MINUTE: Int = 3
    const val MIN_SECONDS_PER_AYAH: Int = 8

    /** The ceiling a fresh install starts with. */
    const val DEFAULT_CAP: Int = 30

    /** The furthest the ceiling can be raised, for the same reason every other knob has one. */
    const val MAX_CAP: Int = 120

    /** What the day's devotions came to, before any of it was spent. */
    data class Today(
        val fromPrayer: Int,
        val fromDhikr: Int,
        val fromQuran: Int,
        /** The sum, held under the ceiling. */
        val total: Int,
        val cap: Int,
    ) {
        /** Whether another devotion today would earn anything at all. */
        val capped: Boolean get() = total >= cap
    }

    fun earned(prayersMarked: Int, beads: Int, ayatRead: Int, cap: Int): Today {
        val prayer = PER_PRAYER * prayersMarked.coerceIn(0, PrayerLog.OWED.size)
        val dhikr = PER_TASBIH_SET * (beads.coerceAtLeast(0) / BEADS_PER_SET)
        val quran = ayatRead.coerceAtLeast(0) / AYAT_PER_MINUTE
        val ceiling = cap.coerceIn(0, MAX_CAP)
        return Today(
            fromPrayer = prayer,
            fromDhikr = dhikr,
            fromQuran = quran,
            total = (prayer + dhikr + quran).coerceAtMost(ceiling),
            cap = ceiling,
        )
    }

    /**
     * How much of the pool the day has used: for every app with a minute cap, whatever it has
     * run past that cap. Each pair is (minutes used today, the app's own daily cap).
     */
    fun spent(usedAndCaps: List<Pair<Int, Int>>): Int =
        usedAndCaps.sumOf { (used, cap) -> (used - cap).coerceAtLeast(0) }

    /** What is left to draw on: never negative, even when a reset tasbīḥ took some back. */
    fun left(earned: Today, spent: Int): Int = (earned.total - spent).coerceAtLeast(0)

    /**
     * Whether moving on to the next ayah at [nowMillis] counts as reading, given when the last
     * one that counted was. The first of the day always counts.
     */
    fun countsAsRead(lastCountedAtMillis: Long?, nowMillis: Long): Boolean =
        lastCountedAtMillis == null || nowMillis - lastCountedAtMillis >= MIN_SECONDS_PER_AYAH * 1000L

    /**
     * How long a page of the mushaf has to be in front of the reader before its āyāt count as
     * read: the same pace as the home screen, an ayah's worth of seconds for each ayah that
     * begins on it. A page is counted once a day however often it is turned back to.
     */
    fun secondsToReadPage(ayatOnPage: Int): Int = MIN_SECONDS_PER_AYAH * ayatOnPage.coerceAtLeast(1)
}
