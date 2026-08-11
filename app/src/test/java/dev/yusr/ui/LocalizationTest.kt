package dev.yusr.ui

import dev.yusr.data.quran.Reciters
import dev.yusr.domain.AppTier
import dev.yusr.domain.CalculationMethod
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
     * The reciters are the one list of names in the app that is data rather than wording, so the
     * table cannot catch anything about them: a reciter added with the Arabic name left off would
     * print a Latin name in the middle of an Arabic list, or an empty row, and nothing else in
     * the app would fail.
     */
    @Test
    fun `every reciter is named in the language being read`() {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        Reciters.ALL.forEach { reciter ->
            assertTrue(
                "no Arabic name: ${reciter.id}",
                reciter.arabicName.any { it in '\u0600'..'\u06FF' },
            )
            assertEquals(reciter.arabicName, reciterName(reciter))
        }
        Locale.setDefault(Locale.ENGLISH)
        Reciters.ALL.forEach { reciter ->
            assertTrue("no name: ${reciter.id}", reciter.name.isNotBlank())
            assertEquals(reciter.name, reciterName(reciter))
        }
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

    /**
     * The pill and the word sit on the same screen — the row you tap, and the line under the app's
     * name — so in English the short one has to be the long one cut short rather than a second
     * vocabulary for the same four things.
     */
    @Test
    fun `the english pill is the english word cut short`() {
        Locale.setDefault(Locale.ENGLISH)
        AppTier.entries.forEach { tier ->
            assertTrue(
                "pill ${tierPill(tier)} is not the start of ${tierName(tier)}",
                tierName(tier).startsWith(tierPill(tier)),
            )
        }
    }

    /**
     * The calculation method used to be named four ways, one of them the enum's own constant, and
     * `UMM_AL_QURA` is that in every language. Both registers are checked in both languages
     * because the settings row prints one and the madhab screen the other.
     */
    @Test
    fun `every calculation method has a name and a pill in both languages`() {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        CalculationMethod.entries.forEach { method ->
            assertTrue("method name: $method", methodName(method).any { it in '\u0600'..'\u06FF' })
            assertTrue("method pill: $method", methodPill(method).any { it in '\u0600'..'\u06FF' })
        }
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Umm al-Qurā", methodName(CalculationMethod.UMM_AL_QURA))
        assertEquals("makkah", methodPill(CalculationMethod.UMM_AL_QURA))
    }

    /**
     * The one that is invisible in English and unmissable in Arabic: `%d` follows the locale and
     * `%s` does not, so a screen that used both printed two numeral systems side by side — the
     * countdown flipping from Western to Arabic-Indic as it crossed a minute, a prayer time in one
     * set of digits beside its offset in the other.
     */
    @Test
    fun `numbers are the same digits whatever the language`() {
        listOf(Locale.ENGLISH, Locale.forLanguageTag("ar")).forEach { locale ->
            Locale.setDefault(locale)
            assertEquals("in $locale", "07:05", DayClock.clock(7 * 60 + 5))
            assertEquals("in $locale", "23:40", DayClock.clock(23 * 60 + 40))
            // Either side of the boundary the countdown crosses on its way to zero.
            assertEquals("in $locale", "1:05", DayClock.formatSeconds(65))
            assertEquals("in $locale", "1,234", t("%,d", 1234))
            assertEquals("in $locale", "21.250", t("%.3f", 21.25))
        }
    }

    /** Out of range rather than invalid: a midnight past the end of the day is still a time. */
    @Test
    fun `a minute of the day outside the day wraps`() {
        assertEquals("00:30", DayClock.clock(24 * 60 + 30))
        assertEquals("23:30", DayClock.clock(-30))
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
