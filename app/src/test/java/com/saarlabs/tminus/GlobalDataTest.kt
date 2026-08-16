package com.saarlabs.tminus

import com.saarlabs.tminus.model.LocationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalDataTest {

    @Test
    fun `schedule filter includes platforms discovered through parent_station`() {
        // The MBTA frequently omits child_stop_ids on a parent station while the platforms still
        // point back at it. Missing those means missing every departure at the station.
        val station =
            Fixtures.stop("place-x", "Station", locationType = LocationType.STATION)
        val platformA = Fixtures.stop("x-1", parent = "place-x")
        val platformB = Fixtures.stop("x-2", parent = "place-x")
        val unrelated = Fixtures.stop("y-1", parent = "place-y")
        val data = Fixtures.globalData(listOf(station, platformA, platformB, unrelated))

        val ids = data.stopIdsForScheduleFilter(station)

        assertEquals(setOf("place-x", "x-1", "x-2"), ids.toSet())
    }

    @Test
    fun `schedule filter also honours declared child_stop_ids`() {
        val station =
            Fixtures.stop(
                "place-x",
                locationType = LocationType.STATION,
                children = listOf("x-1"),
            )
        val platform = Fixtures.stop("x-1")
        val data = Fixtures.globalData(listOf(station, platform))

        assertEquals(setOf("place-x", "x-1"), data.stopIdsForScheduleFilter(station).toSet())
    }

    @Test
    fun `schedule filter resolves upward from a platform to its whole station`() {
        val station = Fixtures.stop("place-x", locationType = LocationType.STATION)
        val platformA = Fixtures.stop("x-1", parent = "place-x")
        val platformB = Fixtures.stop("x-2", parent = "place-x")
        val data = Fixtures.globalData(listOf(station, platformA, platformB))

        assertEquals(setOf("place-x", "x-1", "x-2"), data.stopIdsForScheduleFilter(platformA).toSet())
    }

    @Test
    fun `selectable stops exclude platforms and are sorted by name`() {
        val alewife = Fixtures.stop("place-alfcl", "Alewife", locationType = LocationType.STATION)
        val park = Fixtures.stop("place-pktrm", "Park Street", locationType = LocationType.STATION)
        val platform = Fixtures.stop("pktrm-1", "Park Street Platform", parent = "place-pktrm")
        val data = Fixtures.globalData(listOf(park, platform, alewife))

        val selectable = data.getParentStopsForSelection()

        assertEquals(listOf("Alewife", "Park Street"), selectable.map { it.name })
        assertTrue(selectable.none { it.id == "pktrm-1" })
    }

    @Test
    fun `repeated lookups return the same precomputed list`() {
        val data =
            Fixtures.globalData(
                listOf(Fixtures.stop("place-x", locationType = LocationType.STATION)),
            )

        assertTrue(data.getParentStopsForSelection() === data.getParentStopsForSelection())
    }
}
