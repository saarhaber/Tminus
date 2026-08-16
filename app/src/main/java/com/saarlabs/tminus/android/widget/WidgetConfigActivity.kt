package com.saarlabs.tminus.android.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.saarlabs.tminus.model.WidgetTripConfig
import com.saarlabs.tminus.ui.StopPairPicker
import com.saarlabs.tminus.ui.TminusEdgeToEdge
import com.saarlabs.tminus.ui.theme.TminusTheme
import com.saarlabs.tminus.ui.theme.rememberUserDarkTheme

/** Configures a trip widget: pick an origin and a destination. */
public class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = WidgetConfigStore(applicationContext)
        val appWidgetId =
            intent
                ?.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                )
                ?.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }
                ?: store.takePendingTripConfigWidgetId()

        // Until the user confirms, the launcher must treat this as cancelled — otherwise backing
        // out leaves an unconfigured widget on the home screen.
        setResult(RESULT_CANCELED)

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            val darkTheme = rememberUserDarkTheme()
            TminusEdgeToEdge(darkTheme)
            TminusTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    TripWidgetConfigScreen(
                        onStopsChosen = { from, to ->
                            store.setTripConfig(
                                appWidgetId,
                                WidgetTripConfig(
                                    fromStopId = from.id,
                                    toStopId = to.id,
                                    fromLabel = from.name,
                                    toLabel = to.name,
                                ),
                            )
                            finishConfigured(appWidgetId)
                        },
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    /**
     * Reports success to the launcher and nudges the widget.
     *
     * The nudge is belt-and-braces: the widget collects its configuration as a flow, so a live
     * Glance session already picked the save up. This covers the case where no session is running.
     */
    private fun finishConfigured(appWidgetId: Int) {
        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        WidgetUpdateWorker.enqueueRefresh(applicationContext, intArrayOf(appWidgetId))
        finish()
    }
}

@Composable
private fun TripWidgetConfigScreen(
    onStopsChosen: (com.saarlabs.tminus.model.Stop, com.saarlabs.tminus.model.Stop) -> Unit,
    onCancel: () -> Unit,
) {
    StopPairPicker(onStopsChosen = onStopsChosen, onCancel = onCancel)
}
