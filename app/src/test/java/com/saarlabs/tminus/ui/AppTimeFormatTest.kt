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

    @Test
    fun outOfRangeValuesAreClamped() {
        assertEquals("11:59 PM", formatMinutesFromMidnight(99_999, use24Hour = false))
        assertEquals("12:00 AM", formatMinutesFromMidnight(-5, use24Hour = false))
    }
}
