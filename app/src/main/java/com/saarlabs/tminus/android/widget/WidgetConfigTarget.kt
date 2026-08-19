package com.saarlabs.tminus.android.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * Works out which widget a configuration activity is configuring.
 *
 * Normally the launcher says so in `EXTRA_APPWIDGET_ID`. Some launchers (One UI among them) start
 * the activity without it, which is why the widget records the id it is waiting on. That fallback
 * used to be trusted blindly, and it is wrong in the case a user hits most often: place a second
 * widget while an unconfigured one is still on a home screen, and the *first* widget's id is the
 * one sitting in the store — so the save lands on the wrong widget and the new one keeps showing
 * "tap to set up".
 *
 * So the pending id is only used if it still names a placed, unconfigured widget. Failing that, if
 * exactly one placed widget of this provider is unconfigured, it must be the one being set up.
 */
internal fun resolveConfigTargetId(
    intentId: Int,
    pendingId: Int,
    placedIds: List<Int>,
    isConfigured: (Int) -> Boolean,
): Int {
    if (intentId != INVALID_WIDGET_ID) return intentId

    val unconfigured = placedIds.filterNot(isConfigured)
    if (pendingId != INVALID_WIDGET_ID && pendingId in unconfigured) return pendingId
    return unconfigured.singleOrNull() ?: INVALID_WIDGET_ID
}

/** Every placed widget id for [provider], or empty when the manager cannot be reached. */
internal fun placedWidgetIds(context: Context, provider: Class<*>): List<Int> =
    runCatching {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, provider))
            .toList()
    }
        .getOrDefault(emptyList())
