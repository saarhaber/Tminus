package com.saarlabs.tminus

import com.saarlabs.tminus.usecases.earliestConnectingPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the schedule-pairing rules behind both the trip widget and the commute planner: a usable
 * trip must serve the origin *before* the destination on the same trip, and must not be in the past.
 */
class TripPairingTest {

    private val origin = Fixtures.stop("place-a", "Origin")
    private val destination = Fixtures.stop("place-b", "Destination")
    private val originPlatform = Fixtures.stop("a-1", "Origin — Platform 1", parent = "place-a")
    private val stops =
        Fixtures.globalData(listOf(origin, destination, originPlatform)).stops

    @Test
    fun `picks the earliest departure that reaches the destination`() {
        val response =
            Fixtures.scheduleResponse(
                schedules =
                    listOf(
                        Fixtures.schedule("s1", "trip-late", "place-a", 1, Fixtures.at(9, 0)),
                        Fixtures.schedule("s2", "trip-late", "place-b", 5, Fixtures.at(9, 30)),
                        Fixtures.schedule("s3", "trip-early", "place-a", 1, Fixtures.at(8, 0)),
                        Fixtures.schedule("s4", "trip-early", "place-b", 5, Fixtures.at(8, 30)),
                    ),
                trips = listOf(Fixtures.trip("trip-early", "Red"), Fixtures.trip("trip-late", "Red")),
            )

        val pair =
            earliestConnectingPair(response, stops, "place-a", "place-b", Fixtures.at(7, 0))

        assertEquals("trip-early", pair?.first?.tripId)
    }

    @Test
    fun `ignores departures before now`() {
        val response =
            Fixtures.scheduleResponse(
                schedules =
                    listOf(
                        Fixtures.schedule("s1", "past", "place-a", 1, Fixtures.at(6, 0)),
                        Fixtures.schedule("s2", "past", "place-b", 5, Fixtures.at(6, 30)),
                        Fixtures.schedule("s3", "future", "place-a", 1, Fixtures.at(9, 0)),
                        Fixtures.schedule("s4", "future", "place-b", 5, Fixtures.at(9, 30)),
                    ),
                trips = listOf(Fixtures.trip("past", "Red"), Fixtures.trip("future", "Red")),
            )

        val pair =
            earliestConnectingPair(response, stops, "place-a", "place-b", Fixtures.at(8, 0))

        assertEquals("future", pair?.first?.tripId)
    }

    @Test
    fun `rejects a trip that calls at the destination before the origin`() {
        // The same train serves both stops, but in the wrong order for this rider.
        val response =
            Fixtures.scheduleResponse(
                schedules =
                    listOf(
                        Fixtures.schedule("s1", "wrong-way", "place-b", 1, Fixtures.at(9, 0)),
                        Fixtures.schedule("s2", "wrong-way", "place-a", 5, Fixtures.at(9, 30)),
                    ),
                trips = listOf(Fixtures.trip("wrong-way", "Red")),
            )

        val pair =
            earliestConnectingPair(response, stops, "place-a", "place-b", Fixtures.at(8, 0))

        assertNull(pair)
    }

    @Test
    fun `matches a departure recorded against a child platform of the origin station`() {
        // Schedules reference platform ids; the user picked the parent station.
        val response =
            Fixtures.scheduleResponse(
                schedules =
                    listOf(
                        Fixtures.schedule("s1", "trip", "a-1", 1, Fixtures.at(9, 0)),
                        Fixtures.schedule("s2", "trip", "place-b", 5, Fixtures.at(9, 30)),
                    ),
                trips = listOf(Fixtures.trip("trip", "Red")),
            )

        val pair =
            earliestConnectingPair(response, stops, "place-a", "place-b", Fixtures.at(8, 0))

        assertEquals("trip", pair?.first?.tripId)
    }

    @Test
    fun `honours an upper bound on the departure time`() {
        val response =
            Fixtures.scheduleResponse(
                schedules =
                    listOf(
                        Fixtures.schedule("s1", "trip", "place-a", 1, Fixtures.at(11, 0)),
                        Fixtures.schedule("s2", "trip", "place-b", 5, Fixtures.at(11, 30)),
                    ),
                trips = listOf(Fixtures.trip("trip", "Red")),
            )

        val pair =
            earliestConnectingPair(
                response,
                stops,
                "place-a",
                "place-b",
                now = Fixtures.at(8, 0),
                latest = Fixtures.at(10, 0),
            )

        assertNull(pair)
    }

    @Test
    fun `returns nothing when no trip serves the destination`() {
        val response =
            Fixtures.scheduleResponse(
                schedules = listOf(Fixtures.schedule("s1", "trip", "place-a", 1, Fixtures.at(9, 0))),
                trips = listOf(Fixtures.trip("trip", "Red")),
            )

        assertNull(earliestConnectingPair(response, stops, "place-a", "place-b", Fixtures.at(8, 0)))
    }
}
