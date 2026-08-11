package dev.minimalist.data.quran

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
}
