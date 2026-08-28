package dev.yusr.data.quran

/**
 * Where the rules of tajwīd fall in an ayah, worked out from the text itself.
 *
 * Nothing is downloaded and nothing is bundled for this. The Uthmani text the app already has
 * states most of these rules in its own orthography rather than leaving them to be inferred, and
 * that is what this reads:
 *
 *   * A nūn that is read plainly carries a sukūn; a nūn that is assimilated or hidden carries
 *     nothing at all. So `مِنۡ حَيۡثُ` is iẓhār and `مِن قَبۡلِ` is ikhfāʾ, and neither has to be
 *     decided from the letter that follows.
 *   * A tanwīn is written stacked before the throat letters and open — the two marks side by
 *     side — everywhere else, which is the same distinction said a second way.
 *   * Iqlāb is drawn: a small mīm stands over the nūn or the tanwīn, and nothing else in the
 *     book carries that mark.
 *   * A madd of more than two counts carries the wavy maddah; where the maddah runs into a
 *     hamza it is muttaṣil or munfaṣil by whether the hamza is in the same word, and where it
 *     runs into a sākin letter it is lāzim.
 *   * A letter that is written but not read carries a plain sukūn (U+0652) where a letter that
 *     is read at rest carries the Uthmani one (U+06E1). Two marks that look alike and are not.
 *
 * What is left to work out is small and local: qalqalah is five letters at rest, ghunnah is nūn
 * or mīm with a shadda, the sun letters are a list. The one thing this deliberately does not do
 * is guess: where the orthography does not say, nothing is marked, because a wrong colour on a
 * letter of the Qur'an is worse than no colour at all.
 *
 * The whole book is checked against an independent annotation of it — cpfair/quran-tajweed,
 * built from the Dar al-Maʿrifah masaahif — in `TajweedTest`. That test holds this to the count
 * of every rule in every one of the 6,236 āyāt, so a change here that colours a letter it should
 * not fails the build rather than reaching a reader.
 *
 * Positions are indices into the ayah as it is stored, and every span is one character: the
 * letter the rule falls on, with its marks left uncoloured.
 */
object Tajweed {

    /**
     * The rules, in the order a reader meets them: the nūn's four, the mīm's three, then what
     * belongs to a single letter, then the madds.
     */
    enum class Rule {
        GHUNNAH,
        IDGHAAM_GHUNNAH,
        IDGHAAM_NO_GHUNNAH,
        IDGHAAM_SHAFAWI,
        IDGHAAM_MUTAJAANISAIN,
        IDGHAAM_MUTAQAARIBAIN,
        IKHFA,
        IKHFA_SHAFAWI,
        IQLAB,
        QALQALAH,
        HAMZAT_WASL,
        LAM_SHAMSIYYAH,
        SILENT,
        MADD_2,
        MADD_246,
        MADD_MUTTASIL,
        MADD_MUNFASIL,
        MADD_6,
    }

    /** One rule, on one letter: [start] until [end] in the ayah it was read from. */
    data class Span(val rule: Rule, val start: Int, val end: Int)

    /** Every rule in [text], in no particular order — the caller sorts if it cares. */
    fun annotate(text: String): List<Span> {
        val spans = mutableListOf<Span>()
        val letters = text.indices.filter { isLetter(text[it]) }
        if (letters.isEmpty()) return spans

        val first = letters.first()
        val last = letters.last()
        val order = HashMap<Int, Int>(letters.size)
        letters.forEachIndexed { position, index -> order[index] = position }
        // Word-initial letters an idghaam has already been drawn for. The nūn of `مِن رَّبِّهِمۡ`
        // carries the rule; the rāʾ it runs into is not a second one.
        val assimilated = mutableSetOf<Int>()

        fun add(rule: Rule, at: Int) {
            spans += Span(rule, at, at + 1)
        }

        for (i in letters) {
            val ch = text[i]
            val marks = marksAfter(text, i)
            val next = nextLetter(text, i)

            if (marks.contains(SILENT) || marks.contains(SILENT_UNLESS_STOPPING)) {
                add(Rule.SILENT, i)
                continue
            }
            // ṣalāh, zakāh, ḥayāh: a wāw written where an alif is read, and not read itself.
            if (ch == WAW && marks.contains(DAGGER) && next != null && text[next] == TEH_MARBUTA) {
                add(Rule.SILENT, i)
            }

            val tanween = marks.any { it in TANWEEN }
            val vowelled = marks.any { it in VOWELS }
            val sakin = marks.contains(SUKUN)
            val iqlab = marks.any { it in IQLAB_MARKS }
            val bare = marks.all { it in IQLAB_MARKS || it == MADDAH }

            // --- the nūn at rest, and the tanwīn, which is a nūn at rest written as a vowel
            //
            // Stopping at the end of an ayah, there is no letter for the nūn to run into, so the
            // mark that would have said iqlāb says nothing.
            if (iqlab && i != last) add(Rule.IQLAB, i)

            val bareNoon = ch == NOON && marks.none { it !in PAUSE && it !in IQLAB_MARKS }
            if ((tanween || bareNoon) && !iqlab) {
                val assimilating = bareNoon || marks.any { it in TANWEEN_OPEN }
                var j = next
                // An alif that carries a tanwīn and nothing else is not the letter after it.
                if (j != null && tanween && (text[j] == ALEF || text[j] == ALEF_MAKSURA) &&
                    marksAfter(text, j).none { it !in PAUSE }
                ) {
                    j = nextLetter(text, j)
                }
                if (j != null && assimilating) {
                    when (text[j]) {
                        BEH -> add(Rule.IQLAB, i)
                        in YARMALOON_GHUNNAH -> {
                            add(Rule.IDGHAAM_GHUNNAH, i)
                            assimilated += j
                        }
                        in YARMALOON_PLAIN -> {
                            add(Rule.IDGHAAM_NO_GHUNNAH, i)
                            assimilated += j
                        }
                        in IKHFA_LETTERS -> add(Rule.IKHFA, i)
                    }
                }
            }

            // --- the mīm at rest: hidden before a bāʾ, assimilated into another mīm
            if (ch == MEEM && !vowelled && !marks.contains(SHADDA) && !tanween && next != null) {
                when (text[next]) {
                    BEH -> add(Rule.IKHFA_SHAFAWI, i)
                    MEEM -> {
                        add(Rule.IDGHAAM_SHAFAWI, i)
                        assimilated += next
                    }
                }
            }

            // --- a letter at rest running into the close relative after it
            if ((sakin || (bare && ch !in MADD_LETTERS)) && next != null &&
                marksAfter(text, next).contains(SHADDA) && !isArticleLam(text, i)
            ) {
                val pair = "$ch${text[next]}"
                when (pair) {
                    in MUTAJAANISAIN -> add(Rule.IDGHAAM_MUTAJAANISAIN, i)
                    in MUTAQAARIBAIN -> add(Rule.IDGHAAM_MUTAQAARIBAIN, i)
                }
            }

            // --- qalqalah: the five letters at rest, and the same five stopped on
            if (ch in QALQALAH && (sakin || i == last)) add(Rule.QALQALAH, i)

            // --- the joining hamza, except at the head of an ayah where it is read as a hamza
            if (ch == ALEF_WASLA && i != first) add(Rule.HAMZAT_WASL, i)

            // --- the article's lam before a sun letter, but never the lam of the divine name
            if (ch == LAM && marks.isEmpty() && next != null && isArticleLam(text, i) &&
                text[next] in SUN && marksAfter(text, next).contains(SHADDA)
            ) {
                val after = nextLetter(text, next)
                val divineName = text[next] == LAM && after != null && text[after] == HEH
                if (!divineName) add(Rule.LAM_SHAMSIYYAH, i)
            }
        }

        // --- ghunnah last, because a nūn or mīm an idghaam already ran into is not counted twice
        for (i in letters) {
            val ch = text[i]
            if ((ch == NOON || ch == MEEM) && marksAfter(text, i).contains(SHADDA) &&
                i !in assimilated
            ) {
                spans += Span(Rule.GHUNNAH, i, i + 1)
            }
        }

        spans += madd(text, letters, order)
        return spans
    }

    /**
     * The madds, which are the one family of rules that needs the letter's neighbours on both
     * sides.
     *
     * A madd is carried either by a written letter — alif, wāw, yāʾ after its own vowel — or by
     * one of the small marks that stand for one: the dagger alif, the small wāw of `هُۥ`, the
     * small yāʾ of `بِهِۦ`. Both are walked here, in one pass, because a page has to colour them
     * the same way.
     *
     * Only the madds that are read longer than the two counts of an ordinary one are marked, and
     * the hidden carriers, which are marked because a reader has to be told they are there at
     * all. The plain two-count madd of `قَالَ` is how the letter is read anyway and is left alone.
     */
    private fun madd(text: String, letters: List<Int>, order: Map<Int, Int>): List<Span> {
        val out = mutableListOf<Span>()
        val last = letters.last()
        val count = letters.size
        val carriers = (letters + text.indices.filter { text[it] in HIDDEN_MADD }).sorted()

        for (i in carriers) {
            val ch = text[i]
            val marks = marksAfter(text, i)
            val hidden = ch in HIDDEN_MADD
            if (marks.contains(SILENT) || marks.contains(SILENT_UNLESS_STOPPING)) continue
            // A silent letter is not what a madd runs into: `قَالُوٓاْ إِنَّمَا` is one word to the
            // next, with the unread alif in between.
            val next = nextLetter(text, i, skipSilent = true)

            var maddah = marks.contains(MADDAH)
            if (maddah && !hidden) {
                // The maddah belongs to the nearest carrier. Where a dagger alif stands between
                // the letter and the maddah, the dagger carries it and the letter does not.
                val run = markRun(text, i)
                val toMaddah = run.subSequence(0, run.indexOf(MADDAH))
                if (toMaddah.any { it in HIDDEN_MADD }) maddah = false
            }

            if (maddah) {
                val onConsonant = !hidden && ch !in MADD_LETTERS && !vowelledOrRested(marks)
                if (onConsonant) {
                    // The disconnected letters at the head of a sūrah: alif lām mīm is read lām,
                    // mīm, each of them six counts long.
                    out += Span(Rule.MADD_6, i, i + 1)
                    continue
                }
                if (next != null && isHamza(text, next)) {
                    val joined = !text.substring(i, next).any { it in SEPARATORS }
                    // The vocative yā and the demonstrative hā are separate words written
                    // joined, so their madd runs into the next word's hamza after all.
                    val detached = hidden && wordBefore(text, i).let { before ->
                        before.count { isLetter(it) } == 1 && (before[0] == YEH || before[0] == HEH)
                    }
                    out += Span(
                        if (joined && !detached) Rule.MADD_MUTTASIL else Rule.MADD_MUNFASIL,
                        i,
                        i + 1,
                    )
                } else if (next != null && marksAfter(text, next).let {
                        it.contains(SHADDA) || it.contains(SUKUN)
                    }
                ) {
                    out += Span(Rule.MADD_6, i, i + 1)
                } else if (next == null || order[next] == count - 1) {
                    out += Span(Rule.MADD_246, i, i + 1)
                }
                continue
            }

            val previous = previousLetter(text, i)
            val before = if (previous != null) marksAfter(text, previous) else emptySet()
            var isMadd = false
            var leen = false
            when {
                hidden -> {
                    // A small madd mark at the end of a word, written over a madd letter, is that
                    // letter's own two-count madd and is not a second one: `عَلَىٰ`, `مُوسَىٰ`.
                    val wordFinal = next == null || text.substring(i, next).any { it in SEPARATORS }
                    isMadd = !(wordFinal && previous != null && text[previous] in MADD_LETTERS)
                }
                ch == ALEF ->
                    isMadd = marks.isEmpty() && previous != null && before.contains(FATHA)
                ch == WAW -> {
                    isMadd = marks.none { it in VOWELS || it in TANWEEN } &&
                        !marks.contains(SHADDA) && !marks.contains(SUKUN) &&
                        previous != null && before.contains(DAMMA)
                    leen = marks.contains(SUKUN) && previous != null && before.contains(FATHA)
                }
                ch == YEH || ch == ALEF_MAKSURA -> {
                    isMadd = marks.none { it in VOWELS || it in TANWEEN } &&
                        !marks.contains(SHADDA) && !marks.contains(SUKUN) &&
                        previous != null && before.contains(KASRA)
                    leen = ch == YEH && marks.contains(SUKUN) && previous != null &&
                        before.contains(FATHA)
                }
            }
            if (!isMadd && !leen) continue

            // One letter short of the end of the ayah, stopping stretches it: two, four or six.
            val atStop = i == last || (next != null && order[next] == count - 1)
            if (atStop) {
                out += Span(Rule.MADD_246, i, i + 1)
            } else if (isMadd && hidden) {
                out += Span(Rule.MADD_2, i, i + 1)
            }
        }
        return out
    }

    /** The lam of `al-`, which is what a sun letter assimilates — after the wasl alif or `li-`. */
    private fun isArticleLam(text: String, i: Int): Boolean {
        if (text[i] != LAM) return false
        if (i > 0 && text[i - 1] == ALEF_WASLA) return true
        val start = text.lastIndexOfAny(SEPARATORS.toCharArray(), i - 1) + 1
        val before = text.substring(start, i).filter { isLetter(it) }
        return before == LAM.toString()
    }

    private fun vowelledOrRested(marks: Set<Char>): Boolean =
        marks.any { it in VOWELS } || marks.contains(SHADDA) || marks.contains(SUKUN)

    private fun isHamza(text: String, i: Int): Boolean =
        text[i] in HAMZAS || marksAfter(text, i).let {
            it.contains(HAMZA_ABOVE) || it.contains(HAMZA_BELOW)
        }

    /** Everything hanging on the letter at [i]: its vowel, its shadda, its small marks. */
    private fun marksAfter(text: String, i: Int): Set<Char> {
        var j = i + 1
        var found: MutableSet<Char>? = null
        while (j < text.length && !isLetter(text[j]) && text[j] !in SEPARATORS) {
            (found ?: mutableSetOf<Char>().also { found = it }) += text[j]
            j++
        }
        return found ?: emptySet()
    }

    /** The same marks in the order they are written, for the one rule that needs the order. */
    private fun markRun(text: String, i: Int): String {
        var j = i + 1
        while (j < text.length && !isLetter(text[j]) && text[j] !in SEPARATORS) j++
        return text.substring(i + 1, j)
    }

    private fun nextLetter(text: String, i: Int, skipSilent: Boolean = false): Int? {
        var j = i + 1
        while (j < text.length) {
            if (isLetter(text[j])) {
                val silent = skipSilent && marksAfter(text, j).let {
                    it.contains(SILENT) || it.contains(SILENT_UNLESS_STOPPING)
                }
                if (!silent) return j
            }
            j++
        }
        return null
    }

    private fun previousLetter(text: String, i: Int): Int? {
        var j = i - 1
        while (j >= 0) {
            if (isLetter(text[j])) return j
            j--
        }
        return null
    }

    /** What is written before [i] in its own word. */
    private fun wordBefore(text: String, i: Int): String {
        val start = text.lastIndexOfAny(SEPARATORS.toCharArray(), i - 1) + 1
        return text.substring(start, i)
    }

    private fun isLetter(c: Char): Boolean = c in LETTERS

    // ---- the marks this edition writes ----------------------------------------------------

    /** ARABIC SMALL HIGH DOTLESS HEAD OF KHAH — a letter read at rest. */
    private const val SUKUN = 'ۡ'

    /** ARABIC SUKUN — in this edition, a letter written but not read. */
    private const val SILENT = 'ْ'

    /** ARABIC SMALL HIGH UPRIGHT RECTANGULAR ZERO — read only if you stop on it. */
    private const val SILENT_UNLESS_STOPPING = '۠'

    private const val SHADDA = 'ّ'
    private const val FATHA = 'َ'
    private const val DAMMA = 'ُ'
    private const val KASRA = 'ِ'
    private const val MADDAH = 'ٓ'
    private const val DAGGER = 'ٰ'
    private const val SMALL_WAW = 'ۥ'
    private const val SMALL_YEH = 'ۦ'
    private const val HAMZA_ABOVE = 'ٔ'
    private const val HAMZA_BELOW = 'ٕ'

    private const val ALEF = 'ا'
    private const val ALEF_WASLA = 'ٱ'
    private const val ALEF_MAKSURA = 'ى'
    private const val WAW = 'و'
    private const val YEH = 'ي'
    private const val NOON = 'ن'
    private const val MEEM = 'م'
    private const val LAM = 'ل'
    private const val HEH = 'ه'
    private const val BEH = 'ب'
    private const val TEH_MARBUTA = 'ة'

    private val VOWELS = setOf(FATHA, DAMMA, KASRA)

    /** Stacked, before the throat letters: the tanwīn is read plainly. */
    private val TANWEEN_CLEAR = setOf('ً', 'ٌ', 'ٍ')

    /** Open, the two marks side by side: the tanwīn is assimilated, hidden or turned. */
    private val TANWEEN_OPEN = setOf('ࣰ', 'ࣱ', 'ࣲ')
    private val TANWEEN = TANWEEN_CLEAR + TANWEEN_OPEN

    /** The small mīm, above or below, which is how iqlāb is written. */
    private val IQLAB_MARKS = setOf('ۢ', 'ۭ')

    private val HIDDEN_MADD = setOf(DAGGER, SMALL_WAW, SMALL_YEH)
    private val MADD_LETTERS = setOf(ALEF, WAW, YEH, ALEF_MAKSURA)

    /** The pause marks, the sajda and the rubʿ, none of which break a word. */
    private val PAUSE = setOf(
        'ۖ', 'ۗ', 'ۘ', 'ۙ', 'ۚ', 'ۛ', 'ۜ',
        '۩', '۞', '‏',
    )

    /** A space, and the space that binds two halves of one printed word. */
    private val SEPARATORS = setOf(' ', '\u00A0')

    private val LETTERS = ("ابتثجحخدذرزسشصضطظعغفقكلمنهوىيءأإؤئة" + ALEF_WASLA + 'آ').toSet()
    private val HAMZAS = setOf('ء', 'أ', 'إ', 'ؤ', 'ئ')
    private val YARMALOON_GHUNNAH = setOf(YEH, WAW, MEEM, NOON)
    private val YARMALOON_PLAIN = setOf(LAM, 'ر')
    private val IKHFA_LETTERS = "تثجدذزسشصضطظفقك".toSet()
    private val QALQALAH = "قطبجد".toSet()
    private val SUN = "تثدذرزسشصضطظلن".toSet()

    /** Letters near enough in the mouth that one becomes the other: `عَبَدتُّمۡ`, `أَثۡقَلَت دَّعَوَا`. */
    private val MUTAJAANISAIN = setOf("تط", "طت", "دت", "تد", "ذظ", "ثذ", "بم")

    /** Nearer still: the lam of `بَل رَّفَعَهُ`, the qaf of `نَخۡلُقكُّم`. */
    private val MUTAQAARIBAIN = setOf("لر", "قك")
}
