package com.saarlabs.tminus.android.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Refreshes the home-screen widgets.
 *
 * Bumping the refresh signal is what actually re-renders them: every widget composition collects it,
 * so a bump produces a new composition with a recomputed countdown. [updateAll] then covers widgets
 * whose Glance session is not currently running.
 *
 * The previous implementation resolved individual Glance ids through an 18-attempt retry loop,
 * because `getGlanceIdBy` is unreliable immediately after a widget is placed. That is no longer
 * needed: configuration changes reach a live session through the config store's flow, and this
 * worker only has to nudge everything else. [updateAll] is per-provider, so there is also no way to
 * compose one widget type's content into another provider's instance.
 */
public class WidgetUpdateWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        WidgetConfigStore(applicationContext).bumpRefreshTick()
        return try {
            for (widget in allWidgets()) {
                widget.updateAll(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "widget refresh failed", e)
            Result.retry()
        }
    }

    public companion object {
        private const val TAG = "WidgetUpdateWorker"

        public const val PERIODIC_WORK_NAME: String = "WidgetUpdatePeriodic"

        /** Every widget type the app publishes. */
        private fun allWidgets(): List<GlanceAppWidget> =
            listOf(
                MBTATripWidget(),
                MBTAStationBoardWidget(),
                MBTAFavoritesWidget(),
                MBTAAlertsWidget(),
            )

        /**
         * Refreshes every placed widget. [appWidgetIds] is accepted so call sites can stay explicit
         * about what they just changed, but a refresh is cheap and global, so it is not used to
         * narrow the work.
         */
        @JvmOverloads
        public fun enqueueRefresh(context: Context, appWidgetIds: IntArray? = null) {
            WorkManager.getInstance(context.applicationContext)
                .enqueue(OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build())
        }

        /**
         * Keeps widgets fresh without the user opening the app. 15 minutes is WorkManager's floor;
         * [LiveUpdateManager] provides the finer per-minute cadence while the screen is on.
         */
        public fun ensurePeriodicRefresh(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<WidgetUpdateWorker>(15, TimeUnit.MINUTES).build(),
                )
        }
    }
}
