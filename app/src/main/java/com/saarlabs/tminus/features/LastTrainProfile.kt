package com.saarlabs.tminus.features

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
public enum class LastTrainMode {
    LAST,
    FIRST,
}

/** Notify before the last or first scheduled train at a stop on chosen days. */
@Serializable
public data class LastTrainProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val routeId: String,
    /** MBTA API direction_id (0 or 1). */
    val directionId: Int,
    val stopId: String,
    val stopLabel: String = "",
    val mode: LastTrainMode,
    val daysOfWeek: List<Int>,
    /** Minutes before that departure to notify. */
    val notifyMinutesBefore: Int = 45,
    /** For LAST: search window start (minutes from midnight). Default 18:00. */
    val windowStartMinutes: Int = 18 * 60,
    /** For LAST: search window end. Default 23:59. */
    val windowEndMinutes: Int = 23 * 60 + 59,
    /** For FIRST: search window (default 04:00–10:00). */
    val firstWindowStartMinutes: Int = 4 * 60,
    val firstWindowEndMinutes: Int = 10 * 60,
    val enabled: Boolean = true,
)
