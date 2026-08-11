package dev.yusr.data.net

import dev.yusr.data.db.QuranAyahEntity
import dev.yusr.data.quran.QuranSource
import dev.yusr.data.quran.SurahNames
import dev.yusr.data.quran.UthmaniText
import org.json.JSONObject

/**
 * Fetches the Qur'an once and puts it in the database.
 *
 * After this runs the app never needs a network again for the gate or the mushaf: the ayah shown
 * when you try to open something is read from local storage, in airplane mode, forever.
 *
 * The text is the Uthmani Ḥafṣ edition, and the translation is Ali Quli Qarai's — a Shīʿī one.
 * Both come from the same corpus, verse for verse, which is the reason for taking them from there
 * rather than from two places: ayah 4,231 of one file is ayah 4,231 of the other.
 *
 * The important property of this edition is that it numbers the book the way the mushaf does. The
 * basmala is al-Fātiḥa's first ayah and is *not* folded into the first ayah of any other sūrah, so
 * al-Baqara 2:1 arrives as *alif lām mīm* and is stored as it arrived. Nothing is cut, here or
 * later; the reader draws the heading over a sūrah itself, from [dev.yusr.data.quran.Basmala].
 */
class QuranDownloader(private val quran: QuranSource) {

    /** True when the text is in place — either already there, or downloaded just now. */
    suspend fun download(force: Boolean = false): Boolean {
        if (!force && quran.downloadedCount() >= EXPECTED_AYAT) return true

        val arabic = parse(Http.getText(ARABIC_URL) ?: return false) ?: return false
        if (arabic.size != EXPECTED_AYAT) return false
        // The translation is optional: an Arabic-only mushaf is still a mushaf.
        val english = Http.getText(ENGLISH_URL)
            ?.let { parse(it) }
            ?.takeIf { it.size == EXPECTED_AYAT }

        val rows = arabic.mapIndexed { index, verse ->
            QuranAyahEntity(
                // The corpus is in mushaf order, so the running count is the ayah's number in the
                // book — 1 for al-Fātiḥa 1:1 through 6236 for al-Nās 114:6.
                id = index + 1,
                surah = verse.surah,
                ayah = verse.ayah,
                surahName = SurahNames.transliterated(verse.surah) ?: "surah ${verse.surah}",
                // Stored as it arrived, but for the one place this edition breaks a word in two.
                arabic = UthmaniText.repaired(verse.text),
                // Verse for verse against the Arabic, and only if the whole translation came
                // down — a half-aligned translation would put the wrong English under an ayah,
                // which is worse than none.
                english = english?.getOrNull(index)
                    ?.takeIf { it.surah == verse.surah && it.ayah == verse.ayah }
                    ?.text,
            )
        }
        quran.replaceAll(rows)
        return true
    }

    private data class Verse(val surah: Int, val ayah: Int, val text: String)

    /** `{"quran":[{"chapter":2,"verse":1,"text":"…"}, …]}` — one flat list, in mushaf order. */
    private fun parse(body: String): List<Verse>? = runCatching {
        val verses = JSONObject(body).getJSONArray("quran")
        buildList {
            for (i in 0 until verses.length()) {
                val verse = verses.getJSONObject(i)
                add(
                    Verse(
                        surah = verse.getInt("chapter"),
                        ayah = verse.getInt("verse"),
                        text = verse.getString("text").trim(),
                    ),
                )
            }
        }
    }.getOrNull()

    private companion object {
        /** Uthmani Ḥafṣ, and Ali Quli Qarai's translation. Same corpus, same numbering. */
        const val ARABIC_URL =
            "https://raw.githubusercontent.com/fawazahmed0/quran-api/1/editions/ara-quranuthmanihaf.json"
        const val ENGLISH_URL =
            "https://raw.githubusercontent.com/fawazahmed0/quran-api/1/editions/eng-aliquliqarai.json"

        /** A partial download is worse than none, so anything but the whole book is refused. */
        const val EXPECTED_AYAT = 6236
    }
}
