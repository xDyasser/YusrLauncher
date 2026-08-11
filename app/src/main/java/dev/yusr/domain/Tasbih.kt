package dev.yusr.domain

/**
 * The counter behind the tasbīḥ screen.
 *
 * A physical misbaḥa has no display and no reset button; what it has is beads, and you know where
 * you are by feel. The nearest thing on glass is a count that knows its own cycle, so the screen
 * can say "11 of 33" and mark the moment a set closes rather than running up an undifferentiated
 * number into the hundreds.
 */
object Tasbih {

    /** The sets people actually count in. */
    enum class Cycle(val length: Int, val label: String) {
        /** Subḥān Allāh, al-ḥamdu lillāh, Allāhu akbar after each prayer — thirty-three each. */
        THIRTY_THREE(33, "33"),

        /** The takbīr of the same set, which is the odd one out. */
        THIRTY_FOUR(34, "34"),

        /** A round hundred, for istighfār and ṣalawāt. */
        HUNDRED(100, "100"),
    }

    data class Progress(
        val total: Int,
        val cycle: Cycle,
    ) {
        /** How far into the current set, from 1 up to the cycle length. */
        val inCycle: Int get() = if (total == 0) 0 else ((total - 1) % cycle.length) + 1

        /** How many complete sets have been finished. */
        val completed: Int get() = total / cycle.length

        /** True on exactly the count that closes a set — the moment worth a buzz. */
        val justClosedACycle: Boolean get() = total > 0 && total % cycle.length == 0

        /** "11 of 33". */
        val label: String get() = "$inCycle of ${cycle.length}"
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
