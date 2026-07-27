package com.saarlabs.tminus.android.util

import com.saarlabs.tminus.util.EasternTimeInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.Month

class TimeFormatTest {

    private fun at(hour: Int, minute: Int) =
        EasternTimeInstant(2026, Month.JULY, 27, hour, minute)

    @Test
    fun twelveHourFormatting() {
        assertEquals("12:05 AM", at(0, 5).formattedTime(use24Hour = false))
        assertEquals("9:07 AM", at(9, 7).formattedTime(use24Hour = false))
        assertEquals("12:00 PM", at(12, 0).formattedTime(use24Hour = false))
        assertEquals("11:59 PM", at(23, 59).formattedTime(use24Hour = false))
    }

    @Test
    fun twentyFourHourFormatting() {
        assertEquals("0:05", at(0, 5).formattedTime(use24Hour = true))
        assertEquals("13:30", at(13, 30).formattedTime(use24Hour = true))
        assertEquals("23:59", at(23, 59).formattedTime(use24Hour = true))
    }
}
