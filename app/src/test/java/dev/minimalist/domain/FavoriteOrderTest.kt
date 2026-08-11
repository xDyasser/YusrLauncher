package dev.minimalist.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteOrderTest {

    private val names = listOf("phone", "messages", "mushaf", "maps")

    @Test
    fun `moving a name down puts it after the one it passed`() {
        assertEquals(
            listOf("messages", "mushaf", "phone", "maps"),
            FavoriteOrder.move(names, from = 0, to = 2),
        )
    }

    @Test
    fun `moving a name up puts it before the one it passed`() {
        assertEquals(
            listOf("phone", "maps", "messages", "mushaf"),
            FavoriteOrder.move(names, from = 3, to = 1),
        )
    }

    @Test
    fun `a move to the same place changes nothing`() {
        assertEquals(names, FavoriteOrder.move(names, from = 2, to = 2))
    }

    @Test
    fun `a move off either end of the list is not a move`() {
        assertEquals(names, FavoriteOrder.move(names, from = 0, to = -1))
        assertEquals(names, FavoriteOrder.move(names, from = 0, to = names.size))
        assertEquals(names, FavoriteOrder.move(names, from = names.size, to = 0))
        assertEquals(emptyList<String>(), FavoriteOrder.move(emptyList<String>(), from = 0, to = 0))
    }

    @Test
    fun `half a row of travel is enough to swap with the neighbour`() {
        assertEquals(0, FavoriteOrder.landingIndex(from = 0, dragPx = 49f, rowHeightPx = 100f, size = 4))
        assertEquals(1, FavoriteOrder.landingIndex(from = 0, dragPx = 51f, rowHeightPx = 100f, size = 4))
        assertEquals(2, FavoriteOrder.landingIndex(from = 3, dragPx = -51f, rowHeightPx = 100f, size = 4))
    }

    @Test
    fun `dragging past the ends stops at the first and last places`() {
        assertEquals(3, FavoriteOrder.landingIndex(from = 1, dragPx = 900f, rowHeightPx = 100f, size = 4))
        assertEquals(0, FavoriteOrder.landingIndex(from = 2, dragPx = -900f, rowHeightPx = 100f, size = 4))
    }

    @Test
    fun `an unmeasured row leaves the name where it is`() {
        assertEquals(2, FavoriteOrder.landingIndex(from = 2, dragPx = 400f, rowHeightPx = 0f, size = 4))
        assertEquals(0, FavoriteOrder.landingIndex(from = 0, dragPx = 400f, rowHeightPx = 100f, size = 0))
    }

    @Test
    fun `a name held against either edge walks the list that way`() {
        // A 1000px window, 100px rows: the margins are the top and bottom hundred pixels.
        assertEquals(-1, FavoriteOrder.creepDirection(topPx = 40f, rowHeightPx = 100f, viewportPx = 1000f))
        assertEquals(1, FavoriteOrder.creepDirection(topPx = 850f, rowHeightPx = 100f, viewportPx = 1000f))
    }

    @Test
    fun `a name in the middle of the window leaves the list alone`() {
        assertEquals(0, FavoriteOrder.creepDirection(topPx = 100f, rowHeightPx = 100f, viewportPx = 1000f))
        assertEquals(0, FavoriteOrder.creepDirection(topPx = 799f, rowHeightPx = 100f, viewportPx = 1000f))
    }

    @Test
    fun `a window too shallow for two margins never creeps`() {
        // Otherwise every position is in a margin and the list would scroll of its own accord.
        assertEquals(0, FavoriteOrder.creepDirection(topPx = 10f, rowHeightPx = 100f, viewportPx = 250f))
        assertEquals(0, FavoriteOrder.creepDirection(topPx = 10f, rowHeightPx = 0f, viewportPx = 1000f))
    }

    @Test
    fun `a drag and its landing agree on where the name ends up`() {
        // What the home screen does on every pointer move: work out the row, then move to it.
        val from = 0
        val to = FavoriteOrder.landingIndex(from, dragPx = 260f, rowHeightPx = 100f, size = names.size)
        assertEquals(3, to)
        assertEquals(listOf("messages", "mushaf", "maps", "phone"), FavoriteOrder.move(names, from, to))
    }
}
