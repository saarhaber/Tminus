package com.saarlabs.tminus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saarlabs.tminus.R

private val weekdayLabelPairs =
    listOf(
        1 to R.string.day_mon,
        2 to R.string.day_tue,
        3 to R.string.day_wed,
        4 to R.string.day_thu,
        5 to R.string.day_fri,
        6 to R.string.day_sat,
        7 to R.string.day_sun,
    )

@Composable
internal fun WeekdayChipRow(
    selectedDays: Set<Int>,
    onToggleDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        weekdayLabelPairs.forEach { (dow, labelRes) ->
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = selectedDays.contains(dow),
                onClick = { onToggleDay(dow) },
                label = {
                    Text(
                        stringResource(labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}
