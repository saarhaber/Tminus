package com.saarlabs.tminus.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.saarlabs.tminus.R
import kotlinx.coroutines.launch

@Composable
public fun SettingsContent(
    initialV3: String,
    initialUse24Hour: Boolean,
    onSave: (v3: String, use24Hour: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var v3 by remember(initialV3) { mutableStateOf(initialV3) }
    var formatIndex by remember(initialUse24Hour) {
        mutableIntStateOf(if (initialUse24Hour) 1 else 0)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val use24Hour = formatIndex == 1
    val isDirty = v3 != initialV3 || use24Hour != initialUse24Hour

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.settings_api_keys_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_api_keys_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))

            DocLink(
                label = stringResource(R.string.settings_link_v3_portal),
                url = "https://api-v3.mbta.com/",
            )
            Spacer(Modifier.height(8.dp))
            DocLink(
                label = stringResource(R.string.settings_link_v3_swagger),
                url = "https://api-v3.mbta.com/docs/swagger/index.html",
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = v3,
                onValueChange = { v3 = it },
                label = { Text(stringResource(R.string.settings_v3_key_label)) },
                supportingText = {
                    Text(
                        text =
                            when {
                                isDirty -> stringResource(R.string.settings_v3_key_hint_unsaved)
                                v3.isNotBlank() -> stringResource(R.string.settings_v3_key_hint_saved)
                                else -> stringResource(R.string.settings_v3_key_hint_empty)
                            },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.settings_time_format_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_time_format_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = formatIndex == 0,
                    onClick = { formatIndex = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text(stringResource(R.string.settings_time_format_12h))
                }
                SegmentedButton(
                    selected = formatIndex == 1,
                    onClick = { formatIndex = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text(stringResource(R.string.settings_time_format_24h))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text =
                    if (isDirty) {
                        stringResource(R.string.settings_v3_key_hint_unsaved)
                    } else if (use24Hour) {
                        stringResource(R.string.settings_time_format_24h) + " — " +
                            stringResource(R.string.time_picker_summary_24h)
                    } else {
                        stringResource(R.string.settings_time_format_12h) + " — " +
                            stringResource(R.string.time_picker_summary_12h)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    onSave(v3, use24Hour)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.settings_saved_snackbar),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_save_all))
            }
        }
    }
}

@Composable
private fun DocLink(label: String, url: String) {
    val context = LocalContext.current
    val ann =
        buildAnnotatedString {
            pushStringAnnotation(tag = "URL", annotation = url)
            withStyle(
                style =
                    SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    ),
            ) {
                append(label)
            }
            pop()
        }
    ClickableText(
        text = ann,
        onClick = { offset ->
            ann.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()
                ?.let {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item)))
                }
        },
    )
}
