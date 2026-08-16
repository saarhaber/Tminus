package com.saarlabs.tminus

import com.saarlabs.tminus.model.LocationType
import com.saarlabs.tminus.model.Route
import com.saarlabs.tminus.model.RouteType
import com.saarlabs.tminus.model.Schedule
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.Trip
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.model.response.ScheduleResponse
import com.saarlabs.tminus.util.EasternTimeInstant
import kotlinx.datetime.Month

/** Builders that keep the tests readable; only the fields under test are ever varied. */
internal object Fixtures {

    fun stop(
        id: String,
        name: String = id,
        parent: String? = null,
        children: List<String> = emptyList(),
        locationType: LocationType = LocationType.STOP,
        vehicleType: RouteType? = null,
        platformCode: String? = null,
    ) = Stop(
        id = id,
        latitude = 42.0,
        longitude = -71.0,
        name = name,
        locationType = locationType,
        platformCode = platformCode,
        vehicleType = vehicleType,
        childStopIds = children,
        parentStationId = parent,
    )

    fun route(
        id: String,
        longName: String = id,
        shortName: String = "",
        type: RouteType = RouteType.HEAVY_RAIL,
    ) = Route(
        id = id,
        type = type,
        color = "DA291C",
        directionNames = listOf("South", "North"),
        directionDestinations = listOf("Ashmont", "Alewife"),
        longName = longName,
        shortName = shortName,
        sortOrder = 1,
        textColor = "FFFFFF",
    )

    fun trip(id: String, routeId: String, headsign: String = "Alewife"): Trip =
        Trip(id = id, directionId = 0, headsign = headsign, routeId = routeId)

    fun schedule(
        id: String,
        tripId: String,
        stopId: String,
        stopSequence: Int,
        at: EasternTimeInstant,
        headsign: String? = null,
    ) = Schedule(
        id = id,
        arrivalTime = at,
        departureTime = at,
        stopHeadsign = headsign,
        stopSequence = stopSequence,
        stopId = stopId,
        tripId = tripId,
    )

    fun globalData(stops: List<Stop>, routes: List<Route> = emptyList()) =
        GlobalData(
            stops = stops.associateBy { it.id },
            routes = routes.associateBy { it.id },
        )

    fun scheduleResponse(schedules: List<Schedule>, trips: List<Trip>) =
        ScheduleResponse(schedules = schedules, trips = trips.associateBy { it.id })

    /** A fixed instant so tests never depend on the wall clock. */
    fun at(hour: Int, minute: Int, day: Int = 17): EasternTimeInstant =
        EasternTimeInstant(year = 2026, month = Month.AUGUST, day = day, hour = hour, minute = minute)
}
