package com.saarlabs.tminus.android.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.saarlabs.tminus.AppGraph
import com.saarlabs.tminus.R
import com.saarlabs.tminus.model.WidgetStationBoardConfig
import com.saarlabs.tminus.model.WidgetStationBoardDeparture
import com.saarlabs.tminus.model.WidgetTripConfig
import com.saarlabs.tminus.model.WidgetTripData
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.ui.theme.readFontScale
import com.saarlabs.tminus.util.EasternTimeInstant
import com.saarlabs.tminus.FavoriteStopsStore
import com.saarlabs.tminus.model.WidgetAlertsConfig
import com.saarlabs.tminus.model.WidgetFavoritesConfig
import com.saarlabs.tminus.model.response.MbtaAlertSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

internal const val INVALID_WIDGET_ID: Int = AppWidgetManager.INVALID_APPWIDGET_ID

/** Departures fetched per board; how many are visible depends on the widget's height. */
private const val STATION_BOARD_LIMIT = 12

/** Display preferences shared by both widgets. */
internal data class WidgetDisplayPrefs(val use24Hour: Boolean, val fontScale: Float)

internal sealed interface StationBoardUiState {
    data object Loading : StationBoardUiState

    /** Not bound to a valid app-widget id — nothing can be looked up. */
    data object Invalid : StationBoardUiState

    /** No configuration saved yet: show the "tap to set up" prompt. */
    data object NeedsConfig : StationBoardUiState

    data class Failed(val config: WidgetStationBoardConfig) : StationBoardUiState

    data class Ready(
        val config: WidgetStationBoardConfig,
        val stationTitle: String,
        /** Second header line; names the chosen direction when the board is filtered to one. */
        val scheduledSubtitle: String,
        val departures: List<WidgetStationBoardDeparture>,
        /** When the countdowns were computed; a refresh tick recomputes them without a fetch. */
        val renderedAt: EasternTimeInstant,
    ) : StationBoardUiState
}

internal sealed interface TripUiState {
    data object Loading : TripUiState

    data object Invalid : TripUiState

    data object NeedsConfig : TripUiState

    data class Failed(val config: WidgetTripConfig) : TripUiState

    data class NoTrips(val config: WidgetTripConfig) : TripUiState

    data class Ready(
        val config: WidgetTripConfig,
        val trip: WidgetTripData,
        val renderedAt: EasternTimeInstant,
    ) : TripUiState
}

/**
 * The station board's state, loaded **inside** `provideContent`.
 *
 * That placement is the fix for widgets that stayed on "tap to set up" after being configured:
 * [androidx.glance.appwidget.GlanceAppWidget.update] recomposes content that is already registered,
 * but it does not re-run whatever ran before `provideContent`. A widget that read its configuration
 * up front therefore kept the stale reading for the whole life of the Glance session. Collecting
 * the configuration as a flow also means a save from the configuration activity lands immediately,
 * with no update call at all.
 */
@Composable
internal fun rememberStationBoardState(
    context: Context,
    appWidgetId: Int,
): StationBoardUiState {
    if (appWidgetId == INVALID_WIDGET_ID) return StationBoardUiState.Invalid
    val store = remember(context) { WidgetConfigStore(context) }
    val trigger by
        rememberTrigger(store) { store.stationBoardConfigFlow(appWidgetId) }
            .collectAsState(initial = store.stationBoardConfig(appWidgetId) to store.refreshTick())
    val state by
        produceState<StationBoardUiState>(StationBoardUiState.Loading, trigger) {
            val config = trigger.first
            value =
                if (config == null) {
                    StationBoardUiState.NeedsConfig
                } else {
                    withContext(Dispatchers.IO) { loadStationBoard(context, config) }
                }
        }
    return state
}

/** The trip widget's state. See [rememberStationBoardState] for why the load lives here. */
@Composable
internal fun rememberTripState(context: Context, appWidgetId: Int): TripUiState {
    if (appWidgetId == INVALID_WIDGET_ID) return TripUiState.Invalid
    val store = remember(context) { WidgetConfigStore(context) }
    val trigger by
        rememberTrigger(store) { store.tripConfigFlow(appWidgetId) }
            .collectAsState(initial = store.tripConfig(appWidgetId) to store.refreshTick())
    val state by
        produceState<TripUiState>(TripUiState.Loading, trigger) {
            val config = trigger.first
            value =
                if (config == null) {
                    TripUiState.NeedsConfig
                } else {
                    withContext(Dispatchers.IO) { loadTrip(config) }
                }
        }
    return state
}

@Composable
internal fun rememberDisplayPrefs(context: Context): WidgetDisplayPrefs {
    val app = context.applicationContext
    return remember(app) {
        WidgetDisplayPrefs(
            use24Hour = AppGraph.from(app).settings.use24HourTime(),
            fontScale = readFontScale(app),
        )
    }
}

/**
 * `(config, refreshTick)` pairs. The tick changes on every refresh trigger, which guarantees a new
 * composition — and therefore a recomputed countdown — even when the configuration is unchanged.
 */
@Composable
private fun <T : Any> rememberTrigger(
    store: WidgetConfigStore,
    configFlow: () -> Flow<T?>,
): Flow<Pair<T?, Long>> =
    remember(store) {
        combine(configFlow(), store.refreshTickFlow()) { config, tick -> config to tick }
            .distinctUntilChanged()
    }

private suspend fun loadStationBoard(
    context: Context,
    config: WidgetStationBoardConfig,
): StationBoardUiState {
    val graph = AppGraph.instance ?: return StationBoardUiState.Failed(config)
    val now = EasternTimeInstant.now()
    val globalData =
        when (val g = graph.globalData.getOrLoad()) {
            is ApiResult.Ok -> g.data
            is ApiResult.Error -> return StationBoardUiState.Failed(config)
        }
    val result =
        graph.stationBoardUseCase.getDepartures(
            globalData = globalData,
            stopId = config.stopId,
            routeFilter = config.routeId,
            destinationFilter = config.destinationHeadsign,
            now = now,
            limit = STATION_BOARD_LIMIT,
        )
    return when (result) {
        is ApiResult.Error -> StationBoardUiState.Failed(config)
        is ApiResult.Ok ->
            StationBoardUiState.Ready(
                config = config,
                stationTitle =
                    config.stopLabel.ifEmpty {
                        globalData.getStop(config.stopId)
                            ?.resolveParent(globalData.stops)
                            ?.name
                            .orEmpty()
                    },
                scheduledSubtitle =
                    config.destinationHeadsign?.takeIf { it.isNotBlank() }?.let { destination ->
                        context.getString(
                            R.string.widget_station_board_scheduled_filtered,
                            destination,
                        )
                    }
                        ?: context.getString(R.string.widget_station_board_scheduled_subtitle),
                departures = result.data.departures,
                renderedAt = now,
            )
    }
}

private suspend fun loadTrip(config: WidgetTripConfig): TripUiState {
    val graph = AppGraph.instance ?: return TripUiState.Failed(config)
    val now = EasternTimeInstant.now()
    val globalData =
        when (val g = graph.globalData.getOrLoad()) {
            is ApiResult.Ok -> g.data
            is ApiResult.Error -> return TripUiState.Failed(config)
        }
    val result =
        graph.tripUseCase.getNextTrip(
            globalData = globalData,
            fromStopId = config.fromStopId,
            toStopId = config.toStopId,
            now = now,
        )
    return when (result) {
        is ApiResult.Error -> TripUiState.Failed(config)
        is ApiResult.Ok ->
            result.data.trip?.let { TripUiState.Ready(config, it, now) }
                ?: TripUiState.NoTrips(config)
    }
}

// --- favorites widget ---

internal sealed interface FavoritesUiState {
    data object Loading : FavoritesUiState

    /** No stops starred yet — the widget asks the user to add some rather than showing an error. */
    data object NoFavourites : FavoritesUiState

    data object Failed : FavoritesUiState

    data class Ready(
        val rows: List<FavoriteDepartureRow>,
        val renderedAt: EasternTimeInstant,
    ) : FavoritesUiState
}

/** See [rememberStationBoardState] for why the load lives inside the composition. */
@Composable
internal fun rememberFavoritesState(context: Context, appWidgetId: Int): FavoritesUiState {
    val store = remember(context) { WidgetConfigStore(context) }
    val trigger by
        rememberTrigger(store) { store.favoritesConfigFlow(appWidgetId) }
            .collectAsState(initial = store.favoritesConfig(appWidgetId) to store.refreshTick())
    val state by
        produceState<FavoritesUiState>(FavoritesUiState.Loading, trigger) {
            value = withContext(Dispatchers.IO) { loadFavorites(context, trigger.first) }
        }
    return state
}

private suspend fun loadFavorites(
    context: Context,
    config: WidgetFavoritesConfig?,
): FavoritesUiState {
    val graph = AppGraph.instance ?: return FavoritesUiState.Failed
    val count = (config?.count ?: DEFAULT_FAVORITES).coerceIn(1, MAX_FAVORITES)
    val favoriteIds = FavoriteStopsStore(context.applicationContext).getIds().toList().take(count)
    if (favoriteIds.isEmpty()) return FavoritesUiState.NoFavourites

    val now = EasternTimeInstant.now()
    val globalData =
        when (val g = graph.globalData.getOrLoad()) {
            is ApiResult.Ok -> g.data
            is ApiResult.Error -> return FavoritesUiState.Failed
        }

    // One schedules lookup per favourite, run concurrently so the widget renders in roughly one
    // round trip instead of N. They are usually served from the timetable cache.
    val rows =
        coroutineScope {
            favoriteIds
                .mapNotNull { id -> globalData.getStop(id)?.let { id to it } }
                .map { (id, stop) ->
                    async {
                        val departure =
                            when (
                                val res =
                                    graph.stationBoardUseCase.getDepartures(
                                        globalData = globalData,
                                        stopId = id,
                                        routeFilter = null,
                                        now = now,
                                        limit = 1,
                                    )
                            ) {
                                is ApiResult.Ok -> res.data.departures.firstOrNull()
                                is ApiResult.Error -> null
                            }
                        FavoriteDepartureRow(
                            stopName = stop.resolveParent(globalData.stops).name,
                            departure = departure,
                        )
                    }
                }
                .awaitAll()
        }

    // Soonest first puts stops with an upcoming departure at the top; otherwise keep the user's
    // own favourites order.
    val ordered =
        if (config?.sortBySoonest == true) {
            rows.sortedBy { it.departure?.minutesUntil ?: Int.MAX_VALUE }
        } else {
            rows
        }
    return FavoritesUiState.Ready(rows = ordered, renderedAt = now)
}

// --- service alerts widget ---

/** Alerts for the configured line groups. [failed] keeps the widget's error card behaviour. */
internal data class AlertsUiState(
    val alerts: List<MbtaAlertSummary>,
    val failed: Boolean,
)

/** See [rememberStationBoardState] for why the load lives inside the composition. */
@Composable
internal fun rememberAlertsState(context: Context, appWidgetId: Int): AlertsUiState {
    val store = remember(context) { WidgetConfigStore(context) }
    val trigger by
        rememberTrigger(store) { store.alertsConfigFlow(appWidgetId) }
            .collectAsState(initial = store.alertsConfig(appWidgetId) to store.refreshTick())
    val state by
        produceState(AlertsUiState(emptyList(), failed = false), trigger) {
            value = withContext(Dispatchers.IO) { loadAlerts(trigger.first) }
        }
    return state
}

private suspend fun loadAlerts(config: WidgetAlertsConfig?): AlertsUiState {
    val graph = AppGraph.instance ?: return AlertsUiState(emptyList(), failed = true)
    val routeFilter = AlertsWidgetLines.filterFor(config?.lineGroupIds ?: emptyList())
    return when (val result = graph.client.fetchAlertsForRoute(routeFilter)) {
        is ApiResult.Ok ->
            AlertsUiState(
                alerts = result.data.distinctBy { it.id }.take(MAX_ALERT_ROWS),
                failed = false,
            )
        is ApiResult.Error -> AlertsUiState(emptyList(), failed = true)
    }
}

/** Favourites shown when the widget has no saved configuration. */
private const val DEFAULT_FAVORITES = 3
private const val MAX_FAVORITES = 10
private const val MAX_ALERT_ROWS = 8
