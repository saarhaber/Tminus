package com.saarlabs.tminus.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.saarlabs.tminus.MainActivity
import com.saarlabs.tminus.R
import com.saarlabs.tminus.android.util.colorFromHex
import com.saarlabs.tminus.android.util.formattedTime
import com.saarlabs.tminus.model.WidgetStationBoardDeparture
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.util.EasternTimeInstant

internal data class FavoriteDepartureRow(
    val stopName: String,
    val departure: WidgetStationBoardDeparture?,
)

/**
 * Config-free widget that shows the soonest scheduled departure from each of the user's favorite
 * stops (managed in-app via [FavoriteStopsStore]). Reuses [WidgetStationBoardUseCase] for data and
 * refreshes via the system update period plus Glance host updates.
 */
public class MBTAFavoritesWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId =
            runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }
                .getOrDefault(INVALID_WIDGET_ID)

        // Loaded inside provideContent for the same reason as the other widgets: update() only
        // recomposes already-registered content, so a configuration read before this point would
        // stay stale for the life of the Glance session.
        provideContent {
            val prefs = rememberDisplayPrefs(context)
            when (val state = rememberFavoritesState(context, appWidgetId)) {
                FavoritesUiState.Loading ->
                    FavoritesContent.Message(context, context.getString(R.string.loading))
                FavoritesUiState.NoFavourites ->
                    FavoritesContent.Message(context, context.getString(R.string.widget_favorites_empty))
                FavoritesUiState.Failed ->
                    FavoritesContent.Message(context, context.getString(R.string.widget_favorites_error))
                is FavoritesUiState.Ready ->
                    FavoritesContent.Board(context, state.rows, prefs.use24Hour)
            }
        }
    }

}

private object FavoritesContent {

    private fun color(context: Context, resId: Int): Color =
        Color(ContextCompat.getColor(context, resId).toLong() and 0xFFFFFFFFL)

    @Composable
    fun Message(context: Context, text: String) {
        GlanceTheme {
            Column(
                modifier =
                    GlanceModifier.fillMaxSize()
                        .background(color(context, R.color.widget_surface))
                        .cornerRadius(24.dp)
                        .padding(16.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = text,
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(color(context, R.color.widget_on_surface_variant)),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 5,
                )
            }
        }
    }

    @Composable
    fun Board(context: Context, rows: List<FavoriteDepartureRow>, use24Hour: Boolean) {
        GlanceTheme {
            Column(
                modifier =
                    GlanceModifier.fillMaxSize()
                        .background(color(context, R.color.widget_surface))
                        .cornerRadius(24.dp),
            ) {
                Column(
                    modifier =
                        GlanceModifier.fillMaxWidth()
                            .background(color(context, R.color.widget_header))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(actionStartActivity<MainActivity>()),
                ) {
                    Text(
                        text = context.getString(R.string.widget_favorites_label),
                        style =
                            TextStyle(
                                color = ColorProvider(color(context, R.color.widget_on_header)),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        maxLines = 1,
                    )
                    Text(
                        text = context.getString(R.string.widget_favorites_subtitle),
                        style =
                            TextStyle(
                                color = ColorProvider(color(context, R.color.widget_on_header)),
                                fontSize = 10.sp,
                            ),
                        maxLines = 1,
                    )
                }
                LazyColumn(
                    modifier =
                        GlanceModifier.fillMaxSize()
                            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                ) {
                    items(rows.size) { index ->
                        Column {
                            if (index > 0) Spacer(modifier = GlanceModifier.height(4.dp))
                            FavoriteRow(context, rows[index], use24Hour)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FavoriteRow(context: Context, row: FavoriteDepartureRow, use24Hour: Boolean) {
        val primary = color(context, R.color.widget_on_surface)
        val secondary = color(context, R.color.widget_on_surface_variant)
        val departure = row.departure
        val accent =
            departure?.let { runCatching { colorFromHex(it.route.color) }.getOrNull() }
                ?: color(context, R.color.widget_accent)
        Row(
            modifier =
                GlanceModifier.fillMaxWidth()
                    .background(color(context, R.color.widget_row_bg))
                    .cornerRadius(14.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    GlanceModifier.width(4.dp).fillMaxHeight().background(accent).cornerRadius(4.dp),
            ) {}
            Spacer(modifier = GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = row.stopName,
                    style =
                        TextStyle(
                            color = ColorProvider(primary),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    maxLines = 1,
                )
                Text(
                    text =
                        departure?.headsign
                            ?: context.getString(R.string.widget_station_board_no_departures),
                    style =
                        TextStyle(
                            color = ColorProvider(secondary),
                            fontSize = 10.sp,
                        ),
                    maxLines = 1,
                )
            }
            if (departure != null) {
                val minutesText =
                    if (departure.minutesUntil <= 0) context.getString(R.string.widget_now)
                    else context.getString(R.string.widget_min_short, departure.minutesUntil)
                Spacer(modifier = GlanceModifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = minutesText,
                        style =
                            TextStyle(
                                color = ColorProvider(accent),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                            ),
                        maxLines = 1,
                    )
                    Text(
                        // Commuter rail publishes a track; the board next to it shows one, so a
                        // favourite for the same station should not be the surface that hides it.
                        text =
                            clockWithTrack(
                                context,
                                departure.departureTime.formattedTime(use24Hour),
                                departure.platform,
                            ),
                        style =
                            TextStyle(
                                color = ColorProvider(secondary),
                                fontSize = 10.sp,
                                textAlign = TextAlign.End,
                            ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
