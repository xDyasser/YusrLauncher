package dev.yusr.ui.hub

import dev.yusr.data.quran.Tajweed
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * A colour on a page nobody can read is decoration. The key is the only thing that makes the
 * colours mean anything, so what it must not do is fall behind the engine: a rule added to
 * [Tajweed] and left out of here would paint a letter in a colour the key does not mention.
 */
class TajweedLegendTest {

    private val original: Locale = Locale.getDefault()

    @After
    fun restore() {
        Locale.setDefault(original)
    }

    @Test
    fun `every rule the engine knows is in the key exactly once`() {
        Locale.setDefault(Locale.ENGLISH)
        val listed = tajweedLegend().flatMap { it.rules }
        assertEquals("a rule is in the key twice", listed.size, listed.toSet().size)
        assertEquals(Tajweed.Rule.entries.toSet(), listed.toSet())
    }

    @Test
    fun `nothing in the key is left blank`() {
        Locale.setDefault(Locale.ENGLISH)
        tajweedLegend().forEach { entry ->
            assertTrue("no rules: ${entry.name}", entry.rules.isNotEmpty())
            assertTrue("no name", entry.name.isNotBlank())
            assertTrue("no gloss: ${entry.name}", entry.gloss.isNotBlank())
        }
    }

    /**
     * The wording is written in English and translated through a table keyed on the English, so a
     * line of it added without its translation is silently English on an Arabic phone. Here that
     * is a failure instead.
     */
    @Test
    fun `the key speaks Arabic`() {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        tajweedLegend().forEach { entry ->
            assertTrue("untranslated name: ${entry.name}", entry.name.any { it in '؀'..'ۿ' })
            assertTrue("untranslated gloss: ${entry.name}", entry.gloss.any { it in '؀'..'ۿ' })
            // Both halves, not just one: a gloss is two sentences joined, and a table missing
            // the second of them still comes back with Arabic in it.
            assertTrue("English left in the name: ${entry.name}", entry.name.none { it in 'a'..'z' })
            assertTrue("English left in the gloss: ${entry.name}", entry.gloss.none { it in 'a'..'z' })
        }
    }
}
