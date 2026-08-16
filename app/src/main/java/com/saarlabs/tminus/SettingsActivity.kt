package com.saarlabs.tminus

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.saarlabs.tminus.ui.TminusEdgeToEdge
import com.saarlabs.tminus.ui.theme.TminusTheme
import com.saarlabs.tminus.ui.theme.rememberUserDarkTheme
import androidx.compose.ui.Modifier
import com.saarlabs.tminus.android.widget.WidgetUpdateWorker
import com.saarlabs.tminus.ui.SettingsContent

/** Standalone entry for deep links or shortcuts; main flow uses the Settings tab in [MainActivity]. */
public class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = AppGraph.from(this).settings
        setContent {
            val darkTheme = rememberUserDarkTheme()
            TminusEdgeToEdge(darkTheme)
            TminusTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SettingsContent(
                        initialV3 = settings.apiKey().orEmpty(),
                        initialUse24Hour = settings.use24HourTime(),
                        onSave = { v3, use24Hour ->
                            settings.setApiKey(v3)
                            settings.setUse24HourTime(use24Hour)
                            runCatching {
                                AppGraph.from(this@SettingsActivity).onApiKeyChanged()
                                WidgetUpdateWorker.enqueueRefresh(this@SettingsActivity)
                            }.onFailure { Log.e("SettingsActivity", "applying settings failed", it) }
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
