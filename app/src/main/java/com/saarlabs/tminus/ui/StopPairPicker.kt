package com.saarlabs.tminus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saarlabs.tminus.AppGraph
import com.saarlabs.tminus.R
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.model.response.GlobalData

/** Destinations reachable from an origin, or `null` while they are still loading. */
internal sealed interface ReachableState {
    data object Idle : ReachableState

    data object Loading : ReachableState

    /** The lookup failed; fall back to offering every stop rather than blocking the user. */
    data object Failed : ReachableState

    data class Ready(val stops: List<Stop>) : ReachableState
}

@Composable
internal fun rememberReachableStops(origin: Stop?, globalData: GlobalData?): ReachableState {
    val context = LocalContext.current
    val graph = remember(context) { AppGraph.from(context) }
    val state by
        produceState<ReachableState>(ReachableState.Idle, origin, globalData) {
            val from = origin
            val global = globalData
            if (from == null || global == null) {
                value = ReachableState.Idle
                return@produceState
            }
            value = ReachableState.Loading
            value =
                when (val result = graph.client.fetchReachableDestinationStops(from.id, global.stops)) {
                    is ApiResult.Ok -> ReachableState.Ready(result.data)
                    is ApiResult.Error -> ReachableState.Failed
                }
        }
    return state
}

/**
 * Picks an origin and a destination.
 *
 * The confirm action is a full-width button pinned to the bottom of the screen. It used to be a
 * small text button in the app bar, which left the primary action of the screen in its least
 * reachable corner while the area below it sat empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun StopPairPicker(
    onStopsChosen: (from: Stop, to: Stop) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var fromStop by remember { mutableStateOf<Stop?>(null) }
    var toStop by remember { mutableStateOf<Stop?>(null) }

    val globalState = rememberGlobalData()
    val globalData = (globalState as? GlobalDataState.Ready)?.data
    val reachable = rememberReachableStops(fromStop, globalData)

    val destinationStops: List<Stop>? =
        when {
            globalData == null -> null
            reachable is ReachableState.Ready -> reachable.stops
            reachable is ReachableState.Failed -> globalData.getParentStopsForSelection()
            else -> null
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stop_pair_picker_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when {
                                toStop != null -> toStop = null
                                fromStop != null -> fromStop = null
                                else -> onCancel()
                            }
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.commute_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            val from = fromStop
            val to = toStop
            if (from != null && to != null) {
                Surface(tonalElevation = 3.dp) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Button(
                            onClick = { onStopsChosen(from, to) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(stringResource(R.string.commute_use_stops))
                        }
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.commute_cancel))
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            SelectedStops(
                from = fromStop,
                to = toStop,
                onClearFrom = {
                    fromStop = null
                    toStop = null
                },
                onClearTo = { toStop = null },
            )

            when {
                globalState is GlobalDataState.Failed ->
                    Text(
                        text = stringResource(R.string.widget_loading_timeout_tminus),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp),
                    )

                fromStop == null ->
                    StopPicker(
                        stops = globalData?.getParentStopsForSelection(),
                        onStopChosen = { fromStop = it },
                        header = stringResource(R.string.widget_selecting_from),
                        searchPlaceholder = stringResource(R.string.widget_select_from_stop),
                    )

                toStop == null ->
                    Column(Modifier.fillMaxSize()) {
                        if (reachable is ReachableState.Loading) {
                            LoadingDestinationsRow()
                        }
                        if (reachable is ReachableState.Failed) {
                            Text(
                                text = stringResource(R.string.widget_destinations_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                        StopPicker(
                            stops = destinationStops,
                            onStopChosen = { toStop = it },
                            header = stringResource(R.string.widget_selecting_to),
                            searchPlaceholder = stringResource(R.string.widget_select_to_stop),
                            excludeStopId = fromStop?.id,
                            emptyMessage = stringResource(R.string.widget_destinations_unavailable),
                        )
                    }

                else -> Spacer(Modifier.height(0.dp))
            }
        }
    }
}

@Composable
private fun LoadingDestinationsRow() {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.widget_loading_destinations),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectedStops(
    from: Stop?,
    to: Stop?,
    onClearFrom: () -> Unit,
    onClearTo: () -> Unit,
) {
    if (from == null) return
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        StopChip(label = stringResource(R.string.widget_from), name = from.name, onClear = onClearFrom)
        if (to != null) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = to.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearTo) { Text(stringResource(R.string.widget_clear_stop)) }
            }
        }
    }
}

@Composable
private fun StopChip(label: String, name: String, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: $name",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClear) { Text(stringResource(R.string.widget_clear_stop)) }
    }
}
