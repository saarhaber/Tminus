package com.saarlabs.tminus.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saarlabs.tminus.R

/**
 * Dialog-only composable so [rememberTimePickerState] is never called conditionally inside the
 * parent (Compose requires a stable order of remember calls per composition path).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinutesFromMidnightPickerDialog(
    minutesFromMidnight: Int,
    use24Hour: Boolean,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val total = minutesFromMidnight.coerceIn(0, 24 * 60 - 1)
    val hour24 = total / 60
    val minute = total % 60
    val state =
        rememberTimePickerState(
            initialHour = hour24,
            initialMinute = minute,
            is24Hour = use24Hour,
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            TimePicker(state = state)
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val m = (state.hour * 60 + state.minute).coerceIn(0, 24 * 60 - 1)
                    onConfirm(m)
                },
            ) {
                Text(stringResource(R.string.time_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.time_picker_cancel))
            }
        },
    )
}

/**
 * Opens a Material time picker for a value stored as minutes from midnight (0–1439).
 * [label] describes the field; the button shows the formatted time and which format is active.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MinutesFromMidnightPickerField(
    minutesFromMidnight: Int,
    onMinutesChange: (Int) -> Unit,
    label: String,
    use24Hour: Boolean,
    modifier: Modifier = Modifier,
    showLabelOnButton: Boolean = true,
) {
    var show by remember { mutableStateOf(false) }
    val total = minutesFromMidnight.coerceIn(0, 24 * 60 - 1)
    val summaryFormat =
        stringResource(
            if (use24Hour) {
                R.string.time_picker_summary_24h
            } else {
                R.string.time_picker_summary_12h
            },
        )
    OutlinedButton(
        onClick = { show = true },
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (showLabelOnButton) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                formatMinutesFromMidnight(total, use24Hour),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                summaryFormat,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (show) {
        MinutesFromMidnightPickerDialog(
            minutesFromMidnight = minutesFromMidnight,
            use24Hour = use24Hour,
            label = label,
            onDismiss = { show = false },
            onConfirm = { m ->
                onMinutesChange(m)
                show = false
            },
        )
    }
}
