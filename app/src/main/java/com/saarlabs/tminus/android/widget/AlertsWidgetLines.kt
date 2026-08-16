package com.saarlabs.tminus.android.widget

import com.saarlabs.tminus.R

/**
 * The selectable subway line groups for the service alerts widget, plus the MBTA V3 `filter[route]`
 * value each maps to. Shared by [MBTAAlertsWidget] and the config screen so the ids stay in sync.
 */
internal object AlertsWidgetLines {

    internal data class Line(val id: String, val labelRes: Int, val routeFilter: String)

    val ALL: List<Line> =
        listOf(
            Line("Red", R.string.widget_alerts_line_red, "Red"),
            Line("Orange", R.string.widget_alerts_line_orange, "Orange"),
            Line("Blue", R.string.widget_alerts_line_blue, "Blue"),
            Line("Green", R.string.widget_alerts_line_green, "Green-B,Green-C,Green-D,Green-E"),
            Line("Mattapan", R.string.widget_alerts_line_mattapan, "Mattapan"),
        )

    /** Comma-separated route filter for the selected ids; an empty selection means every line. */
    fun filterFor(selectedIds: List<String>): String {
        val selected = ALL.filter { it.id in selectedIds }.ifEmpty { ALL }
        return selected.joinToString(",") { it.routeFilter }
    }
}
