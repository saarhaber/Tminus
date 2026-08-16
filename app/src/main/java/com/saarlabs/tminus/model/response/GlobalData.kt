package com.saarlabs.tminus.model.response

import com.saarlabs.tminus.model.LocationType
import com.saarlabs.tminus.model.Route
import com.saarlabs.tminus.model.Stop
import kotlinx.serialization.Serializable

/** Stops and routes loaded from the MBTA V3 API for search and labels. */
@Serializable
public data class GlobalData(
    val stops: Map<String, Stop>,
    val routes: Map<String, Route>,
) {
    /**
     * Parent station id → the stops that hang off it.
     *
     * Built once per catalogue rather than re-scanning all ~9 000 stops per lookup, which
     * [stopIdsForScheduleFilter] used to do on every widget render and every commute evaluation.
     */
    private val childrenByParent: Map<String, List<Stop>> by lazy {
        stops.values.filter { it.parentStationId != null }.groupBy { it.parentStationId!! }
    }

    /** Parent stops offered in pickers, sorted by name. Computed once per catalogue. */
    private val selectableStops: List<Stop> by lazy {
        stops.values
            .filter { it.parentStationId == null }
            .filter { it.locationType == LocationType.STATION || it.locationType == LocationType.STOP }
            .sortedBy { it.name }
    }

    public fun getStop(id: String?): Stop? = id?.let { stops[it] }

    public fun getRoute(id: String?): Route? = id?.let { routes[it] }

    public fun getParentStopsForSelection(): List<Stop> = selectableStops

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
        childrenByParent[root.id]?.forEach { out.add(it.id) }
        return out.toList()
    }
}
