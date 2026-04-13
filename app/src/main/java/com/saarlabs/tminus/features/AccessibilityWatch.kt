package com.saarlabs.tminus.features

import java.util.UUID
import kotlinx.serialization.Serializable

/** Notify when MBTA posts alerts (elevator/escalator) affecting a route you care about at a station. */
@Serializable
public data class AccessibilityWatch(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val routeId: String,
    /** Parent station or stop id to match against alert text. */
    val stopId: String,
    val stopLabel: String = "",
    val enabled: Boolean = true,
)
