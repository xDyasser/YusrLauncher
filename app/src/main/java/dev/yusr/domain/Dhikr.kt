package dev.yusr.domain

/**
 * The phrase you type to get past the gate.
 *
 * Typing the app's name proved you meant it; typing a dhikr proves the same thing and puts
 * something better in your mouth on the way through. Either script is accepted, because an
 * Arabic keyboard is not something to require of anybody.
 */
data class Dhikr(
    val arabic: String,
    val transliteration: String,
    val meaning: String,
) {
    /**
     * Accepts the Arabic or the transliteration, ignoring case, spacing, punctuation and the
     * Arabic diacritics a keyboard may or may not produce.
     */
    fun matches(typed: String): Boolean {
        val candidate = normalise(typed)
        if (candidate.isEmpty()) return false
        return candidate == normalise(arabic) || candidate == normalise(transliteration)
    }

    companion object {
        val ALL = listOf(
            Dhikr(
                arabic = "سبحان الله",
                transliteration = "subhan Allah",
                meaning = "glory be to Allah",
            ),
            Dhikr(
                arabic = "الحمد لله",
                transliteration = "alhamdulillah",
                meaning = "all praise is for Allah",
            ),
            Dhikr(
                arabic = "استغفر الله",
                transliteration = "astaghfirullah",
                meaning = "I seek Allah's forgiveness",
            ),
            Dhikr(
                arabic = "لا حول ولا قوة الا بالله",
                transliteration = "la hawla wa la quwwata illa billah",
                meaning = "there is no power nor strength except by Allah",
            ),
            Dhikr(
                arabic = "لا اله الا الله",
                transliteration = "la ilaha illa Allah",
                meaning = "there is no god but Allah",
            ),
            Dhikr(
                arabic = "الله اكبر",
                transliteration = "Allahu akbar",
                meaning = "Allah is greater",
            ),
        )

        /**
         * Which dhikr this attempt calls for. Derived from the app and the day rather than
         * chosen at random, so backing out and trying again asks for the same phrase — the
         * friction is the typing, not a lottery over what to type.
         */
        fun forAttempt(packageName: String, dayOfYear: Int): Dhikr {
            val index = ((packageName.hashCode() + dayOfYear) % ALL.size + ALL.size) % ALL.size
            return ALL[index]
        }

        /**
         * Diacritics, the Arabic tatweel, punctuation and spacing all go, and the several forms
         * of alif and the two of ya are folded together — a typist should not have to know which
         * hamza the phrase was stored with.
         */
        fun normalise(text: String): String {
            val folded = StringBuilder()
            for (character in text.lowercase()) {
                when {
                    character in DIACRITICS -> Unit
                    character == 'ـ' -> Unit
                    character in "أإآٱ" -> folded.append('ا')
                    character == 'ى' -> folded.append('ي')
                    character == 'ة' -> folded.append('ه')
                    character.isLetterOrDigit() -> folded.append(character)
                    else -> Unit
                }
            }
            return folded.toString()
        }

        /** The Arabic combining marks: fatha through sukun, plus the superscript alif. */
        private val DIACRITICS = ('ً'..'ْ') + 'ٰ' + 'ـ'
    }
}
