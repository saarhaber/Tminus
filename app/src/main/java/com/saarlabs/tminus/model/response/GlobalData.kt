package com.saarlabs.tminus.model.response

import com.saarlabs.tminus.model.LocationType
import com.saarlabs.tminus.model.Stop

/** Stops and routes loaded from the MBTA V3 API for search and labels. */
public data class GlobalData(
    val stops: Map<String, Stop>,
    val routes: Map<String, com.saarlabs.tminus.model.Route>,
) {
    public fun getStop(id: String?): Stop? = id?.let { stops[it] }

    public fun getRoute(id: String?): com.saarlabs.tminus.model.Route? = id?.let { routes[it] }

    public fun getParentStopsForSelection(): List<Stop> =
        stops.values
            .filter { it.parentStationId == null }
            .filter { it.locationType == LocationType.STATION || it.locationType == LocationType.STOP }
            .sortedBy { it.name }

    /**
     * Stop IDs to pass to `/schedules?filter[stop]=…`, including platforms under a station.
     * MBTA often omits [Stop.childStopIds] on parent stations; schedules reference platform IDs.
     */
    public fun stopIdsForScheduleFilter(stop: Stop): List<String> {
        val root = stop.resolveParent(stops)
        val out = LinkedHashSet<String>()
        out.add(root.id)
        for (cid in root.childStopIds) {
            if (stops.containsKey(cid)) out.add(cid)
        }
        for (s in stops.values) {
            if (s.parentStationId == root.id) out.add(s.id)
        }
        return out.toList()
    }
}
