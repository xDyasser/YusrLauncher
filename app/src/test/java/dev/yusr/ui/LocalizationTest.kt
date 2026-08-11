package dev.yusr.ui

import dev.yusr.domain.AppTier
import dev.yusr.util.DayClock
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

    /**
     * The four tiers reach a screen three ways — the pills, the line under an app's name, and the
     * description of a queued change — and none of them may fall back on the enum's own spelling,
     * which is English wherever it is printed.
     */
    @Test
    fun `every tier has a word and a pill in both languages`() {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        AppTier.entries.forEach { tier ->
            assertTrue("tier name: $tier", tierName(tier).any { it in '\u0600'..'\u06FF' })
            assertTrue("tier pill: $tier", tierPill(tier).any { it in '\u0600'..'\u06FF' })
        }
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("favourite", tierName(AppTier.FAVORITE))
        assertEquals("fav", tierPill(AppTier.FAVORITE))
    }

    /**
     * The h and the m in "2h 30m" are the first letters of English words, and a duration is the
     * most widely printed thing in the app — the gate, the budgets, the friction knobs, the
     * pending changes all say one.
     */
    @Test
    fun `durations carry their unit into Arabic`() {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("45m", DayClock.formatMinutes(45))
        assertEquals("2h", DayClock.formatMinutes(120))
        assertEquals("2h 30m", DayClock.formatMinutes(150))
        Locale.setDefault(Locale.forLanguageTag("ar"))
        assertEquals("45 د", DayClock.formatMinutes(45))
        assertEquals("2 س", DayClock.formatMinutes(120))
        assertEquals("2 س 30 د", DayClock.formatMinutes(150))
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
