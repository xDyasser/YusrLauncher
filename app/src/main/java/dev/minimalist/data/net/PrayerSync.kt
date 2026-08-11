package dev.minimalist.data.net

import dev.minimalist.data.db.PrayerDayEntity
import dev.minimalist.data.prayer.PrayerRepository
import dev.minimalist.data.settings.PrayerSettings
import dev.minimalist.domain.AsrMethod
import dev.minimalist.domain.CalculationMethod
import org.json.JSONObject
import java.time.LocalDate

/**
 * Refreshes the timetable from Aladhan, a month at a time.
 *
 * This is a convenience, not a dependency. The solver in [dev.minimalist.domain.PrayerTimes]
 * produces the same times to the minute; syncing exists so that a masjid following a slightly
 * different convention can be matched without anyone hand-tuning offsets. If it never succeeds,
 * nothing about the app stops working.
 */
class PrayerSync(private val prayerRepository: PrayerRepository) {

    /** Fetches this month and next, and returns how many days were stored. */
    suspend fun sync(settings: PrayerSettings, today: LocalDate = LocalDate.now()): Int {
        if (!settings.syncOverNetwork || !settings.configured) return 0

        val months = listOf(today, today.plusMonths(1))
        val days = months.flatMap { month -> fetchMonth(settings, month.year, month.monthValue) }
        if (days.isEmpty()) return 0

        prayerRepository.cacheSyncedDays(days)
        prayerRepository.pruneSyncedDays()
        return days.size
    }

    private suspend fun fetchMonth(settings: PrayerSettings, year: Int, month: Int): List<PrayerDayEntity> {
        val url = buildString {
            append("https://api.aladhan.com/v1/calendar/")
            append(year).append('/').append(month)
            append("?latitude=").append(settings.latitude)
            append("&longitude=").append(settings.longitude)
            append("&method=").append(methodId(settings.method))
            append("&school=").append(if (settings.asr == AsrMethod.HANAFI) 1 else 0)
        }

        val body = Http.getText(url) ?: return emptyList()
        val now = System.currentTimeMillis()

        return runCatching {
            val data = JSONObject(body).getJSONArray("data")
            (0 until data.length()).mapNotNull { index ->
                val day = data.getJSONObject(index)
                val timings = day.getJSONObject("timings")
                val gregorian = day.getJSONObject("date").getJSONObject("gregorian").getString("date")

                PrayerDayEntity(
                    date = isoDate(gregorian) ?: return@mapNotNull null,
                    fajr = minuteOfDay(timings.getString("Fajr")) ?: return@mapNotNull null,
                    sunrise = minuteOfDay(timings.getString("Sunrise")) ?: return@mapNotNull null,
                    dhuhr = minuteOfDay(timings.getString("Dhuhr")) ?: return@mapNotNull null,
                    asr = minuteOfDay(timings.getString("Asr")) ?: return@mapNotNull null,
                    maghrib = minuteOfDay(timings.getString("Maghrib")) ?: return@mapNotNull null,
                    isha = minuteOfDay(timings.getString("Isha")) ?: return@mapNotNull null,
                    latitude = settings.latitude,
                    longitude = settings.longitude,
                    fetchedAtMillis = now,
                )
            }
        }.getOrDefault(emptyList())
    }

    /** "15-06-2026" to "2026-06-15". */
    private fun isoDate(ddmmyyyy: String): String? {
        val parts = ddmmyyyy.split("-")
        if (parts.size != 3) return null
        return "${parts[2]}-${parts[1]}-${parts[0]}"
    }

    /** Timings arrive as "04:10 (+03)"; only the clock part matters. */
    private fun minuteOfDay(timing: String): Int? {
        val clock = timing.trim().substringBefore(' ')
        val parts = clock.split(":")
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    /** Aladhan's method numbering, which does not match ours and never will. */
    private fun methodId(method: CalculationMethod): Int = when (method) {
        CalculationMethod.JAFARI -> 0
        CalculationMethod.KARACHI -> 1
        CalculationMethod.ISNA -> 2
        CalculationMethod.MWL -> 3
        CalculationMethod.UMM_AL_QURA -> 4
        CalculationMethod.EGYPTIAN -> 5
        CalculationMethod.TEHRAN -> 7
    }
}
