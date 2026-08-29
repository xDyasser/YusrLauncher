package dev.yusr.ui.hub

/**
 * How a leaf of the mushaf is set: how big its letters are, and how its words are spaced out to
 * reach both margins.
 *
 * A printed page does not scroll, so the size is whatever puts fifteen lines on the screen at
 * once — the smaller of what the height allows and what the width allows. Measured over the whole
 * book the width is what answers on nearly every page: a line of this face runs about eighteen
 * ems, and a phone held upright is about twenty ems wide, so the leaf is short of room across
 * long before it is short of room down.
 *
 * Which is why the width is not to be spent carelessly. The face's own word space is a
 * typesetter's space, 0.22 em; a line of twelve words spends a tenth of the measure on eleven of
 * them, and a tenth of the measure is a tenth off the size every line on the page is set at. The
 * printed mushaf does not do that — it sets its words all but touching and justifies the line
 * with whatever that leaves over — and neither does this.
 */
internal object Setting {

    /**
     * How much of a line's box the letters themselves take up.
     *
     * Measured rather than guessed: over the whole book a line of this face inks 1.52 of its own
     * size from the tops of its marks to the feet of its descenders, and the tallest line in the
     * book inks 1.78. At this fraction an ordinary line fills seven eighths of the room it is
     * given, and on the eight pages of the six hundred and four where the height is what answers
     * at all, the tall lines lean into their neighbours by at most a thirtieth of a line — which
     * is what the printed page does too, and at a mark's width apart nobody has ever noticed.
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
     * @param perLine the height of one of the page's fifteen line boxes
     * @param widest the widest line on the page, in pixels, once its spaces are trimmed, as
     *   measured at the size [at]
     * @param width the width of the leaf
     * @param at the size [widest] was measured at
     */
    fun size(perLine: Float, widest: Float, width: Float, at: Float): Float {
        if (widest <= 0f || at <= 0f) return 0f
        return minOf(perLine * LINE_FILL, at * (width * FIT_MARGIN / widest))
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
     * @param trim [TRIM] at the size the line is set at
     */
    fun spacing(natural: Float, gaps: Int, width: Float, trim: Float): Float {
        if (gaps <= 0) return 0f
        val tight = natural - trim * gaps
        return ((width - tight) / gaps).coerceAtLeast(0f) - trim
    }
}
