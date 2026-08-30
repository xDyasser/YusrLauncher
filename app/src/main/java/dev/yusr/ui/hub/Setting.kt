package dev.yusr.ui.hub

/**
 * How a leaf of the mushaf is set: how big its letters are, how far they are condensed, and how
 * its words are spaced out to reach both margins.
 *
 * A printed page does not scroll, so a page is set at whatever puts fifteen lines on the screen at
 * once — and on a phone held upright it is the width that answers that, not the height. A leaf
 * here is more than twice as tall as it is wide where a printed leaf is about half again; there is
 * room down the page and none at all across it. So the height hardly ever decides and the width
 * nearly always does, and the whole of the arithmetic below is about the width.
 *
 * The width is bought twice over. The face's own word space is a typesetter's space, 0.22 em, and
 * a line of twelve words spends a tenth of the measure on eleven of them; the printed mushaf sets
 * its words all but touching and justifies the line with whatever that leaves over, and so does
 * this. What that still leaves short is taken out of the letters themselves, which are drawn
 * narrower — the same size down the page, condensed across it.
 *
 * Condensing is not a liberty taken with the book. A printed mushaf is written line by line to the
 * measure, and the calligrapher's letters are narrower on a full line than on an empty one. That
 * can be measured: in the Complex's own page fonts every word of every page is a glyph drawn as it
 * was set on that page, and taken across all six hundred and four, the print's tightest page is
 * written at four fifths the width of its middling page and two thirds the width of its loosest.
 * The pages here vary by that very ratio — four fifths, to three figures — because they are the
 * same pages carrying the same words. What this adds is a level: the whole book is set about a
 * tenth narrower than the face draws it, and that tenth is what pays for the size.
 *
 * And it is one size. The print sets the whole book at one and so does this, rather than fitting
 * each page to itself: a size that grew and shrank as the leaf turned would be a different book
 * every page, and a reader would feel that long before they could say what it was.
 */
internal object Setting {

    /** The lines a page of the mushaf holds — every page but the two framed ones at the front. */
    const val LINES = 15

    /**
     * The longest line in the book, as a multiple of the size it is set at.
     *
     * Line ten of page five hundred and fifty-two, measured over every line of every page in the
     * layout the app ships, with the spaces already trimmed to [WORD_SPACE]. It is the line the
     * size of the whole book is worked back from, which is what makes the size one size: fit this
     * and every other line of the mushaf fits, most of them with room to spare.
     *
     * Rounded up rather than down. A page that measures out a hair wider than this on the device
     * than it did here is set a hair smaller than the rest and nobody sees it; a page that does
     * not fit is a word off the edge of the paper.
     */
    const val WIDEST = 20.7f

    /**
     * How narrow the letters may be drawn, as a fraction of their drawn width.
     *
     * The floor, reached on the one page the book is fitted to and approached on a handful of
     * others; the middling page comes out at nine tenths and about a tenth of the book is not
     * condensed at all. Set against the print measured above — whose tightest page is four fifths
     * of its middling one — this is the same spread held one step tighter, and the step is what
     * the size is bought with: at this the whole book is set larger than the roomiest page could
     * be set without it.
     */
    const val SQUEEZE = 0.72f

    /**
     * How much of a line's box the letters themselves take up.
     *
     * Measured rather than guessed: over the whole book a line of this face inks 1.50 of its own
     * size from the tops of its marks to the feet of its descenders, and the tallest line in the
     * book inks 1.77. At this fraction an ordinary line fills seven eighths of the room it is
     * given, and taking every letter at the full width of its box there is no column of any page
     * where one line's ink reaches the line under it — the closest any two come is a thirtieth of
     * a line, on page twenty-one.
     *
     * It is a ceiling rather than a size. On a phone the width answers first and leaves the page
     * well under it; it binds only on a screen short enough that fifteen lines are the tighter
     * of the two, and on the two framed pages at the front, which have eight lines and short ones.
     */
    const val LINE_FILL = 0.57f

    /**
     * The room a line is given, as a multiple of its letters — this orthography stacks its marks.
     *
     * Fifteen of these boxes have to make the leaf, so this and [LINE_FILL] multiply out to one.
     * Any more between them and the fifteenth line is off the bottom of the screen.
     */
    const val LINE_SPACING = 1.75f

    /** How much of the width a fitted line is allowed to fill. The rest is the margin of error. */
    const val FIT_MARGIN = 0.99f

    /** The face's own word space, in ems, measured off the font the page is set in. */
    const val FACE_SPACE = 0.22f

    /** The word space a mushaf line is set with: a hair, and the letters have the rest. */
    const val WORD_SPACE = 0.06f

    /** What comes off each of a line's spaces before the line is measured at all. */
    const val TRIM = FACE_SPACE - WORD_SPACE

    /**
     * The size the page is set at, in pixels.
     *
     * The same for every page of the book, because [ems] is the same for every page of the book:
     * the caller hands in [WIDEST] rather than the page's own longest line. The two framed pages
     * are the exception and are fitted to themselves, which is what keeps them large.
     *
     * @param perLine the height of one of the page's line boxes
     * @param ems the longest line that has to fit, as a multiple of the size it is set at
     * @param width the width of the leaf
     */
    fun size(perLine: Float, ems: Float, width: Float): Float {
        if (perLine <= 0f || ems <= 0f || width <= 0f) return 0f
        return minOf(perLine * LINE_FILL, width * FIT_MARGIN / (ems * SQUEEZE))
    }

    /**
     * How far the letters of the page are condensed, as a fraction of their drawn width.
     *
     * Exactly as far as it takes to put this page's longest line inside the measure at the size
     * the book is set at, and no further — so a page with little on it is not condensed at all,
     * and the page the book was fitted to is condensed to [SQUEEZE]. Which is the printer's own
     * order of things: the size belongs to the book and the width of the letters to the page.
     *
     * @param size the size the page is set at, from [size]
     * @param ems this page's longest line, as a multiple of the size it is set at
     * @param width the width of the leaf
     */
    fun squeeze(size: Float, ems: Float, width: Float): Float {
        if (size <= 0f || ems <= 0f || width <= 0f) return 1f
        return (width * FIT_MARGIN / (size * ems)).coerceIn(SQUEEZE, 1f)
    }

    /**
     * What goes on each of a line's spaces, in pixels, on top of the space the face gives them.
     *
     * The line is set tight first — [TRIM] off every space — and the room that leaves over is
     * shared out equally between them again, which is how the printer takes up what is left at
     * the end of a line of Arabic. So the answer is negative on the line the page was fitted to,
     * which is the line with nothing left over, and grows the further a line falls short of the
     * margin.
     *
     * @param natural the line's width with the face's own spaces in it
     * @param gaps how many spaces the line has; a line with none keeps its width
     * @param width the width of the leaf
     * @param trim [TRIM] at the size the line is set at, condensed as the line is
     */
    fun spacing(natural: Float, gaps: Int, width: Float, trim: Float): Float {
        if (gaps <= 0) return 0f
        val tight = natural - trim * gaps
        return ((width - tight) / gaps).coerceAtLeast(0f) - trim
    }
}
