package com.saarlabs.tminus.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saarlabs.tminus.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.saarlabs.tminus.AppGraph
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.response.ApiResult

/**
 * Picks a single stop.
 *
 * When [restrictToStops] is supplied the picker only offers those stops — the alert editors pass the
 * stops served by the chosen route, so a "last Orange Line train" alert cannot be attached to a bus
 * stop the Orange Line never calls at.
 */
@Composable
public fun StopSearchPicker(
    onStopChosen: (Stop) -> Unit,
    modifier: Modifier = Modifier,
    restrictToStops: List<Stop>? = null,
    restrictionActive: Boolean = false,
) {
    if (restrictionActive) {
        StopPicker(
            stops = restrictToStops,
            onStopChosen = onStopChosen,
            modifier = modifier,
            header = stringResource(R.string.stop_picker_route_scoped_header),
            emptyMessage = stringResource(R.string.stop_picker_route_scoped_empty),
        )
        return
    }

    when (val global = rememberGlobalData()) {
        GlobalDataState.Loading ->
            CircularProgressIndicator(modifier = modifier.padding(24.dp))

        is GlobalDataState.Failed ->
            Column(modifier = modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.widget_loading_timeout_tminus),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

        is GlobalDataState.Ready ->
            StopPicker(
                stops = global.data.getParentStopsForSelection(),
                onStopChosen = onStopChosen,
                modifier = modifier,
            )
    }
}

/**
 * Stops served by [routeId], or `null` while loading.
 *
 * The alert editors scope their stop picker with this. Without it they offered all ~9 000 stops in
 * the system regardless of the chosen route, so it was possible to save an "Orange Line last train"
 * alert against a bus stop the Orange Line never calls at — an alert that could never fire.
 */
@Composable
internal fun rememberStopsForRoute(routeId: String): List<Stop>? {
    val context = LocalContext.current
    val graph = remember(context) { AppGraph.from(context) }
    val stops by
        produceState<List<Stop>?>(null, routeId) {
            val id = routeId.trim()
            if (id.isEmpty()) {
                value = emptyList()
                return@produceState
            }
            value =
                when (val result = graph.client.fetchStopsForRoute(id)) {
                    is ApiResult.Ok -> result.data
                    is ApiResult.Error -> emptyList()
                }
        }
    return stops
}
