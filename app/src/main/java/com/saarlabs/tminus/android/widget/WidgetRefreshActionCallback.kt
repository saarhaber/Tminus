package com.saarlabs.tminus.android.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.saarlabs.tminus.AppGraph

/**
 * One-tap refresh from a widget.
 *
 * Forces a network round trip rather than reusing the cached timetable, because the user only taps
 * this when what they are looking at seems wrong.
 */
public class WidgetRefreshActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appContext = context.applicationContext
        AppGraph.from(appContext).schedules.invalidate()
        WidgetConfigStore(appContext).bumpRefreshTick()
        WidgetUpdateWorker.enqueueRefresh(appContext)
    }
}
