package dev.yusr.domain

import dev.yusr.domain.NotificationPolicy.NotificationFacts
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyTest {

    private fun facts(
        tier: AppTier = AppTier.GATED,
        category: String? = null,
        pinned: Boolean = false,
        clearable: Boolean = true,
        media: Boolean = false,
    ) = NotificationFacts(tier, category, pinned, clearable, media)

    @Test
    fun `an ordinary notification from a gated app is suppressed`() {
        assertTrue(NotificationPolicy.shouldSuppress(facts(), suppressionEnabled = true))
    }

    @Test
    fun `nothing is suppressed when the feature is off`() {
        assertFalse(NotificationPolicy.shouldSuppress(facts(), suppressionEnabled = false))
    }

    @Test
    fun `favourites and allowed apps are left alone`() {
        assertFalse(NotificationPolicy.shouldSuppress(facts(tier = AppTier.FAVORITE), true))
        assertFalse(NotificationPolicy.shouldSuppress(facts(tier = AppTier.ALLOWED), true))
    }

    @Test
    fun `blocked apps are suppressed like gated ones`() {
        assertTrue(NotificationPolicy.shouldSuppress(facts(tier = AppTier.BLOCKED), true))
    }

    @Test
    fun `calls and alarms always get through`() {
        assertFalse(NotificationPolicy.shouldSuppress(facts(category = "call"), true))
        assertFalse(NotificationPolicy.shouldSuppress(facts(category = "alarm"), true))
    }

    /** The bug this exists to prevent: cancelling a player's notification stops playback. */
    @Test
    fun `a media notification is never cancelled`() {
        assertFalse(NotificationPolicy.shouldSuppress(facts(media = true), true))
        assertFalse(NotificationPolicy.shouldSuppress(facts(category = "transport"), true))
    }

    @Test
    fun `pinned and unclearable notifications are never cancelled`() {
        assertFalse(NotificationPolicy.shouldSuppress(facts(pinned = true), true))
        assertFalse(NotificationPolicy.shouldSuppress(facts(clearable = false), true))
    }
}
