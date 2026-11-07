package dev.yusr.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

class MafatihDayTest {

    /** The Gregorian date on which a given Hijri day falls, so the tests read as intentions. */
    private fun gregorianFor(hijriDay: Int, hijriMonth: Int, year: Int = 1448): LocalDate {
        var date = LocalDate.of(2026, 1, 1)
        repeat(800) {
            val hijri = Hijri.of(date)
            if (hijri != null && hijri.day == hijriDay && hijri.month == hijriMonth && hijri.year == year) {
                return date
            }
            date = date.plusDays(1)
        }
        error("no Gregorian date found for $hijriDay/$hijriMonth/$year")
    }

    private fun ids(date: LocalDate) = MafatihDay.readings(date).map { it.entryId }

    @Test
    fun `every id the table hands out is a text in the shipped book`() {
        // The index is the one the APK ships; a renumbered import would break the mapping
        // silently in the app and loudly here.
        val index = File("src/main/assets/mafatih/index.json")
        assertTrue("index at ${index.absolutePath}", index.exists())
        val shipped = buildSet {
            val root = JSONObject(index.readText())
            val chapters = root.getJSONArray("chapters")
            for (c in 0 until chapters.length()) {
                val sections = chapters.getJSONObject(c).getJSONArray("sections")
                for (s in 0 until sections.length()) {
                    val items = sections.getJSONObject(s).getJSONArray("items")
                    for (i in 0 until items.length()) add(items.getJSONObject(i).getString("id"))
                }
            }
        }
        val missing = MafatihDay.ALL_IDS - shipped
        assertTrue("not in the book: $missing", missing.isEmpty())

        // And every one of them has words in it: an id pointing at an empty heading would open a
        // blank page.
        MafatihDay.ALL_IDS.forEach { id ->
            val file = File("src/main/assets/mafatih/$id.json")
            val blocks = JSONObject(file.readText()).getJSONArray("blocks")
            assertTrue("$id is empty", blocks.length() > 0)
        }
    }

    @Test
    fun `every day has at least a dua and a ziyara`() {
        var date = LocalDate.of(2026, 1, 1)
        repeat(400) {
            val readings = MafatihDay.readings(date)
            assertTrue(date.toString(), readings.size >= 2)
            date = date.plusDays(1)
        }
    }

    @Test
    fun `the weekday dua and ziyara follow the day`() {
        // 2026-01-03 is a Saturday.
        val saturday = LocalDate.of(2026, 1, 3)
        assertEquals(DayOfWeek.SATURDAY, saturday.dayOfWeek)
        assertTrue(ids(saturday).containsAll(listOf("m013", "m018")))
        // Sunday: ʿAlī and Fāṭima, both.
        assertTrue(ids(saturday.plusDays(1)).containsAll(listOf("m007", "m019", "m020")))
        assertTrue(ids(saturday.plusDays(2)).containsAll(listOf("m008", "m021", "m022")))
        assertTrue(ids(saturday.plusDays(3)).containsAll(listOf("m009", "m023")))
        assertTrue(ids(saturday.plusDays(4)).containsAll(listOf("m010", "m024")))
        assertTrue(ids(saturday.plusDays(5)).containsAll(listOf("m011", "m025")))
        assertTrue(ids(saturday.plusDays(6)).containsAll(listOf("m012", "m026")))
    }

    @Test
    fun `Thursday leads with Kumayl and Friday with Nudba`() {
        // In a month with no aʿmāl of its own — Rabīʿ al-Thānī — so the weekday is the whole
        // story. (In Rajab the month's aʿmāl rightly come first; that is the next test.)
        val start = gregorianFor(2, 4)
        val thursday = (0..6L).map { start.plusDays(it) }.first { it.dayOfWeek == DayOfWeek.THURSDAY }
        assertEquals("m028", ids(thursday).first())
        val friday = thursday.plusDays(1)
        assertEquals("m158", ids(friday).first())
        assertTrue(ids(friday).contains("m030"))
        // Neither on a Tuesday.
        val tuesday = (0..6L).map { start.plusDays(it) }.first { it.dayOfWeek == DayOfWeek.TUESDAY }
        assertTrue(ids(tuesday).none { it in setOf("m028", "m158", "m030") })
    }

    @Test
    fun `the date outranks the weekday`() {
        // ʿĀshūrāʾ: the ziyāra comes first whatever the weekday.
        assertEquals("m142", ids(gregorianFor(10, 1)).first())
        // The day after the ten, Muḥarram's aʿmāl stay but Ziyārat ʿĀshūrāʾ is no longer led with.
        val eleventh = ids(gregorianFor(11, 1))
        assertTrue(eleventh.contains("m095"))
        assertTrue(!eleventh.contains("m142"))
    }

    @Test
    fun `Ramadan names its nights`() {
        val nineteenth = ids(gregorianFor(19, 9))
        assertTrue(nineteenth.contains("m072"))
        assertTrue(nineteenth.contains("m139")) // Laylat al-Qadr ziyāra
        assertTrue(nineteenth.containsAll(listOf("m066", "m067", "m089")))

        val twentieth = ids(gregorianFor(20, 9))
        assertTrue(!twentieth.contains("m139"))
        assertTrue(twentieth.containsAll(listOf("m066", "m067", "m089")))

        assertTrue(ids(gregorianFor(1, 9)).containsAll(listOf("m070", "m071", "m099")))
    }

    @Test
    fun `the last night of Ramadan is read off the calendar, not off a thirty`() {
        // Whichever of 29 and 30 is the last day of Ramaḍān 1448 gets the farewell; the other
        // does not. Exactly one of them exists or is last.
        val lastDay = (29..30).mapNotNull { day ->
            runCatching { gregorianFor(day, 9) }.getOrNull()
        }.last()
        assertTrue(ids(lastDay).containsAll(listOf("m086", "m181")))
        assertTrue(!ids(lastDay.minusDays(1)).contains("m181"))
    }

    @Test
    fun `the named days of the year are on the right dates`() {
        assertEquals("m056", ids(gregorianFor(1, 7)).first())
        assertEquals("m059", ids(gregorianFor(27, 7)).first())
        assertEquals("m062", ids(gregorianFor(15, 8)).first())
        assertEquals("m090", ids(gregorianFor(1, 10)).first())
        assertEquals("m091", ids(gregorianFor(25, 11)).first())
        assertEquals("m141", ids(gregorianFor(9, 12)).first())
        assertEquals("m140", ids(gregorianFor(10, 12)).first())
        assertEquals("m092", ids(gregorianFor(18, 12)).first())
        assertEquals("m093", ids(gregorianFor(24, 12)).first())
        assertEquals("m143", ids(gregorianFor(20, 2)).first())
    }

    @Test
    fun `an ordinary day in an ordinary month is just the weekday`() {
        // 12 Rabīʿ al-Thānī: no month aʿmāl, so only the weekday pair.
        val readings = MafatihDay.readings(gregorianFor(12, 4))
        assertTrue(readings.size in 2..5)
        assertTrue(readings.none { it.entryId in setOf("m055", "m060", "m095", "m092", "m066") })
    }

    @Test
    fun `nothing is listed twice on one day`() {
        var date = LocalDate.of(2026, 1, 1)
        repeat(400) {
            val readings = ids(date)
            assertEquals(date.toString(), readings.size, readings.toSet().size)
            date = date.plusDays(1)
        }
    }
}
