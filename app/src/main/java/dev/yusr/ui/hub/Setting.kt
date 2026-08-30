package dev.yusr.ui.hub

/**
 * How a leaf of the mushaf is set: how big its letters are, how far they are condensed, and how
 * its words are spaced out to reach both margins.
 *
 * A printed page does not scroll, so the size is whatever puts fifteen lines on the screen at
 * once — the smaller of what the height allows and what the width allows. Measured over the whole
 * book the width is what answers on nearly every page: a line of this face runs about eighteen
 * ems, and a phone held upright is about fifteen ems wide, so the leaf is short of room across
 * long before it is short of room down. Fitted to the width alone the page is set a fifth smaller
 * than the screen would take, and the room under the fifteenth line is simply wasted.
 *
 * So the width is bought back twice over. The face's own word space is a typesetter's space,
 * 0.22 em, and a line of twelve words spends a tenth of the measure on eleven of them; the
 * printed mushaf sets its words all but touching and justifies the line with whatever that
 * leaves over, and so does this. What that still leaves short is taken out of the letters
 * themselves, which are condensed — the same size down the page, drawn a little narrower across
 * it — until the height is what decides the size and none of the leaf is left over. That is not
 * a liberty: a printed mushaf is written line by line to the measure, and its letters are
 * narrower on a full line than on an empty one, which is the same trade made by hand.
 */
internal object Setting {

    /**
     * How much of a line's box the letters themselves take up.
     *
     * Measured rather than guessed: over the whole book a line of this face inks 1.50 of its own
     * size from the tops of its marks to the feet of its descenders, and the tallest line in the
     * book inks 1.77. At this fraction an ordinary line fills seven eighths of the room it is
     * given. Condensing the letters put nearly every page against this rather than against the
     * width, so the room left between two lines was measured again with it: over all six hundred
     * and four pages, taking every letter at the full width of its box, there is no column of any
     * page where one line's ink reaches the line under it, and the closest any two come is a
     * thirtieth of a line, on page twenty-one.
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

    /**
     * How narrow the letters may be drawn, as a fraction of their drawn width.
     *
     * Condensing is what buys the page its size, and it is bought at the letterforms' expense, so
     * it stops here. At this much the height decides the size on five hundred and forty-eight of
     * the six hundred and four pages and the page is set a fifth larger than the width alone
     * would allow; the pages that would need more than this keep their proportions and are set
     * smaller, which is the right way round. A tenth or so is also what the printed page does
     * between its own loosest and tightest lines.
     */
    const val SQUEEZE = 0.87f

    /** The face's own word space, in ems, measured off the font the page is set in. */
    const val FACE_SPACE = 0.22f

    /** The word space a mushaf line is set with: a hair, and the letters have the rest. */
    const val WORD_SPACE = 0.06f

    /** What comes off each of a line's spaces before the line is measured at all. */
    const val TRIM = FACE_SPACE - WORD_SPACE

    /**
     * How far the letters of the page are condensed, as a fraction of their drawn width.
     *
     * Exactly as far as it takes to put the page's longest line inside the measure at the size
     * the height allows, and no further — a page whose lines are short is not condensed at all,
     * and one whose longest line is very long is condensed to [SQUEEZE] and set smaller for the
     * rest. So most pages are touched a little, a few are touched a lot, and the reader turning
     * between them sees the size hold steady rather than the letters change shape.
     *
     * @param perLine the height of one of the page's fifteen line boxes
     * @param widest the widest line on the page, in pixels, once its spaces are trimmed, as
     *   measured at the size [at] and undrawn by any condensing
     * @param width the width of the leaf
     * @param at the size [widest] was measured at
     */
    fun squeeze(perLine: Float, widest: Float, width: Float, at: Float): Float {
        if (widest <= 0f || at <= 0f || perLine <= 0f) return 1f
        val tallest = perLine * LINE_FILL
        return (width * FIT_MARGIN / (widest * tallest / at)).coerceIn(SQUEEZE, 1f)
    }

    /**
     * The size the page is set at, in pixels.
     *
     * @param perLine the height of one of the page's fifteen line boxes
     * @param widest the widest line on the page, in pixels, once its spaces are trimmed, as
     *   measured at the size [at] and undrawn by any condensing
     * @param width the width of the leaf
     * @param at the size [widest] was measured at
     * @param squeeze how far the letters are condensed, from [squeeze]
     */
    fun size(perLine: Float, widest: Float, width: Float, at: Float, squeeze: Float): Float {
        if (widest <= 0f || at <= 0f || squeeze <= 0f) return 0f
        return minOf(perLine * LINE_FILL, at * (width * FIT_MARGIN / (widest * squeeze)))
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
