package dev.yusr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyReviewTest {

    @Test
    fun `the same reason typed differently is one reason`() {
        val reasons = WeeklyReview.reasons(
            listOf("Bored.", "bored", "  BORED  ", "replying to my brother", "Replying to my brother!", "work"),
        )
        assertEquals(WeeklyReview.Reason("Bored.", 3), reasons[0])
        assertEquals(WeeklyReview.Reason("replying to my brother", 2), reasons[1])
        assertEquals(WeeklyReview.Reason("work", 1), reasons[2])
    }

    @Test
    fun `words are kept apart, so different sentences do not collapse`() {
        val reasons = WeeklyReview.reasons(listOf("to do", "todo"))
        assertEquals(2, reasons.size)
    }

    @Test
    fun `Arabic reasons fold their diacritics and hamza`() {
        val reasons = WeeklyReview.reasons(listOf("مللٌ", "ملل", "أريد أن أرى"))
        assertEquals(2, reasons.first().times)
    }

    @Test
    fun `blank reasons are left out and the list is held to the limit`() {
        val reasons = WeeklyReview.reasons(listOf("", "   ", "a", "b", "c", "d", "e", "f"), limit = 3)
        assertEquals(3, reasons.size)
        assertTrue(reasons.none { it.text.isBlank() })
    }

    @Test
    fun `the most frequent comes first however late it was typed`() {
        val reasons = WeeklyReview.reasons(listOf("once", "twice", "twice", "thrice", "thrice", "thrice"))
        assertEquals(listOf(3, 2, 1), reasons.map { it.times })
    }

    @Test
    fun `a week's minutes come to a day's average`() {
        assertEquals(30, WeeklyReview.perDay(210))
        assertEquals(0, WeeklyReview.perDay(210, days = 0))
    }
}
