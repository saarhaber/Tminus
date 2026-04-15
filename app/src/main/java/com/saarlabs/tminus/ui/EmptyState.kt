package com.saarlabs.tminus.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
public fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        if (hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
