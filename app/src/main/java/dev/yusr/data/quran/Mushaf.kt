package dev.yusr.data.quran

import android.content.Context

/**
 * The book as a book: six hundred and four pages you can ask for by number.
 *
 * Everything either side of this is half of one — the layout is a page with no words in it, the
 * downloaded text is words with no pages. A reader should have to know about neither, only that
 * page 293 exists and what is printed on it.
 */
class Mushaf(private val context: Context, private val quran: QuranSource) {

    /**
     * Page [number], set and ready to draw, or null if it cannot be — which for now means only
     * that the Qur'an has not been downloaded onto the phone yet.
     */
    suspend fun page(number: Int): MushafPage? {
        if (number !in 1..MushafLayout.PAGES) return null
        val layout = layout() ?: return null
        val page = layout.page(number) ?: return null

        // The last thing printed on the page tells us how much of the book to read: from the word
        // the page opens on to the ayah its bottom line ends in, and not one ayah further.
        val end = page.lines.filterIsInstance<MushafLayout.Line.Text>().lastOrNull()?.end
            ?: return null
        val ayat = quran.between(
            from = page.start.surah to page.start.ayah,
            to = end.surah to end.ayah,
        )
        if (ayat.isEmpty()) return null

        return MushafPage.compose(page = page, layout = layout, ayat = ayat)
    }

    /** The page a reference is printed on, so a bookmark made anywhere opens the right leaf. */
    suspend fun pageOf(surah: Int, ayah: Int): Int = layout()?.pageOf(surah, ayah) ?: 1

    /** The layout itself, for the header, the footer and the index that read it directly. */
    suspend fun layout(): MushafLayout? = MushafLayout.load(context)
}
