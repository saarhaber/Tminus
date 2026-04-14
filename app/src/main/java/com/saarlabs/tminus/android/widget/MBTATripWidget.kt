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
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
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
import com.saarlabs.tminus.model.WidgetTripConfig
import com.saarlabs.tminus.model.WidgetTripData
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.usecases.WidgetTripUseCase
import com.saarlabs.tminus.MainActivity
import com.saarlabs.tminus.R
import com.saarlabs.tminus.SettingsKeys
import com.saarlabs.tminus.TminusApplication
import com.saarlabs.tminus.GlobalDataStore
import com.saarlabs.tminus.android.util.formattedTime
import com.saarlabs.tminus.android.util.colorFromHex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

public class MBTATripWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    private val widgetTripUseCase: WidgetTripUseCase
        get() = TminusApplication.widgetTripUseCase

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        try {
            provideGlanceInternal(context, id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            android.util.Log.e("MBTATripWidget", "provideGlance failed", e)
            provideContent { WidgetContent.ErrorState(context = context) }
        }
    }

    private suspend fun provideGlanceInternal(context: Context, id: GlanceId) {
        val widgetPreferences = WidgetPreferences(context.applicationContext)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            provideContent { WidgetContent.ErrorState(context = context) }
            return
        }

        var config = withContext(Dispatchers.IO) { widgetPreferences.getConfigOnce(appWidgetId) }
        if (config == null) {
            repeat(8) {
                delay(250)
                config = withContext(Dispatchers.IO) { widgetPreferences.getConfigOnce(appWidgetId) }
                if (config != null) return@repeat
            }
        }
        if (config == null) {
            withContext(Dispatchers.IO) {
                WidgetPreferences(context.applicationContext).setPendingConfigWidgetId(appWidgetId)
            }
            provideContent {
                WidgetContent.ConfigurePrompt(context = context, appWidgetId = appWidgetId)
            }
            return
        }

        val cfg = checkNotNull(config)
        val use24Hour =
            withContext(Dispatchers.IO) {
                context.applicationContext
                    .getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)
                    .getBoolean(SettingsKeys.KEY_USE_24_HOUR, false)
            }
        val globalData =
            when (
                val globalResult = withContext(Dispatchers.IO) { GlobalDataStore.getOrLoad() }
            ) {
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
                                WidgetContent.LoadError(context = context, config = cfg)
                            }
                            return
                        }
                    }
                }
            }

        val result =
            withContext(Dispatchers.IO) {
                widgetTripUseCase.getNextTrip(
                    globalData = globalData,
                    fromStopId = cfg.fromStopId,
                    toStopId = cfg.toStopId,
                )
            }

        when (result) {
            is ApiResult.Error -> {
                provideContent { WidgetContent.LoadError(context = context, config = cfg) }
            }
            is ApiResult.Ok -> {
                val tripData = result.data.trip
                provideContent {
                    if (tripData != null) {
                        WidgetContent.TripData(
                            context = context,
                            config = cfg,
                            tripData = tripData,
                            use24Hour = use24Hour,
                        )
                    } else {
                        WidgetContent.NoTrips(context = context, config = cfg)
                    }
                }
            }
        }
    }
}

private object WidgetContent {

    private data class WidgetTypography(
        val route: TextUnit,
        val train: TextUnit,
        val body: TextUnit,
        val caption: TextUnit,
        val padding: Dp,
        val routeBoxPaddingH: Dp,
        val routeBoxPaddingV: Dp,
        val routeCorner: Dp,
        val gapSmall: Dp,
        val gapMedium: Dp,
        val stackRouteLine: Boolean,
        val stackTripLine: Boolean,
    )

    @Composable
    private fun typography(): WidgetTypography {
        val size = LocalSize.current
        val w = size.width.value.coerceAtLeast(1f)
        val h = size.height.value.coerceAtLeast(1f)
        val shortEdge = minOf(w, h)
        val scale = (shortEdge / 112f).coerceIn(0.55f, 2.2f)
        val stackTrip = w < 210f || h < 100f
        val stackRoute = w < 230f || h < 108f
        val padding = (12f * scale).coerceIn(6f, 20f).dp
        val route = (13f * scale).coerceIn(10f, 20f).sp
        val train = (12f * scale).coerceIn(9f, 18f).sp
        val body = (14f * scale).coerceIn(11f, 22f).sp
        val caption = (12f * scale).coerceIn(9f, 18f).sp
        val gapSmall = (4f * scale).coerceIn(3f, 10f).dp
        val gapMedium = (8f * scale).coerceIn(4f, 14f).dp
        val routeBoxH = (8f * scale).coerceIn(4f, 14f).dp
        val routeBoxV = (6f * scale).coerceIn(4f, 12f).dp
        val routeCorner = (20f * scale).coerceIn(12f, 28f).dp
        return WidgetTypography(
            route = route,
            train = train,
            body = body,
            caption = caption,
            padding = padding,
            routeBoxPaddingH = routeBoxH,
            routeBoxPaddingV = routeBoxV,
            routeCorner = routeCorner,
            gapSmall = gapSmall,
            gapMedium = gapMedium,
            stackRouteLine = stackRoute,
            stackTripLine = stackTrip,
        )
    }

    private fun centeredStyle(color: Color, fontSize: TextUnit): TextStyle =
        TextStyle(
            color = ColorProvider(color),
            fontSize = fontSize,
            textAlign = TextAlign.Center,
        )

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
                            onClick =
                                androidx.glance.appwidget.action.actionStartActivity(
                                    Intent(context, WidgetConfigActivity::class.java).apply {
                                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                    },
                                    androidx.glance.action.actionParametersOf(),
                                )
                        ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = context.getString(R.string.widget_configure),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style = centeredStyle(primaryColor, t.body),
                    maxLines = 4,
                )
                Spacer(modifier = GlanceModifier.height(t.gapMedium))
                Text(
                    text = context.getString(R.string.widget_set_from_to),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style = centeredStyle(deemphasizedColor, t.caption),
                    maxLines = 6,
                )
            }
        }
    }

    @Composable
    fun ErrorState(context: Context) {
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
                    text = context.getString(R.string.widget_unable_to_load),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style = centeredStyle(deemphasizedColor, t.body),
                    maxLines = 6,
                )
            }
        }
    }

    /** Shows the configured route when trip times or network data could not be loaded. */
    @Composable
    fun LoadError(context: Context, config: WidgetTripConfig) {
        val fromLabel = config.fromLabel.ifEmpty { context.getString(R.string.widget_from) }
        val toLabel = config.toLabel.ifEmpty { context.getString(R.string.widget_to) }
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
                    text = "$fromLabel → $toLabel",
                    modifier = GlanceModifier.fillMaxWidth(),
                    style = centeredStyle(primaryColor, t.body),
                    maxLines = 4,
                )
                Spacer(modifier = GlanceModifier.height(t.gapMedium))
                Text(
                    text = context.getString(R.string.widget_trip_times_error),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style = centeredStyle(deemphasizedColor, t.caption),
                    maxLines = 6,
                )
            }
        }
    }

    @Composable
    fun NoTrips(context: Context, config: WidgetTripConfig) {
        val fromLabel = config.fromLabel.ifEmpty { context.getString(R.string.widget_from) }
        val toLabel = config.toLabel.ifEmpty { context.getString(R.string.widget_to) }
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
                    text = "$fromLabel → $toLabel",
                    modifier = GlanceModifier.fillMaxWidth(),
                    style = centeredStyle(primaryColor, t.body),
                    maxLines = 4,
                )
                Spacer(modifier = GlanceModifier.height(t.gapMedium))
                Text(
                    text = context.getString(R.string.widget_no_trips),
                    modifier = GlanceModifier.fillMaxWidth(),
                    style = centeredStyle(deemphasizedColor, t.caption),
                    maxLines = 6,
                )
            }
        }
    }

    @Composable
    fun TripData(
        context: Context,
        config: WidgetTripConfig,
        tripData: WidgetTripData,
        use24Hour: Boolean,
    ) {
        val fromLabel = config.fromLabel.ifEmpty { tripData.fromStop.name }
        val toLabel = config.toLabel.ifEmpty { tripData.toStop.name }
        val defaultColor =
            Color(ContextCompat.getColor(context, R.color.key).toLong() and 0xFFFFFFFFL)
        val routeColor =
            runCatching { colorFromHex(tripData.route.color) }.getOrElse { defaultColor }
        val routeTextColor =
            runCatching { colorFromHex(tripData.route.textColor) }.getOrElse { Color.White }
        val trainLabel =
            tripData.headsign?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.widget_train, tripData.tripId)
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
                Box(
                    modifier =
                        GlanceModifier.fillMaxWidth()
                            .padding(vertical = t.gapSmall)
                            .background(routeColor)
                            .cornerRadius(t.routeCorner)
                            .padding(horizontal = t.routeBoxPaddingH, vertical = t.routeBoxPaddingV),
                    contentAlignment = Alignment.Center,
                ) {
                    if (t.stackRouteLine) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = GlanceModifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = tripData.route.label,
                                modifier = GlanceModifier.fillMaxWidth(),
                                style =
                                    TextStyle(
                                        color = ColorProvider(routeTextColor),
                                        fontSize = t.route,
                                        textAlign = TextAlign.Center,
                                    ),
                                maxLines = 2,
                            )
                            Spacer(modifier = GlanceModifier.height(t.gapSmall))
                            Text(
                                text = trainLabel,
                                modifier = GlanceModifier.fillMaxWidth(),
                                style =
                                    TextStyle(
                                        color = ColorProvider(routeTextColor),
                                        fontSize = t.train,
                                        textAlign = TextAlign.Center,
                                    ),
                                maxLines = 3,
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = GlanceModifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = tripData.route.label,
                                modifier = GlanceModifier.defaultWeight(),
                                style =
                                    TextStyle(
                                        color = ColorProvider(routeTextColor),
                                        fontSize = t.route,
                                        textAlign = TextAlign.Center,
                                    ),
                                maxLines = 2,
                            )
                            Spacer(modifier = GlanceModifier.width(t.gapMedium))
                            Text(
                                text = trainLabel,
                                modifier = GlanceModifier.defaultWeight(),
                                style =
                                    TextStyle(
                                        color = ColorProvider(routeTextColor),
                                        fontSize = t.train,
                                        textAlign = TextAlign.Center,
                                    ),
                                maxLines = 3,
                            )
                        }
                    }
                }
                if (t.stackTripLine) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = GlanceModifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "$fromLabel → $toLabel",
                            modifier = GlanceModifier.fillMaxWidth(),
                            style = centeredStyle(primaryColor, t.body),
                            maxLines = 4,
                        )
                        Spacer(modifier = GlanceModifier.height(t.gapSmall))
                        Text(
                            text = context.getString(R.string.widget_in_minutes, tripData.minutesUntil),
                            modifier = GlanceModifier.fillMaxWidth(),
                            style = centeredStyle(primaryColor, t.body),
                            maxLines = 2,
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = GlanceModifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "$fromLabel → $toLabel",
                            modifier = GlanceModifier.defaultWeight(),
                            style =
                                TextStyle(
                                    color = ColorProvider(primaryColor),
                                    fontSize = t.body,
                                    textAlign = TextAlign.Center,
                                ),
                            maxLines = 3,
                        )
                        Spacer(modifier = GlanceModifier.width(t.gapSmall))
                        Text(
                            text = context.getString(R.string.widget_in_minutes, tripData.minutesUntil),
                            modifier = GlanceModifier.defaultWeight(),
                            style =
                                TextStyle(
                                    color = ColorProvider(primaryColor),
                                    fontSize = t.body,
                                    textAlign = TextAlign.Center,
                                ),
                            maxLines = 2,
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.height(t.gapSmall))
                Text(
                    text =
                        "${tripData.departureTime.formattedTime(use24Hour)} → ${tripData.arrivalTime.formattedTime(use24Hour)}",
                    modifier = GlanceModifier.fillMaxWidth(),
                    style = centeredStyle(deemphasizedColor, t.caption),
                    maxLines = 2,
                )
                if (tripData.fromPlatform != null || tripData.toPlatform != null) {
                    Spacer(modifier = GlanceModifier.height(t.gapSmall))
                    val platformText = buildString {
                        tripData.fromPlatform?.let {
                            append(context.getString(R.string.widget_track_short, it))
                        }
                        if (tripData.fromPlatform != null && tripData.toPlatform != null)
                            append(" • ")
                        tripData.toPlatform?.let {
                            append(context.getString(R.string.widget_track_short, it))
                        }
                    }
                    if (platformText.isNotEmpty()) {
                        Text(
                            text = platformText,
                            modifier = GlanceModifier.fillMaxWidth(),
                            style = centeredStyle(deemphasizedColor, t.caption),
                            maxLines = 3,
                        )
                    }
                }
            }
        }
    }
}
