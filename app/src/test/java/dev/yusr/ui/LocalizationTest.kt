package dev.yusr.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The Arabic table is data, and data keyed on English sentences has exactly one failure mode
 * worth a test: a translation whose placeholders do not match the sentence it replaces. In
 * English that sentence is never formatted twice, so nobody notices until an Arabic phone tries
 * to print "you have opened it %s today" with one argument and a format that wants two.
 *
 * [t] swallows that rather than crashing, which is right at runtime and useless here — so the
 * counting is done directly, where it can fail loudly.
 */
class LocalizationTest {

    private val original: Locale = Locale.getDefault()

    @After
    fun restore() {
        Locale.setDefault(original)
    }

    @Test
    fun `every translation takes the same arguments as the sentence it replaces`() {
        ArabicStrings.TABLE.forEach { (english, arabic) ->
            assertEquals(
                "placeholders in: $english",
                PLACEHOLDER.findAll(english).count(),
                PLACEHOLDER.findAll(arabic).count(),
            )
        }
    }

    @Test
    fun `nothing in the table is left in English`() {
        ArabicStrings.TABLE.forEach { (english, arabic) ->
            assertTrue("untranslated: $english", english != arabic)
            // Not "contains no Latin" — a few of these legitimately keep one, the app's own name
            // among them. What would actually be wrong is a value carried across untouched, so
            // anything with a word in it has to come back with a word in Arabic.
            //
            // The placeholders come out first, on both sides. "‹ %s" is a chevron and a slot with
            // nothing in it to translate, and the s of %s is not a word — mistaking it for one is
            // what this test did when it was first written.
            val words = english.replace(PLACEHOLDER, "")
            val translated = arabic.replace(PLACEHOLDER, "")
            if (words.any { it in 'a'..'z' || it in 'A'..'Z' }) {
                assertTrue("no Arabic in: $english → $arabic", translated.any { it in '\u0600'..'\u06FF' })
            }
        }
    }

    @Test
    fun `english is returned unchanged when the app is not in Arabic`() {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("go and pray. that is the whole idea.", t("go and pray. that is the whole idea."))
        assertEquals("something nobody translated", t("something nobody translated"))
    }

    @Test
    fun `arabic is used when it is there and English falls through when it is not`() {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        assertEquals("الصيام", t("Fasting"))
        // The right failure: an app half translated still works.
        assertEquals("something nobody translated", t("something nobody translated"))
    }

    @Test
    fun `values are filled in, in the order the translation wants them`() {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        assertEquals("الظهر بعد 42 د", t("%s in %s", t("Dhuhr"), "42 د"))
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Dhuhr in 42 m", t("%s in %s", "Dhuhr", "42 m"))
    }

    @Test
    fun `a format that does not match its arguments prints the English rather than throwing`() {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("%s in %s", t("%s in %s", "only one"))
    }

    private companion object {
        /** %s, %d, %,d, %.3f — every shape the table actually uses. */
        val PLACEHOLDER = Regex("%[,.]?\\d*[sdf]")
    }
}
