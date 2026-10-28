package com.pw.parent

import com.pw.parent.utils.ReminderSwipeTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hidden "2 swipes = 1 test notification" gesture must fire exactly once per pair of swipes,
 * must ignore taps / vertical scrolls / gestures that start below the strip, and must respect the
 * cooldown. These are pure-JVM checks, so CI validates the trigger without a device.
 */
class ReminderSwipeTrackerTest {

    private fun tracker() = ReminderSwipeTracker(
        stripHeightPx = 150f,   // ~48dp @ xxhdpi
        minSwipePx = 300f,      // ~96dp
        maxVerticalPx = 150f,
        swipesRequired = 2,
        windowMs = 4_000L,
        cooldownMs = 8_000L
    )

    /** One clean horizontal swipe inside the strip. */
    private fun ReminderSwipeTracker.swipe(timeMs: Long, y: Float = 50f, startX: Float = 100f): ReminderSwipeTracker.Outcome {
        onDown(startX, y, timeMs)
        val outcome = onMove(startX + 400f, y + 6f, timeMs + 150)
        onUp()
        return outcome
    }

    @Test
    fun twoSwipesFireExactlyOneNotification() {
        val t = tracker()
        assertEquals(ReminderSwipeTracker.Outcome.NEEDS_ONE_MORE, t.swipe(1_000))
        assertEquals(ReminderSwipeTracker.Outcome.FIRE, t.swipe(1_500))
    }

    @Test
    fun singleSwipeDoesNotFire() {
        val t = tracker()
        assertEquals(ReminderSwipeTracker.Outcome.NEEDS_ONE_MORE, t.swipe(1_000))
    }

    @Test
    fun swipesOutsideThePairWindowDoNotAccumulate() {
        val t = tracker()
        assertEquals(ReminderSwipeTracker.Outcome.NEEDS_ONE_MORE, t.swipe(1_000))
        // 10s later — the first swipe is stale, so this one starts a new pair.
        assertEquals(ReminderSwipeTracker.Outcome.NEEDS_ONE_MORE, t.swipe(11_000))
    }

    @Test
    fun cooldownPreventsSpamming() {
        val t = tracker()
        assertEquals(ReminderSwipeTracker.Outcome.NEEDS_ONE_MORE, t.swipe(1_000))
        assertEquals(ReminderSwipeTracker.Outcome.FIRE, t.swipe(1_500))
        // Immediately repeating the pair must stay silent during the cooldown…
        assertEquals(ReminderSwipeTracker.Outcome.NEEDS_ONE_MORE, t.swipe(2_000))
        assertEquals(ReminderSwipeTracker.Outcome.NONE, t.swipe(2_500))
        // …and fire again once the cooldown has elapsed.
        assertEquals(ReminderSwipeTracker.Outcome.NEEDS_ONE_MORE, t.swipe(20_000))
        assertEquals(ReminderSwipeTracker.Outcome.FIRE, t.swipe(20_500))
    }

    @Test
    fun gestureBelowTheStripIsIgnored() {
        val t = tracker()
        assertFalse(t.onDown(100f, 400f, 1_000))
        assertEquals(ReminderSwipeTracker.Outcome.NONE, t.onMove(600f, 400f, 1_100))
    }

    @Test
    fun verticalScrollIsIgnored() {
        val t = tracker()
        t.onDown(100f, 40f, 1_000)
        val outcome = t.onMove(110f, 500f, 1_100) // mostly vertical
        t.onUp()
        assertEquals(ReminderSwipeTracker.Outcome.NONE, outcome)
    }

    @Test
    fun tapIsIgnored() {
        val t = tracker()
        t.onDown(100f, 40f, 1_000)
        assertEquals(ReminderSwipeTracker.Outcome.NONE, t.onMove(130f, 42f, 1_050))
        t.onUp()
    }

    @Test
    fun oneGestureCountsOnlyOnce() {
        val t = tracker()
        t.onDown(100f, 40f, 1_000)
        val first = t.onMove(700f, 40f, 1_050)
        // Finger keeps dragging in the same gesture: must not count as another swipe.
        val second = t.onMove(900f, 40f, 1_100)
        t.onUp()
        assertEquals(ReminderSwipeTracker.Outcome.NEEDS_ONE_MORE, first)
        assertEquals(ReminderSwipeTracker.Outcome.NONE, second)
        assertTrue(true)
    }
}
