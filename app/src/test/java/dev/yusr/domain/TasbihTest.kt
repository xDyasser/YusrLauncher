package dev.yusr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // ---- the plain cycles are one phase with no phrase ---------------------------------------

    @Test
    fun `a plain cycle is a single phase as long as itself and has nothing to say`() {
        Tasbih.Cycle.entries.filter { !it.isSequence }.forEach { cycle ->
            assertEquals(1, cycle.phases.size)
            assertEquals(cycle.length, cycle.phases.single().length)
            assertNull(cycle.phases.single().arabic)
            // Inside a single phase the phase position is the cycle position.
            val progress = Tasbih.progress(7, cycle)
            assertEquals(0, progress.phaseIndex)
            assertEquals(progress.inCycle, progress.inPhase)
            assertFalse(progress.justClosedAPhase)
        }
    }

    // ---- the tasbīḥ of al-Zahrāʾ -------------------------------------------------------------

    @Test
    fun `the Zahra cycle is thirty-four, thirty-three, thirty-three, a hundred in all`() {
        val cycle = Tasbih.Cycle.ZAHRA
        assertEquals(100, cycle.length)
        assertEquals(listOf(34, 33, 33), cycle.phases.map { it.length })
        assertTrue(cycle.isSequence)
        assertEquals("Allāhu akbar", cycle.phases[0].transliteration)
        assertEquals("al-ḥamdu lillāh", cycle.phases[1].transliteration)
        assertEquals("subḥāna llāh", cycle.phases[2].transliteration)
    }

    @Test
    fun `the phase changes on the bead after each boundary, not on the boundary`() {
        val cycle = Tasbih.Cycle.ZAHRA
        assertEquals(0, Tasbih.progress(1, cycle).phaseIndex)
        assertEquals(0, Tasbih.progress(34, cycle).phaseIndex)
        assertEquals(1, Tasbih.progress(35, cycle).phaseIndex)
        assertEquals(1, Tasbih.progress(67, cycle).phaseIndex)
        assertEquals(2, Tasbih.progress(68, cycle).phaseIndex)
        assertEquals(2, Tasbih.progress(100, cycle).phaseIndex)
        // And round again.
        assertEquals(0, Tasbih.progress(101, cycle).phaseIndex)
    }

    @Test
    fun `the position inside a phase starts again at one with each new phrase`() {
        val cycle = Tasbih.Cycle.ZAHRA
        assertEquals(34, Tasbih.progress(34, cycle).inPhase)
        assertEquals(1, Tasbih.progress(35, cycle).inPhase)
        assertEquals(33, Tasbih.progress(67, cycle).inPhase)
        assertEquals(1, Tasbih.progress(68, cycle).inPhase)
        assertEquals(33, Tasbih.progress(100, cycle).inPhase)
    }

    @Test
    fun `at nothing the phrase shown is the first one, because that is what the next bead is`() {
        val progress = Tasbih.progress(0, Tasbih.Cycle.ZAHRA)
        assertEquals(0, progress.phaseIndex)
        assertEquals(0, progress.inPhase)
        assertEquals("Allāhu akbar", progress.phase.transliteration)
    }

    @Test
    fun `a phase closing and a cycle closing are never both true on one bead`() {
        val cycle = Tasbih.Cycle.ZAHRA
        (0..201).forEach { total ->
            val progress = Tasbih.progress(total, cycle)
            assertFalse("$total", progress.justClosedAPhase && progress.justClosedACycle)
        }
        assertTrue(Tasbih.progress(34, cycle).justClosedAPhase)
        assertTrue(Tasbih.progress(67, cycle).justClosedAPhase)
        assertFalse(Tasbih.progress(100, cycle).justClosedAPhase)
        assertTrue(Tasbih.progress(100, cycle).justClosedACycle)
        assertFalse(Tasbih.progress(33, cycle).justClosedAPhase)
        assertFalse(Tasbih.progress(35, cycle).justClosedAPhase)
    }

    @Test
    fun `the sequence label names the phrase and counts inside it`() {
        assertEquals("12 of 34 · Allāhu akbar", Tasbih.progress(12, Tasbih.Cycle.ZAHRA).label)
        assertEquals("1 of 33 · al-ḥamdu lillāh", Tasbih.progress(35, Tasbih.Cycle.ZAHRA).label)
        assertEquals("33 of 33 · subḥāna llāh", Tasbih.progress(100, Tasbih.Cycle.ZAHRA).label)
    }
}
