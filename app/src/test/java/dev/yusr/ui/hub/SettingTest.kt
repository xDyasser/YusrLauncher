package dev.yusr.ui.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page's arithmetic, held here rather than found on a screen.
 *
 * Three of these are not really tests of a function at all but of the numbers themselves: fifteen
 * line boxes have to make one leaf, a word space that is trimmed has to be put back, and letters
 * that are condensed have to stay recognisably the letters. All three are the kind of thing that
 * is fine until somebody nudges a constant a year from now, and then the fifteenth line of every
 * page is off the bottom of the phone.
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
    fun `the width is what answers when the letters have no more to give`() {
        // A leaf 1000 wide with a line that wants 2000 at the size it was measured: half of it,
        // and half again as much as that once the letters are drawn as narrow as they go.
        val squeeze = Setting.squeeze(perLine = 200f, widest = 2000f, width = 1000f, at = 100f)
        assertEquals(Setting.SQUEEZE, squeeze, 1e-6f)
        val size = Setting.size(200f, widest = 2000f, width = 1000f, at = 100f, squeeze = squeeze)
        assertEquals(100f * Setting.FIT_MARGIN / 2f / Setting.SQUEEZE, size, 1e-3f)
    }

    @Test
    fun `the height is what answers when the lines are short`() {
        val squeeze = Setting.squeeze(perLine = 200f, widest = 500f, width = 1000f, at = 100f)
        assertEquals(1f, squeeze, 1e-6f)
        val size = Setting.size(200f, widest = 500f, width = 1000f, at = 100f, squeeze = squeeze)
        assertEquals(200f * Setting.LINE_FILL, size, 1e-3f)
    }

    @Test
    fun `the letters are drawn narrower only as far as the longest line needs`() {
        // At the size the height allows this line overruns the leaf by a twentieth, so it is
        // drawn a twentieth narrower — not as narrow as it is allowed to go.
        val perLine = 200f
        val at = 100f
        val widest = 1000f * Setting.FIT_MARGIN / (perLine * Setting.LINE_FILL / at) / 0.95f
        val squeeze = Setting.squeeze(perLine = perLine, widest = widest, width = 1000f, at = at)
        assertEquals(0.95f, squeeze, 1e-4f)
        // And having given exactly that much, the height is what decides the size.
        val size = Setting.size(perLine, widest, width = 1000f, at = at, squeeze = squeeze)
        assertEquals(perLine * Setting.LINE_FILL, size, 1e-3f)
    }

    @Test
    fun `a page whose lines already fit is not touched at all`() {
        val squeeze = Setting.squeeze(perLine = 200f, widest = 100f, width = 1000f, at = 100f)
        assertEquals(1f, squeeze, 0f)
    }

    @Test
    fun `a leaf with no room yet is set at nothing rather than at anything`() {
        assertEquals(1f, Setting.squeeze(perLine = 0f, widest = 0f, width = 0f, at = 0f), 0f)
        assertEquals(0f, Setting.size(perLine = 0f, widest = 0f, width = 0f, at = 0f, 1f), 0f)
    }

    @Test
    fun `the letters keep most of their width`() {
        assertTrue(
            "condensing buys the page its size; past a tenth or so it costs the letterforms",
            Setting.SQUEEZE in 0.85f..1f,
        )
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
