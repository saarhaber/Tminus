package com.saarlabs.tminus.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.saarlabs.tminus.R
import com.saarlabs.tminus.commute.CommuteRepository
import com.saarlabs.tminus.features.AccessibilityRepository
import com.saarlabs.tminus.features.LastTrainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Asks for notification permission once the user has an alert that needs it.
 *
 * A short in-app explanation comes first. The system dialog on its own gives no clue why a transit
 * app wants notifications — and previously it could appear over the launcher, detached from any
 * context, which is a good way to get permanently denied.
 */
@Composable
internal fun NotificationPermissionGate() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val app = remember(context) { context.applicationContext }

    var hasAlerts by remember { mutableStateOf(false) }
    var rationaleDismissed by rememberSaveable { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        hasAlerts =
            withContext(Dispatchers.IO) {
                CommuteRepository(app).loadProfiles().any { it.enabled } ||
                    LastTrainRepository(app).load().any { it.enabled } ||
                    AccessibilityRepository(app).load().any { it.enabled }
            }
    }

    LaunchedEffect(hasAlerts, rationaleDismissed) {
        if (!hasAlerts || rationaleDismissed) return@LaunchedEffect
        val granted =
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        showRationale = !granted
    }

    if (!showRationale) return

    AlertDialog(
        onDismissRequest = {
            showRationale = false
            rationaleDismissed = true
        },
        icon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        },
        title = { Text(stringResource(R.string.notif_rationale_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.notif_rationale_body))
                Spacer(Modifier.height(0.dp))
                Text(
                    text = stringResource(R.string.notif_rationale_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    showRationale = false
                    rationaleDismissed = true
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            ) {
                Text(stringResource(R.string.notif_rationale_allow))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    showRationale = false
                    rationaleDismissed = true
                },
            ) {
                Text(stringResource(R.string.notif_rationale_not_now))
            }
        },
    )
}
