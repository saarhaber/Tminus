package com.saarlabs.tminus.android.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saarlabs.tminus.AppGraph
import com.saarlabs.tminus.R
import com.saarlabs.tminus.android.util.colorFromHex
import com.saarlabs.tminus.model.Route
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.WidgetStationBoardConfig
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.ui.GlobalDataState
import com.saarlabs.tminus.ui.StopPicker
import com.saarlabs.tminus.ui.TminusEdgeToEdge
import com.saarlabs.tminus.ui.rememberGlobalData
import com.saarlabs.tminus.ui.theme.TminusTheme
import com.saarlabs.tminus.ui.theme.rememberUserDarkTheme

/** Configures a station board widget: pick a station, then optionally narrow it to one line. */
public class StationBoardWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = WidgetConfigStore(applicationContext)
        val appWidgetId =
            intent
                ?.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                )
                ?.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }
                ?: store.takePendingStationBoardConfigWidgetId()

        setResult(RESULT_CANCELED)

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            val darkTheme = rememberUserDarkTheme()
            TminusEdgeToEdge(darkTheme)
            TminusTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    StationBoardConfigScreen(
                        onSave = { config ->
                            store.setStationBoardConfig(appWidgetId, config)
                            setResult(
                                RESULT_OK,
                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                            )
                            WidgetUpdateWorker.enqueueRefresh(
                                applicationContext,
                                intArrayOf(appWidgetId),
                            )
                            finish()
                        },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationBoardConfigScreen(
    onSave: (WidgetStationBoardConfig) -> Unit,
    onCancel: () -> Unit,
) {
    var selectedStop by remember { mutableStateOf<Stop?>(null) }
    var selectedRouteId by remember { mutableStateOf<String?>(null) }

    val globalState = rememberGlobalData()
    val globalData = (globalState as? GlobalDataState.Ready)?.data
    val routes = rememberRoutesForStop(selectedStop)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_station_board_configure_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedStop != null) {
                                selectedStop = null
                                selectedRouteId = null
                            } else {
                                onCancel()
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
            val stop = selectedStop
            if (stop != null) {
                Surface(tonalElevation = 3.dp) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Button(
                            onClick = {
                                onSave(
                                    WidgetStationBoardConfig(
                                        stopId = stop.id,
                                        stopLabel = stop.name,
                                        routeId = selectedRouteId,
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(stringResource(R.string.widget_station_board_save))
                        }
                        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.commute_cancel))
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            val stop = selectedStop
            if (stop == null) {
                if (globalState is GlobalDataState.Failed) {
                    Text(
                        text = stringResource(R.string.widget_loading_timeout_tminus),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp),
                    )
                } else {
                    StopPicker(
                        stops = globalData?.getParentStopsForSelection(),
                        onStopChosen = { selectedStop = it },
                        header = stringResource(R.string.widget_station_board_selecting_stop),
                        searchPlaceholder =
                            stringResource(R.string.widget_station_board_select_stop_placeholder),
                    )
                }
            } else {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stop.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(16.dp))
                    RouteFilterSection(
                        routes = routes,
                        selectedRouteId = selectedRouteId,
                        onSelectAll = { selectedRouteId = null },
                        onSelectRoute = { selectedRouteId = it },
                    )
                }
            }
        }
    }
}

/** Routes serving [stop], or `null` while loading. */
@Composable
private fun rememberRoutesForStop(stop: Stop?): List<Route>? {
    val context = LocalContext.current
    val graph = remember(context) { AppGraph.from(context) }
    val routes by
        produceState<List<Route>?>(null, stop) {
            val target = stop
            if (target == null) {
                value = null
                return@produceState
            }
            value =
                when (val result = graph.client.fetchRoutesForStop(target.id)) {
                    is ApiResult.Ok -> result.data
                    // An empty list still lets the user save an all-lines board.
                    is ApiResult.Error -> emptyList()
                }
        }
    return routes
}

/**
 * Line filter chips, tinted with each route's own colour so the Red Line chip reads as red.
 */
@Composable
private fun RouteFilterSection(
    routes: List<Route>?,
    selectedRouteId: String?,
    onSelectAll: () -> Unit,
    onSelectRoute: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.widget_station_board_route_section),
            style = MaterialTheme.typography.titleSmall,
        )
        if (routes == null) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.widget_station_board_loading_routes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedRouteId == null,
                onClick = onSelectAll,
                label = { Text(stringResource(R.string.widget_station_board_route_all)) },
            )
            for (route in routes) {
                val routeColor = remember(route.id) { runCatching { colorFromHex(route.color) }.getOrNull() }
                val labelColor =
                    remember(route.id) { runCatching { colorFromHex(route.textColor) }.getOrNull() }
                FilterChip(
                    selected = selectedRouteId == route.id,
                    onClick = { onSelectRoute(route.id) },
                    label = { Text(route.label) },
                    colors =
                        if (routeColor != null) {
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = routeColor,
                                selectedLabelColor = labelColor ?: Color.White,
                            )
                        } else {
                            FilterChipDefaults.filterChipColors()
                        },
                )
            }
        }
    }
}
