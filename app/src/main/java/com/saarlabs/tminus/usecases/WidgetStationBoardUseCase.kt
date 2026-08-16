package com.saarlabs.tminus.usecases

import com.saarlabs.tminus.data.ScheduleRepository
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.WidgetStationBoardDeparture
import com.saarlabs.tminus.model.WidgetStationBoardOutput
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.model.response.ScheduleResponse
import com.saarlabs.tminus.util.EasternTimeInstant

public class WidgetStationBoardUseCase internal constructor(
    private val schedules: ScheduleRepository,
) {

    public suspend fun getDepartures(
        globalData: GlobalData,
        stopId: String,
        routeFilter: String?,
        now: EasternTimeInstant,
        limit: Int,
    ): ApiResult<WidgetStationBoardOutput> {
        val stop =
            globalData.getStop(stopId)
                ?: return ApiResult.Ok(WidgetStationBoardOutput(emptyList()))
        val stopIds = globalData.stopIdsForScheduleFilter(stop)

        val scheduleResponse =
            when (val result = schedules.scheduleForStops(stopIds, now)) {
                is ApiResult.Ok -> result.data
                is ApiResult.Error ->
                    return ApiResult.Error(code = result.code, message = result.message)
            }

        return ApiResult.Ok(
            WidgetStationBoardOutput(
                buildDepartures(
                    scheduleResponse = scheduleResponse,
                    globalData = globalData,
                    configuredStopId = stopId,
                    routeFilter = routeFilter,
                    now = now,
                    limit = limit,
                ),
            ),
        )
    }

    /**
     * The next [limit] departures, one row per trip (a trip calling at several platforms of the same
     * station must not appear twice), ordered by departure time.
     */
    private fun buildDepartures(
        scheduleResponse: ScheduleResponse,
        globalData: GlobalData,
        configuredStopId: String,
        routeFilter: String?,
        now: EasternTimeInstant,
        limit: Int,
    ): List<WidgetStationBoardDeparture> {
        val stops = globalData.stops
        val earliestPerTrip = LinkedHashMap<String, Pair<com.saarlabs.tminus.model.Schedule, EasternTimeInstant>>()

        for (schedule in scheduleResponse.schedules) {
            if (!Stop.equalOrFamily(schedule.stopId, configuredStopId, stops)) continue
            val departure = schedule.departureTime ?: schedule.arrivalTime ?: continue
            if (departure < now) continue
            val trip = scheduleResponse.trips[schedule.tripId] ?: continue
            if (routeFilter != null && trip.routeId != routeFilter) continue

            val existing = earliestPerTrip[schedule.tripId]
            if (existing == null || departure < existing.second) {
                earliestPerTrip[schedule.tripId] = schedule to departure
            }
        }

        val fallbackStop = globalData.getStop(configuredStopId)?.resolveParent(stops)

        return earliestPerTrip.values
            .sortedBy { it.second }
            .asSequence()
            .mapNotNull { (schedule, departure) ->
                val trip = scheduleResponse.trips[schedule.tripId] ?: return@mapNotNull null
                val route = globalData.getRoute(trip.routeId) ?: return@mapNotNull null
                val scheduleStop = stops[schedule.stopId] ?: fallbackStop
                WidgetStationBoardDeparture(
                    route = route,
                    headsign = schedule.stopHeadsign?.takeIf { it.isNotBlank() } ?: trip.headsign,
                    departureTime = departure,
                    minutesUntil = (departure - now).inWholeMinutes.toInt().coerceAtLeast(0),
                    platform = scheduleStop?.commuterRailPlatform(),
                )
            }
            .take(limit)
            .toList()
    }
}
