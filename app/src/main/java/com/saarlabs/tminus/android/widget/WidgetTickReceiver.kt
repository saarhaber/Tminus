package com.saarlabs.tminus.android.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Consumes each tick from [LiveUpdateManager]: refreshes the widgets and queues the next alarm.
 *
 * A tick is cheap. Bumping the refresh signal recomposes every widget, and the countdown is derived
 * from timetable data that [com.saarlabs.tminus.data.ScheduleRepository] already holds — so a tick
 * normally performs no network I/O at all. Before that cache existed, every minute cost one
 * `/schedules` request per placed widget.
 *
 * [goAsync] keeps the process alive across the coroutine, and the result is always finished so the
 * process can return to idle between ticks.
 */
public class WidgetTickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LiveUpdateManager.ACTION_TICK) return

        val appCtx = context.applicationContext

        // Honour a stop() that raced the alarm: don't update and don't chain.
        if (!LiveUpdateManager.isRunning(appCtx)) {
            LiveUpdateManager.stop(appCtx)
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetConfigStore(appCtx).bumpRefreshTick()
                MBTATripWidget().updateAll(appCtx)
                MBTAStationBoardWidget().updateAll(appCtx)
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "updateAll failed", t)
            } finally {
                try {
                    LiveUpdateManager.scheduleNext(appCtx)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private companion object {
        private const val TAG = "WidgetTickReceiver"
    }
}
