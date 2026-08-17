package dev.yusr.data.quran

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Where the printed mushaf breaks its lines.
 *
 * The downloaded text is the words of the book and none of its typography. This is the rest: for
 * each of the six hundred and four pages, what its fifteen lines hold, down to the word. Put the
 * two together and the app can set a page that breaks where the paper breaks — the same words on
 * the same line, so that whoever has memorised the shape of a page finds it here.
 *
 * It is bundled rather than downloaded. The layout of the mushaf is not news; it has not changed
 * since 1405 and will not, and a reader that has the text but is still waiting to be told how to
 * set it would be a book you cannot open.
 *
 * The layout is the King Fahd Complex's own, taken from the V1 print — the one whose page numbers
 * agree with every other mushaf's, so that "page 293" here is page 293 wherever else it is said.
 * See `tools/build_mushaf_layout.py`, which builds the asset and checks it against the text.
 */
class MushafLayout private constructor(
    private val pages: List<Page>,
    /** The two hundred and forty rubʿ marks, in order — every fourth one opening a ḥizb. */
    private val rubStarts: List<Reference>,
    private val juzStarts: List<Reference>,
    private val sajdaAyat: Set<Reference>,
) {

    /** A place in the book: an ayah, and a word of it. */
    data class Reference(val surah: Int, val ayah: Int, val word: Int = FIRST_WORD) : Comparable<Reference> {
        override fun compareTo(other: Reference): Int =
            compareValuesBy(this, other, { it.surah }, { it.ayah }, { it.word })
    }

    /** One line of a page, before the text is poured into it. */
    sealed interface Line {
        /** The sūrah's name in its band across the page. */
        data class Heading(val surah: Int) : Line

        /** The basmala, printed over a sūrah rather than counted as one of its āyāt. */
        data object Basmala : Line

        /**
         * A line of the text, ending at [end] — the line before it says where this one starts,
         * because the book runs on without a gap.
         *
         * [centred] is the last line of a sūrah, and the eight lines of the two framed pages at
         * the front: set in the middle rather than justified out to both margins.
         */
        data class Text(val end: Reference, val centred: Boolean) : Line
    }

    /** One page: where it starts, and what its lines hold. */
    data class Page(val number: Int, val start: Reference, val lines: List<Line>)

    fun page(number: Int): Page? = pages.getOrNull(number - 1)

    /** The page [surah]:[ayah] is printed on — the page it *starts* on, where it spans two. */
    fun pageOf(surah: Int, ayah: Int): Int {
        val wanted = Reference(surah, ayah)
        // The pages are in order, so this is a search rather than a scan.
        var low = 0
        var high = pages.lastIndex
        var found = 0
        while (low <= high) {
            val middle = (low + high) / 2
            val start = pages[middle].start
            if (Reference(start.surah, start.ayah) <= wanted) {
                found = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return pages[found].number
    }

    /** The first ayah printed on page [number] — where a bookmark lands when a page is turned to. */
    fun firstAyahOn(number: Int): Reference? =
        page(number)?.start?.let { Reference(it.surah, it.ayah) }

    /** Which of the thirty juz page [number] is in. */
    fun juzOf(number: Int): Int {
        val start = page(number)?.start ?: return 1
        val at = Reference(start.surah, start.ayah)
        return juzStarts.indexOfLast { it <= at }.coerceAtLeast(0) + 1
    }

    /** Which of the sixty ḥizbs page [number] is in — a ḥizb being four rubʿ. */
    fun hizbOf(number: Int): Int = (rubIndexOf(number) / QUARTERS_PER_HIZB) + 1

    /**
     * Which quarter of that ḥizb, 0 through 3 — nothing, a quarter, a half, three quarters, which
     * is how the mark in the margin is read.
     */
    fun quarterOf(number: Int): Int = rubIndexOf(number) % QUARTERS_PER_HIZB

    /** The page a juz opens on, for an index that jumps to one. */
    fun pageOfJuz(juz: Int): Int =
        juzStarts.getOrNull(juz - 1)?.let { pageOf(it.surah, it.ayah) } ?: 1

    /** The page a ḥizb opens on. */
    fun pageOfHizb(hizb: Int): Int =
        rubStarts.getOrNull((hizb - 1) * QUARTERS_PER_HIZB)
            ?.let { pageOf(it.surah, it.ayah) } ?: 1

    /** The ayah a ḥizb opens on, so an index can name it. */
    fun startOfHizb(hizb: Int): Reference? = rubStarts.getOrNull((hizb - 1) * QUARTERS_PER_HIZB)

    /** The ayah a juz opens on. */
    fun startOfJuz(juz: Int): Reference? = juzStarts.getOrNull(juz - 1)

    /** Whether a rubʿ opens at this exact word, which is where the ۞ goes. */
    fun rubOpensAt(surah: Int, ayah: Int): Boolean = Reference(surah, ayah) in rubStartAyat

    /** Whether a sajda falls in this ayah. */
    fun hasSajda(surah: Int, ayah: Int): Boolean = Reference(surah, ayah) in sajdaAyat

    private val rubStartAyat: Set<Reference> = rubStarts.map { Reference(it.surah, it.ayah) }.toSet()

    private fun rubIndexOf(number: Int): Int {
        val start = page(number)?.start ?: return 0
        val at = Reference(start.surah, start.ayah)
        return rubStarts.indexOfLast { it <= at }.coerceAtLeast(0)
    }

    companion object {
        const val PAGES = 604
        const val JUZ = 30
        const val HIZB = 60
        const val QUARTERS_PER_HIZB = 4

        /** The first word of an ayah, and the number the asset counts words from. */
        const val FIRST_WORD = 1

        /**
         * The closing marker of an ayah — the ۝ with its number in it — which the layout counts
         * as a word of its own because it takes room on the line like one.
         */
        const val END_MARKER = Int.MAX_VALUE

        private const val ASSET = "mushaf_layout.json"

        @Volatile
        private var loaded: MushafLayout? = null

        /**
         * The layout, parsed once for the life of the process.
         *
         * A hundred kilobytes of JSON is not much, but it is far too much to parse on the frame
         * that turns a page, and every page of the reader needs it. So it is read off the disk
         * once, on a background thread, and held.
         */
        suspend fun load(context: Context): MushafLayout? = loaded ?: withContext(Dispatchers.IO) {
            val parsed = runCatching {
                parse(context.assets.open(ASSET).bufferedReader().use { it.readText() })
            }.getOrNull()
            if (parsed != null) loaded = parsed
            parsed
        }

        /** The same, from the asset's text — which is how a test reads it without a device. */
        internal fun parse(json: String): MushafLayout {
            val root = JSONObject(json)

            val pagesJson = root.getJSONArray("page")
            val pages = (0 until pagesJson.length()).map { index ->
                val page = pagesJson.getJSONObject(index)
                val linesJson = page.getJSONArray("l")
                Page(
                    number = page.getInt("p"),
                    start = reference(page.getString("s")),
                    lines = (0 until linesJson.length()).map { line(linesJson.getString(it)) },
                )
            }

            return MushafLayout(
                pages = pages,
                rubStarts = references(root, "rub"),
                juzStarts = references(root, "juz"),
                sajdaAyat = references(root, "sajda").toSet(),
            )
        }

        private fun references(root: JSONObject, name: String): List<Reference> {
            val array = root.getJSONArray(name)
            return (0 until array.length()).map { reference(array.getString(it)) }
        }

        /** `"2:25:4"` — sūrah, ayah, word; or `"2:25:e"` for the ayah's closing marker. */
        private fun reference(encoded: String): Reference {
            val parts = encoded.split(':')
            val word = parts.getOrNull(2)
            return Reference(
                surah = parts[0].toInt(),
                ayah = parts[1].toInt(),
                word = when (word) {
                    null -> FIRST_WORD
                    "e" -> END_MARKER
                    else -> word.toInt()
                },
            )
        }

        /** `"h2"` a heading, `"b"` the basmala, `"c2:25:4"` a centred line, else a justified one. */
        private fun line(encoded: String): Line = when {
            encoded.startsWith('h') -> Line.Heading(encoded.drop(1).toInt())
            encoded == "b" -> Line.Basmala
            encoded.startsWith('c') -> Line.Text(reference(encoded.drop(1)), centred = true)
            else -> Line.Text(reference(encoded), centred = false)
        }
    }
}
