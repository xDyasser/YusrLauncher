package dev.yusr.ui.hub

import dev.yusr.domain.FastingCalendar
import dev.yusr.domain.Hijri
import dev.yusr.ui.isArabic
import dev.yusr.ui.t

/**
 * What a day on the fasting calendar is called, in the language being read.
 *
 * The calendar itself answers in a [FastingCalendar.Kind] and nothing else, because it is domain
 * code and does not know what language the phone is in. The words are decided here, once, so the
 * tile on the hub and the strip on the fasting screen cannot drift apart.
 *
 * An ordinary day is an empty string: there is nothing to say about it, and a screen that prints
 * "nothing" under every Tuesday is a screen full of noise.
 */
fun fastingLabel(day: FastingCalendar.Day): String = when (day.kind) {
    FastingCalendar.Kind.RAMADAN -> t("Ramaḍān")
    FastingCalendar.Kind.ASHURA -> t("ʿĀshūrāʾ")
    FastingCalendar.Kind.ASHURA_IMSAK -> t("ʿĀshūrāʾ · imsāk till ʿaṣr, not a fast")
    FastingCalendar.Kind.ARAFAH -> t("ʿArafah")
    FastingCalendar.Kind.MABATH -> t("Mabʿath · 27 Rajab")
    FastingCalendar.Kind.MID_SHABAN -> t("15 Shaʿbān")
    FastingCalendar.Kind.DAHW_AL_ARD -> t("Daḥw al-Arḍ · 25 Dhū al-Qaʿda")
    FastingCalendar.Kind.GHADIR -> t("Ghadīr · 18 Dhū al-Ḥijja")
    FastingCalendar.Kind.MUBAHALA -> t("Mubāhala · 24 Dhū al-Ḥijja")
    FastingCalendar.Kind.AYYAM_AL_BID -> {
        // The one label here that is not a fixed phrase, so the day and the month go in from
        // outside — in the digits and the month name of the language being read.
        val hijri = day.hijri
        if (hijri == null) {
            t("White days")
        } else {
            val dayOfMonth = if (isArabic()) Hijri.arabicDigits(hijri.day) else hijri.day.toString()
            val month = if (isArabic()) hijri.monthNameArabic else hijri.monthNameTransliterated
            t("White days · %s %s", dayOfMonth, month)
        }
    }
    FastingCalendar.Kind.MONDAY -> t("Monday")
    FastingCalendar.Kind.THURSDAY -> t("Thursday")
    FastingCalendar.Kind.FIRST_THURSDAY -> t("First Thursday of the month")
    FastingCalendar.Kind.LAST_THURSDAY -> t("Last Thursday of the month")
    FastingCalendar.Kind.MIDDLE_WEDNESDAY -> t("Wednesday after the tenth")
    FastingCalendar.Kind.EID_AL_FITR -> t("ʿĪd al-Fiṭr · no fasting")
    FastingCalendar.Kind.EID_AL_ADHA -> t("ʿĪd al-Aḍḥā · no fasting")
    FastingCalendar.Kind.TASHRIQ -> t("Days of tashrīq · no fasting")
    FastingCalendar.Kind.NONE -> ""
}
