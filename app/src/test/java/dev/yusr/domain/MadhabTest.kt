package dev.yusr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MadhabTest {

    @Test
    fun `only the Hanafi school pushes asr to the second shadow`() {
        Madhab.entries.forEach { madhab ->
            val expected = if (madhab == Madhab.HANAFI) AsrMethod.HANAFI else AsrMethod.STANDARD
            assertEquals(madhab.name, expected, madhab.asr)
        }
    }

    @Test
    fun `the Jafari school brings its own maghrib rule with it`() {
        // Maghrib after sunset rather than at it is the whole difference, and it arrives with the
        // school rather than needing to be found in a second menu.
        assertEquals(CalculationMethod.JAFARI, Madhab.JAFARI.method)
        assertTrue(Madhab.JAFARI.defaultConfig().maghrib is MaghribRule.Angle)
        assertTrue(Madhab.SHAFII.defaultConfig().maghrib is MaghribRule.Sunset)
    }

    @Test
    fun `combining the prayers is offered up front only where it is the norm`() {
        assertTrue(Madhab.JAFARI.combinesByDefault)
        Madhab.entries.filter { it != Madhab.JAFARI }.forEach {
            assertTrue(it.name, !it.combinesByDefault)
        }
    }

    @Test
    fun `the four Sunni schools read one book of supplications and the two Shia schools the other`() {
        listOf(Madhab.HANAFI, Madhab.SHAFII, Madhab.MALIKI, Madhab.HANBALI).forEach {
            assertEquals(it.name, Madhab.Branch.SUNNI, it.branch)
            assertEquals(it.name, SupplicationCollection.HISN, it.collection)
        }
        listOf(Madhab.JAFARI, Madhab.ZAYDI).forEach {
            assertEquals(it.name, Madhab.Branch.SHIA, it.branch)
            assertEquals(it.name, SupplicationCollection.MAFATIH, it.collection)
        }
    }

    @Test
    fun `every school has a name and a note to print beside it`() {
        Madhab.entries.forEach {
            assertTrue(it.name, it.label.isNotBlank())
            assertTrue(it.name, it.arabicLabel.isNotBlank())
            assertTrue(it.name, it.note.isNotBlank())
        }
    }

    @Test
    fun `a school's defaults are the config the solver is handed`() {
        val config = Madhab.HANAFI.defaultConfig()
        assertEquals(AsrMethod.HANAFI, config.asr)
        assertEquals(CalculationMethod.MWL, config.method)
    }
}
