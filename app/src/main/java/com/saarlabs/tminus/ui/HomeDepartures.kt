package com.saarlabs.tminus.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saarlabs.tminus.AppGraph
import com.saarlabs.tminus.FavoriteStopsStore
import com.saarlabs.tminus.R
import com.saarlabs.tminus.android.util.colorFromHex
import com.saarlabs.tminus.model.WidgetStationBoardDeparture
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.util.EasternTimeInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One favourite station and the next few departures from it. */
internal data class FavouriteBoard(
    val stopId: String,
    val stopName: String,
    val departures: List<WidgetStationBoardDeparture>,
)

internal sealed interface HomeBoardsState {
    data object Loading : HomeBoardsState

    /** Nothing starred yet — the caller prompts the user instead of showing an empty section. */
    data object NoFavourites : HomeBoardsState

    data class Ready(val boards: List<FavouriteBoard>) : HomeBoardsState
}

/**
 * Next departures from the user's starred stations.
 *
 * Home used to be three navigation cards and a tip box — a transit app's landing screen with no
 * transit information on it. Favourites already exist for the pickers, so they double as the
 * "stations I care about" list here.
 */
@Composable
internal fun rememberHomeBoards(limitPerStation: Int = 3): HomeBoardsState {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext }
    val favouriteIds = remember(app) { FavoriteStopsStore(app).getIds() }

    val state by
        produceState<HomeBoardsState>(HomeBoardsState.Loading, favouriteIds) {
            if (favouriteIds.isEmpty()) {
                value = HomeBoardsState.NoFavourites
                return@produceState
            }
            value = withContext(Dispatchers.IO) { loadBoards(app, favouriteIds, limitPerStation) }
        }
    return state
}

private suspend fun loadBoards(
    context: Context,
    favouriteIds: Set<String>,
    limitPerStation: Int,
): HomeBoardsState {
    val graph = AppGraph.from(context)
    val globalData =
        when (val result = graph.globalData.getOrLoad()) {
            is ApiResult.Ok -> result.data
            is ApiResult.Error -> return HomeBoardsState.Ready(emptyList())
        }
    val now = EasternTimeInstant.now()
    val boards =
        favouriteIds.mapNotNull { stopId ->
            val stop = globalData.getStop(stopId) ?: return@mapNotNull null
            val departures =
                when (
                    val result =
                        graph.stationBoardUseCase.getDepartures(
                            globalData = globalData,
                            stopId = stopId,
                            routeFilter = null,
                            now = now,
                            limit = limitPerStation,
                        )
                ) {
                    is ApiResult.Ok -> result.data.departures
                    is ApiResult.Error -> return@mapNotNull null
                }
            FavouriteBoard(
                stopId = stopId,
                stopName = stop.resolveParent(globalData.stops).name,
                departures = departures,
            )
        }
    return HomeBoardsState.Ready(boards)
}

@Composable
internal fun FavouriteBoardCard(
    board: FavouriteBoard,
    use24Hour: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = board.stopName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            if (board.departures.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_board_no_departures),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            board.departures.forEachIndexed { index, departure ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                DepartureRow(departure = departure, use24Hour = use24Hour)
            }
        }
    }
}

@Composable
private fun DepartureRow(departure: WidgetStationBoardDeparture, use24Hour: Boolean) {
    val routeColor =
        remember(departure.route.id) {
            runCatching { colorFromHex(departure.route.color) }.getOrNull()
        } ?: MaterialTheme.colorScheme.primary
    val routeTextColor =
        remember(departure.route.id) {
            runCatching { colorFromHex(departure.route.textColor) }.getOrNull()
        } ?: Color.White

    val minutesText =
        if (departure.minutesUntil <= 0) {
            stringResource(R.string.widget_now)
        } else {
            stringResource(R.string.widget_min_short, departure.minutesUntil)
        }
    val clock = departure.departureTime.formatClock(use24Hour)
    val track = departure.platform?.let { stringResource(R.string.widget_track_short, it) }
    val clockLine = track?.let { "$clock · $it" } ?: clock
    // Headsigns like "South Station (Express to Boston Landing after Wellesley Farms)" are far
    // longer than the row is wide. Both halves wrap in full rather than ellipsing: the qualifier
    // is what tells the rider the train skips their stop.
    val (destination, qualifier) =
        remember(departure.headsign) { splitHeadsign(departure.headsign) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        "${departure.route.label} to ${departure.headsign}, $minutesText, $clockLine"
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The chip and the destination share a column rather than a row. Side by side, a full route
        // name ("Framingham / Worcester Line") took most of the width and left the destination as
        // "Wor…" — the one word on the line the rider is actually reading.
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(routeColor)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = departure.route.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = routeTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = destination,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            if (qualifier != null) {
                Text(
                    text = qualifier,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = minutesText,
                style = MaterialTheme.typography.titleSmall,
                color = routeColor,
                maxLines = 1,
            )
            Text(
                text = clockLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun HomeBoardsLoading(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.home_board_loading),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Splits an MBTA headsign into its destination and any trailing parenthetical qualifier, e.g.
 * "South Station (Express to Boston Landing after Wellesley Farms)" becomes
 * "South Station" plus "Express to Boston Landing after Wellesley Farms".
 *
 * The qualifier is real information — it tells a rider which stops the train skips — but it is
 * several times longer than the destination, so it is rendered separately rather than clipped.
 * Only a qualifier that closes at the very end of the string is split off; anything else is left
 * alone so an unbalanced headsign is never silently reshaped.
 */
internal fun splitHeadsign(headsign: String): Pair<String, String?> {
    val trimmed = headsign.trim()
    if (!trimmed.endsWith(")")) return trimmed to null
    val open = trimmed.indexOf('(')
    if (open <= 0) return trimmed to null
    val destination = trimmed.substring(0, open).trim()
    val qualifier = trimmed.substring(open + 1, trimmed.length - 1).trim()
    if (destination.isEmpty() || qualifier.isEmpty()) return trimmed to null
    return destination to qualifier
}
