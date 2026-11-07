package dev.yusr.domain

import java.time.LocalDate

/**
 * The days of the Hijri year that have a name.
 *
 * Like the fasts, these are calendar facts: an app that already knows today's Hijri date knows
 * that it is the thirteenth of Rajab, and so knows it is the birth of Amīr al-Muʾminīn, without
 * being told. What it has to be told is *which* calendar, because the two branches keep very
 * different ones — the Shīʿī year is organised around the births and martyrdoms of the Fourteen,
 * and almost none of those are marked in a Sunnī almanac — so the list is chosen by the madhab,
 * the same as the fasts and the book of supplications.
 *
 * Where the narrations differ on a date, the date is the one the most widely printed Shīʿī
 * calendars use (the convention of the official Iranian calendar, which most of the others
 * follow). Two places where the difference is too well known to pick a side are both kept: the
 * martyrdom of al-Zahrāʾ is marked at the end of each of the two Fāṭimiyya, the seventy-five-day
 * narration (13 Jumādā al-Ūlā) and the ninety-five-day one (3 Jumādā al-Ākhira). A day that is
 * the last of its month (Imam al-Riḍā at the end of Ṣafar, Imam al-Jawād at the end of Dhū
 * al-Qaʿda) is read off the calendar, not off a 30, because a month is 29 or 30 days by sighting.
 *
 * Each occasion carries a [Tone], because a launcher that announced a martyrdom in the voice it
 * uses for an Eid would be worse than one that said nothing; and where Mafātīḥ al-Jinān has the
 * text for the day, the id of it, so the line on the hub opens straight onto the ziyāra.
 *
 * The day is the civil day, like everything else built on [Hijri] for now: the eve of an
 * occasion is not yet marked from the maghrib before it.
 */
object Occasions {

    enum class Tone {
        /** A birth, an Eid, Ghadīr: the gold accent. */
        JOY,

        /** A martyrdom or a death: never gold. */
        GRIEF,

        /** A night of worship, a new year: neither. */
        OBSERVANCE,
    }

    data class Occasion(
        val month: Int,
        /** The day of the month, or [LAST_DAY] for whichever of the 29th and 30th ends it. */
        val day: Int,
        /** English, keyed into the interface's translation table. */
        val label: String,
        val tone: Tone,
        /** The text in Mafātīḥ al-Jinān for the day, when there is one. */
        val entryId: String? = null,
    )

    data class Dated(val date: LocalDate, val occasion: Occasion)

    const val LAST_DAY: Int = -1

    /** What [date] is, by the calendar of [branch]. Usually nothing; sometimes two things. */
    fun on(
        date: LocalDate,
        hijriOffsetDays: Int = 0,
        branch: Madhab.Branch = Madhab.Branch.SUNNI,
    ): List<Occasion> {
        val hijri = Hijri.of(date, hijriOffsetDays) ?: return emptyList()
        val last = Hijri.of(date.plusDays(1), hijriOffsetDays)?.month != hijri.month
        return calendar(branch).filter { occasion ->
            occasion.month == hijri.month &&
                (occasion.day == hijri.day || (occasion.day == LAST_DAY && last))
        }
    }

    /** Every occasion in the [days] beginning at [from], in date order. */
    fun upcoming(
        from: LocalDate,
        days: Int,
        hijriOffsetDays: Int = 0,
        branch: Madhab.Branch = Madhab.Branch.SUNNI,
    ): List<Dated> = (0 until days).flatMap { offset ->
        val date = from.plusDays(offset.toLong())
        on(date, hijriOffsetDays, branch).map { Dated(date, it) }
    }

    fun calendar(branch: Madhab.Branch): List<Occasion> = when (branch) {
        Madhab.Branch.SHIA -> SHIA
        Madhab.Branch.SUNNI -> SUNNI
    }

    private val SHIA = listOf(
        // Muḥarram
        Occasion(1, 1, "The Hijri new year", Tone.OBSERVANCE),
        Occasion(1, 10, "ʿĀshūrāʾ · martyrdom of Imam al-Ḥusayn", Tone.GRIEF, "m142"),
        // 12, 18 and 25 Muḥarram are all narrated; 25 is the one the calendars print.
        Occasion(1, 25, "Martyrdom of Imam al-Sajjād", Tone.GRIEF),
        // Ṣafar
        Occasion(2, 7, "Birth of Imam al-Kāẓim", Tone.JOY),
        Occasion(2, 20, "Arbaʿīn of Imam al-Ḥusayn", Tone.GRIEF, "m143"),
        Occasion(2, 28, "Passing of the Prophet · martyrdom of Imam al-Ḥasan", Tone.GRIEF, "m104"),
        Occasion(2, LAST_DAY, "Martyrdom of Imam al-Riḍā", Tone.GRIEF, "m150"),
        // Rabīʿ al-Awwal
        Occasion(3, 8, "Martyrdom of Imam al-ʿAskarī", Tone.GRIEF, "m153"),
        Occasion(3, 9, "Imamate of Imam al-Mahdī begins", Tone.JOY),
        Occasion(3, 17, "Birth of the Prophet and of Imam al-Ṣādiq", Tone.JOY),
        // Rabīʿ al-Thānī
        Occasion(4, 8, "Birth of Imam al-ʿAskarī", Tone.JOY),
        // Jumādā al-Ūlā
        Occasion(5, 5, "Birth of Sayyida Zaynab", Tone.JOY),
        Occasion(5, 13, "Martyrdom of al-Zahrāʾ · the first Fāṭimiyya", Tone.GRIEF, "m020"),
        // Jumādā al-Ākhira
        Occasion(6, 3, "Martyrdom of al-Zahrāʾ · the second Fāṭimiyya", Tone.GRIEF, "m020"),
        Occasion(6, 20, "Birth of al-Zahrāʾ", Tone.JOY, "m020"),
        // Rajab
        Occasion(7, 1, "Birth of Imam al-Bāqir", Tone.JOY),
        Occasion(7, 3, "Martyrdom of Imam al-Hādī", Tone.GRIEF, "m152"),
        Occasion(7, 10, "Birth of Imam al-Jawād", Tone.JOY),
        Occasion(7, 13, "Birth of Amīr al-Muʾminīn", Tone.JOY, "m113"),
        Occasion(7, 15, "Passing of Sayyida Zaynab", Tone.GRIEF),
        Occasion(7, 25, "Martyrdom of Imam al-Kāẓim", Tone.GRIEF, "m144"),
        Occasion(7, 27, "The Mabʿath", Tone.JOY, "m059"),
        // Shaʿbān
        Occasion(8, 3, "Birth of Imam al-Ḥusayn", Tone.JOY, "m061"),
        Occasion(8, 4, "Birth of al-ʿAbbās", Tone.JOY, "m135"),
        Occasion(8, 5, "Birth of Imam al-Sajjād", Tone.JOY),
        Occasion(8, 11, "Birth of ʿAlī al-Akbar", Tone.JOY),
        Occasion(8, 15, "Birth of Imam al-Mahdī", Tone.JOY, "m062"),
        // Ramaḍān
        Occasion(9, 1, "Ramaḍān begins", Tone.OBSERVANCE, "m070"),
        Occasion(9, 10, "Passing of Khadīja", Tone.GRIEF),
        Occasion(9, 15, "Birth of Imam al-Ḥasan", Tone.JOY),
        Occasion(9, 19, "Laylat al-Qadr · Amīr al-Muʾminīn struck", Tone.GRIEF, "m072"),
        Occasion(9, 21, "Laylat al-Qadr · martyrdom of Amīr al-Muʾminīn", Tone.GRIEF, "m073"),
        Occasion(9, 23, "Laylat al-Qadr", Tone.OBSERVANCE, "m084"),
        // Shawwāl
        Occasion(10, 1, "ʿĪd al-Fiṭr", Tone.JOY, "m090"),
        Occasion(10, 25, "Martyrdom of Imam al-Ṣādiq", Tone.GRIEF),
        // Dhū al-Qaʿda
        Occasion(11, 1, "Birth of Sayyida Maʿṣūma", Tone.JOY, "m177"),
        Occasion(11, 11, "Birth of Imam al-Riḍā", Tone.JOY, "m150"),
        Occasion(11, 25, "Daḥw al-Arḍ", Tone.OBSERVANCE, "m091"),
        Occasion(11, LAST_DAY, "Martyrdom of Imam al-Jawād", Tone.GRIEF, "m144"),
        // Dhū al-Ḥijja
        Occasion(12, 7, "Martyrdom of Imam al-Bāqir", Tone.GRIEF),
        Occasion(12, 9, "ʿArafa · martyrdom of Muslim b. ʿAqīl", Tone.OBSERVANCE, "m141"),
        Occasion(12, 10, "ʿĪd al-Aḍḥā", Tone.JOY, "m140"),
        Occasion(12, 15, "Birth of Imam al-Hādī", Tone.JOY),
        Occasion(12, 18, "ʿĪd al-Ghadīr", Tone.JOY, "m092"),
        Occasion(12, 24, "The Mubāhala", Tone.JOY, "m093"),
    )

    private val SUNNI = listOf(
        Occasion(1, 1, "The Hijri new year", Tone.OBSERVANCE),
        Occasion(1, 10, "ʿĀshūrāʾ", Tone.OBSERVANCE),
        Occasion(3, 12, "Mawlid al-Nabī", Tone.JOY),
        Occasion(7, 27, "Al-Isrāʾ wa al-Miʿrāj", Tone.OBSERVANCE),
        Occasion(8, 15, "Mid-Shaʿbān", Tone.OBSERVANCE),
        Occasion(9, 1, "Ramaḍān begins", Tone.OBSERVANCE),
        Occasion(9, 27, "Laylat al-Qadr, by the common reckoning", Tone.OBSERVANCE),
        Occasion(10, 1, "ʿĪd al-Fiṭr", Tone.JOY),
        Occasion(12, 9, "The day of ʿArafah", Tone.OBSERVANCE),
        Occasion(12, 10, "ʿĪd al-Aḍḥā", Tone.JOY),
    )
}
