package dev.yusr.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * What Mafātīḥ al-Jinān has for today.
 *
 * The book is a calendar as much as a collection: a duʿāʾ for each day of the week, an Imam whose
 * day each weekday is and a ziyāra for him, Kumayl on Thursday night and Nudba on Friday morning,
 * and then a bāb of the year — Rajab's aʿmāl, the nights of Ramaḍān one by one, Ghadīr, the
 * Mubāhala, ʿĀshūrāʾ. A reader who knows the book knows what to open; a reader who does not is
 * handed 194 texts and a table of contents. This is the table the book is organised by, written
 * down once, so that the hub and the home screen can say "tonight: Duʿāʾ Kumayl" and open it.
 *
 * It answers in entry ids and short English labels and nothing else. The ids are the file names
 * the import script wrote (`tools/import_mafatih.py`), which is the one thing here that could go
 * stale; the test checks every one of them against the shipped index. The labels are keys for
 * the interface's own translation table, which is where the Arabic lives.
 *
 * Order is importance: what is particular to the date comes before what is particular to the
 * weekday, and inside each the text comes before the commentary. The first item is what the home
 * screen prints when it has room for one line.
 *
 * The day is the civil day. The book counts nights as the evening *before* the date — Thursday
 * night is Wednesday evening to the clock on the wall, laylat al-qadr is the evening before the
 * 21st — and this does not yet follow it; a Thursday reading appears all Thursday, not from
 * Wednesday's maghrib. That is a known gap, and the one that rolling the Hijri day at maghrib
 * would close.
 */
object MafatihDay {

    /** One text to open, and what to call it. */
    data class Reading(val entryId: String, val label: String)

    fun readings(date: LocalDate, hijriOffsetDays: Int = 0): List<Reading> = buildList {
        val hijri = Hijri.of(date, hijriOffsetDays)
        if (hijri != null) {
            // Whether tomorrow is in another month, which is what "the last day" means on a
            // calendar whose months are 29 or 30 days by observation.
            val lastOfMonth = Hijri.of(date.plusDays(1), hijriOffsetDays)?.month != hijri.month
            addAll(byDate(hijri, lastOfMonth))
        }
        addAll(byWeekday(date.dayOfWeek))
    }

    // ---- the year --------------------------------------------------------------------------------

    private fun byDate(hijri: Hijri.HijriDate, lastOfMonth: Boolean): List<Reading> = buildList {
        val day = hijri.day
        when (hijri.month) {
            1 -> { // Muḥarram
                if (day <= 10) add(Reading("m142", "Ziyārat ʿĀshūrāʾ"))
                add(Reading("m095", "Muḥarram's aʿmāl"))
            }
            2 -> { // Ṣafar
                if (day == 20) add(Reading("m143", "Ziyārat al-Arbaʿīn"))
            }
            7 -> { // Rajab
                if (day == 1) add(Reading("m056", "First of Rajab"))
                if (day == 15) add(Reading("m058", "Mid-Rajab's aʿmāl"))
                if (day == 15) add(Reading("m137", "Ziyārat al-Ḥusayn · mid-Rajab"))
                if (day == 27) add(Reading("m059", "Laylat al-Mabʿath"))
                add(Reading("m055", "Rajab's aʿmāl"))
            }
            8 -> { // Shaʿbān
                if (day == 15) add(Reading("m062", "Mid-Shaʿbān's aʿmāl"))
                if (day == 15) add(Reading("m138", "Ziyārat al-Ḥusayn · mid-Shaʿbān"))
                add(Reading("m060", "Shaʿbān's aʿmāl"))
            }
            9 -> { // Ramaḍān
                if (day == 1) add(Reading("m070", "First night of Ramaḍān"))
                if (day == 1) add(Reading("m071", "First day of Ramaḍān"))
                RAMADAN_NIGHTS[day]?.let { add(Reading(it, "Tonight's duʿāʾ")) }
                if (day in QADR_NIGHTS) add(Reading("m139", "Ziyārat al-Ḥusayn · Laylat al-Qadr"))
                if (lastOfMonth) add(Reading("m086", "Last night of Ramaḍān"))
                if (lastOfMonth) add(Reading("m181", "Farewell to Ramaḍān"))
                add(Reading("m066", "Duʿāʾ al-Iftitāḥ · every night"))
                add(Reading("m067", "Duʿāʾ Abū Ḥamza · at saḥar"))
                add(Reading("m089", "The day's duʿāʾ"))
            }
            10 -> { // Shawwāl
                if (day == 1) add(Reading("m090", "ʿĪd al-Fiṭr's aʿmāl"))
                if (day == 1) add(Reading("m140", "Ziyārat al-Ḥusayn · the Eid"))
            }
            11 -> { // Dhū al-Qaʿda
                if (day == 25) add(Reading("m091", "Daḥw al-Arḍ"))
            }
            12 -> { // Dhū al-Ḥijja
                if (day == 9) add(Reading("m141", "Ziyārat al-Ḥusayn · ʿArafa"))
                if (day == 10) add(Reading("m140", "Ziyārat al-Ḥusayn · the Eid"))
                if (day == 24) add(Reading("m093", "Duʿāʾ of the Mubāhala"))
                if (day <= 10 || day == 18) add(Reading("m092", "Dhū al-Ḥijja's aʿmāl"))
                if (lastOfMonth) add(Reading("m094", "Last day of the year"))
            }
        }
        if (day == 1) add(Reading("m099", "First of the month"))
    }

    /** The nights of Ramaḍān the book gives their own duʿāʾ, keyed by the date they fall on. */
    private val RAMADAN_NIGHTS = mapOf(
        19 to "m072",
        21 to "m073",
        22 to "m074",
        23 to "m075",
        24 to "m076",
        25 to "m077",
        26 to "m078",
        27 to "m079",
        28 to "m080",
        29 to "m081",
        30 to "m082",
    )

    private val QADR_NIGHTS = setOf(19, 21, 23)

    // ---- the week --------------------------------------------------------------------------------

    private fun byWeekday(weekday: DayOfWeek): List<Reading> = buildList {
        when (weekday) {
            DayOfWeek.THURSDAY -> {
                add(Reading("m028", "Duʿāʾ Kumayl · Thursday night"))
                add(Reading("m015", "Thursday night's aʿmāl"))
            }
            DayOfWeek.FRIDAY -> {
                add(Reading("m158", "Duʿāʾ al-Nudba · Friday morning"))
                add(Reading("m030", "Duʿāʾ al-Simāt · Friday afternoon"))
                add(Reading("m016", "Friday's aʿmāl"))
            }
            else -> Unit
        }
        add(Reading(WEEKDAY_DUA.getValue(weekday), WEEKDAY_DUA_LABEL.getValue(weekday)))
        addAll(WEEKDAY_ZIYARA.getValue(weekday))
    }

    /** Bāb 1, faṣl 3: the seven duʿāʾ of the week, from the appendices to al-Ṣaḥīfa. */
    private val WEEKDAY_DUA = mapOf(
        DayOfWeek.SATURDAY to "m013",
        DayOfWeek.SUNDAY to "m007",
        DayOfWeek.MONDAY to "m008",
        DayOfWeek.TUESDAY to "m009",
        DayOfWeek.WEDNESDAY to "m010",
        DayOfWeek.THURSDAY to "m011",
        DayOfWeek.FRIDAY to "m012",
    )

    private val WEEKDAY_DUA_LABEL = mapOf(
        DayOfWeek.SATURDAY to "Duʿāʾ of Saturday",
        DayOfWeek.SUNDAY to "Duʿāʾ of Sunday",
        DayOfWeek.MONDAY to "Duʿāʾ of Monday",
        DayOfWeek.TUESDAY to "Duʿāʾ of Tuesday",
        DayOfWeek.WEDNESDAY to "Duʿāʾ of Wednesday",
        DayOfWeek.THURSDAY to "Duʿāʾ of Thursday",
        DayOfWeek.FRIDAY to "Duʿāʾ of Friday",
    )

    /**
     * Bāb 1, faṣl 5: whose day each day is, and the ziyāra for him. Saturday the Prophet, Sunday
     * ʿAlī and Fāṭima, Monday al-Ḥasan and al-Ḥusayn, Tuesday the next three Imams, Wednesday the
     * four after them, Thursday al-ʿAskarī, Friday Ṣāḥib al-Zamān.
     */
    private val WEEKDAY_ZIYARA = mapOf(
        DayOfWeek.SATURDAY to listOf(Reading("m018", "Ziyāra of the Prophet · his day")),
        DayOfWeek.SUNDAY to listOf(
            Reading("m019", "Ziyāra of Amīr al-Muʾminīn · his day"),
            Reading("m020", "Ziyāra of al-Zahrāʾ · her day"),
        ),
        DayOfWeek.MONDAY to listOf(
            Reading("m021", "Ziyāra of al-Ḥasan · his day"),
            Reading("m022", "Ziyāra of al-Ḥusayn · his day"),
        ),
        DayOfWeek.TUESDAY to listOf(Reading("m023", "Ziyāra of al-Sajjād, al-Bāqir and al-Ṣādiq · their day")),
        DayOfWeek.WEDNESDAY to listOf(Reading("m024", "Ziyāra of al-Kāẕim, al-Riḍā, al-Jawād and al-Hādī · their day")),
        DayOfWeek.THURSDAY to listOf(Reading("m025", "Ziyāra of al-ʿAskarī · his day")),
        DayOfWeek.FRIDAY to listOf(Reading("m026", "Ziyāra of Ṣāḥib al-Zamān · his day")),
    )

    /** Every id this table can hand out, for the test that checks them against the book. */
    internal val ALL_IDS: Set<String> = buildSet {
        (1..12).forEach { month ->
            (1..30).forEach { day ->
                byDate(Hijri.HijriDate(day, month, 1448), lastOfMonth = day == 30).forEach { add(it.entryId) }
            }
        }
        DayOfWeek.entries.forEach { weekday -> byWeekday(weekday).forEach { add(it.entryId) } }
    }

    /** Every label likewise, for the test that every one of them has its Arabic. */
    internal val ALL_LABELS: Set<String> = buildSet {
        (1..12).forEach { month ->
            (1..30).forEach { day ->
                byDate(Hijri.HijriDate(day, month, 1448), lastOfMonth = day == 30).forEach { add(it.label) }
            }
        }
        DayOfWeek.entries.forEach { weekday -> byWeekday(weekday).forEach { add(it.label) } }
    }
}
