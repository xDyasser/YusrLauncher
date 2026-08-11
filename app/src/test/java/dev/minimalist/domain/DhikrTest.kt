package dev.minimalist.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DhikrTest {

    private val astaghfirullah = Dhikr.ALL.first { it.transliteration == "astaghfirullah" }
    private val subhanAllah = Dhikr.ALL.first { it.transliteration == "subhan Allah" }

    @Test
    fun `the transliteration is accepted`() {
        assertTrue(astaghfirullah.matches("astaghfirullah"))
    }

    @Test
    fun `the arabic is accepted`() {
        assertTrue(astaghfirullah.matches("استغفر الله"))
    }

    @Test
    fun `case and surrounding space do not matter`() {
        assertTrue(astaghfirullah.matches("  AstaghfiruLLah  "))
    }

    /** Nobody should have to guess whether the phrase was stored as one word or two. */
    @Test
    fun `internal spacing does not matter`() {
        assertTrue(subhanAllah.matches("subhanallah"))
        assertTrue(subhanAllah.matches("subhan allah"))
        assertTrue(subhanAllah.matches("سبحان الله"))
        assertTrue(subhanAllah.matches("سبحانالله"))
    }

    /** A keyboard that emits harakat, and one that does not, both have to work. */
    @Test
    fun `arabic diacritics are ignored`() {
        assertTrue(subhanAllah.matches("سُبْحَانَ اللَّه"))
    }

    @Test
    fun `the forms of alif are folded together`() {
        val laHawla = Dhikr.ALL.first { it.transliteration.startsWith("la hawla") }
        assertTrue(laHawla.matches("لا حول ولا قوة إلا بالله"))
    }

    @Test
    fun `a different dhikr is not accepted`() {
        assertFalse(astaghfirullah.matches("subhanallah"))
    }

    @Test
    fun `an empty answer is not accepted`() {
        assertFalse(astaghfirullah.matches(""))
        assertFalse(astaghfirullah.matches("   "))
    }

    @Test
    fun `punctuation alone does not pass`() {
        assertFalse(astaghfirullah.matches("..."))
    }

    /** Backing out and coming straight back must ask for the same phrase, not a new one. */
    @Test
    fun `the same app on the same day asks for the same dhikr`() {
        val first = Dhikr.forAttempt("com.example.app", 220)
        val second = Dhikr.forAttempt("com.example.app", 220)
        assertEquals(first, second)
    }

    @Test
    fun `every package resolves to a real dhikr`() {
        val packages = listOf("a", "com.example.app", "com.zzz.something.very.long", "")
        for (name in packages) {
            for (day in 1..366) {
                assertTrue(Dhikr.forAttempt(name, day) in Dhikr.ALL)
            }
        }
    }
}
