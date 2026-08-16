package com.saarlabs.tminus.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.saarlabs.tminus.SettingsKeys
import com.saarlabs.tminus.util.EasternTimeInstant

/** Clock time only, e.g. "7:48 AM" or "07:48". */
internal fun EasternTimeInstant.formatClock(use24Hour: Boolean): String {
    val h = local.hour
    val m = local.minute
    return if (use24Hour) {
        "%02d:%02d".format(h, m)
    } else {
        val h12 = ((h + 11) % 12) + 1
        "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
    }
}

/**
 * Day plus clock time, e.g. "Mon, Aug 17 · 7:48 AM".
 *
 * Anything user-facing goes through this. Preview dialogs used to print `local.toString()`, which
 * renders the raw ISO form ("2026-08-17T07:48") and ignores the 12/24-hour preference the app asks
 * the user to set.
 */
internal fun EasternTimeInstant.formatDayAndClock(use24Hour: Boolean): String {
    val date = local.date
    val day = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    return "$day, $month ${date.dayOfMonth} · ${formatClock(use24Hour)}"
}

/**
 * Formats minutes-from-midnight for display using the chosen clock style.
 *
 * Values of 1440 and above are MBTA service-day times — the small hours of the next morning — and
 * are shown as such rather than being clamped back to 23:59.
 */
internal fun formatMinutesFromMidnight(minutes: Int, use24Hour: Boolean): String {
    val clamped = minutes.coerceIn(0, 27 * 60 - 1)
    val nextDay = clamped >= 24 * 60
    val total = clamped % (24 * 60)
    val h = total / 60
    val m = total % 60
    val clock = formatClockParts(h, m, use24Hour)
    return if (nextDay) "$clock (next day)" else clock
}

private fun formatClockParts(hour: Int, minute: Int, use24Hour: Boolean): String =
    if (use24Hour) {
        "%d:%02d".format(hour, minute)
    } else {
        val h12 = ((hour + 11) % 12) + 1
        "%d:%02d %s".format(h12, minute, if (hour < 12) "AM" else "PM")
    }

@Composable
internal fun rememberUse24HourTime(): Boolean {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE) }
    var use24 by remember { mutableStateOf(prefs.getBoolean(SettingsKeys.KEY_USE_24_HOUR, false)) }
    DisposableEffect(prefs) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
                if (key == null || key == SettingsKeys.KEY_USE_24_HOUR) {
                    use24 = shared.getBoolean(SettingsKeys.KEY_USE_24_HOUR, false)
                }
            }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return use24
}
