package com.pw.parent.utils

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.widget.Toast

/**
 * Installs the hidden "2 swipes → 1 test notification" gesture on a WebView.
 *
 * IMPORTANT: the real status bar (clock / battery / wifi) belongs to SystemUI in a separate
 * window — no app can read touches there. This detector instead watches the strip of the app's
 * own content that sits directly BELOW the status bar (≈48dp), i.e. visually the same "header"
 * area the user swipes at.
 *
 * The listener never consumes the event (it returns `false`), so page scrolling, taps and
 * pinch-zoom keep working exactly as before.
 */
object ReminderSwipeDetector {

    /** Height of the watched strip, below the status bar. */
    private const val STRIP_HEIGHT_DP = 48f

    /** Minimum horizontal travel for one swipe. */
    private const val MIN_SWIPE_DP = 96f

    /** Maximum vertical drift for the gesture to still count as horizontal. */
    private const val MAX_VERTICAL_DP = 48f

    fun attach(webView: WebView, onFire: () -> Unit) {
        val density = webView.resources.displayMetrics.density
        val tracker = ReminderSwipeTracker(
            stripHeightPx = STRIP_HEIGHT_DP * density,
            minSwipePx = MIN_SWIPE_DP * density,
            maxVerticalPx = MAX_VERTICAL_DP * density
        )
        // WebView's own scroll offset is irrelevant: MotionEvent y is already in view coordinates
        // and this listener is on the WebView itself, so y ≈ 0..48dp means "just under the bar".
        val context = webView.context

        @SuppressLint("ClickableViewAccessibility")
        val listener = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tracker.onDown(event.x, event.y, event.eventTime)
                }

                MotionEvent.ACTION_MOVE -> {
                    when (tracker.onMove(event.x, event.y, event.eventTime)) {
                        ReminderSwipeTracker.Outcome.NEEDS_ONE_MORE -> {
                            Toast.makeText(
                                context,
                                "Ek aur swipe — test notification aa jayegi",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        ReminderSwipeTracker.Outcome.FIRE -> {
                            val (title, _) = ReminderScheduler.sendTestNotification(context)
                            Toast.makeText(
                                context,
                                "Test notification sent: $title",
                                Toast.LENGTH_SHORT
                            ).show()
                            onFire()
                        }

                        ReminderSwipeTracker.Outcome.NONE -> Unit
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracker.onUp()
            }
            // Never consume: the page keeps its normal gestures.
            false
        }

        webView.setOnTouchListener(listener)
    }
}
