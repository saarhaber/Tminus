package com.saarlabs.tminus

import android.content.Context
import com.saarlabs.tminus.model.Stop

/** Persists favorite MBTA stations (parent stop ids) for ordering and quick access in pickers. */
public class FavoriteStopsStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)

    /** Favorites in the order the user starred them (insertion order, new stars appended). */
    public fun getIds(): Set<String> {
        val ordered = prefs.getString(SettingsKeys.KEY_FAVORITE_STOP_IDS_ORDERED, null)
        if (ordered != null) {
            return ordered.split("\n").filter { it.isNotEmpty() }.toCollection(LinkedHashSet())
        }
        // Migrate installs that only have the legacy unordered set; alphabetical seed keeps the
        // migration deterministic even though the original starring order is unknown.
        val legacy =
            prefs.getStringSet(SettingsKeys.KEY_FAVORITE_STOP_IDS, emptySet())?.toList().orEmpty()
        val migrated = legacy.sorted()
        prefs.edit()
            .putString(SettingsKeys.KEY_FAVORITE_STOP_IDS_ORDERED, migrated.joinToString("\n"))
            .apply()
        return migrated.toCollection(LinkedHashSet())
    }

    /** @return true if the stop is now a favorite after the toggle. */
    public fun toggle(parentStopId: String): Boolean {
        val next = getIds().toMutableList()
        val nowFavorite =
            if (parentStopId in next) {
                next.remove(parentStopId)
                false
            } else {
                next.add(parentStopId)
                true
            }
        prefs.edit()
            .putString(SettingsKeys.KEY_FAVORITE_STOP_IDS_ORDERED, next.joinToString("\n"))
            .putStringSet(SettingsKeys.KEY_FAVORITE_STOP_IDS, next.toSet())
            .apply()
        return nowFavorite
    }
}

/** Favorites first (by name), then the rest (by name), using resolved parent ids for matching. */
public fun sortStopsWithFavoritesFirst(
    stops: List<Stop>,
    favoriteParentIds: Set<String>,
    allStops: Map<String, Stop>,
): List<Stop> {
    return stops.sortedWith(
        compareBy<Stop> { stop ->
            val parentId = stop.resolveParent(allStops).id
            if (parentId in favoriteParentIds) 0 else 1
        }.thenBy { it.resolveParent(allStops).name.lowercase() },
    )
}
