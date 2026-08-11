package dev.minimalist.data.quran

import android.content.Context
import dev.minimalist.data.db.MinimalistDatabase
import dev.minimalist.data.db.QuranAyahEntity
import dev.minimalist.data.settings.AyahLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** One ayah, ready to put on a screen. */
data class Ayah(
    val surah: Int,
    val ayah: Int,
    val surahName: String,
    val arabic: String,
    val english: String?,
) {
    /** "al-Asr 103:2" */
    val reference: String get() = "$surahName $surah:$ayah"

    /** "العصر ١٠٣:٢" — the same reference, set the way the ayah above it is. */
    val arabicReference: String
        get() {
            val name = SurahNames.arabic(surah) ?: return reference
            return "$name ${SurahNames.arabicDigits(surah)}:${SurahNames.arabicDigits(ayah)}"
        }

    /**
     * The reference in whichever script the ayah itself is being read in. An Arabic-only gate
     * with a transliterated sūrah name under it is half a translation, which is worse than
     * either whole one.
     */
    fun reference(language: AyahLanguage): String =
        if (language == AyahLanguage.ARABIC) arabicReference else reference
}

/**
 * Where the ayah at the gate comes from.
 *
 * The full Qur'an arrives by download and lives in the database afterwards. Until then — and
 * for good if the network is never given — a small bundled set stands in, so the gate is never
 * without one. There is no state in which the gate has nothing to show.
 */
class QuranSource(private val context: Context) {

    private val dao = MinimalistDatabase.get(context).quranAyahDao()

    @Volatile
    private var cachedBundled: List<Ayah>? = null

    suspend fun random(): Ayah? {
        val downloaded = runCatching { dao.random() }.getOrNull()
        return downloaded?.toAyah() ?: bundled().randomOrNull()
    }

    /**
     * The ayah at [surah]:[ayah] — or, if the Qur'an has not been downloaded, the first bundled
     * ayah at or after it.
     *
     * The fallback is what keeps a reference the bundled set has never heard of from showing
     * nothing at all. It also keeps stepping honest: whoever moves on from what is on the screen
     * moves on from the ayah they were actually shown, so the two dozen bundled āyāt are read one
     * after another rather than the same one being handed back until the bookmark catches up.
     */
    suspend fun at(surah: Int, ayah: Int): Ayah? {
        val downloaded = runCatching { dao.at(surah, ayah) }.getOrNull()
        if (downloaded != null) return downloaded.toAyah()
        val bundled = bundled()
        val from = key(surah, ayah)
        // Wraps, because a bookmark past the last bundled ayah is otherwise a blank card.
        return bundled.firstOrNull { key(it.surah, it.ayah) >= from } ?: bundled.firstOrNull()
    }

    suspend fun downloadedCount(): Int = runCatching { dao.count() }.getOrDefault(0)

    /**
     * One sūrah in full, or an empty list if the Qur'an has not been downloaded.
     *
     * The bundled handful is deliberately not used as a fallback here. It is two dozen āyāt drawn
     * from across the whole book — enough to put one at a gate, and nowhere near enough to read a
     * sūrah from. A reader showing three of al-Kahf's hundred and ten would be a broken reader
     * pretending to work, so it says plainly that the text is not there yet instead.
     */
    suspend fun surah(surah: Int): List<Ayah> =
        runCatching { dao.surah(surah).map { it.toAyah() } }.getOrDefault(emptyList())

    suspend fun replaceAll(ayat: List<QuranAyahEntity>) {
        dao.clear()
        // Room takes the lot in one statement badly; chunks keep the transaction sane.
        ayat.chunked(WRITE_CHUNK).forEach { dao.upsertAll(it) }
    }

    private suspend fun bundled(): List<Ayah> = cachedBundled ?: withContext(Dispatchers.IO) {
        val parsed = runCatching {
            val json = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val surah = item.getInt("surah")
                val ayah = item.getInt("ayah")
                Ayah(
                    surah = surah,
                    ayah = ayah,
                    surahName = item.getString("surahName"),
                    arabic = item.getString("arabic"),
                    english = item.optString("english").takeIf { it.isNotBlank() },
                )
            }
        }.getOrDefault(emptyList()).sortedBy { key(it.surah, it.ayah) }
        cachedBundled = parsed
        parsed
    }

    /** Mushaf order as one number, so two references can be compared as they are read. */
    private fun key(surah: Int, ayah: Int) = surah * 1_000 + ayah

    private fun QuranAyahEntity.toAyah() = Ayah(
        surah = surah,
        ayah = ayah,
        surahName = surahName,
        // Read straight out. The basmala was taken off ayah 1 where it is a heading before the row
        // was ever written — on download, or by the migration that fixed a book already stored —
        // so there is nothing left here to correct.
        arabic = arabic,
        english = english,
    )

    private companion object {
        const val ASSET = "ayat_fallback.json"
        const val WRITE_CHUNK = 500
    }
}
