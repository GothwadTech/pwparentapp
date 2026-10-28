package com.pw.parent.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Fires one scheduled parent reminder, then queues the next Monday slot.
 *
 * The chain is what keeps the cadence alive without a server: the worker itself is the only
 * moving part, and WorkManager persists it across reboots / app kills.
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_SLOT_TIME = "slot_time"
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val slotTime = inputData.getLong(KEY_SLOT_TIME, 0L)
        return try {
            if (ReminderScheduler.isEnabled(context) &&
                ReminderScheduler.shouldSendNow(context, slotTime)
            ) {
                val (title, body) = ReminderScheduler.nextMessage(context)

                // Make sure the channel exists even when the process was started by WorkManager
                // (the Activity may never have been opened since boot).
                ParentNotificationHelper.createNotificationChannel(context)
                ParentNotificationHelper.showNotification(context, title, body)

                ReminderScheduler.markSlotSent(context, slotTime)
            }
            Result.success()
        } catch (t: Throwable) {
            // A reminder is best-effort: never let it crash or spin in a retry loop.
            Result.success()
        } finally {
            // Line up the next Monday slot (also after a skipped/failed run). Done in `finally`
            // so a crash above can never break the weekly chain.
            runCatching {
                if (ReminderScheduler.isEnabled(context)) ReminderScheduler.scheduleNext(context)
            }
        }
    }
}
