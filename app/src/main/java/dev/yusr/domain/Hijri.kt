package dev.yusr.domain

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * The Islamic date, for the line under the clock.
 *
 * The underlying calendar is Umm al-Qura, which is calculated rather than sighted and so can sit
 * a day either side of the local announcement. That is what [offsetDays] is for: it is a
 * correction, not a preference, and it applies everywhere the Hijri date is shown.
 */
object Hijri {

    private val MONTHS = listOf(
        "muharram",
        "safar",
        "rabi al-awwal",
        "rabi al-thani",
        "jumada al-ula",
        "jumada al-akhirah",
        "rajab",
        "shaban",
        "ramadan",
        "shawwal",
        "dhu al-qadah",
        "dhu al-hijjah",
    )

    /** The same months set properly, for the places the date is read rather than glanced at. */
    private val MONTHS_TRANSLITERATED = listOf(
        "Muḥarram",
        "Ṣafar",
        "Rabīʿ al-Awwal",
        "Rabīʿ al-Thānī",
        "Jumādā al-Ūlā",
        "Jumādā al-Ākhirah",
        "Rajab",
        "Shaʿbān",
        "Ramaḍān",
        "Shawwāl",
        "Dhū al-Qaʿdah",
        "Dhū al-Ḥijjah",
    )

    private val MONTHS_ARABIC = listOf(
        "محرم",
        "صفر",
        "ربيع الأول",
        "ربيع الثاني",
        "جمادى الأولى",
        "جمادى الآخرة",
        "رجب",
        "شعبان",
        "رمضان",
        "شوال",
        "ذو القعدة",
        "ذو الحجة",
    )

    data class HijriDate(val day: Int, val month: Int, val year: Int) {
        val monthName: String get() = MONTHS[month - 1]

        /** "12 ramadan 1447" — lowercase, like everything else on the home screen. */
        override fun toString(): String = "$day $monthName $year"

        /** "12 Ramaḍān 1447" — the form the home screen sets under the clock. */
        val display: String get() = "$day ${MONTHS_TRANSLITERATED[month - 1]} $year"

        /** "Rajab", "رجب" — the month on its own, for a label that names one. */
        val monthNameTransliterated: String get() = MONTHS_TRANSLITERATED[month - 1]

        val monthNameArabic: String get() = MONTHS_ARABIC[month - 1]

        /** "١٢ رمضان ١٤٤٧" — the same date for a screen already set in Arabic. */
        val arabic: String
            get() = "${arabicDigits(day)} ${MONTHS_ARABIC[month - 1]} ${arabicDigits(year)}"
    }

    /** Western digits to Arabic-Indic ones. A date half in each script is neither. */
    fun arabicDigits(value: Int): String =
        value.toString().map { if (it in '0'..'9') ARABIC_DIGITS[it - '0'] else it }.joinToString("")

    private val ARABIC_DIGITS = listOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    /**
     * The Hijri date for a Gregorian one, or null outside the range the calendar covers
     * (it begins in 1300 AH). A missing date is not worth a crash on the home screen.
     */
    fun of(date: LocalDate, offsetDays: Int = 0): HijriDate? = runCatching {
        val hijri = HijrahDate.from(date.plusDays(offsetDays.toLong()))
        HijriDate(
            day = hijri.get(ChronoField.DAY_OF_MONTH),
            month = hijri.get(ChronoField.MONTH_OF_YEAR),
            year = hijri.get(ChronoField.YEAR),
        )
    }.getOrNull()

    fun format(date: LocalDate, offsetDays: Int = 0): String? = of(date, offsetDays)?.toString()

    fun isRamadan(date: LocalDate, offsetDays: Int = 0): Boolean = of(date, offsetDays)?.month == 9
}
