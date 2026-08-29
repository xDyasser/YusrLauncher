package dev.yusr.ui.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stops are handed straight to a shader, which is unforgiving in one particular way: a stop
 * that goes backwards along the line is not a mistake it reports, it is a gradient it draws
 * wrong. So the arithmetic is held here, away from the page, where two rules landing on the same
 * letter can be tried without a screen.
 *
 * The colours are strings in these tests. Nothing in [Bands] looks at what it is painting with,
 * and a test that does not have to build a `Color` is a test that runs anywhere.
 */
class BandsTest {

    @Test
    fun `a line with nothing to colour is left alone`() {
        assertEquals(emptyList<Pair<Float, String>>(), Bands.stops(emptyList(), 100f, "ink"))
    }

    @Test
    fun `a band is stated twice at each of its edges, so the colour changes rather than blends`() {
        val stops = Bands.stops(listOf(Bands.Band(10f, 20f, "red")), 100f, "ink")
        assertEquals(
            listOf(
                0f to "ink",
                0.1f to "ink",
                0.1f to "red",
                0.2f to "red",
                0.2f to "ink",
                1f to "ink",
            ),
            stops,
        )
    }

    @Test
    fun `the stops never walk backwards`() {
        val bands = listOf(
            Bands.Band(40f, 60f, "green"),
            Bands.Band(10f, 20f, "red"),
            // Two rules on one letter, and a third that starts inside the second.
            Bands.Band(10f, 20f, "blue"),
            Bands.Band(15f, 30f, "purple"),
        )
        val stops = Bands.stops(bands, 100f, "ink")
        stops.zipWithNext { a, b -> assertTrue("$a then $b", b.first >= a.first) }
        assertTrue(stops.all { it.first in 0f..1f })
    }

    @Test
    fun `a band the one before it has already covered is dropped`() {
        val stops = Bands.stops(
            listOf(Bands.Band(10f, 40f, "red"), Bands.Band(20f, 30f, "green")),
            100f,
            "ink",
        )
        assertTrue("green survived: $stops", stops.none { it.second == "green" })
    }

    @Test
    fun `a band of no width is not a band`() {
        assertEquals(emptyList<Pair<Float, String>>(), Bands.stops(listOf(Bands.Band(10f, 10f, "red")), 100f, "ink"))
    }

    @Test
    fun `a line of no width has nowhere to put a colour`() {
        assertEquals(emptyList<Pair<Float, String>>(), Bands.stops(listOf(Bands.Band(0f, 10f, "red")), 0f, "ink"))
    }

    @Test
    fun `a band running off the end of the line is cut at the margin`() {
        val stops = Bands.stops(listOf(Bands.Band(90f, 140f, "red")), 100f, "ink")
        assertTrue(stops.all { it.first <= 1f })
        assertEquals(1f, stops.filter { it.second == "red" }.maxOf { it.first }, 0f)
    }

    @Test
    fun `the bands come out in the order they stand on the line`() {
        val stops = Bands.stops(
            listOf(Bands.Band(60f, 70f, "green"), Bands.Band(10f, 20f, "red")),
            100f,
            "ink",
        )
        val painted = stops.map { it.second }.filter { it != "ink" }.distinct()
        assertEquals(listOf("red", "green"), painted)
    }
}
