package com.saarlabs.tminus.data

import com.saarlabs.tminus.model.LocationType
import com.saarlabs.tminus.model.RouteType
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.response.GlobalData

/**
 * A prebuilt, rankable index over the stop catalogue.
 *
 * Two problems motivated it:
 *
 * * **Jank.** Pickers filtered and re-sorted all ~9 000 stops inside a `remember` keyed on the
 *   search box, i.e. on the main thread on every keystroke. Measured at 60–67% janky frames with
 *   p90 frame times of 109–150 ms. Lower-casing and tokenising once, up front, turns each keystroke
 *   into a scan of precomputed strings that is cheap enough to run off the main thread.
 * * **Relevance.** Matching with a bare `contains` and sorting alphabetically meant searching
 *   "Park" returned `10 Oak Park Dr`, `116 Park Ave` and `1344 Hyde Park Ave` before `Park Street`.
 *   Matches are now ranked by *where* they hit and stations outrank bus stops.
 */
internal class StopSearchIndex private constructor(private val entries: List<Entry>) {

    private class Entry(
        val stop: Stop,
        val lowerName: String,
        /** Offsets at which a word starts, so "street" can match "Park Street" on a word boundary. */
        val wordStarts: IntArray,
        val modeRank: Int,
    )

    /**
     * Stops matching [query], best first.
     *
     * Ordering: favourites, then match quality (exact, prefix, word-start, substring), then mode
     * (rail stations before bus stops), then name. An empty query lists everything in that same
     * order minus the match component.
     */
    fun search(
        query: String,
        favoriteParentIds: Set<String> = emptySet(),
        limit: Int = Int.MAX_VALUE,
    ): List<Stop> {
        val normalized = query.trim().lowercase()
        val scored = ArrayList<ScoredStop>(if (normalized.isEmpty()) entries.size else 64)

        for (entry in entries) {
            val matchRank =
                if (normalized.isEmpty()) MATCH_NONE else entry.matchRank(normalized) ?: continue
            scored.add(
                ScoredStop(
                    stop = entry.stop,
                    favorite = entry.stop.id in favoriteParentIds,
                    matchRank = matchRank,
                    modeRank = entry.modeRank,
                    lowerName = entry.lowerName,
                ),
            )
        }

        scored.sortWith(SCORED_ORDER)
        return if (scored.size > limit) {
            scored.subList(0, limit).map { it.stop }
        } else {
            scored.map { it.stop }
        }
    }

    private fun Entry.matchRank(query: String): Int? {
        val index = lowerName.indexOf(query)
        if (index < 0) return null
        return when {
            index == 0 && lowerName.length == query.length -> MATCH_EXACT
            index == 0 -> MATCH_PREFIX
            wordStarts.contains(index) -> MATCH_WORD_START
            else -> MATCH_SUBSTRING
        }
    }

    private class ScoredStop(
        val stop: Stop,
        val favorite: Boolean,
        val matchRank: Int,
        val modeRank: Int,
        val lowerName: String,
    )

    companion object {
        private const val MATCH_EXACT = 0
        private const val MATCH_PREFIX = 1
        private const val MATCH_WORD_START = 2
        private const val MATCH_SUBSTRING = 3
        private const val MATCH_NONE = 4

        private val SCORED_ORDER =
            compareBy<ScoredStop> { if (it.favorite) 0 else 1 }
                .thenBy { it.matchRank }
                .thenBy { it.modeRank }
                .thenBy { it.lowerName }

        /** Rail and ferry stations are what riders usually mean; bus stops are the long tail. */
        private fun modeRank(stop: Stop): Int =
            when {
                stop.locationType == LocationType.STATION -> 0
                stop.vehicleType == RouteType.HEAVY_RAIL || stop.vehicleType == RouteType.LIGHT_RAIL -> 1
                stop.vehicleType == RouteType.COMMUTER_RAIL -> 2
                stop.vehicleType == RouteType.FERRY -> 3
                else -> 4
            }

        private fun wordStarts(lowerName: String): IntArray {
            val starts = ArrayList<Int>()
            var previousWasSeparator = true
            for (i in lowerName.indices) {
                val c = lowerName[i]
                val separator = !c.isLetterOrDigit()
                if (previousWasSeparator && !separator) starts.add(i)
                previousWasSeparator = separator
            }
            return starts.toIntArray()
        }

        /** Indexes the parent stops offered in pickers. Call off the main thread. */
        fun build(globalData: GlobalData): StopSearchIndex = of(globalData.getParentStopsForSelection())

        /** Indexes an arbitrary stop list, e.g. the stops served by one route. */
        fun of(stops: List<Stop>): StopSearchIndex =
            StopSearchIndex(
                stops.map { stop ->
                    val lower = stop.name.lowercase()
                    Entry(
                        stop = stop,
                        lowerName = lower,
                        wordStarts = wordStarts(lower),
                        modeRank = modeRank(stop),
                    )
                },
            )
    }
}
