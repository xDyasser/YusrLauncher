package dev.yusr.domain

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * The five daily prayers, plus sunrise — which is not a prayer but bounds the fajr window and is
 * worth showing.
 */
enum class Prayer {
    FAJR,
    SUNRISE,
    DHUHR,
    ASR,
    MAGHRIB,
    ISHA,
    ;

    val isPrayer: Boolean get() = this != SUNRISE
}

/**
 * Where the fajr and isha angles come from. These are the published parameters of each
 * authority; none of them is "correct" everywhere, which is why per-prayer offsets exist.
 */
enum class CalculationMethod(
    val fajrAngle: Double,
    val isha: IshaRule,
    val maghrib: MaghribRule,
) {
    /** Muslim World League. */
    MWL(18.0, IshaRule.Angle(17.0), MaghribRule.Sunset),

    /** Islamic Society of North America. */
    ISNA(15.0, IshaRule.Angle(15.0), MaghribRule.Sunset),

    /** Egyptian General Authority of Survey. */
    EGYPTIAN(19.5, IshaRule.Angle(17.5), MaghribRule.Sunset),

    /** Umm al-Qura, Makkah. Isha is a fixed interval after maghrib rather than an angle. */
    UMM_AL_QURA(18.5, IshaRule.MinutesAfterMaghrib(90), MaghribRule.Sunset),

    /** University of Islamic Sciences, Karachi. */
    KARACHI(18.0, IshaRule.Angle(18.0), MaghribRule.Sunset),

    /** Shia Ithna Ashari, Leva Institute, Qum. Maghrib is sunset plus the required delay. */
    JAFARI(16.0, IshaRule.Angle(14.0), MaghribRule.Angle(4.0)),

    /** Institute of Geophysics, University of Tehran. */
    TEHRAN(17.7, IshaRule.Angle(14.0), MaghribRule.Angle(4.5)),
    ;
}

sealed interface IshaRule {
    data class Angle(val degrees: Double) : IshaRule

    data class MinutesAfterMaghrib(val minutes: Int) : IshaRule
}

sealed interface MaghribRule {
    /** Maghrib at sunset, which is what the Sunni methods use. */
    data object Sunset : MaghribRule

    /** Maghrib when the sun is [degrees] below the horizon — the Jafari rule. */
    data class Angle(val degrees: Double) : MaghribRule
}

/** The shadow ratio that starts asr: one shadow-length for most, two for the Hanafi school. */
enum class AsrMethod(val shadowFactor: Double) {
    STANDARD(1.0),
    HANAFI(2.0),
}

/**
 * What to do at latitudes where the sun never reaches the fajr or isha angle, and the honest
 * answer is that there is no such moment that night.
 */
enum class HighLatitudeRule {
    /** Nothing sensible is available, so fall back to the angle-based rule rather than fail. */
    NONE,

    /** Split the night in half. */
    MIDDLE_OF_NIGHT,

    /** A seventh of the night for each of fajr and isha. */
    SEVENTH_OF_NIGHT,

    /** The night portion is proportional to the angle — the most widely used of the three. */
    ANGLE_BASED,
}

data class PrayerConfig(
    val method: CalculationMethod = CalculationMethod.MWL,
    val asr: AsrMethod = AsrMethod.STANDARD,
    /** Overrides the method's own maghrib rule when set; used by nobody but the settings screen. */
    val maghribOverride: MaghribRule? = null,
    val highLatitude: HighLatitudeRule = HighLatitudeRule.ANGLE_BASED,
    /** Manual nudges, in minutes, applied last so the times can be matched to a local masjid. */
    val offsetMinutes: Map<Prayer, Int> = emptyMap(),
) {
    val maghrib: MaghribRule get() = maghribOverride ?: method.maghrib
}

/** One day's times, as minutes past local midnight. */
data class PrayerTimetable(val minutes: Map<Prayer, Int>) {

    fun minuteOfDay(prayer: Prayer): Int = minutes.getValue(prayer)

    fun time(prayer: Prayer): LocalTime = LocalTime.of(minuteOfDay(prayer) / 60, minuteOfDay(prayer) % 60)

    /** The five that are actually prayed, in order. */
    val prayersInOrder: List<Pair<Prayer, Int>>
        get() = Prayer.entries.filter { it.isPrayer }.map { it to minuteOfDay(it) }
}

/**
 * The solar geometry behind the timetable. Pure arithmetic on a date and a place: no Android, no
 * network, no clock. Everything the enforcement layer needs can be computed on a plane.
 *
 * The algorithm is the standard one — Julian day, then the sun's declination and equation of
 * time, then the hour angle at which the sun sits at each prayer's defining altitude.
 */
object PrayerTimes {

    fun compute(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        utcOffsetMinutes: Int,
        config: PrayerConfig = PrayerConfig(),
    ): PrayerTimetable {
        val timeZoneHours = utcOffsetMinutes / 60.0
        // Julian day at local midnight, expressed at Greenwich so the sun's position lines up.
        val jDate = julianDay(date) - longitude / (15.0 * 24.0)

        // Initial guesses, in hours, refined once below. The sun moves slowly enough that one
        // pass is far more precision than a prayer timetable needs.
        var fajr = 5.0 / 24.0
        var sunrise = 6.0 / 24.0
        var dhuhr = 12.0 / 24.0
        var asr = 13.0 / 24.0
        var sunset = 18.0 / 24.0
        var isha = 18.0 / 24.0

        repeat(2) {
            fajr = sunAngleTime(jDate, latitude, config.method.fajrAngle, fajr, ccw = true)
            sunrise = sunAngleTime(jDate, latitude, RISE_SET_ANGLE, sunrise, ccw = true)
            dhuhr = midDay(jDate, dhuhr)
            asr = asrTime(jDate, latitude, config.asr.shadowFactor, asr)
            sunset = sunAngleTime(jDate, latitude, RISE_SET_ANGLE, sunset, ccw = false)
            isha = when (val rule = config.method.isha) {
                is IshaRule.Angle -> sunAngleTime(jDate, latitude, rule.degrees, isha, ccw = false)
                is IshaRule.MinutesAfterMaghrib -> sunset + rule.minutes / 60.0 / 24.0
            }
        }

        val maghrib = when (val rule = config.maghrib) {
            is MaghribRule.Sunset -> sunset
            is MaghribRule.Angle -> sunAngleTime(jDate, latitude, rule.degrees, sunset, ccw = false)
        }

        // A fixed-interval isha hangs off maghrib, so recompute it once maghrib is known.
        val ishaResolved = when (val rule = config.method.isha) {
            is IshaRule.MinutesAfterMaghrib -> maghrib + rule.minutes / 60.0 / 24.0
            is IshaRule.Angle -> isha
        }

        val hours = mutableMapOf(
            Prayer.FAJR to fajr * 24.0,
            Prayer.SUNRISE to sunrise * 24.0,
            Prayer.DHUHR to dhuhr * 24.0,
            Prayer.ASR to asr * 24.0,
            Prayer.MAGHRIB to maghrib * 24.0,
            Prayer.ISHA to ishaResolved * 24.0,
        )

        // Shift from solar time at this longitude to the clock on the wall.
        val shift = timeZoneHours - longitude / 15.0
        hours.keys.toList().forEach { hours[it] = hours.getValue(it) + shift }

        // True sunset, which is what bounds the night — not maghrib, which the Jafari rule puts
        // a few minutes later.
        applyHighLatitudeRules(hours, sunset * 24.0 + shift, config)

        // Dhuhr gets a small nudge past true solar noon: praying exactly at zenith is disliked.
        hours[Prayer.DHUHR] = hours.getValue(Prayer.DHUHR) + DHUHR_MINUTES / 60.0

        val minutes = hours.mapValues { (prayer, value) ->
            val offset = config.offsetMinutes[prayer] ?: 0
            normaliseMinuteOfDay((value * 60.0).roundToInt() + offset)
        }
        return PrayerTimetable(minutes)
    }

    /**
     * The next prayer after [minuteOfDay], and how many minutes away it is. After isha this rolls
     * to the following day's fajr, so the home screen never has nothing to show.
     */
    fun next(timetable: PrayerTimetable, minuteOfDay: Int): NextPrayer {
        val upcoming = timetable.prayersInOrder.firstOrNull { (_, at) -> at > minuteOfDay }
        return if (upcoming != null) {
            NextPrayer(upcoming.first, upcoming.second, upcoming.second - minuteOfDay, tomorrow = false)
        } else {
            val fajr = timetable.minuteOfDay(Prayer.FAJR)
            NextPrayer(Prayer.FAJR, fajr, MINUTES_PER_DAY - minuteOfDay + fajr, tomorrow = true)
        }
    }

    /** The prayer whose time has most recently passed, which is the one currently due. */
    fun current(timetable: PrayerTimetable, minuteOfDay: Int): Prayer =
        timetable.prayersInOrder.lastOrNull { (_, at) -> at <= minuteOfDay }?.first ?: Prayer.ISHA

    /**
     * The evening moment the sun sits [angle] degrees below the horizon, as minutes past local
     * midnight, or null on a day when it never gets that low.
     *
     * The timetable itself does not need this — every prayer in it is already anchored — but the
     * faḍīla windows do: the red twilight leaving the sky is what closes maghrib's preferred time,
     * and that is a depression angle like any other rather than a prayer.
     */
    fun eveningDepression(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        utcOffsetMinutes: Int,
        angle: Double,
    ): Int? {
        val jDate = julianDay(date) - longitude / (15.0 * 24.0)
        var guess = 18.0 / 24.0
        repeat(2) {
            guess = sunAngleTime(jDate, latitude, angle, guess, ccw = false)
            if (guess.isNaN()) return null
        }
        val shift = utcOffsetMinutes / 60.0 - longitude / 15.0
        val hours = guess * 24.0 + shift
        if (hours.isNaN()) return null
        return normaliseMinuteOfDay((hours * 60.0).roundToInt())
    }

    // ---- the arithmetic ------------------------------------------------------------------

    /**
     * Where a depression [angle] never occurs the raw solution is NaN. The high-latitude rules
     * replace it with a proportion of the night; [HighLatitudeRule.NONE] falls back to the
     * angle-based portion rather than leaving a hole in the timetable.
     */
    private fun applyHighLatitudeRules(
        hours: MutableMap<Prayer, Double>,
        sunset: Double,
        config: PrayerConfig,
    ) {
        val sunrise = hours.getValue(Prayer.SUNRISE)
        if (sunrise.isNaN() || sunset.isNaN()) {
            // Polar day or night: nothing angular survives, so split the 24 hours evenly and say
            // so through the times rather than crashing.
            hours[Prayer.FAJR] = 4.0
            hours[Prayer.SUNRISE] = 6.0
            hours[Prayer.ASR] = 15.0
            hours[Prayer.MAGHRIB] = 18.0
            hours[Prayer.ISHA] = 20.0
            return
        }

        val night = 24.0 - (sunset - sunrise)
        val rule = config.highLatitude

        hours[Prayer.FAJR] = adjustToNightPortion(
            time = hours.getValue(Prayer.FAJR),
            base = sunrise,
            portion = nightPortion(rule, config.method.fajrAngle, night),
            before = true,
            clampAlways = rule != HighLatitudeRule.NONE,
        )

        val ishaAngle = (config.method.isha as? IshaRule.Angle)?.degrees ?: 18.0
        hours[Prayer.ISHA] = adjustToNightPortion(
            time = hours.getValue(Prayer.ISHA),
            base = sunset,
            portion = nightPortion(rule, ishaAngle, night),
            before = false,
            clampAlways = rule != HighLatitudeRule.NONE,
        )

        if (hours.getValue(Prayer.MAGHRIB).isNaN()) hours[Prayer.MAGHRIB] = sunset
    }

    private fun nightPortion(rule: HighLatitudeRule, angle: Double, night: Double): Double =
        when (rule) {
            HighLatitudeRule.MIDDLE_OF_NIGHT -> night / 2.0
            HighLatitudeRule.SEVENTH_OF_NIGHT -> night / 7.0
            HighLatitudeRule.ANGLE_BASED, HighLatitudeRule.NONE -> night * angle / 60.0
        }

    /**
     * A time that never occurred becomes the night-portion limit. With
     * [HighLatitudeRule.NONE] a time that *did* occur is left exactly as computed; the other
     * rules also pull an implausibly early fajr or late isha back to the limit.
     */
    private fun adjustToNightPortion(
        time: Double,
        base: Double,
        portion: Double,
        before: Boolean,
        clampAlways: Boolean,
    ): Double {
        val limit = if (before) base - portion else base + portion
        if (time.isNaN()) return limit
        if (!clampAlways) return time
        return if (before) maxOf(time, limit) else minOf(time, limit)
    }

    /** Local solar noon, in days, corrected by the equation of time. */
    private fun midDay(jDate: Double, guess: Double): Double {
        val eqt = sunPosition(jDate + guess).equationOfTime
        return fixHour(12.0 - eqt) / 24.0
    }

    /**
     * The moment the sun sits [angle] degrees below the horizon, as a fraction of a day.
     * [ccw] picks the morning solution; otherwise the evening one.
     */
    private fun sunAngleTime(
        jDate: Double,
        latitude: Double,
        angle: Double,
        guess: Double,
        ccw: Boolean,
    ): Double {
        val decl = sunPosition(jDate + guess).declination
        val noon = midDay(jDate, guess)
        val numerator = -sinDeg(angle) - sinDeg(decl) * sinDeg(latitude)
        val denominator = cosDeg(decl) * cosDeg(latitude)
        val ratio = numerator / denominator
        // Outside [-1, 1] the sun never reaches that angle on this day, at this latitude.
        if (ratio > 1.0 || ratio < -1.0) return Double.NaN
        val hourAngle = acosDeg(ratio) / 15.0
        return noon + (if (ccw) -hourAngle else hourAngle) / 24.0
    }

    /** Asr begins when an object's shadow reaches [factor] times its length, plus noon shadow. */
    private fun asrTime(jDate: Double, latitude: Double, factor: Double, guess: Double): Double {
        val decl = sunPosition(jDate + guess).declination
        val angle = -atanDeg(1.0 / (factor + tanDeg(abs(latitude - decl))))
        return sunAngleTime(jDate, latitude, angle, guess, ccw = false)
    }

    private data class SunPosition(val declination: Double, val equationOfTime: Double)

    /** The sun's declination and the equation of time for a Julian day. */
    private fun sunPosition(jd: Double): SunPosition {
        val d = jd - 2451545.0
        val meanAnomaly = fixAngle(357.529 + 0.98560028 * d)
        val meanLongitude = fixAngle(280.459 + 0.98564736 * d)
        val eclipticLongitude = fixAngle(
            meanLongitude + 1.915 * sinDeg(meanAnomaly) + 0.020 * sinDeg(2 * meanAnomaly),
        )
        val obliquity = 23.439 - 0.00000036 * d

        val declination = asinDeg(sinDeg(obliquity) * sinDeg(eclipticLongitude))
        val rightAscension = fixHour(
            atan2Deg(cosDeg(obliquity) * sinDeg(eclipticLongitude), cosDeg(eclipticLongitude)) / 15.0,
        )
        return SunPosition(
            declination = declination,
            equationOfTime = meanLongitude / 15.0 - rightAscension,
        )
    }

    private fun julianDay(date: LocalDate): Double {
        var year = date.year
        var month = date.monthValue
        val day = date.dayOfMonth
        if (month <= 2) {
            year -= 1
            month += 12
        }
        val a = floor(year / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (year + 4716)) + floor(30.6001 * (month + 1)) + day + b - 1524.5
    }

    private fun normaliseMinuteOfDay(value: Int): Int = ((value % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

    private fun fixAngle(value: Double): Double = wrap(value, 360.0)

    private fun fixHour(value: Double): Double = wrap(value, 24.0)

    private fun wrap(value: Double, range: Double): Double {
        val result = value - range * floor(value / range)
        return if (result < 0) result + range else result
    }

    private const val RISE_SET_ANGLE = 0.833
    private const val DHUHR_MINUTES = 1.0
    private const val MINUTES_PER_DAY = 24 * 60

    private fun sinDeg(d: Double) = sin(Math.toRadians(d))
    private fun cosDeg(d: Double) = cos(Math.toRadians(d))
    private fun tanDeg(d: Double) = tan(Math.toRadians(d))
    private fun asinDeg(x: Double) = Math.toDegrees(asin(x))
    private fun acosDeg(x: Double) = Math.toDegrees(acos(x))
    private fun atanDeg(x: Double) = Math.toDegrees(atan(x))
    private fun atan2Deg(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
}

data class NextPrayer(
    val prayer: Prayer,
    val minuteOfDay: Int,
    val minutesAway: Int,
    val tomorrow: Boolean,
)
