package com.saarlabs.tminus.android.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.delay

/**
 * Forces one widget instance to recompose after prefs change (e.g. configuration). Uses the same
 * retry as [WidgetUpdateWorker] for Glance IDs that are not registered yet.
 */
internal suspend fun updateTripWidgetWithRetry(context: Context, appWidgetId: Int) {
    val glanceAppWidgetManager = GlanceAppWidgetManager(context.applicationContext)
    val widget = MBTATripWidget()
    repeat(WidgetUpdateWorker.MAX_RETRIES) { attempt ->
        try {
            val glanceId = glanceAppWidgetManager.getGlanceIdBy(appWidgetId)
            widget.update(context.applicationContext, glanceId)
            return
        } catch (e: IllegalArgumentException) {
            if (attempt < WidgetUpdateWorker.MAX_RETRIES - 1) delay(WidgetUpdateWorker.RETRY_DELAY_MS)
        }
    }
}

public class WidgetUpdateWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val requestedIds = inputData.getIntArray(KEY_APP_WIDGET_IDS)
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val componentName = ComponentName(applicationContext, MBTATripWidgetReceiver::class.java)
        val appWidgetIds =
            requestedIds?.toList() ?: appWidgetManager.getAppWidgetIds(componentName).toList()

        if (appWidgetIds.isEmpty()) return Result.success()

        val updateIntent =
            Intent(applicationContext, MBTATripWidgetReceiver::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds.toIntArray())
            }
        applicationContext.sendBroadcast(updateIntent)

        val glanceAppWidgetManager = GlanceAppWidgetManager(applicationContext)
        val widget = MBTATripWidget()

        for (appWidgetId in appWidgetIds) {
            updateWithRetry(glanceAppWidgetManager, widget, appWidgetId)
        }

        return Result.success()
    }

    private suspend fun updateWithRetry(
        glanceManager: GlanceAppWidgetManager,
        widget: MBTATripWidget,
        appWidgetId: Int,
    ) {
        repeat(MAX_RETRIES) { attempt ->
            try {
                val glanceId = glanceManager.getGlanceIdBy(appWidgetId)
                widget.update(applicationContext, glanceId)
                return
            } catch (e: IllegalArgumentException) {
                if (attempt < MAX_RETRIES - 1) delay(RETRY_DELAY_MS)
            }
        }
    }

    public companion object {
        public const val WORK_NAME: String = "WidgetUpdate"
        public const val KEY_APP_WIDGET_IDS: String = "appWidgetIds"
        internal const val MAX_RETRIES = 5
        internal const val RETRY_DELAY_MS = 300L

        /**
         * Refreshes trip widgets. Pass specific IDs after configuration, or null to update every
         * placed instance (e.g. when returning to the home screen).
         */
        public fun enqueueRefresh(context: Context, appWidgetIds: IntArray? = null) {
            val builder = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            if (appWidgetIds != null) {
                builder.setInputData(workDataOf(KEY_APP_WIDGET_IDS to appWidgetIds))
            }
            WorkManager.getInstance(context.applicationContext).enqueue(builder.build())
        }
    }
}
