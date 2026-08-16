package com.saarlabs.tminus.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

/**
 * Applies edge-to-edge with system bar icons that match the **app's** theme.
 *
 * `enableEdgeToEdge()` with no arguments derives icon colour from the system's night mode. A user
 * who forces dark mode inside the app on a light-themed device therefore ended up with dark status
 * bar icons on a dark background — the clock was effectively invisible. Re-applying the style
 * whenever [darkTheme] changes keeps the bars readable in every combination.
 */
@Composable
internal fun TminusEdgeToEdge(darkTheme: Boolean) {
    val activity = LocalContext.current.findComponentActivity() ?: return
    SideEffect {
        val scrim = Color.Transparent.toArgb()
        val style =
            if (darkTheme) {
                SystemBarStyle.dark(scrim)
            } else {
                SystemBarStyle.light(scrim, DEFAULT_SCRIM_DARK)
            }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}

/** Fallback scrim for API levels that cannot render light navigation-bar icons. */
private val DEFAULT_SCRIM_DARK = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

private tailrec fun android.content.Context.findComponentActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is android.content.ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
