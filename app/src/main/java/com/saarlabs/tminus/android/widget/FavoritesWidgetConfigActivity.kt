package com.saarlabs.tminus.android.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saarlabs.tminus.R
import com.saarlabs.tminus.model.WidgetFavoritesConfig
import com.saarlabs.tminus.ui.theme.TminusTheme
import com.saarlabs.tminus.ui.theme.rememberUserDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

public class FavoritesWidgetConfigActivity : ComponentActivity() {

    private val widgetPreferences: WidgetPreferences by lazy {
        WidgetPreferences(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)

        val appWidgetId =
            intent?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            TminusTheme(darkTheme = rememberUserDarkTheme()) {
                FavoritesWidgetConfigScreen(
                    appWidgetId = appWidgetId,
                    widgetPreferences = widgetPreferences,
                    onComplete = {
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                        )
                        finish()
                    },
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesWidgetConfigScreen(
    appWidgetId: Int,
    widgetPreferences: WidgetPreferences,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var count by remember { mutableIntStateOf(5) }
    var sortBySoonest by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_favorites_configure_title)) },
                actions = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier.padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.widget_favorites_count_label),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in listOf(3, 5, 8)) {
                    FilterChip(
                        selected = count == option,
                        onClick = { count = option },
                        label = { Text(option.toString()) },
                    )
                }
            }
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .toggleable(value = sortBySoonest, onValueChange = { sortBySoonest = it })
                        .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.widget_favorites_sort_soonest),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = sortBySoonest, onCheckedChange = { sortBySoonest = it })
            }
            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                widgetPreferences.setFavoritesConfig(
                                    appWidgetId,
                                    WidgetFavoritesConfig(count = count, sortBySoonest = sortBySoonest),
                                )
                            }
                            val appContext = context.applicationContext
                            updateFavoritesWidgetWithRetry(appContext, appWidgetId)
                            WidgetUpdateWorker.enqueueRefresh(appContext, intArrayOf(appWidgetId))
                            onComplete()
                        } catch (e: Exception) {
                            android.util.Log.e("FavoritesWidgetConfig", "save failed", e)
                            Toast.makeText(
                                    context,
                                    context.getString(R.string.widget_save_error),
                                    Toast.LENGTH_LONG,
                                )
                                .show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            ) {
                Text(stringResource(R.string.widget_station_board_save))
            }
        }
    }
}
