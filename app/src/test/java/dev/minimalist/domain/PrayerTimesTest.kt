package dev.minimalist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

/**
 * The solver is the one part of this app that can be wrong quietly — a timetable that is ten
 * minutes out still looks like a timetable. These tests pin it from two directions: first
 * principles at the equator, and published timetables for real cities.
 */
class PrayerTimesTest {

    private fun at(hour: Int, minute: Int) = hour * 60 + minute

    private fun assertNear(expected: Int, actual: Int, tolerance: Int = 1, what: String = "") {
        assertTrue(
            "$what expected ${format(expected)} but was ${format(actual)}",
            abs(expected - actual) <= tolerance,
        )
    }

    private fun format(minuteOfDay: Int) = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    // ---- first principles ----------------------------------------------------------------

    /**
     * At the equator on the March equinox, with the clock set to the prime meridian, the sun's
     * behaviour is known without any timetable: solar noon sits about seven and a half minutes
     * late because of the equation of time, and sunrise and sunset are six hours either side.
     */
    @Test
    fun `the equinox at the equator matches the equation of time`() {
        val times = PrayerTimes.compute(
            date = LocalDate.of(2026, 3, 20),
            latitude = 0.0,
            longitude = 0.0,
            utcOffsetMinutes = 0,
            config = PrayerConfig(method = CalculationMethod.MWL),
        )

        assertNear(at(12, 8), times.minuteOfDay(Prayer.DHUHR), 2, "dhuhr")
        assertNear(at(6, 4), times.minuteOfDay(Prayer.SUNRISE), 2, "sunrise")
        assertNear(at(18, 11), times.minuteOfDay(Prayer.MAGHRIB), 2, "maghrib")
    }

    @Test
    fun `sunrise and sunset sit either side of solar noon`() {
        val times = PrayerTimes.compute(
            LocalDate.of(2026, 6, 15), 21.4225, 39.8262, 180,
            PrayerConfig(method = CalculationMethod.UMM_AL_QURA),
        )
        val noon = times.minuteOfDay(Prayer.DHUHR) - 1 // undo the nudge past the zenith
        val morning = noon - times.minuteOfDay(Prayer.SUNRISE)
        val evening = times.minuteOfDay(Prayer.MAGHRIB) - noon
        assertNear(morning, evening, 1, "half-day either side of noon")
    }

    // ---- real cities ---------------------------------------------------------------------

    @Test
    fun `makkah in june matches the umm al-qura timetable`() {
        val times = PrayerTimes.compute(
            LocalDate.of(2026, 6, 15), 21.4225, 39.8262, 180,
            PrayerConfig(method = CalculationMethod.UMM_AL_QURA),
        )

        assertNear(at(4, 10), times.minuteOfDay(Prayer.FAJR), 1, "fajr")
        assertNear(at(5, 38), times.minuteOfDay(Prayer.SUNRISE), 1, "sunrise")
        assertNear(at(12, 22), times.minuteOfDay(Prayer.DHUHR), 1, "dhuhr")
        assertNear(at(15, 41), times.minuteOfDay(Prayer.ASR), 1, "asr")
        assertNear(at(19, 4), times.minuteOfDay(Prayer.MAGHRIB), 1, "maghrib")
        assertNear(at(20, 34), times.minuteOfDay(Prayer.ISHA), 1, "isha")
    }

    @Test
    fun `makkah in december is a shorter day than june`() {
        val december = PrayerTimes.compute(
            LocalDate.of(2026, 12, 15), 21.4225, 39.8262, 180,
            PrayerConfig(method = CalculationMethod.UMM_AL_QURA),
        )

        assertNear(at(5, 29), december.minuteOfDay(Prayer.FAJR), 1, "fajr")
        assertNear(at(6, 51), december.minuteOfDay(Prayer.SUNRISE), 1, "sunrise")
        assertNear(at(17, 41), december.minuteOfDay(Prayer.MAGHRIB), 1, "maghrib")
    }

    @Test
    fun `cairo matches the egyptian timetable`() {
        val times = PrayerTimes.compute(
            LocalDate.of(2026, 3, 20), 30.0444, 31.2357, 120,
            PrayerConfig(method = CalculationMethod.EGYPTIAN),
        )

        assertNear(at(4, 32), times.minuteOfDay(Prayer.FAJR), 1, "fajr")
        assertNear(at(12, 4), times.minuteOfDay(Prayer.DHUHR), 1, "dhuhr")
        assertNear(at(18, 6), times.minuteOfDay(Prayer.MAGHRIB), 1, "maghrib")
        assertNear(at(19, 24), times.minuteOfDay(Prayer.ISHA), 1, "isha")
    }

    @Test
    fun `qum matches the jafari timetable`() {
        val times = PrayerTimes.compute(
            LocalDate.of(2026, 6, 15), 34.6416, 50.8746, 270,
            PrayerConfig(method = CalculationMethod.JAFARI),
        )

        assertNear(at(4, 21), times.minuteOfDay(Prayer.FAJR), 1, "fajr")
        assertNear(at(13, 8), times.minuteOfDay(Prayer.DHUHR), 1, "dhuhr")
        assertNear(at(20, 39), times.minuteOfDay(Prayer.MAGHRIB), 1, "maghrib")
        assertNear(at(21, 40), times.minuteOfDay(Prayer.ISHA), 1, "isha")
    }

    // ---- the rules that distinguish the schools ------------------------------------------

    @Test
    fun `jafari maghrib falls after sunset, not at it`() {
        val date = LocalDate.of(2026, 6, 15)
        val jafari = PrayerTimes.compute(date, 34.6416, 50.8746, 270, PrayerConfig(CalculationMethod.JAFARI))
        val sunni = PrayerTimes.compute(date, 34.6416, 50.8746, 270, PrayerConfig(CalculationMethod.MWL))

        val delay = jafari.minuteOfDay(Prayer.MAGHRIB) - sunni.minuteOfDay(Prayer.MAGHRIB)
        assertTrue("jafari maghrib should trail sunset, was $delay min", delay in 10..30)
    }

    @Test
    fun `hanafi asr falls later than the standard one`() {
        val date = LocalDate.of(2026, 6, 15)
        val standard = PrayerTimes.compute(
            date, 24.8607, 67.0011, 300,
            PrayerConfig(CalculationMethod.KARACHI, asr = AsrMethod.STANDARD),
        )
        val hanafi = PrayerTimes.compute(
            date, 24.8607, 67.0011, 300,
            PrayerConfig(CalculationMethod.KARACHI, asr = AsrMethod.HANAFI),
        )

        assertNear(at(15, 54), standard.minuteOfDay(Prayer.ASR), 1, "standard asr")
        assertNear(at(17, 15), hanafi.minuteOfDay(Prayer.ASR), 1, "hanafi asr")
        // Everything else about the day is identical.
        assertEquals(standard.minuteOfDay(Prayer.DHUHR), hanafi.minuteOfDay(Prayer.DHUHR))
    }

    @Test
    fun `umm al-qura isha is exactly ninety minutes after maghrib`() {
        val times = PrayerTimes.compute(
            LocalDate.of(2026, 6, 15), 21.4225, 39.8262, 180,
            PrayerConfig(method = CalculationMethod.UMM_AL_QURA),
        )
        assertEquals(90, times.minuteOfDay(Prayer.ISHA) - times.minuteOfDay(Prayer.MAGHRIB))
    }

    @Test
    fun `a wider fajr angle means an earlier fajr`() {
        val date = LocalDate.of(2026, 6, 15)
        val place = Triple(30.0444, 31.2357, 120)
        val isna = PrayerTimes.compute(date, place.first, place.second, place.third, PrayerConfig(CalculationMethod.ISNA))
        val egyptian = PrayerTimes.compute(date, place.first, place.second, place.third, PrayerConfig(CalculationMethod.EGYPTIAN))

        // ISNA uses 15°, Egypt 19.5° — the wider angle is reached earlier in the morning.
        assertTrue(egyptian.minuteOfDay(Prayer.FAJR) < isna.minuteOfDay(Prayer.FAJR))
    }

    @Test
    fun `the prayers come out in order`() {
        val times = PrayerTimes.compute(
            LocalDate.of(2026, 6, 15), 21.4225, 39.8262, 180,
            PrayerConfig(method = CalculationMethod.UMM_AL_QURA),
        )
        val order = listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
        val minutes = order.map { times.minuteOfDay(it) }
        assertEquals(minutes.sorted(), minutes)
    }

    // ---- offsets and high latitudes ------------------------------------------------------

    @Test
    fun `manual offsets move only the prayer they name`() {
        val date = LocalDate.of(2026, 6, 15)
        val plain = PrayerTimes.compute(date, 21.4225, 39.8262, 180, PrayerConfig(CalculationMethod.UMM_AL_QURA))
        val nudged = PrayerTimes.compute(
            date, 21.4225, 39.8262, 180,
            PrayerConfig(CalculationMethod.UMM_AL_QURA, offsetMinutes = mapOf(Prayer.FAJR to -3)),
        )

        assertEquals(plain.minuteOfDay(Prayer.FAJR) - 3, nudged.minuteOfDay(Prayer.FAJR))
        assertEquals(plain.minuteOfDay(Prayer.ASR), nudged.minuteOfDay(Prayer.ASR))
    }

    /**
     * In a London June the sun never gets 18° below the horizon, so fajr and isha have no
     * astronomical answer at all. The timetable still has to produce times a person can pray by.
     */
    @Test
    fun `london midsummer still yields a fajr and an isha`() {
        val times = PrayerTimes.compute(
            LocalDate.of(2026, 6, 21), 51.5074, -0.1278, 60,
            PrayerConfig(method = CalculationMethod.MWL, highLatitude = HighLatitudeRule.ANGLE_BASED),
        )

        val fajr = times.minuteOfDay(Prayer.FAJR)
        val sunrise = times.minuteOfDay(Prayer.SUNRISE)
        val maghrib = times.minuteOfDay(Prayer.MAGHRIB)
        val isha = times.minuteOfDay(Prayer.ISHA)

        assertTrue("fajr $fajr should precede sunrise $sunrise", fajr < sunrise)
        assertTrue("isha $isha should follow maghrib $maghrib", isha > maghrib)
    }

    @Test
    fun `the seventh-of-night rule puts fajr closer to sunrise than the middle-of-night one`() {
        val date = LocalDate.of(2026, 6, 21)
        fun fajrUnder(rule: HighLatitudeRule) = PrayerTimes.compute(
            date, 51.5074, -0.1278, 60,
            PrayerConfig(method = CalculationMethod.MWL, highLatitude = rule),
        ).minuteOfDay(Prayer.FAJR)

        assertTrue(fajrUnder(HighLatitudeRule.SEVENTH_OF_NIGHT) > fajrUnder(HighLatitudeRule.MIDDLE_OF_NIGHT))
    }
}
