package com.saarlabs.tminus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.saarlabs.tminus.AppGraph
import com.saarlabs.tminus.FavoriteStopsStore
import com.saarlabs.tminus.R
import com.saarlabs.tminus.data.StopSearchIndex
import com.saarlabs.tminus.model.RouteType
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.model.response.GlobalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of loading the stop catalogue, shared by every screen that needs stops. */
internal sealed interface GlobalDataState {
    data object Loading : GlobalDataState

    data class Failed(val message: String) : GlobalDataState

    data class Ready(val data: GlobalData) : GlobalDataState
}

/** Loads the stop/route catalogue once, off the main thread. */
@Composable
internal fun rememberGlobalData(): GlobalDataState {
    val context = LocalContext.current
    val graph = remember(context) { AppGraph.from(context) }
    val state by
        produceState<GlobalDataState>(GlobalDataState.Loading, graph) {
            value =
                when (val result = graph.globalData.getOrLoad()) {
                    is ApiResult.Ok -> GlobalDataState.Ready(result.data)
                    is ApiResult.Error -> GlobalDataState.Failed(result.message)
                }
        }
    return state
}

/** Builds a search index for [stops] off the main thread. */
@Composable
internal fun rememberStopIndex(stops: List<Stop>?): StopSearchIndex? {
    val index by
        produceState<StopSearchIndex?>(null, stops) {
            val source = stops
            value = if (source == null) null else withContext(Dispatchers.Default) { StopSearchIndex.of(source) }
        }
    return index
}

/**
 * The app's single stop picker: a search field over a ranked, favourite-aware list.
 *
 * Every screen that chooses a stop uses this — the commute editor, the alert editors and both
 * widget configuration activities previously each carried their own copy, which is how the
 * route-scoped pickers ended up offering stops the route does not serve.
 */
@Composable
internal fun StopPicker(
    stops: List<Stop>?,
    onStopChosen: (Stop) -> Unit,
    modifier: Modifier = Modifier,
    header: String? = null,
    emptyMessage: String? = null,
    excludeStopId: String? = null,
    searchPlaceholder: String = stringResource(R.string.stop_search_hint),
) {
    val context = LocalContext.current
    val favoriteStore = remember(context) { FavoriteStopsStore(context) }
    var favoriteIds by remember { mutableStateOf(favoriteStore.getIds()) }
    var query by rememberSaveable { mutableStateOf("") }

    val index = rememberStopIndex(stops)
    val results by rememberStopSearchResults(index, query, favoriteIds, excludeStopId)

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(searchPlaceholder) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )

        if (header != null) {
            Text(
                text = header,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        val settled = results
        when {
            stops == null || settled == null ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.loading),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

            settled.isEmpty() ->
                Text(
                    text =
                        when {
                            query.isNotBlank() -> stringResource(R.string.stop_search_no_results, query)
                            else -> emptyMessage ?: stringResource(R.string.stop_search_empty)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )

            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(settled, key = { it.id }) { stop ->
                        StopRow(
                            stop = stop,
                            favorite = stop.id in favoriteIds,
                            onToggleFavorite = {
                                favoriteStore.toggle(stop.id)
                                favoriteIds = favoriteStore.getIds()
                            },
                            onClick = { onStopChosen(stop) },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
        }
    }
}

/**
 * One selectable stop.
 *
 * [ListItem] gives the row a 56 dp minimum height and a proper click target; the favourite button
 * keeps its own label so TalkBack announces "Add to favourites" separately from the stop name.
 */
@Composable
private fun StopRow(
    stop: Stop,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    val favoriteLabel =
        stringResource(if (favorite) R.string.stop_unfavorite else R.string.stop_favorite)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListItem(
            headlineContent = { Text(stop.name, style = MaterialTheme.typography.bodyLarge) },
            supportingContent =
                stopModeLabel(stop)?.let { label ->
                    { Text(label, style = MaterialTheme.typography.bodySmall) }
                },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(onClickLabel = stop.name, onClick = onClick),
        )
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = favoriteLabel,
                tint =
                    if (favorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics { contentDescription = favoriteLabel },
            )
        }
    }
}

@Composable
private fun stopModeLabel(stop: Stop): String? =
    when (stop.vehicleType) {
        RouteType.HEAVY_RAIL,
        RouteType.LIGHT_RAIL,
        -> stringResource(R.string.mode_subway)
        RouteType.COMMUTER_RAIL -> stringResource(R.string.mode_commuter_rail)
        RouteType.FERRY -> stringResource(R.string.mode_ferry)
        RouteType.BUS -> stringResource(R.string.mode_bus)
        null -> null
    }
