package com.saarlabs.tminus.model

import com.saarlabs.tminus.model.response.GlobalData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun stop(
    id: String,
    parent: String? = null,
    locationType: LocationType = LocationType.STOP,
    children: List<String> = emptyList(),
) = Stop(
    id = id,
    latitude = 0.0,
    longitude = 0.0,
    name = id,
    locationType = locationType,
    childStopIds = children,
    parentStationId = parent,
)

class StopFamilyTest {

    private val stops =
        listOf(
            stop("station", locationType = LocationType.STATION, children = listOf("platform-a")),
            stop("platform-a", parent = "station"),
            stop("platform-b", parent = "station"),
            stop("elsewhere", locationType = LocationType.STATION),
        ).associateBy { it.id }

    @Test
    fun resolveParentWalksToRoot() {
        assertEquals("station", stops.getValue("platform-a").resolveParent(stops).id)
        assertEquals("station", stops.getValue("station").resolveParent(stops).id)
    }

    @Test
    fun resolveParentFallsBackWhenParentMissing() {
        val orphan = stop("orphan", parent = "missing")
        assertEquals("orphan", orphan.resolveParent(stops).id)
    }

    @Test
    fun equalOrFamilyMatchesSiblingsAndParent() {
        assertTrue(Stop.equalOrFamily("platform-a", "platform-a", stops))
        assertTrue(Stop.equalOrFamily("platform-a", "platform-b", stops))
        assertTrue(Stop.equalOrFamily("platform-a", "station", stops))
        assertFalse(Stop.equalOrFamily("platform-a", "elsewhere", stops))
        assertFalse(Stop.equalOrFamily("platform-a", "unknown", stops))
    }

    @Test
    fun stopIdsForScheduleFilterIncludesKnownChildren() {
        val global = GlobalData(stops = stops, routes = emptyMap())
        val ids = global.stopIdsForScheduleFilter(stops.getValue("platform-a"))
        assertEquals(listOf("station", "platform-a", "platform-b"), ids)
    }

    @Test
    fun stopIdsForScheduleFilterSkipsUnknownChildIds() {
        val station = stop("s2", locationType = LocationType.STATION, children = listOf("ghost"))
        val map = mapOf("s2" to station)
        val global = GlobalData(stops = map, routes = emptyMap())
        assertEquals(listOf("s2"), global.stopIdsForScheduleFilter(station))
    }
}
