package dev.yusr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TasbihTest {

    @Test
    fun `a fresh counter is at nothing rather than at one`() {
        val progress = Tasbih.progress(0, Tasbih.Cycle.THIRTY_THREE)
        assertEquals(0, progress.inCycle)
        assertEquals(0, progress.completed)
        assertFalse(progress.justClosedACycle)
    }

    @Test
    fun `the position inside a set runs one to the length, never zero`() {
        assertEquals(1, Tasbih.progress(1, Tasbih.Cycle.THIRTY_THREE).inCycle)
        assertEquals(33, Tasbih.progress(33, Tasbih.Cycle.THIRTY_THREE).inCycle)
        // The bead after a set closes is the first of the next one, not the thirty-fourth.
        assertEquals(1, Tasbih.progress(34, Tasbih.Cycle.THIRTY_THREE).inCycle)
    }

    @Test
    fun `closing a set is announced once and only on the closing bead`() {
        assertFalse(Tasbih.progress(32, Tasbih.Cycle.THIRTY_THREE).justClosedACycle)
        assertTrue(Tasbih.progress(33, Tasbih.Cycle.THIRTY_THREE).justClosedACycle)
        assertFalse(Tasbih.progress(34, Tasbih.Cycle.THIRTY_THREE).justClosedACycle)
        assertTrue(Tasbih.progress(66, Tasbih.Cycle.THIRTY_THREE).justClosedACycle)
    }

    @Test
    fun `completed sets count up as the total passes each length`() {
        assertEquals(0, Tasbih.progress(32, Tasbih.Cycle.THIRTY_THREE).completed)
        assertEquals(1, Tasbih.progress(33, Tasbih.Cycle.THIRTY_THREE).completed)
        assertEquals(3, Tasbih.progress(99, Tasbih.Cycle.THIRTY_THREE).completed)
        assertEquals(1, Tasbih.progress(100, Tasbih.Cycle.HUNDRED).completed)
    }

    @Test
    fun `the label is the thing the screen prints`() {
        assertEquals("11 of 33", Tasbih.progress(11, Tasbih.Cycle.THIRTY_THREE).label)
        assertEquals("34 of 34", Tasbih.progress(34, Tasbih.Cycle.THIRTY_FOUR).label)
    }

    @Test
    fun `a bead back never goes below nothing`() {
        assertEquals(0, Tasbih.decrement(0))
        assertEquals(0, Tasbih.decrement(1))
        assertEquals(9, Tasbih.decrement(10))
    }

    @Test
    fun `a negative total is treated as an empty one rather than trusted`() {
        assertEquals(1, Tasbih.increment(-5))
        assertEquals(0, Tasbih.progress(-5, Tasbih.Cycle.HUNDRED).total)
    }
}
