package com.saarlabs.tminus.model

/** Per-instance config for the service alerts widget: which subway line groups to watch. */
public data class WidgetAlertsConfig(
    val lineGroupIds: List<String>,
)
