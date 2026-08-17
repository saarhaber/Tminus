package com.saarlabs.tminus.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.saarlabs.tminus.R
import com.saarlabs.tminus.android.util.formattedTime
import com.saarlabs.tminus.model.WidgetTripData
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Layouts for the subway / bus / ferry trip widget — everything that is not commuter rail, which
 * has its own station-clock styling in [TripWidgetCommuterLayouts].
 *
 * The three variants exist because the widget is freely resizable: a 4x1 strip cannot show a 44sp
 * countdown, and a 5x4 block looks empty if it only shows what fits in a strip.
 */
internal object TripWidgetRapidTransitLayouts {

    internal enum class TripLayout {
        /** Countdown block beside the stop pair, with a time strip along the bottom. */
        LARGE,

        /** Narrow: countdown centred above the stop pair. */
        COMPACT,

        /** Short: one row — route rail, stop pair, countdown. */
        INLINE,
    }

    internal fun selectLayout(widthDp: Float, heightDp: Float): TripLayout = when {
        heightDp < 130f -> TripLayout.INLINE
        widthDp < 210f -> TripLayout.COMPACT
        else -> TripLayout.LARGE
    }

    internal data class Metrics(
        val routeLabel: TextUnit,
        val headsign: TextUnit,
        val minutes: TextUnit,
        val minutesUnit: TextUnit,
        /** "now" replaces the digits, so it needs its own size — the digit size dwarfs a word. */
        val nowLabel: TextUnit,
        val originStation: TextUnit,
        val destinationStation: TextUnit,
        val times: TextUnit,
        val caption: TextUnit,
        val padding: Dp,
        val headerPaddingV: Dp,
        val gapXs: Dp,
        val gapSm: Dp,
        val gapMd: Dp,
        val railWidth: Dp,
        /** The bottom strip is the first thing dropped when the widget is too short for it. */
        val showTimeStrip: Boolean,
        val showPlatforms: Boolean,
        /** Wide widgets put the countdown next to the stops; squarer ones stack them. */
        val countdownBesideStops: Boolean,
    )

    @Composable
    internal fun metrics(fontScale: Float): Metrics {
        val size = LocalSize.current
        val w = size.width.value.coerceAtLeast(1f)
        val h = size.height.value.coerceAtLeast(1f)
        val shortEdge = min(w, h)
        val layout = selectLayout(w, h)
        val refShort = when (layout) {
            TripLayout.LARGE -> 150f
            TripLayout.COMPACT -> 130f
            TripLayout.INLINE -> 72f
        }
        val refHeight = when (layout) {
            TripLayout.LARGE -> 190f
            TripLayout.COMPACT -> 150f
            TripLayout.INLINE -> 60f
        }
        val edgeScale = (shortEdge / refShort).coerceIn(0.5f, 2.4f)
        val heightScale = (h / refHeight).coerceIn(0.6f, 1.35f)
        // Geometric mean, not the product: multiplying both scales compounds into type so large
        // that a 5x4 widget has to shrink everything again to fit its own body.
        val viewportScale = sqrt(edgeScale * heightScale)
        // Same damping as the other widgets: keep the countdown glyph big without letting a 1.6x
        // system font scale push the stop names into truncation.
        val scale = viewportScale * (0.6f + 0.4f * fontScale)
        val gapScalar = (shortEdge / refShort).coerceIn(0.5f, 2.2f)
        val routeLabel = (13f * scale).coerceIn(9f, 17f)
        val minutesUnit = (13f * scale).coerceIn(9f, 20f)
        val times = (12f * scale).coerceIn(9f, 16f)
        val headerPaddingV = (9f * viewportScale).coerceIn(5f, 16f)
        val gapSm = (5f * gapScalar).coerceIn(3f, 11f)
        val minutes = when (layout) {
            TripLayout.LARGE -> (44f * scale).coerceIn(24f, 64f)
            TripLayout.COMPACT -> (36f * scale).coerceIn(20f, 52f)
            TripLayout.INLINE -> (15f * scale).coerceIn(11f, 20f)
        }
        val originStation = (17f * scale).coerceIn(11f, 24f)
        val destinationStation = (15f * scale).coerceIn(10f, 21f)
        val countdownBesideStops = layout == TripLayout.LARGE && w >= h * 1.45f
        // Fit the body by priority: the countdown keeps its size, the time strip is dropped before
        // the countdown is allowed to shrink, and only then does the countdown give ground. Without
        // this the big glyph is clipped by whatever sits under it.
        val headerHeight = routeLabel * 1.45f + 2f * headerPaddingV
        val bodyHeight = (h - headerHeight - 2f * gapSm).coerceAtLeast(20f)
        val stripHeight = times * 1.5f + 3f * gapSm
        val stackedStopsHeight =
            if (countdownBesideStops) 0f else (originStation + destinationStation) * 1.35f + gapSm
        val countdownHeight = minutes * 1.35f
        val smallestCountdown = countdownHeight * 0.62f
        val showTimeStrip =
            layout != TripLayout.INLINE &&
                h >= 150f &&
                smallestCountdown + stackedStopsHeight + stripHeight <= bodyHeight
        val countdownSpace =
            bodyHeight - stackedStopsHeight - (if (showTimeStrip) stripHeight else 0f)
        val countdownFit =
            if (layout == TripLayout.INLINE || countdownHeight <= countdownSpace) {
                1f
            } else {
                (countdownSpace / countdownHeight).coerceIn(0.45f, 1f)
            }
        return Metrics(
            routeLabel = routeLabel.sp,
            headsign = (12f * scale).coerceIn(8f, 15f).sp,
            minutes = (minutes * countdownFit).sp,
            minutesUnit = (minutesUnit * countdownFit).coerceAtLeast(8f).sp,
            nowLabel = (minutes * countdownFit * 0.5f).coerceIn(13f, 34f).sp,
            originStation = originStation.sp,
            destinationStation = destinationStation.sp,
            times = times.sp,
            caption = (11f * scale).coerceIn(8f, 16f).sp,
            padding = (14f * viewportScale).coerceIn(8f, 22f).dp,
            headerPaddingV = headerPaddingV.dp,
            gapXs = (2f * gapScalar).coerceIn(1f, 5f).dp,
            gapSm = gapSm.dp,
            gapMd = (10f * gapScalar).coerceIn(6f, 18f).dp,
            railWidth = (4f * gapScalar).coerceIn(3f, 7f).dp,
            showTimeStrip = showTimeStrip,
            countdownBesideStops = countdownBesideStops,
            showPlatforms = h >= 190f && w >= 240f,
        )
    }

    @Composable
    internal fun RapidTransitTrip(
        context: Context,
        tripData: WidgetTripData,
        fromLabel: String,
        toLabel: String,
        routeColor: Color,
        routeTextColor: Color,
        onSurface: Color,
        onSurfaceVariant: Color,
        rowBackground: Color,
        use24Hour: Boolean,
        fontScale: Float,
        surfaceModifier: GlanceModifier,
    ) {
        val size = LocalSize.current
        val layout = selectLayout(size.width.value, size.height.value)
        val m = metrics(fontScale)
        val minutes = tripData.minutesUntil.coerceAtLeast(0)
        val headsign =
            tripData.headsign?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.widget_train, tripData.tripId)
        val departure = tripData.departureTime.formattedTime(use24Hour)
        val arrival = tripData.arrivalTime.formattedTime(use24Hour)

        when (layout) {
            TripLayout.LARGE ->
                LargeLayout(
                    context = context,
                    tripData = tripData,
                    fromLabel = fromLabel,
                    toLabel = toLabel,
                    headsign = headsign,
                    minutes = minutes,
                    departure = departure,
                    arrival = arrival,
                    routeColor = routeColor,
                    routeTextColor = routeTextColor,
                    onSurface = onSurface,
                    onSurfaceVariant = onSurfaceVariant,
                    rowBackground = rowBackground,
                    m = m,
                    surfaceModifier = surfaceModifier,
                )
            TripLayout.COMPACT ->
                CompactLayout(
                    context = context,
                    tripData = tripData,
                    fromLabel = fromLabel,
                    toLabel = toLabel,
                    minutes = minutes,
                    departure = departure,
                    routeColor = routeColor,
                    routeTextColor = routeTextColor,
                    onSurface = onSurface,
                    onSurfaceVariant = onSurfaceVariant,
                    m = m,
                    surfaceModifier = surfaceModifier,
                )
            TripLayout.INLINE ->
                InlineLayout(
                    context = context,
                    tripData = tripData,
                    fromLabel = fromLabel,
                    toLabel = toLabel,
                    minutes = minutes,
                    departure = departure,
                    routeColor = routeColor,
                    onSurface = onSurface,
                    onSurfaceVariant = onSurfaceVariant,
                    m = m,
                    surfaceModifier = surfaceModifier,
                )
        }
    }

    /**
     * Countdown and unit as one block, sitting on a shared bottom edge. The unit used to live on
     * the body's next text line, which left "min" stranded under the stop names as the widget grew.
     */
    @Composable
    private fun CountdownBlock(
        context: Context,
        minutes: Int,
        routeColor: Color,
        onSurfaceVariant: Color,
        m: Metrics,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text =
                    if (minutes <= 0) {
                        context.getString(R.string.widget_now)
                    } else {
                        minutes.toString()
                    },
                style =
                    TextStyle(
                        color = ColorProvider(routeColor),
                        fontSize = if (minutes <= 0) m.nowLabel else m.minutes,
                        fontWeight = FontWeight.Bold,
                    ),
                maxLines = 1,
            )
            if (minutes > 0) {
                Spacer(modifier = GlanceModifier.width(m.gapXs))
                Text(
                    text = context.getString(R.string.widget_min_unit),
                    // Nudge off the descender line so the unit reads as sitting on the digits.
                    modifier = GlanceModifier.padding(bottom = m.gapXs),
                    style =
                        TextStyle(
                            color = ColorProvider(onSurfaceVariant),
                            fontSize = m.minutesUnit,
                            fontWeight = FontWeight.Medium,
                        ),
                    maxLines = 1,
                )
            }
        }
    }

    @Composable
    private fun RouteHeader(
        tripData: WidgetTripData,
        headsign: String,
        routeColor: Color,
        routeTextColor: Color,
        m: Metrics,
        showHeadsign: Boolean,
    ) {
        Row(
            modifier =
                GlanceModifier.fillMaxWidth()
                    .background(routeColor)
                    .padding(horizontal = m.padding, vertical = m.headerPaddingV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tripData.route.label,
                modifier = GlanceModifier.defaultWeight(),
                style =
                    TextStyle(
                        color = ColorProvider(routeTextColor),
                        fontSize = m.routeLabel,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                    ),
                maxLines = 1,
            )
            if (showHeadsign) {
                Spacer(modifier = GlanceModifier.width(m.gapSm))
                Text(
                    text = headsign,
                    style =
                        TextStyle(
                            color = ColorProvider(routeTextColor.copy(alpha = 0.88f)),
                            fontSize = m.headsign,
                            textAlign = TextAlign.End,
                        ),
                    maxLines = 1,
                )
            }
        }
    }

    /** "Davis" over "→ Park Street" — the stop pair reads as one block beside the countdown. */
    @Composable
    private fun StopPair(
        context: Context,
        fromLabel: String,
        toLabel: String,
        onSurface: Color,
        onSurfaceVariant: Color,
        m: Metrics,
        modifier: GlanceModifier,
        center: Boolean,
    ) {
        val align = if (center) TextAlign.Center else TextAlign.Start
        Column(
            modifier = modifier,
            horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Text(
                text = fromLabel,
                modifier = GlanceModifier.fillMaxWidth(),
                style =
                    TextStyle(
                        color = ColorProvider(onSurface),
                        fontSize = m.originStation,
                        fontWeight = FontWeight.Bold,
                        textAlign = align,
                    ),
                maxLines = 2,
            )
            Spacer(modifier = GlanceModifier.height(m.gapXs))
            Text(
                text = context.getString(R.string.widget_trip_to_arrow, toLabel),
                modifier = GlanceModifier.fillMaxWidth(),
                style =
                    TextStyle(
                        color = ColorProvider(onSurfaceVariant),
                        fontSize = m.destinationStation,
                        fontWeight = FontWeight.Medium,
                        textAlign = align,
                    ),
                maxLines = 2,
            )
        }
    }

    @Composable
    private fun LargeLayout(
        context: Context,
        tripData: WidgetTripData,
        fromLabel: String,
        toLabel: String,
        headsign: String,
        minutes: Int,
        departure: String,
        arrival: String,
        routeColor: Color,
        routeTextColor: Color,
        onSurface: Color,
        onSurfaceVariant: Color,
        rowBackground: Color,
        m: Metrics,
        surfaceModifier: GlanceModifier,
    ) {
        Column(modifier = surfaceModifier) {
            RouteHeader(
                tripData = tripData,
                headsign = headsign,
                routeColor = routeColor,
                routeTextColor = routeTextColor,
                m = m,
                showHeadsign = true,
            )
            Box(
                modifier =
                    GlanceModifier.fillMaxWidth()
                        .defaultWeight()
                        .padding(horizontal = m.padding, vertical = m.gapSm),
                contentAlignment = Alignment.CenterStart,
            ) {
                // Side by side only when the widget is genuinely wide. Near a square the countdown
                // squeezes the stop column enough that "Park Street" wraps mid-name.
                if (m.countdownBesideStops) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CountdownBlock(
                            context = context,
                            minutes = minutes,
                            routeColor = routeColor,
                            onSurfaceVariant = onSurfaceVariant,
                            m = m,
                        )
                        Spacer(modifier = GlanceModifier.width(m.gapMd))
                        StopPair(
                            context = context,
                            fromLabel = fromLabel,
                            toLabel = toLabel,
                            onSurface = onSurface,
                            onSurfaceVariant = onSurfaceVariant,
                            m = m,
                            modifier = GlanceModifier.defaultWeight(),
                            center = false,
                        )
                    }
                } else {
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        CountdownBlock(
                            context = context,
                            minutes = minutes,
                            routeColor = routeColor,
                            onSurfaceVariant = onSurfaceVariant,
                            m = m,
                        )
                        Spacer(modifier = GlanceModifier.height(m.gapSm))
                        StopPair(
                            context = context,
                            fromLabel = fromLabel,
                            toLabel = toLabel,
                            onSurface = onSurface,
                            onSurfaceVariant = onSurfaceVariant,
                            m = m,
                            modifier = GlanceModifier.fillMaxWidth(),
                            center = false,
                        )
                    }
                }
            }
            if (m.showTimeStrip) {
                TimeStrip(
                    context = context,
                    tripData = tripData,
                    departure = departure,
                    arrival = arrival,
                    onSurface = onSurface,
                    onSurfaceVariant = onSurfaceVariant,
                    rowBackground = rowBackground,
                    m = m,
                )
            }
        }
    }

    @Composable
    private fun TimeStrip(
        context: Context,
        tripData: WidgetTripData,
        departure: String,
        arrival: String,
        onSurface: Color,
        onSurfaceVariant: Color,
        rowBackground: Color,
        m: Metrics,
    ) {
        val platforms = platformText(context, tripData)
        // Full-bleed footer band, mirroring the route band on top, so the times line up with the
        // stop names above instead of starting a few dp inside them.
        Row(
            modifier =
                GlanceModifier.fillMaxWidth()
                    .background(rowBackground)
                    .padding(horizontal = m.padding, vertical = m.gapSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = context.getString(R.string.widget_trip_time_range, departure, arrival),
                modifier = GlanceModifier.defaultWeight(),
                style =
                    TextStyle(
                        color = ColorProvider(onSurface),
                        fontSize = m.times,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Start,
                    ),
                maxLines = 1,
            )
            if (m.showPlatforms && platforms != null) {
                Spacer(modifier = GlanceModifier.width(m.gapSm))
                Text(
                    text = platforms,
                    style =
                        TextStyle(
                            color = ColorProvider(onSurfaceVariant),
                            fontSize = m.caption,
                            textAlign = TextAlign.End,
                        ),
                    maxLines = 1,
                )
            }
        }
    }

    @Composable
    private fun CompactLayout(
        context: Context,
        tripData: WidgetTripData,
        fromLabel: String,
        toLabel: String,
        minutes: Int,
        departure: String,
        routeColor: Color,
        routeTextColor: Color,
        onSurface: Color,
        onSurfaceVariant: Color,
        m: Metrics,
        surfaceModifier: GlanceModifier,
    ) {
        Column(modifier = surfaceModifier) {
            RouteHeader(
                tripData = tripData,
                headsign = "",
                routeColor = routeColor,
                routeTextColor = routeTextColor,
                m = m,
                showHeadsign = false,
            )
            Column(
                modifier =
                    GlanceModifier.fillMaxWidth()
                        .defaultWeight()
                        .padding(horizontal = m.gapMd, vertical = m.gapSm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CountdownBlock(
                    context = context,
                    minutes = minutes,
                    routeColor = routeColor,
                    onSurfaceVariant = onSurfaceVariant,
                    m = m,
                )
                Spacer(modifier = GlanceModifier.height(m.gapSm))
                StopPair(
                    context = context,
                    fromLabel = fromLabel,
                    toLabel = toLabel,
                    onSurface = onSurface,
                    onSurfaceVariant = onSurfaceVariant,
                    m = m,
                    modifier = GlanceModifier.fillMaxWidth(),
                    center = true,
                )
                if (m.showTimeStrip) {
                    Spacer(modifier = GlanceModifier.height(m.gapSm))
                    Text(
                        text = context.getString(R.string.widget_trip_departs_at, departure),
                        modifier = GlanceModifier.fillMaxWidth(),
                        style =
                            TextStyle(
                                color = ColorProvider(onSurfaceVariant),
                                fontSize = m.caption,
                                textAlign = TextAlign.Center,
                            ),
                        maxLines = 1,
                    )
                }
            }
        }
    }

    @Composable
    private fun InlineLayout(
        context: Context,
        tripData: WidgetTripData,
        fromLabel: String,
        toLabel: String,
        minutes: Int,
        departure: String,
        routeColor: Color,
        onSurface: Color,
        onSurfaceVariant: Color,
        m: Metrics,
        surfaceModifier: GlanceModifier,
    ) {
        Row(
            modifier = surfaceModifier.padding(horizontal = m.gapMd, vertical = m.gapSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    GlanceModifier.width(m.railWidth)
                        .fillMaxHeight()
                        .background(routeColor)
                        .cornerRadius(m.railWidth),
            ) {}
            Spacer(modifier = GlanceModifier.width(m.gapSm))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = tripData.route.label,
                    style =
                        TextStyle(
                            color = ColorProvider(onSurfaceVariant),
                            fontSize = m.caption,
                        ),
                    maxLines = 1,
                )
                Text(
                    text = context.getString(R.string.widget_trip_from_to, fromLabel, toLabel),
                    style =
                        TextStyle(
                            color = ColorProvider(onSurface),
                            fontSize = m.destinationStation,
                            fontWeight = FontWeight.Bold,
                        ),
                    maxLines = 1,
                )
            }
            Spacer(modifier = GlanceModifier.width(m.gapSm))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text =
                        if (minutes <= 0) {
                            context.getString(R.string.widget_now)
                        } else {
                            context.getString(R.string.widget_min_short, minutes)
                        },
                    style =
                        TextStyle(
                            color = ColorProvider(routeColor),
                            fontSize = m.minutes,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                        ),
                    maxLines = 1,
                )
                Text(
                    text = departure,
                    style =
                        TextStyle(
                            color = ColorProvider(onSurfaceVariant),
                            fontSize = m.caption,
                            textAlign = TextAlign.End,
                        ),
                    maxLines = 1,
                )
            }
        }
    }

    /** Null when the trip has no platform information at either end. */
    internal fun platformText(context: Context, tripData: WidgetTripData): String? {
        val from = tripData.fromPlatform?.takeIf { it.isNotBlank() }
        val to = tripData.toPlatform?.takeIf { it.isNotBlank() }
        val parts =
            listOfNotNull(from, to).map { context.getString(R.string.widget_track_short, it) }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
    }
}
