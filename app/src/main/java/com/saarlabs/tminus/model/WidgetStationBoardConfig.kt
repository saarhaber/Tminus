package com.saarlabs.tminus.model

import kotlinx.serialization.Serializable

@Serializable
public data class WidgetStationBoardConfig(
    val stopId: String,
    val stopLabel: String = "",
    /** When null, show all routes serving the stop. */
    val routeId: String? = null,
)
