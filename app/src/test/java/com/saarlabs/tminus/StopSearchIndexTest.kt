package com.saarlabs.tminus

import com.saarlabs.tminus.data.StopSearchIndex
import com.saarlabs.tminus.model.LocationType
import com.saarlabs.tminus.model.RouteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StopSearchIndexTest {

    private val parkStreet =
        Fixtures.stop("place-pktrm", "Park Street", locationType = LocationType.STATION)
    private val parkAve = Fixtures.stop("1234", "116 Park Ave", vehicleType = RouteType.BUS)
    private val hydeParkAve = Fixtures.stop("5678", "1344 Hyde Park Ave", vehicleType = RouteType.BUS)
    private val oakParkDr = Fixtures.stop("9012", "10 Oak Park Dr", vehicleType = RouteType.BUS)

    /** "park" appears mid-word here, so it must rank below any word-boundary match. */
    private val sparkman = Fixtures.stop("3456", "Sparkman Ave", vehicleType = RouteType.BUS)

    private val index =
        StopSearchIndex.of(listOf(oakParkDr, parkAve, sparkman, hydeParkAve, parkStreet))

    @Test
    fun `ranks the station above bus stops that merely contain the query`() {
        // Regression: a plain `contains` + alphabetical sort put "10 Oak Park Dr" and
        // "116 Park Ave" above Park Street, which is what a rider searching "Park" wants.
        val results = index.search("Park")

        assertEquals(parkStreet.id, results.first().id)
    }

    @Test
    fun `prefers prefix matches, then word starts, then mid-word substrings`() {
        val results = index.search("park").map { it.name }

        // Park Street matches at position 0; the three bus stops match at a word boundary and fall
        // back to name order; Sparkman only matches inside a word and ranks last.
        assertEquals("Park Street", results.first())
        assertEquals("Sparkman Ave", results.last())
        assertEquals(
            listOf("10 Oak Park Dr", "116 Park Ave", "1344 Hyde Park Ave"),
            results.subList(1, results.size - 1),
        )
    }

    @Test
    fun `matching is case insensitive`() {
        assertEquals(index.search("PARK").map { it.id }, index.search("park").map { it.id })
    }

    @Test
    fun `favourites come first regardless of match quality`() {
        val results = index.search("park", favoriteParentIds = setOf(oakParkDr.id))

        assertEquals(oakParkDr.id, results.first().id)
    }

    @Test
    fun `an empty query lists every stop with stations first`() {
        val results = index.search("")

        assertEquals(5, results.size)
        assertEquals(parkStreet.id, results.first().id)
    }

    @Test
    fun `a query that matches nothing returns nothing`() {
        assertTrue(index.search("Alewife").isEmpty())
    }

    @Test
    fun `limit caps the number of results`() {
        assertEquals(2, index.search("park", limit = 2).size)
    }
}
