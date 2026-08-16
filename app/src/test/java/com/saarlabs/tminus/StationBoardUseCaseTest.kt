package com.saarlabs.tminus

import com.saarlabs.tminus.model.LocationType
import com.saarlabs.tminus.model.RouteType
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.model.response.ScheduleResponse
import com.saarlabs.tminus.usecases.buildDepartures
import com.saarlabs.tminus.util.EasternTimeInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StationBoardUseCaseTest {

    private val station =
        Fixtures.stop("place-pktrm", "Park Street", locationType = LocationType.STATION)
    private val platform1 = Fixtures.stop("pktrm-1", parent = "place-pktrm")
    private val platform2 = Fixtures.stop("pktrm-2", parent = "place-pktrm")
    private val red = Fixtures.route("Red", "Red Line")
    private val green = Fixtures.route("Green-B", "Green Line B", type = RouteType.LIGHT_RAIL)
    private val global =
        Fixtures.globalData(listOf(station, platform1, platform2), listOf(red, green))

    @Test
    fun `a trip calling at two platforms of one station appears once, at its earliest time`() {
        val response =
            Fixtures.scheduleResponse(
                schedules =
                    listOf(
                        Fixtures.schedule("s1", "trip-1", "pktrm-1", 3, Fixtures.at(9, 10)),
                        Fixtures.schedule("s2", "trip-1", "pktrm-2", 4, Fixtures.at(9, 12)),
                    ),
                trips = listOf(Fixtures.trip("trip-1", "Red")),
            )

        val departures = board(response, now = Fixtures.at(9, 0))

        assertEquals(1, departures.size)
        assertEquals(10, departures.single().minutesUntil)
    }

    @Test
    fun `departures are ordered by time, not by trip id`() {
        val response =
            Fixtures.scheduleResponse(
                schedules =
                    listOf(
                        Fixtures.schedule("s1", "zzz", "pktrm-1", 1, Fixtures.at(9, 20)),
                        Fixtures.schedule("s2", "aaa", "pktrm-1", 1, Fixtures.at(9, 5)),
                    ),
                trips =
                    listOf(
                        Fixtures.trip("zzz", "Red", headsign = "Later"),
                        Fixtures.trip("aaa", "Red", headsign = "Sooner"),
                    ),
            )

        assertEquals(
            listOf("Sooner", "Later"),
            board(response, now = Fixtures.at(9, 0)).map { it.headsign },
        )
    }

    @Test
    fun `the route filter excludes other lines`() {
        val response =
            Fixtures.scheduleResponse(
                schedules =
                    listOf(
                        Fixtures.schedule("s1", "red-trip", "pktrm-1", 1, Fixtures.at(9, 5)),
                        Fixtures.schedule("s2", "green-trip", "pktrm-2", 1, Fixtures.at(9, 6)),
                    ),
                trips =
                    listOf(
                        Fixtures.trip("red-trip", "Red"),
                        Fixtures.trip("green-trip", "Green-B"),
                    ),
            )

        val departures = board(response, now = Fixtures.at(9, 0), routeFilter = "Red")

        assertEquals(listOf("Red"), departures.map { it.route.id })
    }

    @Test
    fun `departures already gone are dropped`() {
        val response =
            Fixtures.scheduleResponse(
                schedules =
                    listOf(
                        Fixtures.schedule("s1", "gone", "pktrm-1", 1, Fixtures.at(8, 55)),
                        Fixtures.schedule("s2", "next", "pktrm-1", 1, Fixtures.at(9, 5)),
                    ),
                trips =
                    listOf(
                        Fixtures.trip("gone", "Red", headsign = "Gone"),
                        Fixtures.trip("next", "Red", headsign = "Next"),
                    ),
            )

        assertEquals(listOf("Next"), board(response, now = Fixtures.at(9, 0)).map { it.headsign })
    }

    @Test
    fun `minutes until is derived from the requested moment, so a tick can recompute it`() {
        val response =
            Fixtures.scheduleResponse(
                schedules = listOf(Fixtures.schedule("s1", "t", "pktrm-1", 1, Fixtures.at(9, 17))),
                trips = listOf(Fixtures.trip("t", "Red")),
            )

        assertEquals(17, board(response, now = Fixtures.at(9, 0)).single().minutesUntil)
        assertEquals(2, board(response, now = Fixtures.at(9, 15)).single().minutesUntil)
    }

    @Test
    fun `a stop headsign overrides the trip headsign`() {
        val response =
            Fixtures.scheduleResponse(
                schedules =
                    listOf(
                        Fixtures.schedule(
                            "s1",
                            "t",
                            "pktrm-1",
                            1,
                            Fixtures.at(9, 10),
                            headsign = "Braintree",
                        ),
                    ),
                trips = listOf(Fixtures.trip("t", "Red", headsign = "Ashmont")),
            )

        assertEquals("Braintree", board(response, now = Fixtures.at(9, 0)).single().headsign)
    }

    @Test
    fun `platform labels appear for commuter rail`() {
        val crStation = Fixtures.stop("place-cr", "Back Bay", locationType = LocationType.STATION)
        val crPlatform =
            Fixtures.stop(
                "cr-1",
                parent = "place-cr",
                vehicleType = RouteType.COMMUTER_RAIL,
                platformCode = "3",
            )
        val data = Fixtures.globalData(listOf(crStation, crPlatform), listOf(red))
        val response =
            Fixtures.scheduleResponse(
                schedules = listOf(Fixtures.schedule("s1", "t", "cr-1", 1, Fixtures.at(9, 10))),
                trips = listOf(Fixtures.trip("t", "Red")),
            )

        val departures =
            board(response, now = Fixtures.at(9, 0), global = data, stopId = "place-cr")

        assertEquals("3", departures.single().platform)
    }

    @Test
    fun `subway departures carry no platform label`() {
        val response =
            Fixtures.scheduleResponse(
                schedules = listOf(Fixtures.schedule("s1", "t", "pktrm-1", 1, Fixtures.at(9, 10))),
                trips = listOf(Fixtures.trip("t", "Red")),
            )

        assertNull(board(response, now = Fixtures.at(9, 0)).single().platform)
    }

    @Test
    fun `the limit caps how many departures are returned`() {
        val response =
            Fixtures.scheduleResponse(
                schedules =
                    (1..10).map {
                        Fixtures.schedule("s$it", "trip-$it", "pktrm-1", 1, Fixtures.at(9, it))
                    },
                trips = (1..10).map { Fixtures.trip("trip-$it", "Red") },
            )

        assertEquals(3, board(response, now = Fixtures.at(9, 0), limit = 3).size)
    }

    @Test
    fun `a trip on an unknown route is skipped rather than crashing the board`() {
        val response =
            Fixtures.scheduleResponse(
                schedules = listOf(Fixtures.schedule("s1", "t", "pktrm-1", 1, Fixtures.at(9, 10))),
                trips = listOf(Fixtures.trip("t", "NotARoute")),
            )

        assertEquals(emptyList<Any>(), board(response, now = Fixtures.at(9, 0)))
    }

    private fun board(
        response: ScheduleResponse,
        now: EasternTimeInstant,
        routeFilter: String? = null,
        global: GlobalData = this.global,
        stopId: String = "place-pktrm",
        limit: Int = 12,
    ) = buildDepartures(
        scheduleResponse = response,
        globalData = global,
        configuredStopId = stopId,
        routeFilter = routeFilter,
        now = now,
        limit = limit,
    )
}
