package com.saarlabs.tminus.model

import kotlinx.serialization.Serializable

/** Per-instance config for the service alerts widget: which subway line groups to watch. */
@Serializable
public data class WidgetAlertsConfig(
    val lineGroupIds: List<String> = emptyList(),
)
