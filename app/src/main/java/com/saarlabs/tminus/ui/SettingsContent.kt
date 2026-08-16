package com.saarlabs.tminus.ui

import android.content.Context
import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.saarlabs.tminus.AppGraph
import com.saarlabs.tminus.R
import com.saarlabs.tminus.SettingsKeys
import com.saarlabs.tminus.android.widget.WidgetUpdateWorker
import kotlinx.coroutines.launch

private const val GITHUB_NEW_ISSUE_URL = "https://github.com/saarhaber/Tminus/issues/new"
private const val GITHUB_CONTRIBUTING_URL = "https://github.com/saarhaber/Tminus/blob/main/CONTRIBUTING.md"

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
    val prefs = remember(context) {
        context.getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)
    }
    var themeMode by remember {
        mutableStateOf(
            prefs.getString(SettingsKeys.KEY_THEME_MODE, SettingsKeys.THEME_SYSTEM)
                ?: SettingsKeys.THEME_SYSTEM,
        )
    }
    val themeIndex =
        when (themeMode) {
            SettingsKeys.THEME_LIGHT -> 1
            SettingsKeys.THEME_DARK -> 2
            else -> 0
        }
    val settings = remember(context) { AppGraph.from(context).settings }
    val savedMessage = stringResource(R.string.settings_saved_snackbar)
    var dynamicColor by remember { mutableStateOf(settings.dynamicColorEnabled()) }
    var fontPercent by remember {
        mutableIntStateOf(
            prefs.getInt(
                SettingsKeys.KEY_FONT_SCALE_PERCENT,
                SettingsKeys.FONT_SCALE_DEFAULT_PERCENT,
            ),
        )
    }
    val use24Hour = formatIndex == 1
    val apiDirty = v3 != initialV3
    val formatDirty = use24Hour != initialUse24Hour

    val scroll = rememberScrollState()
    // No Scaffold here: this composable is hosted inside the app's Scaffold, and nesting a second
    // one applied the window insets twice — which is what left a band of dead space above the title.
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(20.dp))

            SettingsSection(
                icon = Icons.Filled.Palette,
                title = stringResource(R.string.settings_appearance_title),
                body = stringResource(R.string.settings_appearance_body),
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = themeIndex == 0,
                        onClick = {
                            themeMode = SettingsKeys.THEME_SYSTEM
                            prefs.edit()
                                .putString(SettingsKeys.KEY_THEME_MODE, SettingsKeys.THEME_SYSTEM)
                                .apply()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    ) {
                        Icon(
                            Icons.Default.BrightnessAuto,
                            contentDescription =
                                stringResource(R.string.settings_theme_system_cd),
                        )
                    }
                    SegmentedButton(
                        selected = themeIndex == 1,
                        onClick = {
                            themeMode = SettingsKeys.THEME_LIGHT
                            prefs.edit()
                                .putString(SettingsKeys.KEY_THEME_MODE, SettingsKeys.THEME_LIGHT)
                                .apply()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    ) {
                        Icon(
                            Icons.Default.LightMode,
                            contentDescription =
                                stringResource(R.string.settings_theme_light_cd),
                        )
                    }
                    SegmentedButton(
                        selected = themeIndex == 2,
                        onClick = {
                            themeMode = SettingsKeys.THEME_DARK
                            prefs.edit()
                                .putString(SettingsKeys.KEY_THEME_MODE, SettingsKeys.THEME_DARK)
                                .apply()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    ) {
                        Icon(
                            Icons.Default.DarkMode,
                            contentDescription =
                                stringResource(R.string.settings_theme_dark_cd),
                        )
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = dynamicColor,
                                    onValueChange = {
                                        dynamicColor = it
                                        settings.setDynamicColorEnabled(it)
                                    },
                                    role = Role.Switch,
                                )
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = stringResource(R.string.settings_dynamic_color_title),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.settings_dynamic_color_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = dynamicColor, onCheckedChange = null)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            SettingsSection(
                icon = Icons.Filled.FormatSize,
                title = stringResource(R.string.settings_font_scale_title),
                body = stringResource(R.string.settings_font_scale_body),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "A",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = fontPercent.toFloat(),
                        onValueChange = { v ->
                            val step = (v / 5f).toInt() * 5
                            val clamped =
                                step.coerceIn(
                                    SettingsKeys.FONT_SCALE_MIN_PERCENT,
                                    SettingsKeys.FONT_SCALE_MAX_PERCENT,
                                )
                            if (clamped != fontPercent) {
                                fontPercent = clamped
                                prefs.edit()
                                    .putInt(SettingsKeys.KEY_FONT_SCALE_PERCENT, clamped)
                                    .apply()
                            }
                        },
                        // Widgets read font scale once during composition, so they keep the old size
                        // until something forces a recompose; refresh once the user lets go.
                        onValueChangeFinished = {
                            runCatching {
                                WidgetUpdateWorker.enqueueRefresh(context, appWidgetIds = null)
                            }
                        },
                        valueRange =
                            SettingsKeys.FONT_SCALE_MIN_PERCENT.toFloat()..
                                SettingsKeys.FONT_SCALE_MAX_PERCENT.toFloat(),
                        steps =
                            ((SettingsKeys.FONT_SCALE_MAX_PERCENT -
                                SettingsKeys.FONT_SCALE_MIN_PERCENT) / 5) - 1,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Text(
                        text = "A",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.settings_font_scale_value, fontPercent),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.settings_font_scale_preview),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            SettingsSection(
                icon = Icons.Filled.Schedule,
                title = stringResource(R.string.settings_time_format_title),
                body = stringResource(R.string.settings_time_format_body),
            ) {
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
                        if (formatDirty) {
                            stringResource(R.string.settings_time_format_hint_unsaved)
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
            }

            Spacer(Modifier.height(16.dp))

            SettingsSection(
                icon = Icons.Filled.Key,
                title = stringResource(R.string.settings_api_keys_title),
                body = stringResource(R.string.settings_api_keys_body),
            ) {
                DocLink(
                    label = stringResource(R.string.settings_link_v3_portal),
                    url = "https://api-v3.mbta.com/",
                )
                Spacer(Modifier.height(6.dp))
                DocLink(
                    label = stringResource(R.string.settings_link_v3_swagger),
                    url = "https://api-v3.mbta.com/docs/swagger/index.html",
                )
                Spacer(Modifier.height(14.dp))
                var keyVisible by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = v3,
                    onValueChange = { v3 = it },
                    label = { Text(stringResource(R.string.settings_v3_key_label)) },
                    // Masked by default: an API key is a credential, and Settings is a screen people
                    // open in public.
                    visualTransformation =
                        if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector =
                                    if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription =
                                    stringResource(
                                        if (keyVisible) R.string.settings_key_hide
                                        else R.string.settings_key_show,
                                    ),
                            )
                        }
                    },
                    supportingText = {
                        Text(
                            text =
                                when {
                                    apiDirty ->
                                        stringResource(R.string.settings_v3_key_hint_unsaved)
                                    v3.isNotBlank() ->
                                        stringResource(R.string.settings_v3_key_hint_saved)
                                    else ->
                                        stringResource(R.string.settings_v3_key_hint_empty)
                                },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onSave(v3, use24Hour)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            savedMessage,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.settings_save_all))
            }

            Spacer(Modifier.height(24.dp))

            SettingsSection(
                icon = Icons.Filled.Handshake,
                title = stringResource(R.string.roadmap_section_community),
                body = stringResource(R.string.roadmap_footer),
            ) {
                DocLink(
                    label = stringResource(R.string.roadmap_feature_contributing),
                    url = GITHUB_CONTRIBUTING_URL,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_NEW_ISSUE_URL)),
                                )
                            },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.roadmap_report_issue),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SettingsSection(
    icon: ImageVector,
    title: String,
    body: String,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

/** An external documentation link. Uses [LinkAnnotation], which `ClickableText` was deprecated for. */
@Composable
private fun DocLink(label: String, url: String) {
    val linkStyle =
        SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        )
    val annotated =
        buildAnnotatedString {
            withLink(LinkAnnotation.Url(url, TextLinkStyles(style = linkStyle))) { append(label) }
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(text = annotated, style = MaterialTheme.typography.bodyMedium)
    }
}
