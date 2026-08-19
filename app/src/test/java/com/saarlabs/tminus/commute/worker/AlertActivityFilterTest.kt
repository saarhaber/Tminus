package com.saarlabs.tminus.commute.worker

import com.saarlabs.tminus.network.ALERT_ACTIVITIES_ACCESSIBILITY
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `filter[activity]` on the V3 alerts endpoint **replaces** the API's default set rather than
 * extending it, and the default (`BOARD`, `EXIT`, `RIDE`) shares no member with the activities lift
 * and escalator outages are filed under. Both halves of the list are therefore load-bearing:
 * dropping the accessibility activities silently returns zero `ELEVATOR_CLOSURE`s however many are
 * live — the state the station-alerts feature shipped in — and dropping the defaults does the same
 * to `STOP_CLOSURE`, which the worker also acts on.
 */
internal class AlertActivityFilterTest {

    @Test
    fun accessibilityFilterAsksForLiftAndEscalatorActivities() {
        assertTrue("USING_WHEELCHAIR" in ALERT_ACTIVITIES_ACCESSIBILITY)
        assertTrue("USING_ESCALATOR" in ALERT_ACTIVITIES_ACCESSIBILITY)
    }

    @Test
    fun accessibilityFilterRestatesTheDefaultsItReplaces() {
        // Without these a plain stop closure stops being reported, because naming any activity
        // discards BOARD/EXIT/RIDE instead of adding to them.
        assertTrue(listOf("BOARD", "EXIT", "RIDE").all { it in ALERT_ACTIVITIES_ACCESSIBILITY })
    }
}
