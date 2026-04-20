package com.saarlabs.tminus.commute.worker

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules a one-off [TminusNotificationWorker] run at the exact time a notification should fire,
 * rather than relying on the 15-minute periodic tick which drifts.
 *
 * The periodic worker identifies *which* trip to notify about; this scheduler wakes the worker up
 * again right at `leaveAtMs` (or `arrivalMs`) so the notification appears close to the user-chosen
 * lead time instead of whenever the next 15-minute tick happens to land.
 */
internal object PreciseNotificationScheduler {
    /**
     * Only schedule precise wakeups for trips within this window. Beyond this we'll be picked up
     * naturally on the next periodic tick.
     */
    private const val MAX_LOOKAHEAD_MS = 45L * 60_000L

    /** Don't schedule for events in the past or extremely near-future (periodic tick handles those). */
    private const val MIN_LEAD_MS = 60_000L

    fun scheduleAt(context: Context, tag: String, triggerAtMs: Long) {
        val nowMs = System.currentTimeMillis()
        val delayMs = triggerAtMs - nowMs
        if (delayMs < MIN_LEAD_MS || delayMs > MAX_LOOKAHEAD_MS) return
        val work =
            OneTimeWorkRequestBuilder<TminusNotificationWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag(TAG_PREFIX + tag)
                .build()
        WorkManager.getInstance(context.applicationContext).enqueue(work)
    }

    private const val TAG_PREFIX = "tminus_precise_"
}
