package com.saarlabs.tminus.commute

import com.saarlabs.tminus.model.RouteType
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.WidgetTripData
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.model.response.ScheduleResponse
import com.saarlabs.tminus.util.EasternTimeInstant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * Finds the next trip between two stops whose departure falls in
 * [max(now, windowStart), windowEnd] (for commute windows).
 */
internal object CommuteTripPlanner {

    /**
     * Next trip whose departure is in the commute time-of-day window, on or after [now], on a weekday
     * in [selectedDaysOfWeek] (1 = Mon … 7 = Sun). Uses actual timestamps from [response] (any service dates
     * the API returns), not synthetic calendar windows — so it matches evening previews against tomorrow’s
     * schedules. If [selectedDaysOfWeek] is empty, any weekday is allowed.
     */
    fun findNextCommutePreviewTrip(
        response: ScheduleResponse,
        globalData: GlobalData,
        fromStopId: String,
        toStopId: String,
        now: EasternTimeInstant,
        windowStartMinutesFromMidnight: Int,
        windowEndMinutesFromMidnight: Int,
        selectedDaysOfWeek: Set<Int>,
    ): WidgetTripData? {
        val stops = globalData.stops
        val allowed = if (selectedDaysOfWeek.isEmpty()) (1..7).toSet() else selectedDaysOfWeek
        val fromSchedules =
            response.schedules.filter { Stop.equalOrFamily(it.stopId, fromStopId, stops) }
        val toSchedules =
            response.schedules.filter { Stop.equalOrFamily(it.stopId, toStopId, stops) }

        val tripPairs =
            fromSchedules.flatMap { fromSchedule ->
                toSchedules
                    .filter {
                        it.tripId == fromSchedule.tripId &&
                            it.stopSequence > fromSchedule.stopSequence
                    }
                    .map { toSchedule -> fromSchedule to toSchedule }
            }

        val nextTrip =
            tripPairs
                .filter { (from, _) ->
                    val depTime = from.departureTime ?: from.arrivalTime ?: return@filter false
                    if (depTime < now) return@filter false
                    if (isoDayOfWeek(depTime.local.date) !in allowed) return@filter false
                    val depMin = depTime.local.hour * 60 + depTime.local.minute
                    depMin in windowStartMinutesFromMidnight..windowEndMinutesFromMidnight
                }
                .minByOrNull { (from, _) ->
                    val depTime = from.departureTime ?: from.arrivalTime!!
                    depTime.instant
                } ?: return null

        val (fromSchedule, toSchedule) = nextTrip
        val trip = response.trips[fromSchedule.tripId] ?: return null
        val route = globalData.getRoute(trip.routeId) ?: return null

        val fromResolved =
            globalData.getStop(fromStopId)?.resolveParent(globalData.stops) ?: return null
        val toResolved = globalData.getStop(toStopId)?.resolveParent(globalData.stops) ?: return null
        val fromScheduleStop = stops[fromSchedule.stopId] ?: fromResolved
        val toScheduleStop = stops[toSchedule.stopId] ?: toResolved

        val departureTime = fromSchedule.departureTime ?: fromSchedule.arrivalTime ?: return null
        val arrivalTime = toSchedule.arrivalTime ?: toSchedule.departureTime ?: return null

        val minutesUntil = (departureTime - now).inWholeMinutes.toInt().coerceAtLeast(0)

        val fromPlatform =
            if (fromScheduleStop.vehicleType == RouteType.COMMUTER_RAIL)
                fromScheduleStop.platformCode
            else null
        val toPlatform =
            if (toScheduleStop.vehicleType == RouteType.COMMUTER_RAIL) toScheduleStop.platformCode
            else null

        return WidgetTripData(
            fromStop = fromResolved,
            toStop = toResolved,
            route = route,
            tripId = trip.id,
            departureTime = departureTime,
            arrivalTime = arrivalTime,
            minutesUntil = minutesUntil,
            fromPlatform = fromPlatform,
            toPlatform = toPlatform,
            headsign = fromSchedule.stopHeadsign ?: trip.headsign,
        )
    }

    fun findNextTripInWindow(
        response: ScheduleResponse,
        globalData: GlobalData,
        fromStopId: String,
        toStopId: String,
        now: EasternTimeInstant,
        windowStart: EasternTimeInstant,
        windowEnd: EasternTimeInstant,
    ): WidgetTripData? {
        val stops = globalData.stops
        val fromSchedules =
            response.schedules.filter { Stop.equalOrFamily(it.stopId, fromStopId, stops) }
        val toSchedules =
            response.schedules.filter { Stop.equalOrFamily(it.stopId, toStopId, stops) }

        val tripPairs =
            fromSchedules.flatMap { fromSchedule ->
                toSchedules
                    .filter {
                        it.tripId == fromSchedule.tripId &&
                            it.stopSequence > fromSchedule.stopSequence
                    }
                    .map { toSchedule -> fromSchedule to toSchedule }
            }

        val lower = if (now > windowStart) now else windowStart

        val nextTrip =
            tripPairs
                .filter { (from, _) ->
                    val depTime = from.departureTime ?: from.arrivalTime ?: return@filter false
                    depTime >= lower && depTime <= windowEnd
                }
                .minByOrNull { (from, _) ->
                    val depTime = from.departureTime ?: from.arrivalTime!!
                    depTime.instant
                } ?: return null

        val (fromSchedule, toSchedule) = nextTrip
        val trip = response.trips[fromSchedule.tripId] ?: return null
        val route = globalData.getRoute(trip.routeId) ?: return null

        val fromResolved =
            globalData.getStop(fromStopId)?.resolveParent(globalData.stops) ?: return null
        val toResolved = globalData.getStop(toStopId)?.resolveParent(globalData.stops) ?: return null
        val fromScheduleStop = stops[fromSchedule.stopId] ?: fromResolved
        val toScheduleStop = stops[toSchedule.stopId] ?: toResolved

        val departureTime = fromSchedule.departureTime ?: fromSchedule.arrivalTime ?: return null
        val arrivalTime = toSchedule.arrivalTime ?: toSchedule.departureTime ?: return null

        val minutesUntil = (departureTime - now).inWholeMinutes.toInt().coerceAtLeast(0)

        val fromPlatform =
            if (fromScheduleStop.vehicleType == RouteType.COMMUTER_RAIL)
                fromScheduleStop.platformCode
            else null
        val toPlatform =
            if (toScheduleStop.vehicleType == RouteType.COMMUTER_RAIL) toScheduleStop.platformCode
            else null

        return WidgetTripData(
            fromStop = fromResolved,
            toStop = toResolved,
            route = route,
            tripId = trip.id,
            departureTime = departureTime,
            arrivalTime = arrivalTime,
            minutesUntil = minutesUntil,
            fromPlatform = fromPlatform,
            toPlatform = toPlatform,
            headsign = fromSchedule.stopHeadsign ?: trip.headsign,
        )
    }

    /** 1 = Monday … 7 = Sunday (matches commute day chips). */
    private fun isoDayOfWeek(date: LocalDate): Int =
        when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> 1
            DayOfWeek.TUESDAY -> 2
            DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4
            DayOfWeek.FRIDAY -> 5
            DayOfWeek.SATURDAY -> 6
            DayOfWeek.SUNDAY -> 7
        }
}
