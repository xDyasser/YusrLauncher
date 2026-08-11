package dev.yusr.data.quran

import android.content.Context
import dev.yusr.domain.SupplicationCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The adhkār and duʿāʾ that ship with the app.
 *
 * Both books are bundled rather than fetched, so nothing here depends on a network or on a host
 * still being up in five years. They arrive in two different shapes for two different reasons.
 *
 * Ḥiṣn al-Muslim is a hand-made selection in one small file, `assets/supplications.json`, where
 * every entry is a few lines of Arabic with an English rendering beside it. Mafātīḥ al-Jinān is
 * the whole book — three bābs and two appendices, 194 texts, some of them fifty thousand
 * characters long — so it is an index in `assets/mafatih/index.json` and one file per text
 * beside it, opened when that text is. Reading four lines of a taʿqīb should not parse a
 * megabyte.
 *
 * Both are flattened into the same three types below, so the screen that reads them does not know
 * or care which it has.
 *
 * Every text carries where it came from. That is not decoration — a supplication with no
 * provenance is one nobody can check, and this is the part of the app where being checkable
 * matters most.
 */
data class SupplicationEntry(
    val id: String,
    /** As the book has it, in the book's own words. */
    val title: String,
    /**
     * Only where the text has a settled English name; never invented for the sake of the slot.
     * It is a gloss on [title], not a replacement for it: the book's own heading is what names
     * a text, in either language.
     */
    val english: String?,
    /** How much Arabic is behind it, so the list can say what it is asking for. */
    val arabicLength: Int,
)

data class SupplicationSection(
    val id: String,
    val title: String,
    val subtitle: String?,
    val items: List<SupplicationEntry>,
)

data class SupplicationChapter(val title: String, val sections: List<SupplicationSection>)

data class SupplicationBook(
    val id: String,
    val title: String,
    val arabicTitle: String,
    val attribution: String,
    /** The edition, and what its non-Arabic parts are. Printed once, at the head of the book. */
    val source: String?,
    val chapters: List<SupplicationChapter>,
) {
    val count: Int get() = chapters.sumOf { chapter -> chapter.sections.sumOf { it.items.size } }
}

/** One run of one kind of writing. A long duʿāʾ is a few hundred of these. */
data class SupplicationBlock(val kind: Kind, val text: String) {
    enum class Kind {
        /** The supplication. The only part of any of this that is not somebody's commentary. */
        ARABIC,

        /** The compiler around it: when to read it, who narrated it, how many times. */
        NOTE,

        /** A rendering of the Arabic above it. Only Ḥiṣn has one, and it is English. */
        TRANSLATION,
    }
}

data class SupplicationText(
    val id: String,
    val title: String,
    val english: String?,
    val blocks: List<SupplicationBlock>,
) {
    val hasTranslation: Boolean
        get() = blocks.any { it.kind == SupplicationBlock.Kind.TRANSLATION }
}

class Supplications(private val context: Context) {

    suspend fun books(): List<SupplicationBook> = withContext(Dispatchers.IO) {
        cachedBooks ?: buildList {
            runCatching { mafatihIndex() }.getOrNull()?.let(::add)
            runCatching { hisn().first }.getOrNull()?.let(::add)
        }.also { cachedBooks = it }
    }

    suspend fun book(collection: SupplicationCollection): SupplicationBook? {
        val id = when (collection) {
            SupplicationCollection.MAFATIH -> MAFATIH
            SupplicationCollection.HISN -> HISN
        }
        return books().firstOrNull { it.id == id }
    }

    /** The other book, which stays one tap away whatever madhab is set. */
    suspend fun other(collection: SupplicationCollection): SupplicationBook? {
        val theirs = book(collection)
        return books().firstOrNull { it.id != theirs?.id }
    }

    /**
     * One text, read now rather than at startup. For Mafātīḥ that is a file per text; for Ḥiṣn
     * the whole book is small enough that it was already in hand.
     */
    suspend fun text(bookId: String, entryId: String): SupplicationText? = withContext(Dispatchers.IO) {
        when (bookId) {
            MAFATIH -> runCatching { mafatihText(entryId) }.getOrNull()
            HISN -> runCatching { hisn().second[entryId] }.getOrNull()
            else -> null
        }
    }

    // ---- Mafātīḥ al-Jinān: an index and a file per text ------------------------------------------

    private fun mafatihIndex(): SupplicationBook {
        val root = JSONObject(asset("$MAFATIH/index.json"))
        val chapters = root.getJSONArray("chapters").objects().mapIndexed { chapterIndex, chapter ->
            SupplicationChapter(
                title = chapter.getString("title"),
                sections = chapter.getJSONArray("sections").objects()
                    .mapIndexed { sectionIndex, section ->
                        SupplicationSection(
                            id = "$chapterIndex.$sectionIndex",
                            title = section.getString("title"),
                            subtitle = null,
                            items = section.getJSONArray("items").objects().map { item ->
                                SupplicationEntry(
                                    id = item.getString("id"),
                                    title = item.getString("title"),
                                    english = item.optString("english").ifBlank { null },
                                    arabicLength = item.optInt("arabic"),
                                )
                            },
                        )
                    },
            )
        }
        return SupplicationBook(
            id = MAFATIH,
            title = root.getString("title"),
            arabicTitle = root.optString("arabicTitle"),
            attribution = root.optString("attribution"),
            source = root.optString("source").ifBlank { null },
            chapters = chapters,
        )
    }

    private fun mafatihText(entryId: String): SupplicationText? {
        // The id comes from the index this app wrote, but it addresses a file, so it is checked
        // rather than trusted: nothing outside the book's own directory is openable through it.
        if (!entryId.matches(SAFE_ID)) return null
        // The title lives in the index rather than in the file, so it is looked up there. The
        // index is already in hand by the time anything is open enough to be tapped.
        val entry = (cachedBooks?.firstOrNull { it.id == MAFATIH } ?: mafatihIndex())
            .chapters.asSequence()
            .flatMap { it.sections.asSequence() }
            .flatMap { it.items.asSequence() }
            .firstOrNull { it.id == entryId }
        val root = JSONObject(asset("$MAFATIH/$entryId.json"))
        val blocks = root.getJSONArray("blocks").objects().mapNotNull { block ->
            val kind = when (block.optString("k")) {
                "a" -> SupplicationBlock.Kind.ARABIC
                "n" -> SupplicationBlock.Kind.NOTE
                "p" -> SupplicationBlock.Kind.TRANSLATION
                else -> null
            } ?: return@mapNotNull null
            SupplicationBlock(kind, block.optString("t"))
        }
        return SupplicationText(
            id = entryId,
            title = entry?.title.orEmpty(),
            english = entry?.english,
            blocks = blocks,
        )
    }

    // ---- Ḥiṣn al-Muslim: one small file, read whole ---------------------------------------------

    /**
     * The selection and its texts together, because it is a few pages: splitting it would cost a
     * second file open to save nothing.
     */
    private fun hisn(): Pair<SupplicationBook, Map<String, SupplicationText>> {
        cachedHisn?.let { return it }
        val book = JSONObject(asset(HISN_ASSET)).getJSONArray("collections").objects()
            .first { it.getString("id") == HISN }

        val texts = mutableMapOf<String, SupplicationText>()
        val sections = book.getJSONArray("sections").objects().map { section ->
            val sectionId = section.getString("id")
            SupplicationSection(
                id = sectionId,
                title = section.getString("title"),
                subtitle = section.optString("subtitle").ifBlank { null },
                items = section.getJSONArray("items").objects().mapIndexed { index, item ->
                    val id = "$sectionId.$index"
                    val arabic = item.getString("arabic")
                    val title = item.getString("title")
                    texts[id] = SupplicationText(
                        id = id,
                        title = title,
                        english = null,
                        blocks = buildList {
                            item.optString("note").ifBlank { null }
                                ?.let { add(SupplicationBlock(SupplicationBlock.Kind.NOTE, it)) }
                            add(SupplicationBlock(SupplicationBlock.Kind.ARABIC, arabic))
                            item.optString("english").ifBlank { null }?.let {
                                add(SupplicationBlock(SupplicationBlock.Kind.TRANSLATION, it))
                            }
                            item.optString("source").ifBlank { null }
                                ?.let { add(SupplicationBlock(SupplicationBlock.Kind.NOTE, it)) }
                        },
                    )
                    SupplicationEntry(id = id, title = title, english = null, arabicLength = arabic.length)
                },
            )
        }

        val result = SupplicationBook(
            id = HISN,
            title = book.getString("title"),
            arabicTitle = book.optString("arabicTitle"),
            attribution = book.optString("attribution"),
            source = book.optString("missing").ifBlank { null },
            chapters = listOf(SupplicationChapter(title = "", sections = sections)),
        ) to texts.toMap()
        cachedHisn = result
        return result
    }

    private fun asset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private companion object {
        const val MAFATIH = "mafatih"
        const val HISN = "hisn"
        const val HISN_ASSET = "supplications.json"
        val SAFE_ID = Regex("^[a-z0-9]+$")

        @Volatile
        var cachedBooks: List<SupplicationBook>? = null

        @Volatile
        var cachedHisn: Pair<SupplicationBook, Map<String, SupplicationText>>? = null
    }
}
