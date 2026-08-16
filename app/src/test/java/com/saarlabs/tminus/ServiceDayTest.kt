package com.saarlabs.tminus

import com.saarlabs.tminus.ui.formatMinutesFromMidnight
import kotlinx.datetime.Month
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * MBTA service days run past midnight. These cases pin the behaviour that used to be clamped away,
 * which made "last train home" unable to describe a train leaving at 00:40.
 */
class ServiceDayTest {

    @Test
    fun `an hour after midnight still belongs to the previous service day`() {
        val lateNight = Fixtures.at(hour = 0, minute = 40, day = 18)

        assertEquals("2026-08-17", lateNight.serviceDate.toString())
    }

    @Test
    fun `three in the morning starts a new service day`() {
        val earlyMorning = Fixtures.at(hour = 3, minute = 0, day = 18)

        assertEquals("2026-08-18", earlyMorning.serviceDate.toString())
    }

    @Test
    fun `evening belongs to its own calendar day`() {
        val evening = Fixtures.at(hour = 23, minute = 30, day = 17)

        assertEquals("2026-08-17", evening.serviceDate.toString())
    }

    @Test
    fun `window minutes past midnight are labelled as the next day`() {
        assertEquals("2:00 AM (next day)", formatMinutesFromMidnight(26 * 60, use24Hour = false))
        assertEquals("2:00 (next day)", formatMinutesFromMidnight(26 * 60, use24Hour = true))
    }

    @Test
    fun `window minutes within the day are formatted normally`() {
        assertEquals("11:59 PM", formatMinutesFromMidnight(23 * 60 + 59, use24Hour = false))
        assertEquals("6:00 PM", formatMinutesFromMidnight(18 * 60, use24Hour = false))
        assertEquals("18:00", formatMinutesFromMidnight(18 * 60, use24Hour = true))
    }

    @Test
    fun `midnight reads as twelve AM, not zero`() {
        assertEquals("12:00 AM", formatMinutesFromMidnight(0, use24Hour = false))
    }

    @Test
    fun `noon reads as twelve PM`() {
        assertEquals("12:00 PM", formatMinutesFromMidnight(12 * 60, use24Hour = false))
    }

    @Test
    fun `service date rolls correctly across a month boundary`() {
        val justAfterMidnight =
            com.saarlabs.tminus.util.EasternTimeInstant(
                year = 2026,
                month = Month.SEPTEMBER,
                day = 1,
                hour = 1,
                minute = 15,
            )

        assertEquals("2026-08-31", justAfterMidnight.serviceDate.toString())
    }
}
