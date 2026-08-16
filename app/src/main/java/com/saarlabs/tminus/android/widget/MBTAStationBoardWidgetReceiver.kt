package com.saarlabs.tminus.android.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

public class MBTAStationBoardWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = MBTAStationBoardWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val configStore = WidgetConfigStore(context.applicationContext)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            appWidgetIds.forEach { configStore.removeStationBoardConfig(it) }
        }
        LiveUpdateManager.ensureRunningIfNeeded(context.applicationContext)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        LiveUpdateManager.ensureRunningIfNeeded(context.applicationContext)
    }
}
