package dev.minimalist.data.quran

import dev.minimalist.data.settings.AyahLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AyahReferenceTest {

    private val alAsr = Ayah(
        surah = 103,
        ayah = 2,
        surahName = "Al-Asr",
        arabic = "إِنَّ الْإِنسَانَ لَفِي خُسْرٍ",
        english = "Indeed, mankind is in loss.",
    )

    @Test
    fun `an arabic gate names the surah in arabic, digits included`() {
        assertEquals("العصر ١٠٣:٢", alAsr.reference(AyahLanguage.ARABIC))
    }

    @Test
    fun `the other two keep the transliterated name`() {
        assertEquals("Al-Asr 103:2", alAsr.reference(AyahLanguage.ENGLISH))
        assertEquals("Al-Asr 103:2", alAsr.reference(AyahLanguage.BOTH))
    }

    @Test
    fun `every surah has an arabic name`() {
        (1..114).forEach { surah ->
            assertEquals("surah $surah", true, SurahNames.arabic(surah)?.isNotBlank() == true)
        }
        assertEquals(null, SurahNames.arabic(0))
        assertEquals(null, SurahNames.arabic(115))
    }

    /** A number we cannot name falls back rather than losing the reference altogether. */
    @Test
    fun `an impossible surah number keeps the latin reference`() {
        val broken = alAsr.copy(surah = 200, surahName = "Nowhere")
        assertEquals("Nowhere 200:2", broken.reference(AyahLanguage.ARABIC))
    }
}
