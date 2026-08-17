package dev.yusr.data.quran

/**
 * A page of the mushaf with its words in it — the layout filled from the downloaded text.
 *
 * [MushafLayout] knows that line seven of page five ends at the fourth word of 2:25 and nothing
 * about what that word is; [QuranSource] knows the words and nothing about pages. This is where
 * the two meet, and it is the last place in the app where a page is data rather than pixels.
 */
data class MushafPage(
    val number: Int,
    val lines: List<Line>,
    /** The sūrah the page opens in, which is the one named at its head. */
    val surah: Int,
    /** The first ayah printed on the page — where the bookmark goes when the page is turned to. */
    val firstAyah: Int,
) {

    sealed interface Line {
        /** The sūrah's name in its band across the page. */
        data class Heading(val surah: Int) : Line

        /** The basmala over a sūrah. */
        data object Basmala : Line

        /**
         * A line of text. [centred] lines — the last of a sūrah, and every line of the two framed
         * opening pages — sit in the middle; the rest are justified out to both margins, which is
         * what makes a mushaf page a block rather than a ragged edge.
         */
        data class Text(val words: List<Word>, val centred: Boolean) : Line
    }

    /** One thing set on a line: a word of the text, an ayah's number, or a mark in the margin. */
    data class Word(
        val surah: Int,
        val ayah: Int,
        val text: String,
        val kind: Kind,
    ) {
        enum class Kind {
            /** A word of the Qur'an. */
            TEXT,

            /** The ۝ closing an ayah, with the ayah's number inside it. */
            END,

            /** The ۞ standing at the head of a rubʿ — a quarter of a ḥizb. */
            RUB,
        }
    }

    companion object {

        /** ARABIC END OF AYAH — the medallion that takes the ayah's number inside it. */
        private const val END_OF_AYAH = "۝"

        /** ARABIC START OF RUB EL HIZB — the ۞ that marks a quarter in the margin. */
        private const val RUB_EL_HIZB = "۞"

        /**
         * Sets [page] from [ayat], which must hold every ayah the page touches.
         *
         * The layout gives each line an end and no start, because in a book there is no gap
         * between one line and the next: the line begins at the word after the one the line above
         * finished on. So this walks the page's words in order, from the word the page opens at,
         * handing each line everything up to and including its own end. Anything the text cannot
         * supply — a page whose āyāt were not fetched — ends the page early rather than guessing.
         */
        fun compose(
            page: MushafLayout.Page,
            layout: MushafLayout,
            ayat: List<Ayah>,
        ): MushafPage? {
            val byReference = ayat.associateBy { it.surah to it.ayah }
            val words = mutableMapOf<Pair<Int, Int>, List<String>>()

            // Split once per ayah and kept, because an ayah that runs over four lines would
            // otherwise be split four times.
            fun wordsOf(surah: Int, ayah: Int): List<String>? {
                words[surah to ayah]?.let { return it }
                val text = byReference[surah to ayah]?.arabic ?: return null
                return UthmaniText.words(surah, ayah, text).also { words[surah to ayah] = it }
            }

            // Where the page's text is up to: the ayah, and which word of it comes next. The
            // marker closing an ayah counts as a word here exactly as it does on the page, since
            // it takes room on the line like one and a line can begin with it.
            var surah = page.start.surah
            var ayah = page.start.ayah
            var index = page.start.word

            val lines = mutableListOf<Line>()
            for (line in page.lines) {
                when (line) {
                    is MushafLayout.Line.Heading -> lines += Line.Heading(line.surah)
                    is MushafLayout.Line.Basmala -> lines += Line.Basmala
                    is MushafLayout.Line.Text -> {
                        val set = mutableListOf<Word>()
                        while (true) {
                            // Past the end the line was supposed to stop at. That can only mean
                            // the text and the layout disagree about where a word is, and setting
                            // a page from the wrong words is worse than not setting it: the
                            // reader shows the download notice instead.
                            if (MushafLayout.Reference(surah, ayah) >
                                MushafLayout.Reference(line.end.surah, line.end.ayah)
                            ) {
                                return null
                            }

                            val all = wordsOf(surah, ayah) ?: return null
                            val last = all.size
                            val marker = index > last

                            if (!marker && index == MushafLayout.FIRST_WORD &&
                                layout.rubOpensAt(surah, ayah)
                            ) {
                                set += Word(surah, ayah, RUB_EL_HIZB, Word.Kind.RUB)
                            }

                            set += if (marker) {
                                Word(
                                    surah = surah,
                                    ayah = ayah,
                                    text = END_OF_AYAH + SurahNames.arabicDigits(ayah),
                                    kind = Word.Kind.END,
                                )
                            } else {
                                Word(surah, ayah, all[index - 1], Word.Kind.TEXT)
                            }

                            val done = line.end.surah == surah && line.end.ayah == ayah &&
                                (line.end.word == index ||
                                    (line.end.word == MushafLayout.END_MARKER && marker))

                            // On to the next word, which at the end of an ayah is the next ayah's
                            // first — the book runs on through the line breaks and the sūrahs.
                            if (marker) {
                                val next = SurahNames.next(surah, ayah)
                                surah = next.first
                                ayah = next.second
                                index = MushafLayout.FIRST_WORD
                            } else {
                                index += 1
                            }

                            if (done) break
                        }
                        lines += Line.Text(set, centred = line.centred)
                    }
                }
            }

            return MushafPage(
                number = page.number,
                lines = lines,
                surah = page.start.surah,
                firstAyah = page.start.ayah,
            )
        }
    }
}
