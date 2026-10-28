package com.pw.parent.utils

/**
 * Pure logic for the hidden "2 swipes = 1 test notification" gesture.
 *
 * Why a gesture at all: the real reminders only fire on Monday, so checking how a notification
 * looks would otherwise mean waiting a week. The detector watches the top strip of the WebView
 * (the area just below the system status bar — the status bar itself belongs to SystemUI and
 * cannot be observed by the app) and fires after two horizontal swipes.
 *
 * Kept free of Android imports so it is unit-testable on the JVM
 * (see `ReminderSwipeTrackerTest`).
 */
class ReminderSwipeTracker(
    private val stripHeightPx: Float,
    private val minSwipePx: Float,
    private val maxVerticalPx: Float,
    private val swipesRequired: Int = 2,
    private val windowMs: Long = 4_000L,
    private val cooldownMs: Long = 8_000L
) {

    enum class Outcome {
        /** Nothing happened (tap, vertical scroll, gesture started too low, cooldown…). */
        NONE,

        /** Swipe registered, but the user still has to swipe again. */
        NEEDS_ONE_MORE,

        /** Enough swipes: post the test notification now. */
        FIRE
    }

    private var tracking = false
    private var alreadyCounted = false
    private var startX = 0f
    private var startY = 0f
    private var swipes = 0
    private var windowStart = 0L
    private var lastFired = Long.MIN_VALUE

    /** True when the touch started inside the top strip, i.e. it may become a swipe. */
    fun onDown(x: Float, y: Float, timeMs: Long): Boolean {
        tracking = y <= stripHeightPx
        alreadyCounted = false
        startX = x
        startY = y
        if (tracking && timeMs - windowStart > windowMs) {
            // Too long since the previous swipe — start a fresh pair.
            swipes = 0
            windowStart = timeMs
        }
        return tracking
    }

    fun onMove(x: Float, y: Float, timeMs: Long): Outcome {
        if (!tracking || alreadyCounted) return Outcome.NONE

        val dx = x - startX
        val dy = y - startY

        // Must be a mostly-horizontal drag of a decent length, so normal taps/scrolls are ignored.
        if (kotlin.math.abs(dx) < minSwipePx) return Outcome.NONE
        if (kotlin.math.abs(dy) > maxVerticalPx) return Outcome.NONE

        alreadyCounted = true
        if (timeMs - windowStart > windowMs) {
            swipes = 0
            windowStart = timeMs
        }
        swipes += 1

        if (swipes < swipesRequired) return Outcome.NEEDS_ONE_MORE

        // Two swipes collected: fire once, then stay quiet for the cooldown period.
        swipes = 0
        windowStart = timeMs
        if (timeMs - lastFired < cooldownMs) return Outcome.NONE
        lastFired = timeMs
        return Outcome.FIRE
    }

    fun onUp() {
        tracking = false
        alreadyCounted = false
    }
}
