package com.saarlabs.tminus.commute

import com.saarlabs.tminus.model.LocationType
import com.saarlabs.tminus.model.Route
import com.saarlabs.tminus.model.RouteType
import com.saarlabs.tminus.model.Schedule
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.Trip
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.model.response.ScheduleResponse
import com.saarlabs.tminus.util.EasternTimeInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.datetime.Month

class CommuteTripPlannerTest {

    private val route =
        Route(
            id = "CR-Test",
            type = RouteType.COMMUTER_RAIL,
            color = "80276C",
            directionNames = listOf("Outbound", "Inbound"),
            directionDestinations = listOf("Away", "Boston"),
            longName = "Test Line",
            shortName = "",
            sortOrder = 1,
            textColor = "FFFFFF",
        )

    private fun stop(id: String) =
        Stop(
            id = id,
            latitude = 0.0,
            longitude = 0.0,
            name = id,
            locationType = LocationType.STATION,
        )

    private val globalData =
        GlobalData(
            stops = listOf(stop("from"), stop("to")).associateBy { it.id },
            routes = mapOf(route.id to route),
        )

    /** Monday 2026-07-27. */
    private fun at(hour: Int, minute: Int, day: Int = 27) =
        EasternTimeInstant(2026, Month.JULY, day, hour, minute)

    private fun schedule(
        id: String,
        tripId: String,
        stopId: String,
        sequence: Int,
        departure: EasternTimeInstant?,
        arrival: EasternTimeInstant? = departure,
    ) = Schedule(
        id = id,
        arrivalTime = arrival,
        departureTime = departure,
        stopHeadsign = null,
        stopSequence = sequence,
        stopId = stopId,
        tripId = tripId,
    )

    private fun trip(id: String) =
        Trip(id = id, directionId = 1, headsign = "Boston", routeId = route.id)

    private fun response(vararg tripIds: String, schedules: List<Schedule>) =
        ScheduleResponse(
            schedules = schedules,
            trips = tripIds.associateWith { trip(it) },
        )

    @Test
    fun findsEarliestTripInWindow() {
        val resp =
            response(
                "t1", "t2",
                schedules =
                    listOf(
                        schedule("s1", "t1", "from", 1, at(8, 30)),
                        schedule("s2", "t1", "to", 5, at(9, 0)),
                        schedule("s3", "t2", "from", 1, at(8, 10)),
                        schedule("s4", "t2", "to", 5, at(8, 40)),
                    ),
            )
        val tripData =
            CommuteTripPlanner.findNextTripInWindow(
                response = resp,
                globalData = globalData,
                fromStopId = "from",
                toStopId = "to",
                now = at(7, 0),
                windowStart = at(8, 0),
                windowEnd = at(9, 0),
            )
        assertNotNull(tripData)
        assertEquals("t2", tripData.tripId)
        assertEquals(at(8, 10), tripData.departureTime)
        assertEquals(at(8, 40), tripData.arrivalTime)
    }

    @Test
    fun ignoresTripsGoingTheWrongDirection() {
        // "to" comes before "from" on the trip, so this trip should not match.
        val resp =
            response(
                "t1",
                schedules =
                    listOf(
                        schedule("s1", "t1", "to", 1, at(8, 0)),
                        schedule("s2", "t1", "from", 5, at(8, 30)),
                    ),
            )
        assertNull(
            CommuteTripPlanner.findNextTripInWindow(
                response = resp,
                globalData = globalData,
                fromStopId = "from",
                toStopId = "to",
                now = at(7, 0),
                windowStart = at(7, 0),
                windowEnd = at(10, 0),
            ),
        )
    }

    @Test
    fun windowLowerBoundIsNowWhenNowIsInsideWindow() {
        val resp =
            response(
                "t1", "t2",
                schedules =
                    listOf(
                        schedule("s1", "t1", "from", 1, at(8, 10)),
                        schedule("s2", "t1", "to", 5, at(8, 40)),
                        schedule("s3", "t2", "from", 1, at(8, 50)),
                        schedule("s4", "t2", "to", 5, at(9, 20)),
                    ),
            )
        val tripData =
            CommuteTripPlanner.findNextTripInWindow(
                response = resp,
                globalData = globalData,
                fromStopId = "from",
                toStopId = "to",
                now = at(8, 20),
                windowStart = at(8, 0),
                windowEnd = at(23, 0),
            )
        assertEquals("t2", assertNotNull(tripData).tripId)
    }

    @Test
    fun previewFiltersBySelectedDaysOfWeek() {
        // 2026-07-27 is a Monday (ISO day 1); 2026-07-28 is a Tuesday (day 2).
        val resp =
            response(
                "mon", "tue",
                schedules =
                    listOf(
                        schedule("s1", "mon", "from", 1, at(8, 0, day = 27)),
                        schedule("s2", "mon", "to", 5, at(8, 30, day = 27)),
                        schedule("s3", "tue", "from", 1, at(8, 0, day = 28)),
                        schedule("s4", "tue", "to", 5, at(8, 30, day = 28)),
                    ),
            )
        val tuesdayOnly =
            CommuteTripPlanner.findNextCommutePreviewTrip(
                response = resp,
                globalData = globalData,
                fromStopId = "from",
                toStopId = "to",
                now = at(6, 0, day = 27),
                windowStartMinutesFromMidnight = 7 * 60,
                windowEndMinutesFromMidnight = 9 * 60,
                selectedDaysOfWeek = setOf(2),
            )
        assertEquals("tue", assertNotNull(tuesdayOnly).tripId)
    }

    @Test
    fun previewFiltersByTimeOfDayWindow() {
        val resp =
            response(
                "early", "inWindow",
                schedules =
                    listOf(
                        schedule("s1", "early", "from", 1, at(6, 0)),
                        schedule("s2", "early", "to", 5, at(6, 30)),
                        schedule("s3", "inWindow", "from", 1, at(8, 15)),
                        schedule("s4", "inWindow", "to", 5, at(8, 45)),
                    ),
            )
        val tripData =
            CommuteTripPlanner.findNextCommutePreviewTrip(
                response = resp,
                globalData = globalData,
                fromStopId = "from",
                toStopId = "to",
                now = at(5, 0),
                windowStartMinutesFromMidnight = 8 * 60,
                windowEndMinutesFromMidnight = 9 * 60,
                selectedDaysOfWeek = emptySet(),
            )
        assertEquals("inWindow", assertNotNull(tripData).tripId)
    }
}
