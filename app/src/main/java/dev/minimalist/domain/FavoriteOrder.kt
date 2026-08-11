package dev.minimalist.domain

import kotlin.math.roundToInt

/**
 * Moving a favourite up or down the home screen.
 *
 * The home screen's list is the one place in this app where the order is the user's own — nothing
 * infers it, and nothing sorts it back. The arithmetic lives here, away from the composition, so
 * that "where does the name land?" can be answered without a phone.
 */
object FavoriteOrder {

    /**
     * [items] with the entry at [from] taken out and put back at [to].
     *
     * Out-of-range indices give the list back untouched: a drag that ends outside the list is a
     * drag that did not happen, not one that throws.
     */
    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (from !in items.indices || to !in items.indices || from == to) return items
        val moved = items.toMutableList()
        moved.add(to, moved.removeAt(from))
        return moved
    }

    /**
     * Where a name dragged [dragPx] from row [from] would land, given rows of [rowHeightPx].
     *
     * Half a row's travel is enough to swap with the neighbour, which is what a finger expects:
     * the dragged name is level with the one it is displacing at exactly that point.
     */
    fun landingIndex(from: Int, dragPx: Float, rowHeightPx: Float, size: Int): Int {
        if (size <= 0) return 0
        // Before the first layout pass there is no row height to divide by, and nothing has been
        // drawn for the finger to have moved past either.
        if (rowHeightPx <= 0f) return from.coerceIn(0, size - 1)
        val rows = (dragPx / rowHeightPx).roundToInt()
        return (from + rows).coerceIn(0, size - 1)
    }

    /**
     * Which way the list should walk under a name held at [topPx] from the top of the visible
     * list: -1 towards the first name, 1 towards the last, 0 for a name that is nowhere near an
     * edge.
     *
     * The margin is one row deep at each end, which is what makes the gesture possible at all —
     * a favourite can be dragged past the bottom of a list taller than the screen without the
     * finger ever leaving the glass.
     */
    fun creepDirection(topPx: Float, rowHeightPx: Float, viewportPx: Float): Int {
        // Nothing has been measured yet, or the window is too shallow for a margin at each end
        // to mean anything: standing still is the honest answer.
        if (rowHeightPx <= 0f || viewportPx < rowHeightPx * 3) return 0
        return when {
            topPx < rowHeightPx -> -1
            topPx + rowHeightPx > viewportPx - rowHeightPx -> 1
            else -> 0
        }
    }
}
