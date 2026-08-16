package com.saarlabs.tminus.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class AppTimeFormatTest {

    @Test
    fun twelveHourFormatting() {
        assertEquals("12:00 AM", formatMinutesFromMidnight(0, use24Hour = false))
        assertEquals("11:59 AM", formatMinutesFromMidnight(11 * 60 + 59, use24Hour = false))
        assertEquals("12:30 PM", formatMinutesFromMidnight(12 * 60 + 30, use24Hour = false))
        assertEquals("11:59 PM", formatMinutesFromMidnight(24 * 60 - 1, use24Hour = false))
    }

    @Test
    fun twentyFourHourFormatting() {
        assertEquals("0:00", formatMinutesFromMidnight(0, use24Hour = true))
        assertEquals("18:05", formatMinutesFromMidnight(18 * 60 + 5, use24Hour = true))
    }

    /**
     * MBTA service days run to 26:59, and "last train" windows rely on that, so minutes at or above
     * 1440 are the small hours of the next morning rather than something to clamp back to 23:59.
     */
    @Test
    fun minutesPastMidnightAreLabelledAsNextDay() {
        assertEquals("12:40 AM (next day)", formatMinutesFromMidnight(24 * 60 + 40, use24Hour = false))
        assertEquals("2:00 AM (next day)", formatMinutesFromMidnight(26 * 60, use24Hour = false))
        assertEquals("2:00 (next day)", formatMinutesFromMidnight(26 * 60, use24Hour = true))
    }

    @Test
    fun outOfRangeValuesAreClamped() {
        // Clamped to the end of the service day (26:59), not to 23:59.
        assertEquals("2:59 AM (next day)", formatMinutesFromMidnight(99_999, use24Hour = false))
        assertEquals("12:00 AM", formatMinutesFromMidnight(-5, use24Hour = false))
    }
}
