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

/** Formats minutes-from-midnight (0 … 24*60-1) for display using the chosen clock style. */
internal fun formatMinutesFromMidnight(minutes: Int, use24Hour: Boolean): String {
    val total = minutes.coerceIn(0, 24 * 60 - 1)
    val h = total / 60
    val m = total % 60
    return if (use24Hour) {
        "%d:%02d".format(h, m)
    } else {
        val am = h < 12
        val h12 =
            when (h) {
                0 -> 12
                in 1..12 -> h
                else -> h - 12
            }
        "%d:%02d %s".format(h12, m, if (am) "AM" else "PM")
    }
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
