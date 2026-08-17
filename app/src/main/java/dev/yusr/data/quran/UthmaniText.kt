package dev.yusr.data.quran

/**
 * The one repair the downloaded Uthmani Ḥafṣ text needs.
 *
 * That edition writes an open tanwīn over a letter and then puts the space *before* the alif
 * carrying it, so `إِصۡرࣰ ا` arrives as two words where the mushaf has one. It happens about two
 * and a half thousand times, which is a page of the reader broken every few pages.
 *
 * Nothing else about the text is touched. No word begins with a bare alif in this orthography — a
 * word opening on one is written with hamza or waṣla — so a space in exactly this position is
 * never a word break, and closing it cannot run two words together.
 *
 * It is done once, as the text is stored, and the pairs are written out rather than worked out:
 * the three open tanwīn marks against the two letters that can carry one.
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

    /** [text] with every broken tanwīn closed up, and nothing else changed. */
    fun repaired(text: String): String {
        var repaired = text
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
    fun words(surah: Int, ayah: Int, text: String): List<String> {
        var prepared = repaired(text)
        WORD_BOUNDARIES[surah to ayah]?.forEach { (asWritten, asPrinted) ->
            prepared = prepared.replace(asWritten, asPrinted)
        }
        return prepared.split(' ').filter { it.isNotEmpty() }
    }

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
