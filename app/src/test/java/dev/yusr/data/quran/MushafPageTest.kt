package dev.yusr.data.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pouring the text into the lines.
 *
 * The layout says only where each line *ends*, because in a book there is no gap between one line
 * and the next. Everything here is about the walk that follows from that: the right words on the
 * right line, the ayah's number where the ayah closes, and the next line starting on the word
 * after — across an ayah's end, across a sūrah's, and across the turn of a page.
 */
class MushafPageTest {

    private fun ayah(surah: Int, number: Int, text: String) =
        Ayah(surah = surah, ayah = number, surahName = "", arabic = text, english = null)

    /** A layout of one page, written in the asset's own notation. */
    private fun layoutOf(vararg lines: String, start: String = "1:1:1"): MushafLayout {
        val body = lines.joinToString(",") { "\"$it\"" }
        return MushafLayout.parse(
            """
            {"version":1,"pages":1,"linesPerPage":${lines.size},"words":0,
             "rub":["1:1"],"juz":["1:1"],"sajda":["1:1"],
             "page":[{"p":1,"s":"$start","l":[$body]}]}
            """.trimIndent(),
        )
    }

    private fun compose(layout: MushafLayout, ayat: List<Ayah>) =
        MushafPage.compose(layout.page(1)!!, layout, ayat)

    private fun words(line: MushafPage.Line) =
        (line as MushafPage.Line.Text).words.filter { it.kind == MushafPage.Word.Kind.TEXT }
            .map { it.text }

    @Test
    fun `a line ends on the word the layout says it ends on`() {
        val layout = layoutOf("1:1:2", "1:1:4")
        val page = compose(layout, listOf(ayah(1, 1, "one two three four")))!!

        assertEquals(listOf("one", "two"), words(page.lines[0]))
        assertEquals(listOf("three", "four"), words(page.lines[1]))
    }

    @Test
    fun `a line ending an ayah carries its number`() {
        val layout = layoutOf("1:1:e")
        val page = compose(layout, listOf(ayah(1, 1, "one two")))!!

        val set = (page.lines[0] as MushafPage.Line.Text).words
        assertEquals(3, set.size)
        assertEquals(MushafPage.Word.Kind.END, set.last().kind)
        // The medallion takes the ayah's number in the digits the page is set in.
        assertTrue(set.last().text.contains('١'))
    }

    @Test
    fun `a marker left over runs on to the top of the next line`() {
        // The layout does this six hundred times over: the last word of an ayah finishes a line
        // and its number opens the one below.
        val layout = layoutOf("1:1:2", "1:2:e")
        val page = compose(
            layout,
            listOf(ayah(1, 1, "one two"), ayah(1, 2, "three")),
        )!!

        assertEquals(listOf("one", "two"), words(page.lines[0]))
        val second = (page.lines[1] as MushafPage.Line.Text).words
        assertEquals(MushafPage.Word.Kind.END, second.first().kind)
        assertEquals(1, second.first().ayah)
        assertEquals(listOf("three"), words(page.lines[1]))
    }

    @Test
    fun `a line runs on from one surah into the next`() {
        // Al-Nās follows al-Falaq on the same page, and the walk has to cross the join.
        val layout = layoutOf("113:5:e", "114:1:e", start = "113:5:1")
        val page = compose(
            layout,
            listOf(ayah(113, 5, "falaq"), ayah(114, 1, "nas")),
        )!!

        assertEquals(listOf("falaq"), words(page.lines[0]))
        assertEquals(listOf("nas"), words(page.lines[1]))
        assertEquals(114, (page.lines[1] as MushafPage.Line.Text).words.last().ayah)
    }

    @Test
    fun `a page can open partway through an ayah`() {
        // Which most of them do: only a hundred and fourteen pages of six hundred and four begin
        // an ayah at their top line.
        val layout = layoutOf("1:1:4", start = "1:1:3")
        val page = compose(layout, listOf(ayah(1, 1, "one two three four")))!!

        assertEquals(listOf("three", "four"), words(page.lines[0]))
    }

    @Test
    fun `headings and the basmala take a line and no words`() {
        val layout = layoutOf("h2", "b", "2:1:e", start = "2:1:1")
        val page = compose(layout, listOf(ayah(2, 1, "alif lam mim")))!!

        assertEquals(MushafPage.Line.Heading(2), page.lines[0])
        assertEquals(MushafPage.Line.Basmala, page.lines[1])
        assertEquals(listOf("alif", "lam", "mim"), words(page.lines[2]))
    }

    @Test
    fun `a rub opens with its mark`() {
        val layout = layoutOf("1:1:e")
        val page = compose(layout, listOf(ayah(1, 1, "one")))!!

        // The layout above puts a rubʿ at 1:1, so the ۞ stands before the first word.
        val set = (page.lines[0] as MushafPage.Line.Text).words
        assertEquals(MushafPage.Word.Kind.RUB, set.first().kind)
        assertEquals("۞", set.first().text)
    }

    @Test
    fun `a centred line stays centred`() {
        val layout = layoutOf("c1:1:e")
        val page = compose(layout, listOf(ayah(1, 1, "one")))!!

        assertTrue((page.lines[0] as MushafPage.Line.Text).centred)
    }

    @Test
    fun `a page with text missing is no page at all`() {
        // Rather than a page with a hole in it. The reader shows the download notice instead,
        // which is true, where half a page of the Qur'an would not be.
        val layout = layoutOf("1:1:2", "1:2:e")
        assertNull(compose(layout, listOf(ayah(1, 1, "one two"))))
    }

    @Test
    fun `the words of an ayah are the words the mushaf counts`() {
        // The four āyāt where this edition and the printed page disagree about where a word
        // ends. If these ever come out differently, every line after them holds the wrong words.
        assertEquals(8, UthmaniText.words(15, 7, LAW_MA).size)
        assertEquals(12, UthmaniText.words(27, 20, MA_LIYA).size)
        assertEquals(8, UthmaniText.words(36, 22, WA_MA_LIYA).size)
        // The one that goes the other way: two words in the text, one on the page,
        // bound by the space that keeps a line from breaking between its halves.
        assertEquals(3, UthmaniText.words(37, 130, IL_YASIN).size)
        assertTrue(UthmaniText.words(37, 130, IL_YASIN).last().contains('\u00A0'))
    }

    @Test
    fun `an ordinary ayah is simply its words`() {
        val basmala = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"
        assertEquals(4, UthmaniText.words(1, 1, basmala).size)
        // And the repair still happens on the way through.
        assertEquals(2, UthmaniText.words(2, 286, "إِصۡرࣰ ا كَمَا").size)
    }

    @Test
    fun `a composed page knows where it opens`() {
        val layout = layoutOf("2:5:e", start = "2:5:1")
        val page = compose(layout, listOf(ayah(2, 5, "one")))
        assertNotNull(page)
        assertEquals(2, page!!.surah)
        assertEquals(5, page.firstAyah)
    }

    private companion object {
        /** Al-Ḥijr 15:7, al-Naml 27:20, Yā Sīn 36:22 and al-Ṣāffāt 37:130, as downloaded. */
        const val LAW_MA = "لَّوۡمَا تَأۡتِينَا بِٱلۡمَلَٰٓئِكَةِ إِن كُنتَ مِنَ ٱلصَّٰدِقِينَ"
        const val MA_LIYA =
            "وَتَفَقَّدَ ٱلطَّيۡرَ فَقَالَ مَالِيَ لَآ أَرَى ٱلۡهُدۡهُدَ أَمۡ كَانَ مِنَ ٱلۡغَآئِبِينَ"
        const val WA_MA_LIYA = "وَمَالِيَ لَآ أَعۡبُدُ ٱلَّذِي فَطَرَنِي وَإِلَيۡهِ تُرۡجَعُونَ"
        const val IL_YASIN = "سَلَٰمٌ عَلَىٰٓ إِلۡ يَاسِينَ"
    }
}
