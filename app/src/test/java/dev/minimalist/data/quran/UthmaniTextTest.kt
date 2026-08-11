package dev.minimalist.data.quran

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Like the basmala, the risk here is doing too much rather than too little: a repair that closed
 * up a real word break would run two words of the Qur'an into one. So most of these check that
 * text comes back exactly as it went in.
 */
class UthmaniTextTest {

    @Test
    fun `a tanwin split from its alif is closed up`() {
        // Al-Baqara 2:286 — "iṣran", written by this edition as إِصۡرࣰ + space + ا.
        assertEquals("إِصۡرࣰا كَمَا", UthmaniText.repaired("إِصۡرࣰ ا كَمَا"))
    }

    @Test
    fun `a tanwin split from its alif maqsura is closed up`() {
        assertEquals("هُدࣰى", UthmaniText.repaired("هُدࣰ ى"))
    }

    @Test
    fun `each of the three tanwin marks is closed`() {
        assertEquals("رࣰا", UthmaniText.repaired("رࣰ ا"))
        assertEquals("رࣱا", UthmaniText.repaired("رࣱ ا"))
        assertEquals("رࣲا", UthmaniText.repaired("رࣲ ا"))
    }

    @Test
    fun `a word boundary after a tanwin is a word boundary`() {
        // "…ࣰ" followed by a word opening on wāw or yāʾ is two words in the mushaf too, and the
        // space between them has to survive.
        val andSo = "خَيۡرࣰ وَهُوَ"
        assertEquals(andSo, UthmaniText.repaired(andSo))
        val theyKnow = "عِلۡمࣰ يَعۡلَمُونَ"
        assertEquals(theyKnow, UthmaniText.repaired(theyKnow))
    }

    @Test
    fun `text with no broken tanwin is handed back untouched`() {
        val fatiha = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"
        assertEquals(fatiha, UthmaniText.repaired(fatiha))
        assertEquals("الٓمٓ", UthmaniText.repaired("الٓمٓ"))
        assertEquals("", UthmaniText.repaired(""))
    }

    @Test
    fun `an ordinary space before an alif is left alone`() {
        // No tanwīn in front of it, so it is an ordinary break between two words.
        val plain = "وَلَا ا"
        assertEquals(plain, UthmaniText.repaired(plain))
    }

    @Test
    fun `repairing twice changes nothing the second time`() {
        val once = UthmaniText.repaired("إِصۡرࣰ ا كَمَا")
        assertEquals(once, UthmaniText.repaired(once))
    }
}
