package com.saarlabs.tminus.usecases

import com.saarlabs.tminus.data.ScheduleRepository
import com.saarlabs.tminus.model.RouteType
import com.saarlabs.tminus.model.Schedule
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.WidgetTripData
import com.saarlabs.tminus.model.WidgetTripOutput
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.model.response.ScheduleResponse
import com.saarlabs.tminus.util.EasternTimeInstant

/**
 * Next trip between two stops (ported from [mbta/mobile_app#1593](https://github.com/mbta/mobile_app/pull/1593)),
 * using the public MBTA V3 API instead of the MBTA Go backend.
 */
public class WidgetTripUseCase internal constructor(private val schedules: ScheduleRepository) {

    public suspend fun getNextTrip(
        globalData: GlobalData,
        fromStopId: String,
        toStopId: String,
        now: EasternTimeInstant = EasternTimeInstant.now(),
    ): ApiResult<WidgetTripOutput> {
        val fromStop = globalData.getStop(fromStopId) ?: return ApiResult.Ok(WidgetTripOutput(null))
        val toStop = globalData.getStop(toStopId) ?: return ApiResult.Ok(WidgetTripOutput(null))

        val requestStopIds =
            (globalData.stopIdsForScheduleFilter(fromStop) + globalData.stopIdsForScheduleFilter(toStop))
                .distinct()

        val scheduleResponse =
            when (val result = schedules.scheduleForStops(requestStopIds, now)) {
                is ApiResult.Ok -> result.data
                is ApiResult.Error ->
                    return ApiResult.Error(code = result.code, message = result.message)
            }

        return ApiResult.Ok(
            WidgetTripOutput(findNextTrip(scheduleResponse, globalData, fromStopId, toStopId, now)),
        )
    }

    private fun findNextTrip(
        response: ScheduleResponse,
        globalData: GlobalData,
        fromStopId: String,
        toStopId: String,
        now: EasternTimeInstant,
    ): WidgetTripData? {
        val stops = globalData.stops
        val pair = earliestConnectingPair(response, stops, fromStopId, toStopId, now) ?: return null
        val (fromSchedule, toSchedule) = pair

        val trip = response.trips[fromSchedule.tripId] ?: return null
        val route = globalData.getRoute(trip.routeId) ?: return null

        val fromResolved = globalData.getStop(fromStopId)?.resolveParent(stops) ?: return null
        val toResolved = globalData.getStop(toStopId)?.resolveParent(stops) ?: return null
        val fromScheduleStop = stops[fromSchedule.stopId] ?: fromResolved
        val toScheduleStop = stops[toSchedule.stopId] ?: toResolved

        val departureTime = fromSchedule.departureTime ?: fromSchedule.arrivalTime ?: return null
        val arrivalTime = toSchedule.arrivalTime ?: toSchedule.departureTime ?: return null

        return WidgetTripData(
            fromStop = fromResolved,
            toStop = toResolved,
            route = route,
            tripId = trip.id,
            departureTime = departureTime,
            arrivalTime = arrivalTime,
            minutesUntil = (departureTime - now).inWholeMinutes.toInt().coerceAtLeast(0),
            fromPlatform = fromScheduleStop.commuterRailPlatform(),
            toPlatform = toScheduleStop.commuterRailPlatform(),
            headsign = fromSchedule.stopHeadsign ?: trip.headsign,
        )
    }
}

/** Platform/track labels are only meaningful — and only published — for commuter rail. */
internal fun Stop.commuterRailPlatform(): String? =
    if (vehicleType == RouteType.COMMUTER_RAIL) platformCode else null

/**
 * The earliest departure at [fromStopId], on or after [now], that is followed later in the same trip
 * by a stop at [toStopId].
 *
 * Schedules are indexed by trip id first. Pairing the two lists directly is quadratic, and both
 * routinely hold hundreds of rows for a busy station, so the previous `flatMap`/`filter` form did
 * tens of thousands of comparisons on every widget refresh.
 */
internal fun earliestConnectingPair(
    response: ScheduleResponse,
    stops: Map<String, Stop>,
    fromStopId: String,
    toStopId: String,
    now: EasternTimeInstant,
    latest: EasternTimeInstant? = null,
    accept: (EasternTimeInstant) -> Boolean = { true },
): Pair<Schedule, Schedule>? {
    val destinationsByTrip = HashMap<String, MutableList<Schedule>>()
    for (schedule in response.schedules) {
        if (!Stop.equalOrFamily(schedule.stopId, toStopId, stops)) continue
        destinationsByTrip.getOrPut(schedule.tripId) { mutableListOf() }.add(schedule)
    }
    if (destinationsByTrip.isEmpty()) return null

    var best: Pair<Schedule, Schedule>? = null
    var bestDeparture: EasternTimeInstant? = null

    for (origin in response.schedules) {
        if (!Stop.equalOrFamily(origin.stopId, fromStopId, stops)) continue
        val departure = origin.departureTime ?: origin.arrivalTime ?: continue
        if (departure < now) continue
        if (latest != null && departure > latest) continue
        if (!accept(departure)) continue
        if (bestDeparture != null && departure >= bestDeparture) continue

        val destination =
            destinationsByTrip[origin.tripId]
                ?.filter { it.stopSequence > origin.stopSequence }
                ?.minByOrNull { it.stopSequence }
                ?: continue

        best = origin to destination
        bestDeparture = departure
    }
    return best
}
