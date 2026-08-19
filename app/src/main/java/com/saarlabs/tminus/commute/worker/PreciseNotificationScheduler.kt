package com.saarlabs.tminus.commute.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
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


    /**
     * Queue a wakeup for [tag]'s event, replacing any wakeup already queued for it.
     *
     * The periodic worker re-plans the same trip on every tick, so this is called repeatedly for
     * one departure. Plain `enqueue` gave each call its own work request: a single arrival had
     * four wakeups queued against it in testing, and each one wakes the process and re-runs the
     * whole worker — MBTA fetches included — only for the delivery marker to discard the result.
     * Keying the work by [tag] collapses those to one, and REPLACE keeps the newest trigger time
     * when a schedule shifts.
     */
    fun scheduleAt(context: Context, tag: String, triggerAtMs: Long) {
        val nowMs = System.currentTimeMillis()
        val delayMs = triggerAtMs - nowMs
        // Any positive delay is worth a wakeup. Near-future events used to be left to the periodic
        // tick, but that tick is up to 15 minutes away and the trip it re-plans by then is a
        // different one — a leave time 50 seconds out was simply dropped.
        if (delayMs <= 0 || delayMs > MAX_LOOKAHEAD_MS) return
        val uniqueName = TAG_PREFIX + tag
        val work =
            OneTimeWorkRequestBuilder<TminusNotificationWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag(uniqueName)
                .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, work)
    }

    private const val TAG_PREFIX = "tminus_precise_"
}
