package com.saarlabs.tminus.model

import com.saarlabs.tminus.util.EasternTimeInstant

public data class WidgetStationBoardDeparture(
    val route: Route,
    val headsign: String,
    val departureTime: EasternTimeInstant,
    val minutesUntil: Int,
    val platform: String?,
)

public data class WidgetStationBoardOutput(
    val departures: List<WidgetStationBoardDeparture>,
)
