package dev.yusr.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class OccasionsTest {

    private val shia = Madhab.Branch.SHIA
    private val sunni = Madhab.Branch.SUNNI

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

    private fun labels(date: LocalDate, branch: Madhab.Branch) = Occasions.on(date, 0, branch).map { it.label }

    @Test
    fun `the thirteenth of Rajab is the birth of Amir al-Muminin for a Shia and nothing for a Sunni`() {
        val date = gregorianFor(13, 7)
        assertEquals(listOf("Birth of Amīr al-Muʾminīn"), labels(date, shia))
        assertTrue(labels(date, sunni).isEmpty())
    }

    @Test
    fun `Ashura is grief for a Shia and an observance for a Sunni`() {
        val date = gregorianFor(10, 1)
        assertEquals(Occasions.Tone.GRIEF, Occasions.on(date, 0, shia).single().tone)
        assertEquals(Occasions.Tone.OBSERVANCE, Occasions.on(date, 0, sunni).single().tone)
    }

    @Test
    fun `both Fatimiyya are kept`() {
        assertEquals(Occasions.Tone.GRIEF, Occasions.on(gregorianFor(13, 5), 0, shia).single().tone)
        assertEquals(Occasions.Tone.GRIEF, Occasions.on(gregorianFor(3, 6), 0, shia).single().tone)
    }

    @Test
    fun `the Mawlid is the seventeenth for a Shia and the twelfth for a Sunni`() {
        assertTrue(labels(gregorianFor(17, 3), shia).single().startsWith("Birth of the Prophet"))
        assertTrue(labels(gregorianFor(12, 3), shia).isEmpty())
        assertEquals(listOf("Mawlid al-Nabī"), labels(gregorianFor(12, 3), sunni))
    }

    @Test
    fun `a last-day occasion lands on whichever day ends the month`() {
        // Ṣafar 1448: find its last day by walking to the first of Rabīʿ al-Awwal.
        val firstOfRabi = gregorianFor(1, 3)
        val lastOfSafar = firstOfRabi.minusDays(1)
        assertTrue(labels(lastOfSafar, shia).contains("Martyrdom of Imam al-Riḍā"))
        assertTrue(labels(lastOfSafar.minusDays(1), shia).none { it == "Martyrdom of Imam al-Riḍā" })
        // And it is never on the first of the next month.
        assertTrue(labels(firstOfRabi, shia).none { it == "Martyrdom of Imam al-Riḍā" })
    }

    @Test
    fun `every month-and-day in the calendars is a real Hijri date`() {
        listOf(shia, sunni).forEach { branch ->
            Occasions.calendar(branch).forEach { occasion ->
                assertTrue(occasion.label, occasion.month in 1..12)
                assertTrue(occasion.label, occasion.day == Occasions.LAST_DAY || occasion.day in 1..30)
            }
        }
    }

    @Test
    fun `every occasion in the Shia calendar comes round in a year`() {
        // A year of days from the first of Muḥarram 1448 should meet every one of them once.
        val start = gregorianFor(1, 1)
        val seen = Occasions.upcoming(start, 355, 0, shia).map { it.occasion }.toSet()
        val missing = Occasions.calendar(shia).toSet() - seen
        assertTrue("never landed: ${missing.map { it.label }}", missing.isEmpty())
    }

    @Test
    fun `upcoming is in date order and no wider than asked`() {
        val from = LocalDate.of(2026, 3, 1)
        val list = Occasions.upcoming(from, 60, 0, shia)
        assertEquals(list.sortedBy { it.date }, list)
        assertTrue(list.all { !it.date.isBefore(from) && it.date.isBefore(from.plusDays(60)) })
    }

    @Test
    fun `every linked text is in the shipped book and not empty`() {
        val index = JSONObject(File("src/main/assets/mafatih/index.json").readText())
        val shipped = buildSet {
            val chapters = index.getJSONArray("chapters")
            for (c in 0 until chapters.length()) {
                val sections = chapters.getJSONObject(c).getJSONArray("sections")
                for (s in 0 until sections.length()) {
                    val items = sections.getJSONObject(s).getJSONArray("items")
                    for (i in 0 until items.length()) add(items.getJSONObject(i).getString("id"))
                }
            }
        }
        val linked = (Occasions.calendar(shia) + Occasions.calendar(sunni)).mapNotNull { it.entryId }.toSet()
        val missing = linked - shipped
        assertTrue("not in the book: $missing", missing.isEmpty())
        linked.forEach { id ->
            val blocks = JSONObject(File("src/main/assets/mafatih/$id.json").readText()).getJSONArray("blocks")
            assertTrue("$id is empty", blocks.length() > 0)
        }
    }

    @Test
    fun `no two Shia occasions share a label`() {
        val labels = Occasions.calendar(shia).map { it.label }
        // The two Fāṭimiyya are distinguished in their labels, so none repeats.
        assertEquals(labels.size, labels.toSet().size)
    }
}
