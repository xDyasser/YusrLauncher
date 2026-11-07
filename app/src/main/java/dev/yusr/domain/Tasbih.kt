package dev.yusr.domain

/**
 * The counter behind the tasbīḥ screen.
 *
 * A physical misbaḥa has no display and no reset button; what it has is beads, and you know where
 * you are by feel. The nearest thing on glass is a count that knows its own cycle, so the screen
 * can say "11 of 33" and mark the moment a set closes rather than running up an undifferentiated
 * number into the hundreds.
 *
 * One cycle is not a plain number but a sequence: the tasbīḥ of Fāṭima al-Zahrāʾ, which is
 * thirty-four *Allāhu akbar*, then thirty-three *al-ḥamdu lillāh*, then thirty-three *subḥāna
 * llāh*, said after every prayer. On a misbaḥa the three are told apart by the two dividing beads;
 * here they are told apart by a change of phrase on the screen and a buzz at each boundary, so a
 * thumb that is not being watched still knows when to change what it is saying.
 */
object Tasbih {

    /** One run of one phrase inside a cycle. A plain cycle is one phase with no phrase. */
    data class Phase(
        val length: Int,
        /** The phrase, or null for a cycle that counts beads without saying which. */
        val arabic: String? = null,
        val transliteration: String? = null,
    )

    /** The sets people actually count in. */
    enum class Cycle(val label: String, val phases: List<Phase>) {
        /** Subḥān Allāh, al-ḥamdu lillāh, Allāhu akbar after each prayer — thirty-three each. */
        THIRTY_THREE("33", listOf(Phase(33))),

        /** The takbīr of the same set, which is the odd one out. */
        THIRTY_FOUR("34", listOf(Phase(34))),

        /** A round hundred, for istighfār and ṣalawāt. */
        HUNDRED("100", listOf(Phase(100))),

        /** The tasbīḥ of al-Zahrāʾ: 34, 33, 33, in that order, each with its phrase. */
        ZAHRA(
            "Zahrāʾ",
            listOf(
                Phase(34, "اللهُ أَكْبَر", "Allāhu akbar"),
                Phase(33, "الحَمْدُ لِلّه", "al-ḥamdu lillāh"),
                Phase(33, "سُبْحانَ الله", "subḥāna llāh"),
            ),
        ),
        ;

        /** Beads in the whole cycle. */
        val length: Int = phases.sumOf { it.length }

        /** Whether the screen has a phrase to put under the number. */
        val isSequence: Boolean get() = phases.size > 1
    }

    data class Progress(
        val total: Int,
        val cycle: Cycle,
    ) {
        /** How far into the current cycle, from 1 up to the cycle length. */
        val inCycle: Int get() = if (total == 0) 0 else ((total - 1) % cycle.length) + 1

        /** How many complete cycles have been finished. */
        val completed: Int get() = total / cycle.length

        /** True on exactly the count that closes a cycle — the moment worth a buzz. */
        val justClosedACycle: Boolean get() = total > 0 && total % cycle.length == 0

        /**
         * Which phase of the cycle the current bead is in. At nothing it is the first, because
         * that is the phrase the next bead will be.
         */
        val phaseIndex: Int
            get() {
                if (inCycle == 0) return 0
                var remaining = inCycle
                cycle.phases.forEachIndexed { index, phase ->
                    if (remaining <= phase.length) return index
                    remaining -= phase.length
                }
                return cycle.phases.lastIndex
            }

        val phase: Phase get() = cycle.phases[phaseIndex]

        /** How far into the current phase, from 1 up to its length; 0 at the start of a cycle. */
        val inPhase: Int
            get() {
                if (inCycle == 0) return 0
                val before = cycle.phases.take(phaseIndex).sumOf { it.length }
                return inCycle - before
            }

        /**
         * True on the bead that closes a phase that is not the last — the moment to change the
         * phrase. The last phase closes the cycle, and [justClosedACycle] says so instead, so a
         * caller buzzing on either never buzzes twice for one bead.
         */
        val justClosedAPhase: Boolean
            get() = inCycle > 0 && inPhase == phase.length && phaseIndex < cycle.phases.lastIndex

        /** "11 of 33", or "12 of 34 · Allāhu akbar" in a sequence. */
        val label: String
            get() {
                val phrase = phase.transliteration
                return if (cycle.isSequence && phrase != null) {
                    "$inPhase of ${phase.length} · $phrase"
                } else {
                    "$inCycle of ${cycle.length}"
                }
            }
    }

    fun progress(total: Int, cycle: Cycle): Progress = Progress(total.coerceAtLeast(0), cycle)

    /** One bead. */
    fun increment(total: Int): Int = total.coerceAtLeast(0) + 1

    /**
     * Back one bead, which a physical misbaḥa cannot do and a counter should — a double tap
     * against a trouser pocket is the commonest way a count goes wrong.
     */
    fun decrement(total: Int): Int = (total - 1).coerceAtLeast(0)
}
