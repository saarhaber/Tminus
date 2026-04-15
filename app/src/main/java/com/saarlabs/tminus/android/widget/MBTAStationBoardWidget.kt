package com.saarlabs.tminus.android.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.saarlabs.tminus.model.WidgetStationBoardConfig
import com.saarlabs.tminus.model.WidgetStationBoardDeparture
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.usecases.WidgetStationBoardUseCase
import com.saarlabs.tminus.util.EasternTimeInstant
import com.saarlabs.tminus.MainActivity
import com.saarlabs.tminus.R
import com.saarlabs.tminus.SettingsKeys
import com.saarlabs.tminus.TminusApplication
import com.saarlabs.tminus.GlobalDataStore
import com.saarlabs.tminus.android.util.colorFromHex
import com.saarlabs.tminus.android.util.formattedTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

public class MBTAStationBoardWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    private val useCase: WidgetStationBoardUseCase
        get() = TminusApplication.widgetStationBoardUseCase

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        try {
            provideGlanceInternal(context, id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            android.util.Log.e("MBTAStationBoardWidget", "provideGlance failed", e)
            provideContent { StationBoardContent.ErrorState(context = context) }
        }
    }

    private suspend fun provideGlanceInternal(context: Context, id: GlanceId) {
        val widgetPreferences = WidgetPreferences(context.applicationContext)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            provideContent { StationBoardContent.ErrorState(context = context) }
            return
        }

        var config = withContext(Dispatchers.IO) { widgetPreferences.getStationBoardConfigOnce(appWidgetId) }
        if (config == null) {
            repeat(8) {
                delay(250)
                config = withContext(Dispatchers.IO) { widgetPreferences.getStationBoardConfigOnce(appWidgetId) }
                if (config != null) return@repeat
            }
        }
        if (config == null) {
            // #region agent log
            AgentDebugLog.log(
                "MBTAStationBoardWidget.kt:provideGlanceInternal",
                "showing station configure prompt (no saved config)",
                "H4",
                mapOf("appWidgetId" to appWidgetId),
            )
            // #endregion
            withContext(Dispatchers.IO) {
                WidgetPreferences(context.applicationContext).setPendingStationBoardConfigWidgetId(appWidgetId)
            }
            provideContent {
                StationBoardContent.ConfigurePrompt(context = context, appWidgetId = appWidgetId)
            }
            return
        }

        val cfg = checkNotNull(config)
        GlobalDataStore.awaitClientReady()
        val use24Hour =
            withContext(Dispatchers.IO) {
                context.applicationContext
                    .getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(SettingsKeys.KEY_USE_24_HOUR, false)
            }
        val globalData =
            when (val globalResult = withContext(Dispatchers.IO) { GlobalDataStore.getOrLoad() }) {
                is ApiResult.Ok -> globalResult.data
                is ApiResult.Error -> {
                    when (
                        val retry =
                            withContext(Dispatchers.IO) {
                                GlobalDataStore.getOrLoad(forceRefresh = true)
                            }
                    ) {
                        is ApiResult.Ok -> retry.data
                        is ApiResult.Error -> {
                            provideContent {
                                StationBoardContent.LoadError(context = context, config = cfg)
                            }
                            return
                        }
                    }
                }
            }

        val result =
            withContext(Dispatchers.IO) {
                useCase.getDepartures(
                    globalData = globalData,
                    stopId = cfg.stopId,
                    routeFilter = cfg.routeId,
                    now = EasternTimeInstant.now(),
                    limit = 5,
                )
            }

        when (result) {
            is ApiResult.Error -> {
                provideContent { StationBoardContent.LoadError(context = context, config = cfg) }
            }
            is ApiResult.Ok -> {
                val departures = result.data.departures
                val stationTitle =
                    cfg.stopLabel.ifEmpty {
                        globalData.getStop(cfg.stopId)?.resolveParent(globalData.stops)?.name.orEmpty()
                    }
                provideContent {
                    StationBoardContent.Board(
                        context = context,
                        stationTitle = stationTitle,
                        departures = departures,
                        use24Hour = use24Hour,
                    )
                }
            }
        }
    }
}

private object StationBoardContent {

    private data class BoardTypography(
        val title: TextUnit,
        val subtitle: TextUnit,
        val route: TextUnit,
        val headsign: TextUnit,
        val time: TextUnit,
        val caption: TextUnit,
        val padding: Dp,
        val routeBoxPaddingH: Dp,
        val routeBoxPaddingV: Dp,
        val routeCorner: Dp,
        val gapSmall: Dp,
        val gapMedium: Dp,
        val stackTime: Boolean,
    )

    @Composable
    private fun typography(): BoardTypography {
        val size = LocalSize.current
        val w = size.width.value.coerceAtLeast(1f)
        val h = size.height.value.coerceAtLeast(1f)
        val shortEdge = minOf(w, h)
        val scale = (shortEdge / 112f).coerceIn(0.55f, 2.2f)
        val stackTime = w < 260f
        val padding = (12f * scale).coerceIn(6f, 20f).dp
        val title = (15f * scale).coerceIn(12f, 22f).sp
        val subtitle = (11f * scale).coerceIn(9f, 16f).sp
        val route = (11f * scale).coerceIn(9f, 16f).sp
        val headsign = (12f * scale).coerceIn(10f, 17f).sp
        val time = (13f * scale).coerceIn(11f, 18f).sp
        val caption = (11f * scale).coerceIn(9f, 15f).sp
        val gapSmall = (4f * scale).coerceIn(3f, 10f).dp
        val gapMedium = (8f * scale).coerceIn(4f, 14f).dp
        val routeBoxH = (6f * scale).coerceIn(4f, 12f).dp
        val routeBoxV = (4f * scale).coerceIn(3f, 10f).dp
        val routeCorner = (8f * scale).coerceIn(6f, 14f).dp
        return BoardTypography(
            title = title,
            subtitle = subtitle,
            route = route,
            headsign = headsign,
            time = time,
            caption = caption,
            padding = padding,
            routeBoxPaddingH = routeBoxH,
            routeBoxPaddingV = routeBoxV,
            routeCorner = routeCorner,
            gapSmall = gapSmall,
            gapMedium = gapMedium,
            stackTime = stackTime,
        )
    }

    private fun rowCountForHeight(heightDp: Float): Int =
        if (heightDp >= 180f) 5 else 3

    /**
     * Keeps route pills readable inside a width-capped chip: shortens common commuter-rail patterns
     * (e.g. "Framingham / Worcester Line" → "Framingham/Worcester") while leaving names like "Red Line" unchanged.
     */
    private fun routeLabelForWidget(label: String): String {
        var s = label.trim().replace(" / ", "/")
        if (s.contains("/") && s.endsWith(" Line", ignoreCase = true)) {
            s = s.removeSuffix(" Line").trimEnd()
        }
        return s
    }

    @Composable
    fun ConfigurePrompt(context: Context, appWidgetId: Int) {
        val primaryColor =
            Color(ContextCompat.getColor(context, R.color.key).toLong() and 0xFFFFFFFFL)
        val deemphasizedColor =
            Color(ContextCompat.getColor(context, R.color.deemphasized).toLong() and 0xFFFFFFFFL)
        val bgColor = Color(ContextCompat.getColor(context, R.color.fill2).toLong() and 0xFFFFFFFFL)
        GlanceTheme {
            val t = typography()
            Column(
                modifier =
                    GlanceModifier.fillMaxWidth()
                        .background(bgColor)
                        .padding(t.padding)
                        .clickable(
                            androidx.glance.appwidget.action.actionStartActivity(
                                Intent(context, StationBoardWidgetConfigActivity::class.java).apply {
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                },
                                actionParametersOf(),
                            ),
                        ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = context.getString(R.string.widget_station_board_configure_hint),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(deemphasizedColor),
                            fontSize = t.caption,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 4,
                )
                Spacer(modifier = GlanceModifier.height(t.gapMedium))
                Text(
                    text = context.getString(R.string.widget_configure),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(primaryColor),
                            fontSize = t.title,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 2,
                )
            }
        }
    }

    @Composable
    fun ErrorState(context: Context) {
        val primaryColor =
            Color(ContextCompat.getColor(context, R.color.key).toLong() and 0xFFFFFFFFL)
        val deemphasizedColor =
            Color(ContextCompat.getColor(context, R.color.deemphasized).toLong() and 0xFFFFFFFFL)
        val bgColor = Color(ContextCompat.getColor(context, R.color.fill2).toLong() and 0xFFFFFFFFL)
        GlanceTheme {
            val t = typography()
            Column(
                modifier =
                    GlanceModifier.fillMaxWidth()
                        .background(bgColor)
                        .padding(t.padding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = context.getString(R.string.widget_unable_to_load),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(deemphasizedColor),
                            fontSize = t.title,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 4,
                )
                Spacer(modifier = GlanceModifier.height(t.gapMedium))
                Text(
                    text = context.getString(R.string.widget_tap_to_refresh),
                    modifier =
                        GlanceModifier.fillMaxWidth()
                            .clickable(actionRunCallback<WidgetRefreshActionCallback>(actionParametersOf())),
                    style =
                        TextStyle(
                            color = ColorProvider(primaryColor),
                            fontSize = t.title,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 1,
                )
            }
        }
    }

    @Composable
    fun LoadError(context: Context, config: WidgetStationBoardConfig) {
        val stopLabel = config.stopLabel.ifEmpty { context.getString(R.string.widget_station_board_selecting_stop) }
        val primaryColor =
            Color(ContextCompat.getColor(context, R.color.key).toLong() and 0xFFFFFFFFL)
        val deemphasizedColor =
            Color(ContextCompat.getColor(context, R.color.deemphasized).toLong() and 0xFFFFFFFFL)
        val bgColor = Color(ContextCompat.getColor(context, R.color.fill2).toLong() and 0xFFFFFFFFL)
        GlanceTheme {
            val t = typography()
            Column(
                modifier =
                    GlanceModifier.fillMaxWidth()
                        .background(bgColor)
                        .padding(t.padding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stopLabel,
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(primaryColor),
                            fontSize = t.title,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 3,
                )
                Spacer(modifier = GlanceModifier.height(t.gapMedium))
                Text(
                    text = context.getString(R.string.widget_station_board_times_error),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(deemphasizedColor),
                            fontSize = t.caption,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 6,
                )
                Spacer(modifier = GlanceModifier.height(t.gapMedium))
                Text(
                    text = context.getString(R.string.widget_tap_to_refresh),
                    modifier =
                        GlanceModifier.fillMaxWidth()
                            .clickable(actionRunCallback<WidgetRefreshActionCallback>(actionParametersOf())),
                    style =
                        TextStyle(
                            color = ColorProvider(primaryColor),
                            fontSize = t.title,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 1,
                )
            }
        }
    }

    @Composable
    fun NoDepartures(context: Context, stationTitle: String) {
        val stopLabel = stationTitle.ifEmpty { context.getString(R.string.widget_station_board_selecting_stop) }
        val primaryColor =
            Color(ContextCompat.getColor(context, R.color.key).toLong() and 0xFFFFFFFFL)
        val deemphasizedColor =
            Color(ContextCompat.getColor(context, R.color.deemphasized).toLong() and 0xFFFFFFFFL)
        val bgColor = Color(ContextCompat.getColor(context, R.color.fill2).toLong() and 0xFFFFFFFFL)
        GlanceTheme {
            val t = typography()
            Column(
                modifier =
                    GlanceModifier.fillMaxWidth()
                        .background(bgColor)
                        .padding(t.padding)
                        .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stopLabel,
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(primaryColor),
                            fontSize = t.title,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 3,
                )
                Spacer(modifier = GlanceModifier.height(t.gapSmall))
                Text(
                    text = context.getString(R.string.widget_station_board_scheduled_subtitle),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(deemphasizedColor),
                            fontSize = t.subtitle,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 2,
                )
                Spacer(modifier = GlanceModifier.height(t.gapMedium))
                Text(
                    text = context.getString(R.string.widget_station_board_no_departures),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(deemphasizedColor),
                            fontSize = t.caption,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 4,
                )
            }
        }
    }

    @Composable
    fun Board(
        context: Context,
        stationTitle: String,
        departures: List<WidgetStationBoardDeparture>,
        use24Hour: Boolean,
    ) {
        val size = LocalSize.current
        val maxRows = rowCountForHeight(size.height.value)
        val shown = departures.take(maxRows)
        if (shown.isEmpty()) {
            NoDepartures(
                context = context,
                stationTitle =
                    stationTitle.ifEmpty {
                        context.getString(R.string.widget_station_board_selecting_stop)
                    },
            )
            return
        }

        val stopLabel =
            stationTitle.ifEmpty { context.getString(R.string.widget_station_board_selecting_stop) }
        val primaryColor =
            Color(ContextCompat.getColor(context, R.color.key).toLong() and 0xFFFFFFFFL)
        val deemphasizedColor =
            Color(ContextCompat.getColor(context, R.color.deemphasized).toLong() and 0xFFFFFFFFL)
        val bgColor = Color(ContextCompat.getColor(context, R.color.fill2).toLong() and 0xFFFFFFFFL)
        val defaultColor =
            Color(ContextCompat.getColor(context, R.color.key).toLong() and 0xFFFFFFFFL)

        GlanceTheme {
            val t = typography()
            Column(
                modifier =
                    GlanceModifier.fillMaxWidth()
                        .background(bgColor)
                        .padding(t.padding)
                        .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.Top,
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = stopLabel,
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(primaryColor),
                            fontSize = t.title,
                            textAlign = TextAlign.Start,
                        ),
                    maxLines = 2,
                )
                Spacer(modifier = GlanceModifier.height(t.gapSmall))
                Text(
                    text = context.getString(R.string.widget_station_board_scheduled_subtitle),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(deemphasizedColor),
                            fontSize = t.subtitle,
                            textAlign = TextAlign.Start,
                        ),
                    maxLines = 1,
                )
                Spacer(modifier = GlanceModifier.height(t.gapMedium))
                for (d in shown) {
                    DepartureRow(
                        context = context,
                        departure = d,
                        use24Hour = use24Hour,
                        defaultColor = defaultColor,
                        typography = t,
                    )
                    Spacer(modifier = GlanceModifier.height(t.gapMedium))
                }
            }
        }
    }

    @Composable
    private fun DepartureRow(
        context: Context,
        departure: WidgetStationBoardDeparture,
        use24Hour: Boolean,
        defaultColor: Color,
        typography: BoardTypography,
    ) {
        val routeColor =
            runCatching { colorFromHex(departure.route.color) }.getOrElse { defaultColor }
        val routeTextColor =
            runCatching { colorFromHex(departure.route.textColor) }.getOrElse { Color.White }
        val primaryColor =
            Color(ContextCompat.getColor(context, R.color.key).toLong() and 0xFFFFFFFFL)
        val deemphasizedColor =
            Color(ContextCompat.getColor(context, R.color.deemphasized).toLong() and 0xFFFFFFFFL)
        val t = typography

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    GlanceModifier
                        .defaultWeight()
                        .background(routeColor)
                        .cornerRadius(t.routeCorner)
                        .padding(horizontal = t.routeBoxPaddingH, vertical = t.routeBoxPaddingV),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = routeLabelForWidget(departure.route.label),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style =
                        TextStyle(
                            color = ColorProvider(routeTextColor),
                            fontSize = t.route,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 2,
                )
            }
            Spacer(modifier = GlanceModifier.width(t.gapMedium))
            if (t.stackTime) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = departure.headsign,
                        modifier = GlanceModifier.fillMaxWidth(),
                        style =
                            TextStyle(
                                color = ColorProvider(primaryColor),
                                fontSize = t.headsign,
                                textAlign = TextAlign.Start,
                            ),
                        maxLines = 3,
                    )
                    departure.platform?.let { plat ->
                        Spacer(modifier = GlanceModifier.height(t.gapSmall))
                        Text(
                            text = context.getString(R.string.widget_track_short, plat),
                            style =
                                TextStyle(
                                    color = ColorProvider(deemphasizedColor),
                                    fontSize = t.caption,
                                    textAlign = TextAlign.Start,
                                ),
                            maxLines = 1,
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(t.gapSmall))
                    Text(
                        text = departure.departureTime.formattedTime(use24Hour),
                        style =
                            TextStyle(
                                color = ColorProvider(primaryColor),
                                fontSize = t.time,
                                textAlign = TextAlign.Start,
                            ),
                        maxLines = 1,
                    )
                    Text(
                        text = context.getString(R.string.widget_in_minutes, departure.minutesUntil),
                        style =
                            TextStyle(
                                color = ColorProvider(deemphasizedColor),
                                fontSize = t.caption,
                                textAlign = TextAlign.Start,
                            ),
                        maxLines = 1,
                    )
                }
            } else {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = departure.headsign,
                            modifier = GlanceModifier.fillMaxWidth(),
                            style =
                                TextStyle(
                                    color = ColorProvider(primaryColor),
                                    fontSize = t.headsign,
                                    textAlign = TextAlign.Start,
                                ),
                            maxLines = 3,
                        )
                        departure.platform?.let { plat ->
                            Spacer(modifier = GlanceModifier.height(t.gapSmall))
                            Text(
                                text = context.getString(R.string.widget_track_short, plat),
                                modifier = GlanceModifier.fillMaxWidth(),
                                style =
                                    TextStyle(
                                        color = ColorProvider(deemphasizedColor),
                                        fontSize = t.caption,
                                        textAlign = TextAlign.Start,
                                    ),
                                maxLines = 1,
                            )
                        }
                    }
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = departure.departureTime.formattedTime(use24Hour),
                            modifier = GlanceModifier.fillMaxWidth(),
                            style =
                                TextStyle(
                                    color = ColorProvider(primaryColor),
                                    fontSize = t.time,
                                    textAlign = TextAlign.End,
                                ),
                            maxLines = 1,
                        )
                        Text(
                            text = context.getString(R.string.widget_in_minutes, departure.minutesUntil),
                            modifier = GlanceModifier.fillMaxWidth(),
                            style =
                                TextStyle(
                                    color = ColorProvider(deemphasizedColor),
                                    fontSize = t.caption,
                                    textAlign = TextAlign.End,
                                ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
