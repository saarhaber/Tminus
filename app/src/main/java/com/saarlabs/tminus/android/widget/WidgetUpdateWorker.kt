package com.saarlabs.tminus.android.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

/**
 * Updates a single Glance widget instance by scanning [GlanceAppWidgetManager.getGlanceIds] for
 * [widget]'s own provider class and matching [GlanceAppWidgetManager.getAppWidgetId]. Scanning the
 * per-provider registry (instead of the unchecked [GlanceAppWidgetManager.getGlanceIdBy]) guarantees
 * we never compose one widget type's content into another provider's instance; if Glance has not
 * registered the id yet (common right after configuration), this returns false and callers retry.
 */
private suspend fun tryUpdateGlanceWidget(
    context: Context,
    glanceAppWidgetManager: GlanceAppWidgetManager,
    appWidgetId: Int,
    widget: GlanceAppWidget,
): Boolean {
    val appCtx = context.applicationContext
    return try {
        val glanceIds = glanceAppWidgetManager.getGlanceIds(widget.javaClass)
        for (glanceId in glanceIds) {
            try {
                if (glanceAppWidgetManager.getAppWidgetId(glanceId) == appWidgetId) {
                    widget.update(appCtx, glanceId)
                    return true
                }
            } catch (_: IllegalArgumentException) {
                continue
            }
        }
        false
    } catch (_: IllegalArgumentException) {
        false
    }
}

/**
 * Forces one trip widget instance to recompose after prefs change (e.g. configuration). Uses the same
 * retry as [WidgetUpdateWorker] for Glance IDs that are not registered yet.
 */
internal suspend fun updateTripWidgetWithRetry(context: Context, appWidgetId: Int) {
    updateGlanceWidgetWithRetry(
        context = context,
        appWidgetId = appWidgetId,
        widget = MBTATripWidget(),
    )
}

/**
 * Same as [updateTripWidgetWithRetry] for the station board widget.
 */
internal suspend fun updateStationBoardWidgetWithRetry(context: Context, appWidgetId: Int) {
    updateGlanceWidgetWithRetry(
        context = context,
        appWidgetId = appWidgetId,
        widget = MBTAStationBoardWidget(),
    )
}

/** Same as [updateTripWidgetWithRetry] for the favorites widget. */
internal suspend fun updateFavoritesWidgetWithRetry(context: Context, appWidgetId: Int) {
    updateGlanceWidgetWithRetry(
        context = context,
        appWidgetId = appWidgetId,
        widget = MBTAFavoritesWidget(),
    )
}

/** Same as [updateTripWidgetWithRetry] for the service alerts widget. */
internal suspend fun updateAlertsWidgetWithRetry(context: Context, appWidgetId: Int) {
    updateGlanceWidgetWithRetry(
        context = context,
        appWidgetId = appWidgetId,
        widget = MBTAAlertsWidget(),
    )
}

private suspend fun updateGlanceWidgetWithRetry(
    context: Context,
    appWidgetId: Int,
    widget: GlanceAppWidget,
) {
    val glanceAppWidgetManager = GlanceAppWidgetManager(context.applicationContext)
    repeat(WidgetUpdateWorker.MAX_RETRIES) { attempt ->
        if (tryUpdateGlanceWidget(context, glanceAppWidgetManager, appWidgetId, widget)) {
            return
        }
        if (attempt < WidgetUpdateWorker.MAX_RETRIES - 1) delay(WidgetUpdateWorker.RETRY_DELAY_MS)
    }
}

public class WidgetUpdateWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    /** Every widget provider this worker refreshes, with a factory for its Glance widget. */
    private data class Provider(
        val receiver: Class<out android.content.BroadcastReceiver>,
        val newWidget: () -> GlanceAppWidget,
    )

    private val providers =
        listOf(
            Provider(MBTATripWidgetReceiver::class.java) { MBTATripWidget() },
            Provider(MBTAStationBoardWidgetReceiver::class.java) { MBTAStationBoardWidget() },
            Provider(MBTAFavoritesWidgetReceiver::class.java) { MBTAFavoritesWidget() },
            Provider(MBTAAlertsWidgetReceiver::class.java) { MBTAAlertsWidget() },
        )

    override suspend fun doWork(): Result {
        val requestedIds = inputData.getIntArray(KEY_APP_WIDGET_IDS)
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)

        val idsByProvider = providers.associateWith { mutableListOf<Int>() }
        val ambiguousIds = mutableListOf<Int>()

        if (requestedIds != null) {
            for (id in requestedIds) {
                val className = appWidgetManager.getAppWidgetInfo(id)?.provider?.className
                val provider = providers.firstOrNull { it.receiver.name == className }
                if (provider != null) idsByProvider.getValue(provider).add(id) else ambiguousIds.add(id)
            }
        } else {
            for (provider in providers) {
                val component = ComponentName(applicationContext, provider.receiver)
                idsByProvider.getValue(provider).addAll(appWidgetManager.getAppWidgetIds(component).toList())
            }
        }

        if (idsByProvider.values.all { it.isEmpty() } && ambiguousIds.isEmpty()) {
            return Result.success()
        }

        for ((provider, ids) in idsByProvider) {
            if (ids.isEmpty()) continue
            val updateIntent =
                Intent(applicationContext, provider.receiver).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids.toIntArray())
                }
            applicationContext.sendBroadcast(updateIntent)
        }

        val glanceAppWidgetManager = GlanceAppWidgetManager(applicationContext)

        for ((provider, ids) in idsByProvider) {
            for (appWidgetId in ids) {
                updateWithRetry(glanceAppWidgetManager, provider.newWidget(), appWidgetId)
            }
        }
        // Provider info was unavailable (registry lag on some launchers): try every widget type.
        // tryUpdateGlanceWidget only matches ids registered to the widget's own provider class, so
        // at most one type actually composes.
        for (appWidgetId in ambiguousIds) {
            updateAmbiguousWithRetry(glanceAppWidgetManager, appWidgetId)
        }

        return Result.success()
    }

    private suspend fun updateAmbiguousWithRetry(
        glanceManager: GlanceAppWidgetManager,
        appWidgetId: Int,
    ) {
        repeat(MAX_RETRIES) { attempt ->
            for (provider in providers) {
                if (tryUpdateGlanceWidget(applicationContext, glanceManager, appWidgetId, provider.newWidget())) {
                    return
                }
            }
            if (attempt < MAX_RETRIES - 1) delay(RETRY_DELAY_MS)
        }
    }

    private suspend fun updateWithRetry(
        glanceManager: GlanceAppWidgetManager,
        widget: GlanceAppWidget,
        appWidgetId: Int,
    ) {
        repeat(MAX_RETRIES) { attempt ->
            if (tryUpdateGlanceWidget(applicationContext, glanceManager, appWidgetId, widget)) {
                return
            }
            if (attempt < MAX_RETRIES - 1) delay(RETRY_DELAY_MS)
        }
    }

    public companion object {
        public const val WORK_NAME: String = "WidgetUpdate"
        public const val PERIODIC_WORK_NAME: String = "WidgetUpdatePeriodic"
        public const val KEY_APP_WIDGET_IDS: String = "appWidgetIds"
        /** Glance id registry can lag right after configuration — extra attempts reduce stuck placeholders. */
        internal const val MAX_RETRIES = 18
        internal const val RETRY_DELAY_MS = 450L

        /**
         * Refreshes home screen widgets (trip, station board, favorites, alerts). Pass specific IDs
         * after configuration, or null to update every placed instance (e.g. when returning to the
         * home screen).
         */
        public fun enqueueRefresh(context: Context, appWidgetIds: IntArray? = null) {
            val builder = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            if (appWidgetIds != null) {
                builder.setInputData(workDataOf(KEY_APP_WIDGET_IDS to appWidgetIds))
            }
            WorkManager.getInstance(context.applicationContext).enqueue(builder.build())
        }

        /**
         * Schedules a periodic refresh of every placed widget so the "min until departure" and
         * displayed times stay fresh without the user having to open the app or tap refresh. 15 min
         * is WorkManager's floor for periodic work.
         */
        public fun ensurePeriodicRefresh(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<WidgetUpdateWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }
    }
}
