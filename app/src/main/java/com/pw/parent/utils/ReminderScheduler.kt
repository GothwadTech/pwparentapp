package com.pw.parent.utils

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Scheduled parent reminders — **no Firebase / no server required**.
 *
 * Push notifications (FCM) need `google-services.json` and a backend that sends messages. These
 * reminders are generated on the device itself, so they work today, offline, for every install.
 *
 * Cadence (local time, Monday only): 06:00, 08:00, 10:00, 12:00, 14:00, 16:00, 18:00.
 * Exactly the window the school shares weekly results in, so a parent gets a nudge every two
 * hours from morning to evening with a rotating "check your child's performance / result is out"
 * style message.
 *
 * Implementation: one chained [OneTimeWorkRequest] at a time. The worker fires, posts the
 * notification and schedules the next slot, so there is no polling loop and no battery drain.
 * WorkManager persists the queue across reboots and app-kills, so the schedule survives even if
 * the user swipes the app away.
 */
object ReminderScheduler {

    /** Hours of the day (24h, local time) a reminder is delivered on Monday. */
    val SLOT_HOURS = intArrayOf(6, 8, 10, 12, 14, 16, 18)

    private const val PREFS = "parent_reminder_prefs"
    private const val KEY_ENABLED = "reminders_enabled"
    private const val KEY_LAST_SLOT = "last_sent_slot_time"
    private const val KEY_MESSAGE_INDEX = "next_message_index"
    private const val UNIQUE_WORK_NAME = "parent_weekly_reminder"

    /** Rotating, parent-facing reminders. `{app}` is replaced with the app name. */
    private val MESSAGES = listOf(
        "Parents, please track your child's performance" to
            "Open {app} and check today's activity, tests and progress reports.",
        "Test result is out 🎉" to
            "Your child's latest test result has been published. Tap to view the report now.",
        "Performance check-in" to
            "2 minutes today can help your child a lot — review marks and weak topics in {app}.",
        "Weekly progress update" to
            "See how your child performed this week: attendance, tests and teacher feedback.",
        "Homework & test reminder" to
            "Don't forget to check pending homework and upcoming tests in {app}.",
        "Parents, results are live now" to
            "Tap to see the score card and subject-wise analysis for your child.",
        "Keep the streak going 💪" to
            "Your child has been improving. Review the progress report and praise the effort!"
    )

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) scheduleNext(context) else cancelAll(context)
    }

    /**
     * (Re)schedules the next reminder. Safe to call on every app start: it recomputes the next
     * future slot and replaces the pending work, so a clock/timezone change cannot drift it.
     */
    fun ensureScheduled(context: Context) {
        if (isEnabled(context)) scheduleNext(context)
    }

    fun scheduleNext(context: Context) {
        val slotTime = nextSlotTime()
        val delayMillis = (slotTime - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(androidx.work.workDataOf(ReminderWorker.KEY_SLOT_TIME to slotTime))
            .addTag(UNIQUE_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /** Milliseconds from now until the next Monday slot (always > 0). */
    internal fun millisUntilNextSlot(now: Long = System.currentTimeMillis()): Long {
        return nextSlotTime(now) - now
    }

    internal fun nextSlotTime(now: Long = System.currentTimeMillis()): Long {
        val base = Calendar.getInstance().apply { timeInMillis = now }
        for (dayOffset in 0..7) {
            for (hour in SLOT_HOURS) {
                val candidate = (base.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                // 60s guard so a slot that is already due fires "now" instead of being skipped.
                if (candidate.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY &&
                    candidate.timeInMillis > now + 60_000L
                ) {
                    return candidate.timeInMillis
                }
            }
        }
        // Unreachable in practice: there is always a Monday within 7 days.
        return now + TimeUnit.DAYS.toMillis(7)
    }

    /**
     * True when this worker run should actually notify.
     *
     * WorkManager may fire late (Doze / battery saver), so the check is "this slot has not been
     * delivered yet and the delay is still reasonable" instead of a strict "is it exactly 06:00".
     * That also makes duplicate executions harmless.
     */
    internal fun shouldSendNow(context: Context, slotTime: Long): Boolean {
        if (slotTime <= 0L) return false
        val now = System.currentTimeMillis()
        // More than 3h late means the slot is stale (e.g. phone was off all morning) — skip it.
        if (now > slotTime + TimeUnit.HOURS.toMillis(3)) return false
        // Same slot already delivered? Duplicate executions become harmless.
        return prefs(context).getLong(KEY_LAST_SLOT, -1L) != slotTime
    }

    /** Rotates through [MESSAGES] so consecutive reminders never repeat. */
    internal fun nextMessage(context: Context): Pair<String, String> {
        val prefs = prefs(context)
        val index = prefs.getInt(KEY_MESSAGE_INDEX, 0).mod(MESSAGES.size)
        val (title, body) = MESSAGES[index]
        prefs.edit().putInt(KEY_MESSAGE_INDEX, (index + 1) % MESSAGES.size).apply()
        val appName = com.pw.parent.BuildConfig.APP_NAME
        return title to body.replace("{app}", appName)
    }

    /**
     * Posts one reminder right now, ignoring the Monday schedule. Used by the hidden
     * "2 swipes on the top strip" gesture and by the JS bridge (`sendTestReminder()`), so the
     * notification copy/layout can be checked without waiting for Monday.
     *
     * @return the title/body that was shown, so callers can surface it.
     */
    fun sendTestNotification(context: Context): Pair<String, String> {
        val message = nextMessage(context)
        ParentNotificationHelper.createNotificationChannel(context)
        ParentNotificationHelper.showNotification(context, message.first, message.second)
        return message
    }

    internal fun markSlotSent(context: Context, slotTime: Long) {
        prefs(context).edit().putLong(KEY_LAST_SLOT, slotTime).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
