package com.saarlabs.tminus

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Typed access to the app's settings.
 *
 * All writes use `apply()`: these are small values on the UI thread, and `commit()` blocks on an
 * fsync. Reads are cheap after the first one — [SharedPreferences] keeps the parsed file in memory.
 */
public class SettingsStore internal constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)

    public fun apiKey(): String? = prefs.getString(SettingsKeys.KEY_V3_API, null)?.takeIf { it.isNotBlank() }

    public fun use24HourTime(): Boolean = prefs.getBoolean(SettingsKeys.KEY_USE_24_HOUR, false)

    public fun themeMode(): String =
        prefs.getString(SettingsKeys.KEY_THEME_MODE, SettingsKeys.THEME_SYSTEM) ?: SettingsKeys.THEME_SYSTEM

    public fun dynamicColorEnabled(): Boolean =
        prefs.getBoolean(SettingsKeys.KEY_DYNAMIC_COLOR, true)

    public fun fontScalePercent(): Int =
        prefs.getInt(SettingsKeys.KEY_FONT_SCALE_PERCENT, SettingsKeys.FONT_SCALE_DEFAULT_PERCENT)
            .coerceIn(SettingsKeys.FONT_SCALE_MIN_PERCENT, SettingsKeys.FONT_SCALE_MAX_PERCENT)

    public fun setApiKey(value: String?) {
        prefs.edit().putString(SettingsKeys.KEY_V3_API, value?.takeIf { it.isNotBlank() }).apply()
    }

    public fun setUse24HourTime(value: Boolean) {
        prefs.edit().putBoolean(SettingsKeys.KEY_USE_24_HOUR, value).apply()
    }

    public fun setThemeMode(value: String) {
        prefs.edit().putString(SettingsKeys.KEY_THEME_MODE, value).apply()
    }

    public fun setDynamicColorEnabled(value: Boolean) {
        prefs.edit().putBoolean(SettingsKeys.KEY_DYNAMIC_COLOR, value).apply()
    }

    public fun setFontScalePercent(value: Int) {
        prefs.edit()
            .putInt(
                SettingsKeys.KEY_FONT_SCALE_PERCENT,
                value.coerceIn(
                    SettingsKeys.FONT_SCALE_MIN_PERCENT,
                    SettingsKeys.FONT_SCALE_MAX_PERCENT,
                ),
            )
            .apply()
    }

    /** Emits the current value of [key]'s derived state, then again on every change. */
    internal fun <T> observe(vararg keys: String, read: () -> T): Flow<T> =
        callbackFlow {
            val watched = keys.toSet()
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
                    if (changed == null || changed in watched) trySend(read())
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(read())
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
            .conflate()
            .distinctUntilChanged()
}
