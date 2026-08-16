package com.saarlabs.tminus.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.saarlabs.tminus.data.StopSearchIndex
import com.saarlabs.tminus.model.Stop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How many results a picker shows. Enough to scroll, few enough to compose quickly. */
private const val MAX_RESULTS = 200

/** Debounce so a fast typist triggers one search, not one per character. */
private const val SEARCH_DEBOUNCE_MS = 120L

/**
 * Search results for a stop picker, computed off the main thread. `null` means "not computed yet",
 * which the picker renders as a spinner rather than as "no matches".
 *
 * Filtering and sorting used to happen inside a `remember` in the composable, so every keystroke ran
 * a filter-plus-sort over ~9 000 stops on the UI thread. Moving it to [Dispatchers.Default] behind a
 * short debounce is what takes the pickers back to a smooth frame rate.
 */
@Composable
internal fun rememberStopSearchResults(
    index: StopSearchIndex?,
    query: String,
    favoriteIds: Set<String>,
    excludeStopId: String? = null,
): State<List<Stop>?> =
    produceState<List<Stop>?>(initialValue = null, index, query, favoriteIds, excludeStopId) {
        val currentIndex = index
        if (currentIndex == null) {
            value = null
            return@produceState
        }
        if (query.isNotEmpty()) delay(SEARCH_DEBOUNCE_MS)
        value =
            withContext(Dispatchers.Default) {
                currentIndex
                    .search(query, favoriteIds, limit = MAX_RESULTS)
                    .let { results ->
                        if (excludeStopId == null) results else results.filter { it.id != excludeStopId }
                    }
            }
    }
