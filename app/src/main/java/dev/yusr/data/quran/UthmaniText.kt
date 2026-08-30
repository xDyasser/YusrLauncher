package dev.yusr.data.quran

/**
 * The two repairs the downloaded Uthmani Ḥafṣ text needs.
 *
 * That edition writes an open tanwīn over a letter and then puts the space *before* the alif
 * carrying it, so `إِصۡرࣰ ا` arrives as two words where the mushaf has one. It happens about two
 * and a half thousand times, which is a page of the reader broken every few pages.
 *
 * And it opens a hundred and ninety-nine āyāt with a ۞, the mark that stands at the head of a
 * rubʿ. That mark belongs to the page rather than to the ayah — [MushafLayout] knows which word
 * of the book a quarter opens on and [MushafPage] sets the mark there itself — so an edition that
 * carries one in the text puts a second one on the page beside it, and prints one in the middle
 * of any ayah quoted on its own elsewhere in the app.
 *
 * Nothing else about the text is touched. No word begins with a bare alif in this orthography — a
 * word opening on one is written with hamza or waṣla — so a space in exactly this position is
 * never a word break, and closing it cannot run two words together; and the ۞ is never anywhere
 * but the first character of the ayah, so taking it from there takes nothing else.
 */
object UthmaniText {

    /** Open fatḥatān, ḍammatān and kasratān, each split from the alif or alif maqṣūra after it. */
    private val SPLIT_TANWIN: List<String> = listOf(
        "ࣰ ا", "ࣰ ى",
        "ࣱ ا", "ࣱ ى",
        "ࣲ ا", "ࣲ ى",
    )

    /** The same pairs with the space taken out — what each of [SPLIT_TANWIN] should have been. */
    private val JOINED_TANWIN: List<String> = SPLIT_TANWIN.map { it.filterNot { c -> c == ' ' } }

    /** ARABIC START OF RUB EL HIZB — the ۞ this edition opens a quarter's first ayah with. */
    private const val RUB_EL_HIZB = '\u06DE'

    /**
     * [text] with every broken tanwīn closed up and the page's own rubʿ mark taken off the front,
     * and nothing else changed.
     *
     * Done as the text is stored, and again wherever a stored ayah is read: an install that
     * fetched the book before this was written is not going to fetch it a second time.
     */
    fun repaired(text: String): String {
        var repaired = text.removePrefix(RUB_EL_HIZB.toString())
        SPLIT_TANWIN.forEachIndexed { index, split ->
            repaired = repaired.replace(split, JOINED_TANWIN[index])
        }
        return repaired
    }

    /**
     * The words of an ayah, counted the way the printed mushaf counts them.
     *
     * The page layout says things like "line 7 ends at the fourth word of 2:25", so the reader
     * and the layout have to agree on what a word is. Almost everywhere that is simply the text
     * split at its spaces — but in four āyāt the printed mushaf and this edition draw a word
     * boundary in different places, because the same phrase can be written joined or separate:
     *
     *   15:7   لَّوۡ مَا  — the mushaf sets it as two words, the edition writes it as one
     *   27:20  مَا لِيَ   — the same
     *   36:22  وَمَا لِيَ — the same
     *   37:130 إِلۡ يَاسِينَ — the other way about: two here, one word on the page
     *
     * So four āyāt are fixed here, by name, and nothing else is touched. The splits put the
     * space where the page has one; the join keeps the space visible but binds the two halves
     * into a single word, which is what stops the page from breaking the line between them.
     *
     * The same four exceptions are written into `tools/build_mushaf_layout.py`, which checks the
     * whole book adds up before it writes the layout out. If they ever drift apart, the build of
     * the asset fails rather than the reader quietly setting the wrong words on a line.
     */
    fun words(surah: Int, ayah: Int, text: String): List<String> =
        placed(surah, ayah, text).map { it.text }

    /** One word of an ayah, and where it begins in the text [prepared] returns. */
    data class Word(val text: String, val start: Int)

    /**
     * The same words, each with its place in the ayah.
     *
     * The place is what lets a rule of tajwīd found in the ayah be drawn on the word that holds
     * it: the rules are worked out over the whole ayah, because a nūn at the end of one word is
     * decided by the letter at the start of the next, and the page is set one word at a time.
     */
    fun placed(surah: Int, ayah: Int, text: String): List<Word> {
        val prepared = prepared(surah, ayah, text)
        val words = mutableListOf<Word>()
        var start = 0
        while (start <= prepared.length) {
            val end = prepared.indexOf(' ', start).takeIf { it >= 0 } ?: prepared.length
            if (end > start) words += Word(prepared.substring(start, end), start)
            start = end + 1
        }
        return words
    }

    /**
     * The ayah as the page sets it: the broken tanwīn closed up, and the four āyāt where the
     * mushaf draws a word boundary in another place put right.
     */
    fun prepared(surah: Int, ayah: Int, text: String): String {
        var prepared = repaired(text)
        WORD_BOUNDARIES[surah to ayah]?.forEach { (asWritten, asPrinted) ->
            prepared = prepared.replace(asWritten, asPrinted)
        }
        return prepared
    }

    /**
     * The same text as the face of the mushaf can set it.
     *
     * Unicode gave the open tanwīn its own codepoints (U+08F0, U+08F1, U+08F2) in 2016, and this
     * edition writes them; the font the app sets the page in was cut before that and knows only
     * the ordinary fatḥatān, ḍammatān and kasratān. Left alone, six and a half thousand marks
     * would come out as empty boxes, or be drawn by whatever fallback face the phone happened to
     * pick — which is worse, because a mark positioned by one font over a letter drawn by another
     * lands nowhere near it.
     *
     * So the three are folded onto the three the font has. What is lost is a distinction the
     * printed mushaf draws and this one now cannot: an open tanwīn is written with its two
     * strokes side by side to say the nūn is sounded plainly, a stacked one with them one above
     * the other. The reading is unchanged and so are the colours — [Tajweed] is worked out on the
     * text as the edition writes it, before this is applied, and it is that difference the rules
     * of iẓhār and ikhfāʾ are read from.
     *
     * One character in, one character out, so anything already worked out by position — a rule of
     * tajwīd, a word's place in the ayah — still points where it pointed.
     */
    fun printed(text: String): String {
        if (text.none { OPEN_TANWIN.containsKey(it) }) return text
        return buildString(text.length) { text.forEach { append(OPEN_TANWIN[it] ?: it) } }
    }

    /** The open tanwīn marks, each against the one the mushaf's face draws in its place. */
    private val OPEN_TANWIN: Map<Char, Char> = mapOf(
        '\u08F0' to '\u064B',
        '\u08F1' to '\u064C',
        '\u08F2' to '\u064D',
    )

    /** Binds two halves of one printed word together, and is drawn as an ordinary space. */
    private const val BINDING_SPACE = '\u00A0'

    /** The four āyāt where the edition and the page disagree about where a word ends. */
    private val WORD_BOUNDARIES: Map<Pair<Int, Int>, List<Pair<String, String>>> = mapOf(
        (15 to 7) to listOf("لَّوۡمَا" to "لَّوۡ مَا"),
        (27 to 20) to listOf("مَالِيَ" to "مَا لِيَ"),
        (36 to 22) to listOf("وَمَالِيَ" to "وَمَا لِيَ"),
        (37 to 130) to listOf("إِلۡ يَاسِينَ" to "إِلۡ${BINDING_SPACE}يَاسِينَ"),
    )
}
