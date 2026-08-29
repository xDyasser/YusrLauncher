package dev.yusr.ui.hub

/**
 * The colours of tajwīd, laid across the line rather than put on its letters.
 *
 * A colour on a letter is the obvious way to do this and it cannot be done that way at all. A
 * line of text on Android is drawn one piece at a time, and it is cut into pieces wherever the
 * paint changes: a different colour on a letter starts a new piece, and each piece is handed to
 * the shaper on its own. Arabic does not survive that. The ḥarakāt are attached to their letters
 * by the font, in a table that can only reach inside one piece — so a letter coloured apart from
 * its own fatḥa loses it: the mark falls back to the baseline and is drawn over the letter it was
 * supposed to sit above. Which is what a reader sees: a coloured letter whose vowel has gone.
 *
 * Nor is including the marks in the colour enough. Cutting a word anywhere breaks the rest of the
 * font's work on it too — the joined forms, the ligatures, the variant shapes this face carries
 * for the printed mushaf. Shaping the whole book both ways and comparing the glyphs, a colour on
 * the letter alone draws 99.5% of the āyāt differently from the way the face sets them, and a
 * colour on the letter with its marks 99.2%. Only a cut at a space is free.
 *
 * So the line is drawn in one piece, in one colour, through a gradient: a shader that is the ink
 * colour everywhere except across the stretches where a coloured letter stands. The type is
 * shaped once, exactly as the face asks; the colour is painted over the top of it. What that
 * gives up is a letter and its neighbour sharing a single glyph — the alif of لا cannot be
 * coloured without the lām, because they are one shape and a shape has no seam — and that is the
 * whole of the price.
 */
internal object Bands {

    /** A stretch of a line that takes a colour of its own: [from] until [to], in pixels. */
    data class Band<T>(val from: Float, val to: Float, val paint: T)

    /**
     * The bands written as gradient stops — a fraction of the line's width, and the colour that
     * begins there.
     *
     * Every edge is stated twice, the old colour and then the new one at the same place, which is
     * what makes a hard edge out of something built to blend. Between the bands the stops carry
     * [ink], so the letters no rule falls on are the colour the page is set in.
     *
     * Bands are taken in order and clipped against the one before, so two rules landing on the
     * same letter cannot walk backwards and produce a gradient the shader will not accept. An
     * empty list means there is nothing to colour and the line should be drawn plainly, which is
     * most lines of the book.
     */
    fun <T> stops(bands: List<Band<T>>, width: Float, ink: T): List<Pair<Float, T>> {
        if (width <= 0f || bands.isEmpty()) return emptyList()

        val stops = mutableListOf(0f to ink)
        var edge = 0f
        bands.sortedBy { it.from }.forEach { band ->
            val from = (band.from / width).coerceIn(edge, 1f)
            val to = (band.to / width).coerceIn(from, 1f)
            if (to <= from) return@forEach
            stops += from to ink
            stops += from to band.paint
            stops += to to band.paint
            stops += to to ink
            edge = to
        }
        if (stops.size == 1) return emptyList()

        stops += 1f to ink
        return stops
    }
}
