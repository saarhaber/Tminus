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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
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
                leadMinutes = 0,
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
                leadMinutes = 0,
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
                leadMinutes = 0,
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
                leadMinutes = 0,
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
                leadMinutes = 0,
            )
        assertEquals("inWindow", assertNotNull(tripData).tripId)
    }

    /** [count] trips leaving "from" every [everyMinutes] starting at [first], 25 minutes to "to". */
    private fun frequentService(
        first: EasternTimeInstant,
        count: Int,
        everyMinutes: Int,
    ): ScheduleResponse {
        val ids = (0 until count).map { "t$it" }
        val schedules =
            ids.flatMapIndexed { i, id ->
                val departure = first + (i * everyMinutes).minutes
                listOf(
                    schedule("f$i", id, "from", 1, departure),
                    schedule("a$i", id, "to", 5, departure + 25.minutes),
                )
            }
        return response(*ids.toTypedArray(), schedules = schedules)
    }

    @Test
    fun skipsDeparturesTooSoonToReachAtTheChosenLead() {
        val resp =
            response(
                "soon", "reachable",
                schedules =
                    listOf(
                        schedule("s1", "soon", "from", 1, at(8, 2)),
                        schedule("s2", "soon", "to", 5, at(8, 32)),
                        schedule("s3", "reachable", "from", 1, at(8, 10)),
                        schedule("s4", "reachable", "to", 5, at(8, 40)),
                    ),
            )
        val tripData =
            CommuteTripPlanner.findNextTripInWindow(
                response = resp,
                globalData = globalData,
                fromStopId = "from",
                toStopId = "to",
                now = at(8, 0),
                windowStart = at(7, 45),
                windowEnd = at(8, 45),
                leadMinutes = 12,
            )
        // Two minutes' notice is no notice at all when the user asked for twelve.
        assertEquals("reachable", assertNotNull(tripData).tripId)
    }

    @Test
    fun holdsTheSelectedTripWhileItsAlertIsStillPostable() {
        val resp = frequentService(at(8, 0), count = 12, everyMinutes = 5)
        fun tripAt(now: EasternTimeInstant) =
            CommuteTripPlanner.findNextTripInWindow(
                response = resp,
                globalData = globalData,
                fromStopId = "from",
                toStopId = "to",
                now = now,
                windowStart = at(7, 45),
                windowEnd = at(8, 45),
                leadMinutes = 12,
            )

        // The moment the 8:10 train's alert comes due: its leave time is 7:58, and the alert is
        // postable for five minutes from there.
        val due = at(7, 59)
        val selected = assertNotNull(tripAt(due))
        assertEquals(at(8, 10), selected.departureTime)
        assertTrue(
            isLeaveAlertDue(
                leaveAtMs = at(7, 58).toEpochMilliseconds(),
                nowMs = due.toEpochMilliseconds(),
            ),
        )
        // A wakeup that lands late must still find the trip it woke up for. If selection stepped
        // on to the next train here, no train's alert would ever be postable — that was the bug.
        for (late in listOf(1, 2, 4)) {
            assertEquals(
                selected.tripId,
                assertNotNull(tripAt(due + late.minutes)).tripId,
                "selection moved on $late min after the alert came due",
            )
        }
    }

    @Test
    fun aFrequentRouteAlertsWhicheverMinuteTheWorkerFirstRuns() {
        // The reported failure: on a 5-minute headway with a 12-minute lead, the alert was
        // unpostable at every minute of the window, because selection always returned a train
        // closer than the lead. Sweep every starting minute rather than trusting one alignment.
        val lead = 12
        val resp = frequentService(at(7, 30), count = 25, everyMinutes = 5)
        for (offset in 0 until 60) {
            val start = at(7, 30) + offset.minutes
            var firedAt: EasternTimeInstant? = null
            var departure: EasternTimeInstant? = null
            var now = start
            while (firedAt == null && now <= at(9, 0)) {
                val trip =
                    CommuteTripPlanner.findNextTripInWindow(
                        response = resp,
                        globalData = globalData,
                        fromStopId = "from",
                        toStopId = "to",
                        now = now,
                        windowStart = at(7, 45),
                        windowEnd = at(8, 45),
                        leadMinutes = lead,
                    )
                if (trip != null) {
                    val leaveAtMs =
                        trip.departureTime.toEpochMilliseconds() - lead * 60_000L
                    if (isLeaveAlertDue(leaveAtMs, now.toEpochMilliseconds())) {
                        firedAt = now
                        departure = trip.departureTime
                    }
                }
                now += 1.minutes
            }
            val at = assertNotNull(firedAt, "no alert at all for a worker starting at $start")
            val notice = (assertNotNull(departure) - at).inWholeMinutes
            assertTrue(
                notice in (lead - LEAVE_ALERT_WINDOW_MINUTES).toLong()..lead.toLong(),
                "alert for a worker starting at $start gave $notice min notice, not ~$lead",
            )
        }
    }

    private fun preview(
        resp: ScheduleResponse,
        now: EasternTimeInstant,
        leadMinutes: Int,
        windowStartMinutes: Int = 15 * 60,
        windowEndMinutes: Int = 17 * 60,
    ) =
        CommuteTripPlanner.findNextCommutePreviewTrip(
            response = resp,
            globalData = globalData,
            fromStopId = "from",
            toStopId = "to",
            now = now,
            windowStartMinutesFromMidnight = windowStartMinutes,
            windowEndMinutesFromMidnight = windowEndMinutes,
            selectedDaysOfWeek = emptySet(),
            leadMinutes = leadMinutes,
        )

    @Test
    fun previewSkipsDeparturesTooSoonToReachAtTheChosenLead() {
        // The reported failure: a 4:20 PM preview of a 5-minute headway showed the 4:22 departure
        // and a "leave by" of 4:10, twelve minutes before the user was even looking at it.
        val resp = frequentService(at(16, 0), count = 12, everyMinutes = 5)
        val lead = 12
        val tripData = assertNotNull(preview(resp, now = at(16, 20), leadMinutes = lead))
        assertEquals(at(16, 30), tripData.departureTime)
        val leaveAt = tripData.departureTime - lead.minutes
        assertTrue(
            leaveAt >= at(16, 20) - LEAVE_ALERT_WINDOW_MINUTES.minutes,
            "preview offered a leave time of $leaveAt, well before 16:20",
        )
    }

    @Test
    fun previewKeepsATrainWhoseLeaveTimeHasJustPassed() {
        // Exactly the grace boundary: the 8:10 train's leave time was 7:58 and its alert is still
        // postable at 8:03, so the preview must still be about that train and not the next one.
        val resp = frequentService(at(8, 0), count = 12, everyMinutes = 5)
        val tripData =
            assertNotNull(
                preview(
                    resp,
                    now = at(8, 3),
                    leadMinutes = 12,
                    windowStartMinutes = 7 * 60,
                    windowEndMinutes = 9 * 60,
                ),
            )
        assertEquals(at(8, 10), tripData.departureTime)
    }

    @Test
    fun previewNamesTheSameTrainTheAlertWould() {
        // The point of the change: the editor's sample and the notification are about one train.
        val lead = 12
        val resp = frequentService(at(7, 30), count = 25, everyMinutes = 5)
        var now = at(7, 30)
        while (now <= at(8, 45)) {
            val previewed =
                preview(
                    resp,
                    now = now,
                    leadMinutes = lead,
                    windowStartMinutes = 7 * 60 + 45,
                    windowEndMinutes = 8 * 60 + 45,
                )
            val alerted =
                CommuteTripPlanner.findNextTripInWindow(
                    response = resp,
                    globalData = globalData,
                    fromStopId = "from",
                    toStopId = "to",
                    now = now,
                    windowStart = at(7, 45),
                    windowEnd = at(8, 45),
                    leadMinutes = lead,
                )
            assertEquals(
                alerted?.tripId,
                previewed?.tripId,
                "preview and alert disagreed about the train at $now",
            )
            now += 1.minutes
        }
    }

    @Test
    fun previewShowsNothingWhenNoTrainIsLeftToCatch() {
        val resp = frequentService(at(8, 0), count = 3, everyMinutes = 5)
        assertNull(
            preview(
                resp,
                now = at(8, 5),
                leadMinutes = 30,
                windowStartMinutes = 7 * 60,
                windowEndMinutes = 9 * 60,
            ),
        )
    }
}
