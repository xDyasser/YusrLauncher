package dev.minimalist.domain

import java.time.LocalDateTime

/**
 * When the held-back notifications should go out.
 *
 * Phrased as "has a delivery slot passed that we have not delivered for yet?" rather than "is it
 * exactly nine o'clock?", because a background job that runs a few minutes late must not skip a
 * whole half-day of notifications.
 */
object DigestSchedule {

    /** The latest morning or evening slot at or before [now]. */
    fun mostRecentSlot(now: LocalDateTime, morningHour: Int, eveningHour: Int): LocalDateTime {
        val today = now.toLocalDate()
        val candidates = listOf(
            today.atTime(morningHour, 0),
            today.atTime(eveningHour, 0),
            today.minusDays(1).atTime(morningHour, 0),
            today.minusDays(1).atTime(eveningHour, 0),
        )
        return candidates.filter { !it.isAfter(now) }.max()
    }

    /** True when a slot has passed since the last delivery. */
    fun shouldDeliver(
        now: LocalDateTime,
        lastDeliveredAt: LocalDateTime?,
        morningHour: Int,
        eveningHour: Int,
    ): Boolean {
        val slot = mostRecentSlot(now, morningHour, eveningHour)
        return lastDeliveredAt == null || lastDeliveredAt.isBefore(slot)
    }
}
