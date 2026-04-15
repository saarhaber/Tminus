package com.saarlabs.tminus.android.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import com.saarlabs.tminus.GlobalDataStore

/**
 * One-tap refresh for Glance widgets: re-runs [com.saarlabs.tminus.android.widget.MBTATripWidget] /
 * [MBTAStationBoardWidget] without opening the configuration activity again (e.g. after a Glance
 * registration race or a transient network error).
 */
public class WidgetRefreshActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appContext = context.applicationContext
        val appWidgetId = GlanceAppWidgetManager(appContext).getAppWidgetId(glanceId)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        GlobalDataStore.invalidate()
        WidgetUpdateWorker.enqueueRefresh(appContext, intArrayOf(appWidgetId))
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val isTrip =
            appWidgetManager
                .getAppWidgetIds(ComponentName(appContext, MBTATripWidgetReceiver::class.java))
                .contains(appWidgetId)
        val isStation =
            appWidgetManager
                .getAppWidgetIds(ComponentName(appContext, MBTAStationBoardWidgetReceiver::class.java))
                .contains(appWidgetId)
        when {
            isTrip -> updateTripWidgetWithRetry(appContext, appWidgetId)
            isStation -> updateStationBoardWidgetWithRetry(appContext, appWidgetId)
            else -> {
                // Last resort: [getAppWidgetInfo] can be null or inconsistent on some devices.
                when (appWidgetManager.getAppWidgetInfo(appWidgetId)?.provider) {
                    ComponentName(appContext, MBTATripWidgetReceiver::class.java) ->
                        updateTripWidgetWithRetry(appContext, appWidgetId)
                    ComponentName(appContext, MBTAStationBoardWidgetReceiver::class.java) ->
                        updateStationBoardWidgetWithRetry(appContext, appWidgetId)
                }
            }
        }
    }
}
