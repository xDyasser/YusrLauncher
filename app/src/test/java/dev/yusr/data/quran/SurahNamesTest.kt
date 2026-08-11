package dev.yusr.data.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurahNamesTest {

    @Test
    fun `the counts add up to the whole Qur'an`() {
        // If this fails the reader will run off the end of a sūrah, or stop short of it, and a
        // recitation download will fetch the wrong number of files.
        assertEquals(6236, (1..SurahNames.COUNT).sumOf { SurahNames.ayahCount(it) })
    }

    @Test
    fun `every surah has a name in both scripts and a length`() {
        (1..SurahNames.COUNT).forEach { surah ->
            assertNotNull("$surah arabic", SurahNames.arabic(surah))
            assertNotNull("$surah transliterated", SurahNames.transliterated(surah))
            assertTrue("$surah length", SurahNames.ayahCount(surah) > 0)
        }
    }

    @Test
    fun `the well known lengths are right`() {
        assertEquals(7, SurahNames.ayahCount(1))
        assertEquals(286, SurahNames.ayahCount(2))
        assertEquals(110, SurahNames.ayahCount(18))
        assertEquals(6, SurahNames.ayahCount(114))
    }

    @Test
    fun `nothing is a surah outside one to a hundred and fourteen`() {
        assertNull(SurahNames.arabic(0))
        assertNull(SurahNames.arabic(115))
        assertEquals(0, SurahNames.ayahCount(0))
        assertEquals(0, SurahNames.ayahCount(115))
    }

    @Test
    fun `the place of revelation is right for the ones everybody knows`() {
        assertTrue(SurahNames.isMakki(1))
        assertTrue(SurahNames.isMakki(18))
        assertTrue(!SurahNames.isMakki(2))
        assertTrue(!SurahNames.isMakki(9))
        assertTrue(!SurahNames.isMakki(24))
    }

    @Test
    fun `the subtitle is the line the reader prints`() {
        assertEquals("Sūra 18 · 110 āyāt · Makkī", SurahNames.subtitle(18))
        assertEquals("Sūra 2 · 286 āyāt · Madanī", SurahNames.subtitle(2))
    }

    @Test
    fun `the index is every surah, once, in order`() {
        val all = SurahNames.all()
        assertEquals(114, all.size)
        assertEquals(1, all.first())
        assertEquals(114, all.last())
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `the next ayah is the next one in the mushaf`() {
        assertEquals(1 to 2, SurahNames.next(1, 1))
        assertEquals(2 to 256, SurahNames.next(2, 255))
    }

    @Test
    fun `the end of a surah runs on into the one after it`() {
        assertEquals(2 to 1, SurahNames.next(1, 7))
        assertEquals(19 to 1, SurahNames.next(18, 110))
    }

    @Test
    fun `the end of the book comes back to the opening`() {
        assertEquals(1 to 1, SurahNames.next(114, 6))
    }

    /** A bookmark on a number that is not a sūrah starts again rather than going further wrong. */
    @Test
    fun `an impossible reference starts over`() {
        assertEquals(1 to 1, SurahNames.next(0, 1))
        assertEquals(1 to 1, SurahNames.next(115, 1))
    }

    /** Stepping from every ayah in order visits all 6,236 and lands back where it started. */
    @Test
    fun `stepping through the whole Qur'an is a single cycle`() {
        var reference = 1 to 1
        val seen = HashSet<Pair<Int, Int>>()
        repeat(6236) {
            assertTrue("repeated $reference", seen.add(reference))
            reference = SurahNames.next(reference.first, reference.second)
        }
        assertEquals(1 to 1, reference)
    }

    @Test
    fun `eastern digits are used for the numbers beside Arabic names`() {
        assertEquals("١٠٣", SurahNames.arabicDigits(103))
        assertEquals("٦٢٣٦", SurahNames.arabicDigits(6236))
    }
}
