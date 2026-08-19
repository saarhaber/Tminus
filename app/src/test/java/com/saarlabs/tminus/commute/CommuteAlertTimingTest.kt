package com.saarlabs.tminus.commute

import com.saarlabs.tminus.util.EasternTimeInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

class CommuteAlertTimingTest {

    private fun profile(
        target: Int,
        before: Int = 45,
        after: Int = 45,
        lead: Int = 12,
    ) = CommuteProfile(
        name = "Test",
        fromStopId = "from",
        toStopId = "to",
        daysOfWeek = listOf(1),
        targetMinutesFromMidnight = target,
        windowMinutesBefore = before,
        windowMinutesAfter = after,
        notifyLeadMinutes = lead,
    )

    /** Monday 2026-07-27. */
    private val serviceDate = LocalDate(2026, Month.JULY, 27)

    private fun at(hour: Int, minute: Int, day: Int = 27) =
        EasternTimeInstant(2026, Month.JULY, day, hour, minute)

    @Test
    fun anEveningWindowRunsPastMidnightIntoTheNextCalendarDay() {
        val window = commuteWindow(serviceDate, profile(target = 23 * 60 + 50))
        assertEquals(23 * 60 + 5, window.startMinutes)
        assertEquals(24 * 60 + 35, window.endMinutes)
        assertEquals(at(23, 5), window.start)
        assertEquals(at(0, 35, day = 28), window.end)
    }

    @Test
    fun anAfterMidnightTargetSitsOnTheServiceDayThatCarriesIt() {
        // 00:20 is 24:20 of the previous service day, and its window has to be built there.
        val window = commuteWindow(serviceDate, profile(target = 20, before = 45, after = 15))
        assertEquals(24 * 60 - 25, window.startMinutes)
        assertEquals(24 * 60 + 35, window.endMinutes)
        assertEquals(at(23, 35), window.start)
        assertEquals(at(0, 35, day = 28), window.end)
    }

    @Test
    fun aWindowIsClampedToTheEndsOfTheServiceDay() {
        val early = commuteWindow(serviceDate, profile(target = 4 * 60, before = 180))
        assertEquals(60, early.startMinutes)
        val late = commuteWindow(serviceDate, profile(target = 25 * 60, after = 180))
        assertEquals(SERVICE_DAY_MAX_MINUTES, late.endMinutes)
        assertEquals(at(2, 59, day = 28), late.end)
    }

    @Test
    fun serviceDayHoursPastMidnightAreWrittenAsTheApiExpects() {
        assertEquals("07:05", serviceDayHHmm(7 * 60 + 5))
        assertEquals("24:35", serviceDayHHmm(24 * 60 + 35))
        assertEquals("26:59", serviceDayHHmm(30 * 60))
    }

    @Test
    fun theLowerBoundLeavesRoomToActOnTheAlert() {
        val bound =
            commuteDepartureLowerBound(
                now = at(8, 0),
                windowStart = at(7, 0),
                leadMinutes = 12,
            )
        // The lead minus the grace the alert is still postable in — see the doc on the function.
        assertEquals(at(8, 7), bound)
    }

    @Test
    fun theLowerBoundNeverReachesBackToADepartedTrain() {
        val bound =
            commuteDepartureLowerBound(
                now = at(8, 0),
                windowStart = at(7, 0),
                leadMinutes = 1,
            )
        assertEquals(at(8, 0), bound)
    }

    @Test
    fun theLowerBoundStillRespectsTheStartOfTheWindow() {
        val bound =
            commuteDepartureLowerBound(
                now = at(6, 0),
                windowStart = at(7, 0),
                leadMinutes = 12,
            )
        assertEquals(at(7, 0), bound)
    }

    @Test
    fun anAlertIsDueFromItsLeaveTimeUntilTheWindowRunsOut() {
        val leaveAt = at(8, 0).toEpochMilliseconds()
        assertFalse(isLeaveAlertDue(leaveAt, at(7, 59).toEpochMilliseconds()))
        assertTrue(isLeaveAlertDue(leaveAt, leaveAt))
        assertTrue(isLeaveAlertDue(leaveAt, at(8, 4).toEpochMilliseconds()))
        assertFalse(isLeaveAlertDue(leaveAt, at(8, 5).toEpochMilliseconds()))
    }
}
