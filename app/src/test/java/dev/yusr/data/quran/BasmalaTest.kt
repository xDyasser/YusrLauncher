package dev.yusr.data.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Three facts about the book, held as constants. Nothing here parses an ayah or cuts one — the
 * downloaded text already numbers the basmala the way the mushaf does — so what is worth testing
 * is only that the two sūrahs which are exceptions stay exceptions.
 */
class BasmalaTest {

    @Test
    fun `the basmala stands over every sura but al-Tawba`() {
        (1..114).forEach { surah ->
            assertEquals(surah.toString(), surah != 9, Basmala.heads(surah))
        }
    }

    @Test
    fun `it is an ayah in al-Fatiha and nowhere else`() {
        assertTrue(Basmala.isAyah(1))
        (2..114).forEach { surah ->
            assertFalse(surah.toString(), Basmala.isAyah(surah))
        }
    }

    @Test
    fun `the heading is printed above every sura but al-Fatiha and al-Tawba`() {
        (1..114).forEach { surah ->
            // Al-Fātiḥa prints it as its first ayah, so the reader must not also head it with one.
            val expected = surah != 1 && surah != 9
            assertEquals(surah.toString(), expected, Basmala.headingBelongsAbove(surah, 1))
        }
    }

    @Test
    fun `the heading is printed at the top of a sura and nowhere else in it`() {
        (1..114).forEach { surah ->
            assertFalse(surah.toString(), Basmala.headingBelongsAbove(surah, 2))
            assertFalse(surah.toString(), Basmala.headingBelongsAbove(surah, 7))
        }
    }

    @Test
    fun `a number that is not a sura gets nothing`() {
        listOf(0, -1, 115, 1_000).forEach { surah ->
            assertFalse(surah.toString(), Basmala.heads(surah))
            assertFalse(surah.toString(), Basmala.headingBelongsAbove(surah, 1))
        }
    }

    @Test
    fun `the heading is spelled as the edition spells al-Fatiha's first ayah`() {
        // The exact bytes of 1:1 in the Uthmani Ḥafṣ text the mushaf is downloaded from. If the
        // heading drifts from these, it is set in a different hand from the āyāt beneath it.
        assertEquals("بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ", Basmala.ARABIC)
    }
}
