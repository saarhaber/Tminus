package com.saarlabs.tminus.android.widget

import android.content.Context
import com.saarlabs.tminus.R
import com.saarlabs.tminus.model.WidgetTripData

/**
 * Track labels, shared by every surface that can show one.
 *
 * MBTA only publishes `platform_code` for commuter rail, so these return null for subway, bus and
 * ferry — which is why every call site has to cope with "no track" rather than reserving space for
 * one.
 */

/** Separates the origin and destination tracks on the trip widget. */
private const val TRACK_SEPARATOR = " • "

/**
 * The non-blank track codes among [values], in order, without repeating one.
 *
 * De-duplicating matters for the trip widget: a train that leaves track 2 and arrives on track 2
 * would otherwise read "Track 2 • Track 2", which looks like a rendering bug rather than a fact.
 */
internal fun trackCodes(vararg values: String?): List<String> =
    values.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct()

/** "Track 2" / "Track 2 • Track 5", or null when neither end of the trip has a track. */
internal fun tripTrackText(context: Context, tripData: WidgetTripData): String? =
    trackCodes(tripData.fromPlatform, tripData.toPlatform)
        .map { context.getString(R.string.widget_track_short, it) }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(TRACK_SEPARATOR)

/** "Track 2" for a single departure, or null when it has no track. */
internal fun departureTrackText(context: Context, platform: String?): String? =
    trackCodes(platform).firstOrNull()?.let { context.getString(R.string.widget_track_short, it) }

/** "7:45 AM" or "7:45 AM · Track 2" — the schedule line used by the boards. */
internal fun clockWithTrack(context: Context, clock: String, platform: String?): String =
    departureTrackText(context, platform)?.let { track ->
        context.getString(R.string.widget_clock_with_track, clock, track)
    } ?: clock
