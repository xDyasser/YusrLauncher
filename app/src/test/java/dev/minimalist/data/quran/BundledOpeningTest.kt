package dev.minimalist.data.quran

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ayah a fresh install opens on comes out of the bundled set by reference, and the lookup
 * that finds it falls back to the next ayah along rather than failing loudly. Drop 94:6 from the
 * asset and the home screen would quietly open on something else, so the asset is checked here.
 */
class BundledOpeningTest {

    private val asset = File("src/main/assets/ayat_fallback.json").readText()

    @Test
    fun `the bundled set carries ash-Sharh 94-6`() {
        val entry = Regex("""\{[^{}]*"surah":\s*94,\s*"ayah":\s*6,[^{}]*}""")
            .find(asset)
        assertTrue("94:6 is missing from ayat_fallback.json", entry != null)
        val arabic = Regex(""""arabic":\s*"([^"]*)"""").find(entry!!.value)?.groupValues?.get(1)
        assertTrue("94:6 has lost its Arabic text", arabic?.isNotBlank() == true)
    }
}
