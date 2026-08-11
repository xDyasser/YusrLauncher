package dev.yusr.data.quran

/**
 * The basmala, and where it belongs.
 *
 * It heads a hundred and thirteen sūrahs. Al-Tawba is the one it does not head — the mushaf opens
 * that sūrah on its words, with no heading above them. And in exactly one sūrah it is an ayah in
 * its own right: al-Fātiḥa, where it is the first. Everywhere else it is a heading, printed above
 * the sūrah and outside the numbering.
 *
 * Those are three fixed facts about the book, so they are three constants here. Nothing about the
 * basmala is worked out from the text at runtime, and nothing is ever cut out of an ayah: the
 * edition the app downloads already draws the line where the mushaf draws it, so al-Baqara 2:1 is
 * *alif lām mīm* and nothing else. What is here is only the heading itself and the question of
 * which sūrahs it goes above.
 */
object Basmala {

    /**
     * The heading itself, letter for letter as the Uthmani Hafs text writes it — the same bytes
     * as al-Fātiḥa 1:1 in the edition the mushaf is downloaded from, so the heading above a sūrah
     * is set in the same hand as the āyāt under it.
     */
    const val ARABIC = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"

    /** Al-Tawba, the sūrah with no basmala over it. */
    const val WITHOUT_IT = 9

    /** Al-Fātiḥa, the sūrah whose first ayah is the basmala. */
    const val AS_ITS_FIRST_AYAH = 1

    /** How many sūrahs there are — the bound on a number being a sūrah at all. */
    private const val SURAHS = 114

    /** Whether the basmala stands over [surah], as an ayah or as a heading. */
    fun heads(surah: Int): Boolean = surah in 1..SURAHS && surah != WITHOUT_IT

    /** Whether the basmala is an ayah of [surah] rather than a heading over it. */
    fun isAyah(surah: Int): Boolean = surah == AS_ITS_FIRST_AYAH

    /**
     * Whether the reader should print the heading above [surah] before [ayah].
     *
     * True at the top of every sūrah the basmala heads without belonging to. Al-Tawba has no
     * heading, al-Fātiḥa's first ayah *is* the basmala and is printed as the ayah it is, and
     * nowhere is it printed anywhere but at the top.
     */
    fun headingBelongsAbove(surah: Int, ayah: Int): Boolean =
        ayah == 1 && heads(surah) && !isAyah(surah)
}
