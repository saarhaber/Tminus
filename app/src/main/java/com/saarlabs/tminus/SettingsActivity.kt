package com.saarlabs.tminus

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.saarlabs.tminus.ui.theme.TminusTheme
import com.saarlabs.tminus.ui.theme.rememberUserDarkTheme
import androidx.compose.ui.Modifier
import com.saarlabs.tminus.android.widget.WidgetUpdateWorker
import com.saarlabs.tminus.ui.SettingsContent

/** Standalone entry for deep links or shortcuts; main flow uses the Settings tab in [MainActivity]. */
public class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences(SettingsKeys.PREFS, MODE_PRIVATE)
        setContent {
            val darkTheme = rememberUserDarkTheme()
            TminusTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SettingsContent(
                        initialV3 = prefs.getString(SettingsKeys.KEY_V3_API, "") ?: "",
                        initialUse24Hour = prefs.getBoolean(SettingsKeys.KEY_USE_24_HOUR, false),
                        onSave = { v3, use24Hour ->
                            prefs.edit()
                                .putString(SettingsKeys.KEY_V3_API, v3.ifBlank { null })
                                .putBoolean(SettingsKeys.KEY_USE_24_HOUR, use24Hour)
                                .commit()
                            GlobalDataStore.invalidate()
                            runCatching { TminusApplication.refreshNetworking() }
                                .onFailure { Log.e("SettingsActivity", "refreshNetworking failed", it) }
                            runCatching {
                                WidgetUpdateWorker.enqueueRefresh(this@SettingsActivity, appWidgetIds = null)
                            }.onFailure { Log.e("SettingsActivity", "enqueueRefresh failed", it) }
                            Toast.makeText(
                                this@SettingsActivity,
                                getString(R.string.settings_saved_snackbar),
                                Toast.LENGTH_SHORT,
                            ).show()
                            finish()
                        },
                    )
                }
            }
        }
    }
}
