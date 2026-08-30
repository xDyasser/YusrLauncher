package dev.yusr.ui.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page's arithmetic, held here rather than found on a screen.
 *
 * Some of these are not really tests of a function at all but of the numbers themselves: fifteen
 * line boxes have to make one leaf, a word space that is trimmed has to be put back, the measure
 * the whole book is fitted to has to be about the width of a phone, and no page may be set wider
 * than the paper. All of them are the kind of thing that is fine until somebody nudges a constant
 * a year from now, and then the fifteenth line of every page is off the bottom of the screen or
 * the last word of every line is off the side of it.
 */
class SettingTest {

    @Test
    fun `fifteen line boxes make a leaf and no more`() {
        assertTrue(
            "a line's box is its letters times the leading; fifteen of them must fit the leaf",
            Setting.LINE_FILL * Setting.LINE_SPACING <= 1f,
        )
    }

    @Test
    fun `the words are set tighter than the face would space them, and never at nothing`() {
        assertTrue(Setting.WORD_SPACE > 0f)
        assertTrue(Setting.WORD_SPACE < Setting.FACE_SPACE)
        assertEquals(Setting.FACE_SPACE - Setting.WORD_SPACE, Setting.TRIM, 1e-6f)
    }

    @Test
    fun `the book is fitted to about the width of a phone`() {
        // These two multiply out to the measure, in ems: the longest line in the book, drawn as
        // narrow as the letters are allowed to go. A phone held upright is about fifteen ems
        // across in this face, and a pair of constants that came out at ten or at twenty would
        // mean one of them had been changed without the other.
        val measure = Setting.WIDEST * Setting.SQUEEZE
        assertTrue("the book is fitted to $measure ems", measure in 13f..17f)
        assertTrue(Setting.SQUEEZE in 0.65f..1f)
    }

    @Test
    fun `the width is what answers when the lines are long`() {
        // A leaf 1000 wide and a book whose longest line wants twenty times its own size: the
        // size is what the width allows once the letters are drawn as narrow as they go.
        val size = Setting.size(perLine = 400f, ems = 20f, width = 1000f)
        assertEquals(1000f * Setting.FIT_MARGIN / (20f * Setting.SQUEEZE), size, 1e-3f)
        assertEquals(Setting.SQUEEZE, Setting.squeeze(size, ems = 20f, width = 1000f), 1e-6f)
    }

    @Test
    fun `the height is what answers when there is no room down the page`() {
        val size = Setting.size(perLine = 100f, ems = 10f, width = 1000f)
        assertEquals(100f * Setting.LINE_FILL, size, 1e-3f)
        // And having been set by the height, the letters are left at their own width.
        assertEquals(1f, Setting.squeeze(size, ems = 10f, width = 1000f), 1e-6f)
    }

    @Test
    fun `the whole book is set at one size and it is the letters that move`() {
        // Both pages are handed the longest line in the book rather than their own, which is what
        // makes the size one size; what tells them apart is how narrow they are drawn.
        val crowded = Setting.WIDEST
        val airy = Setting.WIDEST * 0.8f
        val size = Setting.size(perLine = 400f, ems = Setting.WIDEST, width = 1000f)
        assertEquals(size, Setting.size(perLine = 400f, ems = Setting.WIDEST, width = 1000f), 0f)
        assertEquals(Setting.SQUEEZE, Setting.squeeze(size, crowded, 1000f), 1e-6f)
        assertEquals(Setting.SQUEEZE / 0.8f, Setting.squeeze(size, airy, 1000f), 1e-4f)
    }

    @Test
    fun `a page whose lines already fit is not touched at all`() {
        val size = Setting.size(perLine = 400f, ems = Setting.WIDEST, width = 1000f)
        // Short enough that the measure is reached without condensing at all — which is about a
        // tenth of the pages of the book.
        val short = Setting.WIDEST * Setting.SQUEEZE
        assertEquals(1f, Setting.squeeze(size, ems = short, width = 1000f), 1e-4f)
        assertEquals(1f, Setting.squeeze(size, ems = short / 2f, width = 1000f), 0f)
    }

    @Test
    fun `no page is ever set wider than the leaf`() {
        // The one thing that must never happen, over every page the book could hold — including
        // one that measures out longer on the device than it did when [Setting.WIDEST] was taken.
        (100..250).forEach { tenths ->
            val ems = tenths / 10f
            val size = Setting.size(perLine = 400f, ems = maxOf(ems, Setting.WIDEST), width = 1000f)
            val squeeze = Setting.squeeze(size, ems = ems, width = 1000f)
            val drawn = size * squeeze * ems
            assertTrue("a line of $ems ems runs to $drawn", drawn <= 1000f)
        }
    }

    @Test
    fun `a leaf with no room yet is set at nothing rather than at anything`() {
        assertEquals(0f, Setting.size(perLine = 0f, ems = 0f, width = 0f), 0f)
        assertEquals(1f, Setting.squeeze(size = 0f, ems = 0f, width = 0f), 0f)
    }

    @Test
    fun `the line the page was fitted to is the line that is set tight`() {
        // The page is fitted so that this line, trimmed, is exactly the width of the leaf.
        val trim = 6f
        val gaps = 10
        val natural = 1000f + trim * gaps
        assertEquals(-trim, Setting.spacing(natural, gaps, width = 1000f, trim = trim), 1e-3f)
    }

    @Test
    fun `what a short line falls short by is shared out between its spaces`() {
        // Set tight the line is 940 wide on a leaf of 1000, so sixty is split four ways.
        val trim = 6f
        val gaps = 4
        val natural = 940f + trim * gaps
        val spacing = Setting.spacing(natural, gaps, width = 1000f, trim = trim)
        assertEquals(60f / 4f - trim, spacing, 1e-3f)
    }

    @Test
    fun `a line still too wide once it is tight is squeezed no further`() {
        val trim = 6f
        val spacing = Setting.spacing(natural = 4000f, gaps = 8, width = 1000f, trim = trim)
        assertEquals(-trim, spacing, 1e-3f)
    }

    @Test
    fun `a line with no space in it keeps its own width`() {
        assertEquals(0f, Setting.spacing(natural = 400f, gaps = 0, width = 1000f, trim = 6f), 0f)
    }
}
