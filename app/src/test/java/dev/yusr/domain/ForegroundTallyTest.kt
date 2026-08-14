package dev.yusr.domain

import dev.yusr.domain.ForegroundEvent.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundTallyTest {

    private val dayStart = 1_000_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val app = "com.example.app"
    private val other = "com.example.other"

    private fun at(minutesIn: Long) = dayStart + minutesIn * minute

    private fun foreground(packageName: String, minutesIn: Long) =
        ForegroundEvent(packageName, Kind.FOREGROUND, at(minutesIn))

    private fun background(packageName: String, minutesIn: Long) =
        ForegroundEvent(packageName, Kind.BACKGROUND, at(minutesIn))

    private fun idle(minutesIn: Long) = ForegroundEvent(null, Kind.IDLE, at(minutesIn))

    private fun minutesOf(snapshot: ForegroundSnapshot, packageName: String, now: Long): Int =
        (snapshot.usageFor(packageName, now).millis / minute).toInt()

    @Test
    fun `a visit is counted from resumed to paused`() {
        val snapshot = ForegroundTally.tally(
            listOf(foreground(app, 10), background(app, 25)),
            dayStart,
            at(120),
        )

        assertEquals(15, minutesOf(snapshot, app, at(120)))
        assertEquals(1, snapshot.usageFor(app, at(120)).opens)
    }

    @Test
    fun `an app still in front is counted up to now`() {
        val snapshot = ForegroundTally.tally(listOf(foreground(app, 10)), dayStart, at(40))

        assertEquals(30, minutesOf(snapshot, app, at(40)))
        assertEquals(app, snapshot.foreground)
    }

    /**
     * The whole reason this exists. An app left in front when the screen goes off was charged for
     * the night by the launcher's own tally, which is not what the phone reports and not what
     * anybody means by time spent.
     */
    @Test
    fun `the screen going off ends the visit`() {
        val snapshot = ForegroundTally.tally(
            listOf(foreground(app, 10), idle(25)),
            dayStart,
            at(8 * 60),
        )

        assertEquals(15, minutesOf(snapshot, app, at(8 * 60)))
        assertNull(snapshot.foreground)
    }

    @Test
    fun `time asleep is not carried forward when the screen is still off`() {
        val snapshot = ForegroundTally.tally(
            listOf(foreground(app, 10), background(app, 25)),
            dayStart,
            at(8 * 60),
            interactiveNow = false,
        )

        assertEquals(15, minutesOf(snapshot, app, at(8 * 60)))
        assertNull(snapshot.foreground)
    }

    @Test
    fun `one app coming forward ends the one before it`() {
        val snapshot = ForegroundTally.tally(
            listOf(foreground(app, 10), foreground(other, 20)),
            dayStart,
            at(30),
        )

        assertEquals(10, minutesOf(snapshot, app, at(30)))
        assertEquals(10, minutesOf(snapshot, other, at(30)))
    }

    /** Two activities of one app is one visit; the system reports it as a pause and a resume. */
    @Test
    fun `moving between screens of one app is not a second open`() {
        val snapshot = ForegroundTally.tally(
            listOf(foreground(app, 10), background(app, 20), foreground(app, 20), background(app, 30)),
            dayStart,
            at(60),
        )

        assertEquals(1, snapshot.usageFor(app, at(60)).opens)
        assertEquals(20, minutesOf(snapshot, app, at(60)))
    }

    @Test
    fun `going away and coming back is a second open`() {
        val snapshot = ForegroundTally.tally(
            listOf(foreground(app, 10), foreground(other, 20), foreground(app, 30)),
            dayStart,
            at(40),
        )

        assertEquals(2, snapshot.usageFor(app, at(40)).opens)
        assertEquals(20, minutesOf(snapshot, app, at(40)))
    }

    /** Picking the phone back up where you left it is the same visit, not a fresh one. */
    @Test
    fun `unlocking back into the same app does not spend another open`() {
        val snapshot = ForegroundTally.tally(
            listOf(foreground(app, 10), idle(20), foreground(app, 60)),
            dayStart,
            at(70),
        )

        assertEquals(1, snapshot.usageFor(app, at(70)).opens)
        assertEquals(20, minutesOf(snapshot, app, at(70)))
    }

    @Test
    fun `an app already open at midnight is only charged for today's share`() {
        val snapshot = ForegroundTally.tally(
            listOf(
                ForegroundEvent(app, Kind.FOREGROUND, dayStart - hour),
                background(app, 30),
            ),
            dayStart,
            at(60),
        )

        assertEquals(30, minutesOf(snapshot, app, at(60)))
        assertEquals(0, snapshot.usageFor(app, at(60)).opens)
    }

    @Test
    fun `events after the window are ignored`() {
        val snapshot = ForegroundTally.tally(
            listOf(foreground(app, 10), foreground(other, 50)),
            dayStart,
            at(30),
        )

        assertEquals(20, minutesOf(snapshot, app, at(30)))
        assertEquals(0, minutesOf(snapshot, other, at(30)))
    }

    @Test
    fun `out of order events are put back in order`() {
        val snapshot = ForegroundTally.tally(
            listOf(background(app, 25), foreground(app, 10)),
            dayStart,
            at(60),
        )

        assertEquals(15, minutesOf(snapshot, app, at(60)))
    }

    /** Read a few seconds later, only the app still in front can have gained anything. */
    @Test
    fun `a snapshot carries the open app forward without being read again`() {
        val snapshot = ForegroundTally.tally(
            listOf(foreground(other, 5), background(other, 10), foreground(app, 10)),
            dayStart,
            at(20),
        )

        assertEquals(15, minutesOf(snapshot, app, at(25)))
        assertEquals(5, minutesOf(snapshot, other, at(25)))
    }

    @Test
    fun `a snapshot is never read backwards`() {
        val snapshot = ForegroundTally.tally(listOf(foreground(app, 10)), dayStart, at(20))

        assertEquals(10, minutesOf(snapshot, app, at(15)))
    }

    @Test
    fun `the day's totals span every app`() {
        val snapshot = ForegroundTally.tally(
            listOf(
                foreground(app, 0),
                foreground(other, 15),
                background(other, 45),
            ),
            dayStart,
            at(60),
        )

        assertEquals(45L * minute, snapshot.totalMillis(at(60)))
        assertEquals(2, snapshot.totalOpens())
    }

    @Test
    fun `nothing at all is an empty tally`() {
        val snapshot = ForegroundTally.tally(emptyList(), dayStart, at(60))

        assertEquals(0L, snapshot.totalMillis(at(60)))
        assertEquals(0, snapshot.totalOpens())
        assertNull(snapshot.foreground)
    }
}
