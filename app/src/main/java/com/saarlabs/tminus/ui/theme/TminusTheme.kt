package com.saarlabs.tminus.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.saarlabs.tminus.SettingsKeys

/** Deep navy aligned with launcher canvas [ic_launcher_background] (#0B1426). */
private val BrandNavy = Color(0xFF0B1426)

/** Cool white aligned with surface; avoids Material light defaults (Neutral94 etc.) that read lavender. */
private val LightSurface = Color(0xFFFAFCFF)

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF1E3A5F),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD4E3F5),
        onPrimaryContainer = BrandNavy,
        secondary = Color(0xFF3D5A73),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFC8D8E8),
        onSecondaryContainer = BrandNavy,
        tertiary = Color(0xFF5C7A94),
        onTertiary = Color.White,
        surface = LightSurface,
        surfaceVariant = Color(0xFFE1E6EE),
        background = LightSurface,
        surfaceTint = Color.Transparent,
        surfaceDim = Color(0xFFE8EDF3),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF7F9FC),
        surfaceContainer = Color(0xFFF2F5F9),
        surfaceContainerHigh = Color(0xFFECF0F5),
        surfaceContainerHighest = Color(0xFFE5EAF0),
    )

/** Navy-aligned ramp; avoids Material defaults (e.g. Neutral12 #211F26) that read purple-grey. */
private val DarkSurface = Color(0xFF121820)

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF9BB9E8),
        onPrimary = BrandNavy,
        primaryContainer = Color(0xFF284060),
        onPrimaryContainer = Color(0xFFD4E3F5),
        secondary = Color(0xFFB0C8DC),
        onSecondary = BrandNavy,
        secondaryContainer = Color(0xFF3D5A73),
        onSecondaryContainer = Color(0xFFE1EEF8),
        tertiary = Color(0xFF9DB3C4),
        surface = DarkSurface,
        surfaceVariant = Color(0xFF2A3440),
        background = DarkSurface,
        surfaceTint = Color.Transparent,
        surfaceDim = Color(0xFF0E141C),
        surfaceBright = Color(0xFF1E2833),
        surfaceContainerLowest = Color(0xFF0A1018),
        surfaceContainerLow = Color(0xFF141C26),
        surfaceContainer = Color(0xFF1A222C),
        surfaceContainerHigh = Color(0xFF202830),
        surfaceContainerHighest = Color(0xFF2A323D),
    )

@Composable
public fun TminusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content,
    )
}

/**
 * Resolves whether to use the dark palette from [SettingsKeys.KEY_THEME_MODE], and recomposes when
 * that preference changes (e.g. from Settings).
 */
@Composable
public fun rememberUserDarkTheme(): Boolean {
    val context = LocalContext.current
    val prefs =
        remember(context) {
            context.getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)
        }
    var mode by remember {
        mutableStateOf(
            prefs.getString(SettingsKeys.KEY_THEME_MODE, SettingsKeys.THEME_SYSTEM)
                ?: SettingsKeys.THEME_SYSTEM,
        )
    }
    DisposableEffect(prefs) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
                if (key == SettingsKeys.KEY_THEME_MODE) {
                    mode =
                        shared.getString(SettingsKeys.KEY_THEME_MODE, SettingsKeys.THEME_SYSTEM)
                            ?: SettingsKeys.THEME_SYSTEM
                }
            }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val isSystemDark = isSystemInDarkTheme()
    return when (mode) {
        SettingsKeys.THEME_LIGHT -> false
        SettingsKeys.THEME_DARK -> true
        else -> isSystemDark
    }
}
