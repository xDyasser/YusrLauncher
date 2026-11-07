package dev.yusr.domain

/**
 * The week, added up — a muḥāsaba rather than a dashboard.
 *
 * The usage screen says where today went. This says what the week was: how many prayers were
 * marked and how many are owed, how many fasts were kept, how much dhikr was counted, how long
 * went to the apps behind the gate — and, most usefully, *why*. Every pass through the gate asks
 * for a reason, and those reasons are the user's own account of what they wanted the phone for.
 * Read back together, grouped and counted, "bored" eleven times is an argument no setting could
 * make.
 *
 * Pure arithmetic over records the app already keeps; nothing new is stored for it.
 */
object WeeklyReview {

    const val DAYS: Int = 7

    /** One reason as typed, counted with the others that say the same thing. */
    data class Reason(val text: String, val times: Int)

    /**
     * The reasons typed at the gate, grouped by what they say rather than how they were typed —
     * case, spacing and punctuation folded, Arabic diacritics too — and most frequent first. The
     * wording shown is the first one typed, which is the user's own.
     */
    fun reasons(typed: List<String>, limit: Int = 5): List<Reason> {
        val groups = linkedMapOf<String, MutableList<String>>()
        typed.forEach { raw ->
            val key = normalise(raw)
            if (key.isNotEmpty()) groups.getOrPut(key) { mutableListOf() }.add(raw.trim())
        }
        return groups.values
            .map { Reason(text = it.first(), times = it.size) }
            .sortedByDescending { it.times }
            .take(limit)
    }

    /** Folded the way the dhikr at the gate is, so the same reason typed two ways is one reason. */
    internal fun normalise(text: String): String =
        Dhikr.normalise(text).let { folded ->
            // Dhikr.normalise drops spaces; a reason is a sentence, so the words are kept apart
            // by re-folding word by word.
            text.split(Regex("\\s+")).map { Dhikr.normalise(it) }.filter { it.isNotEmpty() }
                .joinToString(" ")
                .ifEmpty { folded }
        }

    /** Minutes, or how many hours and minutes, a week of gated time came to on average a day. */
    fun perDay(totalMinutes: Int, days: Int = DAYS): Int = if (days <= 0) 0 else totalMinutes / days
}
