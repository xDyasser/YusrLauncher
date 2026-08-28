package dev.yusr.data.quran

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * One passage of al-Mīzān: the run of āyāt it covers, and where to find its text.
 *
 * The run matters as much as the text. Al-Mīzān does not comment ayah by ayah — it sets out a
 * few āyāt, gives the *bayān* on all of them together, then what the traditions say — so the
 * tafsīr of 2:3 is the passage 2:1-5 that 2:3 lives inside, and a reader who taps one ayah is
 * shown the passage and told which āyāt it is about.
 */
data class TafsirPassage(
    val id: String,
    val surah: Int,
    val from: Int,
    val to: Int,
    /** The passage's own section headings: bayān, the traditions, and whatever else it has. */
    val headings: List<String>,
) {
    /** True when this passage is the tafsīr of [ayah] in [surah]. */
    fun covers(surah: Int, ayah: Int): Boolean =
        this.surah == surah && ayah in from..to

    /** "البقرة ٢:١-٥", or "الفاتحة ١:٧" where the passage is one ayah. */
    val arabicReference: String
        get() {
            val name = SurahNames.arabic(surah) ?: "$surah"
            val first = SurahNames.arabicDigits(from)
            val place = if (from == to) first else "$first-${SurahNames.arabicDigits(to)}"
            return "$name ${SurahNames.arabicDigits(surah)}:$place"
        }
}

/** One run of one kind of writing, which is all a passage of al-Mīzān is made of. */
data class TafsirBlock(
    val kind: Kind,
    val text: String,
    /**
     * Where the book sets a phrase in bold — almost always `قوله تعالى`, which is how al-Mīzān
     * says "and now the words of the ayah I am about to explain".
     */
    val emphasis: List<IntRange> = emptyList(),
) {
    enum class Kind {
        /** A section: the bayān, the traditions, the philosophical discussion. */
        HEADING,

        /** One of the āyāt the passage is about, quoted at its head as the book prints it. */
        AYAH,

        /** Al-Ṭabāṭabāʾī's own words, which is nearly all of it. */
        PROSE,

        /** The rare note the edition sets apart from the commentary. */
        FOOTNOTE,
    }
}

/** A passage with its text read in. */
data class TafsirText(val passage: TafsirPassage, val blocks: List<TafsirBlock>)

/** What the book is and whose it is, printed once at the head of the tafsīr. */
data class TafsirBook(
    val title: String,
    val arabicTitle: String,
    val attribution: String,
    val source: String?,
    val passages: List<TafsirPassage>,
)

/**
 * Al-Mīzān fī tafsīr al-Qurʾān, bundled whole.
 *
 * The Arabic original of ʿAllāma al-Ṭabāṭabāʾī's tafsīr — every ayah of the book is inside one
 * of its 594 passages, and all of it is in the APK. It is not fetched, which means the tafsīr
 * works the way everything else here works: on a phone that has never had a network and never
 * will.
 *
 * It is stored the way Mafātīḥ is, an index and a file per passage, for the same reason: the
 * book is twenty-five megabytes of Arabic, and opening the tafsīr of one ayah should read one
 * passage rather than five hundred and ninety-three others. `tools/import_almizan.py` writes
 * both, and checks as it goes that the passages tile the whole book with no gap and no overlap.
 *
 * The Markdown the source publishes is parsed here rather than at build time, so that what is
 * committed in `assets/almizan` is the book's own text, unedited, and this is the only place
 * that decides what a heading or a quoted ayah looks like on a screen.
 */
class Tafsir(private val context: Context) {

    /** The index, with every passage in it. Small enough to keep, at 594 rows. */
    suspend fun book(): TafsirBook? = withContext(Dispatchers.IO) {
        cachedBook ?: runCatching { readIndex() }.getOrNull()?.also { cachedBook = it }
    }

    /** The passage [ayah] belongs to, or null if the index cannot be read. */
    suspend fun passageFor(surah: Int, ayah: Int): TafsirPassage? =
        book()?.passages?.firstOrNull { it.covers(surah, ayah) }

    /** One passage, read now rather than at startup. */
    suspend fun text(passage: TafsirPassage): TafsirText? = withContext(Dispatchers.IO) {
        cachedText?.takeIf { it.passage.id == passage.id }?.let { return@withContext it }
        // The id comes from the index this app wrote, but it addresses a file, so it is checked
        // rather than trusted: nothing outside the tafsīr's own directory is openable through it.
        if (!passage.id.matches(SAFE_ID)) return@withContext null
        val body = runCatching { asset("$DIRECTORY/${passage.id}.md") }.getOrNull()
            ?: return@withContext null
        TafsirText(passage, parse(body)).also { cachedText = it }
    }

    /** The tafsīr of one ayah, in one call, for a screen that has an ayah and wants the text. */
    suspend fun at(surah: Int, ayah: Int): TafsirText? =
        passageFor(surah, ayah)?.let { text(it) }

    private fun readIndex(): TafsirBook = parseIndex(asset("$DIRECTORY/index.json"))

    private fun asset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    /**
     * Everything about reading the book that does not need a device, kept here so the tests can
     * hold the committed assets to it directly.
     */
    companion object {
        internal const val DIRECTORY = "almizan"
        private val SAFE_ID = Regex("^p[0-9]{3}$")

        @Volatile
        private var cachedBook: TafsirBook? = null

        /**
         * The last passage read, kept because the reader opens the same one twice — once when
         * the ayah is tapped and again when the sheet is reopened on the ayah beside it.
         */
        @Volatile
        private var cachedText: TafsirText? = null

        /** The index as it is committed: the book's names, and all 594 passages in order. */
        fun parseIndex(json: String): TafsirBook {
            val root = JSONObject(json)
            val passages = root.getJSONArray("passages").objects().map { passage ->
                TafsirPassage(
                    id = passage.getString("id"),
                    surah = passage.getInt("s"),
                    from = passage.getInt("a"),
                    to = passage.getInt("b"),
                    headings = passage.optJSONArray("h")?.let { headings ->
                        (0 until headings.length()).map { headings.getString(it) }
                    }.orEmpty(),
                )
            }
            return TafsirBook(
                title = root.getString("title"),
                arabicTitle = root.optString("arabicTitle"),
                attribution = root.optString("attribution"),
                source = root.optString("source").ifBlank { null },
                passages = passages,
            )
        }

        private fun JSONArray.objects(): List<JSONObject> =
            (0 until length()).map { getJSONObject(it) }

        /**
         * The book's Markdown, turned into blocks a screen can set.
         *
         * The shape is regular, which is why this is thirty lines rather than a parser: a `##`
         * line is a section heading, a fence is either one of the āyāt the passage is about or
         * one of the six footnotes in the whole book, `****` is the rule the edition draws under
         * the āyāt, and everything else is a paragraph. Bold runs are kept as positions rather
         * than as markup, so nothing downstream has to know what an asterisk meant.
         */
        fun parse(body: String): List<TafsirBlock> {
            val blocks = mutableListOf<TafsirBlock>()
            val paragraph = StringBuilder()
            var fence: String? = null
            val fenced = StringBuilder()

            fun flushParagraph() {
                val text = paragraph.toString().trim()
                paragraph.setLength(0)
                if (text.isNotEmpty()) blocks += emphasised(TafsirBlock.Kind.PROSE, text)
            }

            for (line in body.lineSequence()) {
                val trimmed = line.trim()
                if (fence != null) {
                    if (trimmed.startsWith("```")) {
                        val kind = if (fence == "footnote") {
                            TafsirBlock.Kind.FOOTNOTE
                        } else {
                            TafsirBlock.Kind.AYAH
                        }
                        val text = fenced.toString().split(WHITESPACE).filter { it.isNotEmpty() }
                            .joinToString(" ")
                        fenced.setLength(0)
                        fence = null
                        if (text.isNotEmpty()) blocks += emphasised(kind, text)
                    } else {
                        fenced.append(line).append('\n')
                    }
                    continue
                }

                when {
                    trimmed.startsWith("```") -> {
                        flushParagraph()
                        fence = trimmed.removePrefix("```").trim().ifEmpty { "arabic" }
                    }
                    trimmed.startsWith("#") -> {
                        flushParagraph()
                        val heading = trimmed.trimStart('#').trim()
                        if (heading.isNotEmpty()) {
                            blocks += emphasised(TafsirBlock.Kind.HEADING, heading)
                        }
                    }
                    // The rule the edition draws between the āyāt and the commentary on them.
                    // The reader draws its own, so this one is dropped rather than set as text.
                    trimmed.all { it == '*' } && trimmed.length >= 4 -> flushParagraph()
                    trimmed.isEmpty() -> flushParagraph()
                    else -> {
                        if (paragraph.isNotEmpty()) paragraph.append(' ')
                        paragraph.append(trimmed)
                    }
                }
            }
            flushParagraph()
            return blocks
        }

        private val WHITESPACE = Regex("\\s+")

        /**
         * Takes the `**` out of a line and says where the bold was.
         *
         * An odd number of markers means the edition left one open — it happens twice in the
         * whole book — and the line is then taken as it stands rather than half-emphasised.
         */
        fun emphasised(kind: TafsirBlock.Kind, text: String): TafsirBlock {
            if (!text.contains(MARKER) || text.split(MARKER).size % 2 == 0) {
                return TafsirBlock(kind, text)
            }
            val plain = StringBuilder()
            val runs = mutableListOf<IntRange>()
            var open = -1
            var index = 0
            while (index < text.length) {
                if (text.startsWith(MARKER, index)) {
                    if (open < 0) {
                        open = plain.length
                    } else {
                        runs += open until plain.length
                        open = -1
                    }
                    index += MARKER.length
                } else {
                    plain.append(text[index])
                    index++
                }
            }
            return TafsirBlock(kind, plain.toString(), runs)
        }

        private const val MARKER = "**"
    }
}
