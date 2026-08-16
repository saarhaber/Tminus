package com.saarlabs.tminus.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saarlabs.tminus.R
import com.saarlabs.tminus.model.Stop

/**
 * Picks a single stop.
 *
 * When [restrictToStops] is supplied the picker only offers those stops — the alert editors pass the
 * stops served by the chosen route, so a "last Orange Line train" alert cannot be attached to a bus
 * stop the Orange Line never calls at.
 */
@Composable
public fun StopSearchPicker(
    onStopChosen: (Stop) -> Unit,
    modifier: Modifier = Modifier,
    restrictToStops: List<Stop>? = null,
    restrictionActive: Boolean = false,
) {
    if (restrictionActive) {
        StopPicker(
            stops = restrictToStops,
            onStopChosen = onStopChosen,
            modifier = modifier,
            header = stringResource(R.string.stop_picker_route_scoped_header),
            emptyMessage = stringResource(R.string.stop_picker_route_scoped_empty),
        )
        return
    }

    when (val global = rememberGlobalData()) {
        GlobalDataState.Loading ->
            CircularProgressIndicator(modifier = modifier.padding(24.dp))

        is GlobalDataState.Failed ->
            Column(modifier = modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.widget_loading_timeout_tminus),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

        is GlobalDataState.Ready ->
            StopPicker(
                stops = global.data.getParentStopsForSelection(),
                onStopChosen = onStopChosen,
                modifier = modifier,
            )
    }
}
