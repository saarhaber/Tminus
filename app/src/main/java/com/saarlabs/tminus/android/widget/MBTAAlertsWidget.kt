package com.saarlabs.tminus.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.appwidget.AppWidgetManager
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.saarlabs.tminus.GlobalDataStore
import com.saarlabs.tminus.MainActivity
import com.saarlabs.tminus.R
import com.saarlabs.tminus.TminusApplication
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.model.response.MbtaAlertSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Config-free widget listing active service alerts across the rapid-transit (subway) lines. Reuses
 * [com.saarlabs.tminus.network.MbtaV3Client.fetchAlertsForRoute] with a comma-separated route filter
 * so a single request covers every subway line.
 */
public class MBTAAlertsWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        try {
            provideGlanceInternal(context, id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            android.util.Log.e("MBTAAlertsWidget", "provideGlance failed", e)
            provideContent {
                AlertsContent.Board(context, error = true, alerts = emptyList())
            }
        }
    }

    private suspend fun provideGlanceInternal(context: Context, id: GlanceId) {
        GlobalDataStore.awaitClientReady()
        if (!GlobalDataStore.isClientReady()) {
            provideContent { AlertsContent.Board(context, error = true, alerts = emptyList()) }
            return
        }
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config =
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    WidgetPreferences(context.applicationContext).getAlertsConfigOnce(appWidgetId)
                }
            }
        val routeFilter = AlertsWidgetLines.filterFor(config?.lineGroupIds ?: emptyList())
        val result =
            withContext(Dispatchers.IO) {
                GlobalDataStore.client.fetchAlertsForRoute(routeFilter)
            }
        when (result) {
            is ApiResult.Error ->
                provideContent { AlertsContent.Board(context, error = true, alerts = emptyList()) }
            is ApiResult.Ok -> {
                val alerts = result.data.distinctBy { it.id }.take(MAX_ALERTS)
                provideContent { AlertsContent.Board(context, error = false, alerts = alerts) }
            }
        }
    }

    private companion object {
        const val MAX_ALERTS = 8
    }
}

private object AlertsContent {

    private fun color(context: Context, resId: Int): Color =
        Color(ContextCompat.getColor(context, resId).toLong() and 0xFFFFFFFFL)

    private fun prettyEffect(effect: String?): String? =
        effect
            ?.takeIf { it.isNotBlank() }
            ?.split('_')
            ?.joinToString(" ") { part ->
                part.lowercase().replaceFirstChar { it.uppercase() }
            }

    @Composable
    fun Board(context: Context, error: Boolean, alerts: List<MbtaAlertSummary>) {
        GlanceTheme {
            Column(
                modifier =
                    GlanceModifier.fillMaxSize()
                        .background(color(context, R.color.widget_surface))
                        .cornerRadius(24.dp),
            ) {
                Column(
                    modifier =
                        GlanceModifier.fillMaxWidth()
                            .background(color(context, R.color.widget_header))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(actionStartActivity<MainActivity>()),
                ) {
                    Text(
                        text = context.getString(R.string.widget_alerts_label),
                        style =
                            TextStyle(
                                color = ColorProvider(color(context, R.color.widget_on_header)),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        maxLines = 1,
                    )
                    Text(
                        text = context.getString(R.string.widget_alerts_subtitle),
                        style =
                            TextStyle(
                                color = ColorProvider(color(context, R.color.widget_on_header)),
                                fontSize = 10.sp,
                            ),
                        maxLines = 1,
                    )
                }

                if (error) {
                    Message(context, context.getString(R.string.widget_alerts_error))
                } else if (alerts.isEmpty()) {
                    Message(context, context.getString(R.string.widget_alerts_none))
                } else {
                    LazyColumn(
                        modifier =
                            GlanceModifier.fillMaxSize()
                                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                    ) {
                        items(alerts.size) { index ->
                            Column {
                                if (index > 0) Spacer(modifier = GlanceModifier.height(4.dp))
                                AlertRow(context, alerts[index])
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Message(context: Context, text: String) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = text,
                modifier = GlanceModifier.fillMaxWidth(),
                style =
                    TextStyle(
                        color = ColorProvider(color(context, R.color.widget_on_surface_variant)),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    ),
                maxLines = 5,
            )
        }
    }

    @Composable
    private fun AlertRow(context: Context, alert: MbtaAlertSummary) {
        val accent = color(context, R.color.widget_accent)
        Row(
            modifier =
                GlanceModifier.fillMaxWidth()
                    .background(color(context, R.color.widget_row_bg))
                    .cornerRadius(14.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                prettyEffect(alert.effect)?.let { effect ->
                    Text(
                        text = effect,
                        style =
                            TextStyle(
                                color = ColorProvider(accent),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        maxLines = 1,
                    )
                }
                Text(
                    text = alert.header,
                    style =
                        TextStyle(
                            color = ColorProvider(color(context, R.color.widget_on_surface)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    maxLines = 3,
                )
            }
        }
    }
}
