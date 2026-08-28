package dev.yusr.data.quran

import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tajwīd engine, run over the whole Qur'an and held against somebody else's reading of it.
 *
 * Colouring a letter of the Qur'an wrongly is not a cosmetic bug — somebody learns their
 * recitation from it — so this is not a test of a handful of cases. Every one of the 6,236 āyāt
 * is annotated here and compared, rule by rule, with `cpfair/quran-tajweed`: an annotation of
 * the same book built from the Dar al-Maʿrifah colour masaahif, by somebody else, from sources
 * that have nothing to do with this repository. `tools/build_tajweed_reference.py` writes the
 * fixture and says where both halves of it come from.
 *
 * The two do not agree everywhere, and the numbers below are honest about it rather than
 * rounded away. They annotate two different printings: the reference's own positions are indices
 * into a 2017 Tanzil text whose orthography differs from the edition this app downloads — a
 * different sukūn, a different tanwīn, a space in a different place — so the positions cannot be
 * compared at all, and what is compared is how many of each rule each finds in each ayah. The
 * residue after that is convention: whether the wāw of `ٱلصَّلَوٰةَ` counts as unread, whether the
 * madd at a stop is counted where the tanwīn carries it. Each rule below is held to the number
 * of āyāt it agrees on today. Improving the engine raises these; loosening it fails the build.
 */
class TajweedTest {

    /** One ayah of the fixture: the text the engine will meet, and the reference's counts. */
    private data class Reference(
        val surah: Int,
        val ayah: Int,
        val text: String,
        val counts: Map<Tajweed.Rule, Int>,
    )

    private val book: List<Reference> by lazy {
        val file = File("src/test/resources/tajweed_reference.tsv.gz")
        GZIPInputStream(file.inputStream()).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.map { line ->
                val (surah, ayah, text, counts) = line.split('\t')
                Reference(
                    surah = surah.toInt(),
                    ayah = ayah.toInt(),
                    text = text,
                    counts = counts.split(',').filter { it.isNotBlank() }.associate { pair ->
                        val (rule, count) = pair.split(':')
                        Tajweed.Rule.valueOf(rule) to count.toInt()
                    },
                )
            }.toList()
        }
    }

    private fun counted(text: String): Map<Tajweed.Rule, Int> =
        Tajweed.annotate(text).groupingBy { it.rule }.eachCount()

    @Test
    fun `the fixture is the whole book`() {
        assertEquals(6236, book.size)
        assertEquals(1 to 1, book.first().let { it.surah to it.ayah })
        assertEquals(114 to 6, book.last().let { it.surah to it.ayah })
    }

    @Test
    fun `every span falls on one letter inside its own ayah`() {
        book.forEach { reference ->
            Tajweed.annotate(reference.text).forEach { span ->
                val where = "${reference.surah}:${reference.ayah}"
                assertTrue("$where starts at ${span.start}", span.start >= 0)
                assertTrue("$where ends at ${span.end}", span.end <= reference.text.length)
                assertEquals("$where covers more than a letter", 1, span.end - span.start)
            }
        }
    }

    @Test
    fun `no ayah is annotated twice in the same place with the same rule`() {
        book.forEach { reference ->
            val spans = Tajweed.annotate(reference.text)
            assertEquals(
                "${reference.surah}:${reference.ayah} has a rule drawn twice on one letter",
                spans.size,
                spans.distinct().size,
            )
        }
    }

    @Test
    fun `the book as a whole agrees with the reference annotation`() {
        val agreeing = book.count { counted(it.text) == it.counts }
        // 5,805 of 6,236 āyāt, where agreement means every one of the eighteen rules found the
        // same number of times. The rest are the conventions the two editions differ on.
        assertTrue("only $agreeing āyāt agree in full", agreeing >= 5805)
    }

    @Test
    fun `each rule agrees with the reference on all but a few ayat`() {
        // The floor for each rule, in āyāt out of 6,236, as measured. A rule that starts firing
        // where it should not takes its own number down and this notices which one.
        val floors = mapOf(
            Tajweed.Rule.HAMZAT_WASL to 6236,
            Tajweed.Rule.IDGHAAM_NO_GHUNNAH to 6236,
            Tajweed.Rule.IDGHAAM_SHAFAWI to 6236,
            Tajweed.Rule.IKHFA_SHAFAWI to 6236,
            Tajweed.Rule.IKHFA to 6235,
            Tajweed.Rule.QALQALAH to 6235,
            Tajweed.Rule.IDGHAAM_MUTAJAANISAIN to 6235,
            Tajweed.Rule.IDGHAAM_GHUNNAH to 6232,
            Tajweed.Rule.IDGHAAM_MUTAQAARIBAIN to 6232,
            Tajweed.Rule.MADD_6 to 6229,
            Tajweed.Rule.IQLAB to 6218,
            Tajweed.Rule.LAM_SHAMSIYYAH to 6217,
            Tajweed.Rule.MADD_MUNFASIL to 6185,
            Tajweed.Rule.GHUNNAH to 6174,
            Tajweed.Rule.MADD_MUTTASIL to 6164,
            Tajweed.Rule.SILENT to 6162,
            Tajweed.Rule.MADD_2 to 6156,
            Tajweed.Rule.MADD_246 to 6103,
        )
        assertEquals("every rule needs a floor", Tajweed.Rule.entries.toSet(), floors.keys)

        val agreeing = mutableMapOf<Tajweed.Rule, Int>()
        book.forEach { reference ->
            val found = counted(reference.text)
            Tajweed.Rule.entries.forEach { rule ->
                if ((found[rule] ?: 0) == (reference.counts[rule] ?: 0)) {
                    agreeing[rule] = (agreeing[rule] ?: 0) + 1
                }
            }
        }
        floors.forEach { (rule, floor) ->
            val count = agreeing[rule] ?: 0
            assertTrue("$rule agrees on $count āyāt, below its floor of $floor", count >= floor)
        }
    }

    /**
     * Al-Fātiḥa's first ayah, letter by letter, checked by hand against the printed page.
     *
     * The whole-book test above says the engine agrees with somebody else. This one says what it
     * actually draws, on the one ayah every reader of this app knows by heart.
     */
    @Test
    fun `the basmala is annotated as it is read`() {
        val text = book.first { it.surah == 1 && it.ayah == 1 }.text
        val drawn = Tajweed.annotate(text)
            .sortedBy { it.start }
            .map { text[it.start] to it.rule }

        assertEquals(
            listOf(
                // بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ — the wasl alif of the divine name, whose own
                // lam is never a sun lam; then the two of al-Raḥmān and al-Raḥīm, which are.
                'ٱ' to Tajweed.Rule.HAMZAT_WASL,
                'ٱ' to Tajweed.Rule.HAMZAT_WASL,
                'ل' to Tajweed.Rule.LAM_SHAMSIYYAH,
                // The dagger alif of al-Raḥmān: a madd nobody would see was there.
                'ٰ' to Tajweed.Rule.MADD_2,
                'ٱ' to Tajweed.Rule.HAMZAT_WASL,
                'ل' to Tajweed.Rule.LAM_SHAMSIYYAH,
                // Stopping on al-Raḥīm stretches its yāʾ: two counts, or four, or six.
                'ي' to Tajweed.Rule.MADD_246,
            ),
            drawn,
        )
    }

    /**
     * A nūn at rest, four times over, which is the rule a colour mushaf exists to teach.
     *
     * Every one of these is decided by the orthography rather than by the engine guessing: the
     * marked sukūn of `مِنۡ` against the bare nūn of `مِن`, and the small mīm that says iqlāb.
     */
    @Test
    fun `the noon at rest takes the rule the page gives it`() {
        fun rulesOn(surah: Int, ayah: Int, letter: Char): List<Tajweed.Rule> {
            val text = book.first { it.surah == surah && it.ayah == ayah }.text
            return Tajweed.annotate(text).filter { text[it.start] == letter }.map { it.rule }
        }

        // 2:4 — مِن قَبۡلِكَ, a bare nūn before qāf: hidden.
        assertTrue(rulesOn(2, 4, 'ن').contains(Tajweed.Rule.IKHFA))
        // 2:5 — مِّن رَّبِّهِمۡ, a bare nūn before rāʾ: assimilated, without ghunnah.
        assertTrue(rulesOn(2, 5, 'ن').contains(Tajweed.Rule.IDGHAAM_NO_GHUNNAH))
        // 2:87 — مِنۢ بَعۡدِهِ, the small mīm over the nūn: turned into a mīm.
        assertTrue(rulesOn(2, 87, 'ن').contains(Tajweed.Rule.IQLAB))
        // 2:8 — مِنَ ٱلنَّاسِ and مَن يَقُولُ: one vowelled nūn that is no rule at all, one bare
        // nūn before yāʾ that is assimilated with ghunnah.
        assertTrue(rulesOn(2, 8, 'ن').contains(Tajweed.Rule.IDGHAAM_GHUNNAH))
    }

    @Test
    fun `the disconnected letters at the head of a sura are held for six counts`() {
        // الٓمٓ — al-Baqara opens on two letters, each of them read the long way.
        val text = book.first { it.surah == 2 && it.ayah == 1 }.text
        val spans = Tajweed.annotate(text)
        assertEquals(2, spans.count { it.rule == Tajweed.Rule.MADD_6 })
    }

    @Test
    fun `an ayah with nothing in it to colour is left alone`() {
        // The engine is asked for empty and blank text by nothing in the app, but a page that
        // fails to draw is worse than one drawn plainly.
        assertEquals(emptyList<Tajweed.Span>(), Tajweed.annotate(""))
        assertEquals(emptyList<Tajweed.Span>(), Tajweed.annotate("   "))
    }
}
