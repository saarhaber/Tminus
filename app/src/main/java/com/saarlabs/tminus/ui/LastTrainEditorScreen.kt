package com.saarlabs.tminus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.saarlabs.tminus.model.Route
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.AppGraph
import com.saarlabs.tminus.R
import com.saarlabs.tminus.features.LastTrainMode
import com.saarlabs.tminus.features.LastTrainProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
public fun LastTrainEditorScreen(
    initial: LastTrainProfile?,
    onSave: (LastTrainProfile) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var routeId by rememberSaveable { mutableStateOf(initial?.routeId ?: "Orange") }
    var directionId by rememberSaveable { mutableStateOf(initial?.directionId ?: 0) }
    var mode by remember { mutableStateOf(initial?.mode ?: LastTrainMode.LAST) }
    // Stop id, not a Stop: Stop is not Parcelable, so this is what survives a rotation.
    var stopId by rememberSaveable { mutableStateOf(initial?.stopId) }
    var notifyMin by rememberSaveable { mutableStateOf((initial?.notifyMinutesBefore ?: 45).toString()) }
    var winStart by rememberSaveable { mutableStateOf(initial?.windowStartMinutes ?: 18 * 60) }
    var winEnd by rememberSaveable { mutableStateOf(initial?.windowEndMinutes ?: 26 * 60) }
    var firstStart by rememberSaveable { mutableStateOf(initial?.firstWindowStartMinutes ?: 4 * 60) }
    var firstEnd by rememberSaveable { mutableStateOf(initial?.firstWindowEndMinutes ?: 10 * 60) }
    val use24Hour = rememberUse24HourTime()
    var days by rememberSaveable(stateSaver = daysSaver) {
        mutableStateOf((initial?.daysOfWeek ?: listOf(1, 2, 3, 4, 5, 6, 7)).toSet())
    }
    var enabled by rememberSaveable { mutableStateOf(initial?.enabled ?: true) }
    var showStopDialog by rememberSaveable { mutableStateOf(false) }
    val routeScopedStops = rememberStopsForRoute(routeId)
    var globalData by remember { mutableStateOf<GlobalData?>(null) }
    var routeMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val graph = remember(context) { AppGraph.from(context) }

    val stop =
        remember(globalData, stopId) {
            val data = globalData
            data?.getStop(stopId)?.resolveParent(data.stops)
        }

    LaunchedEffect(Unit) {
        when (val r = withContext(Dispatchers.IO) { graph.globalData.getOrLoad() }) {
            is ApiResult.Ok -> {
                globalData = r.data
            }
            is ApiResult.Error -> {}
        }
    }

    val routeList: List<Route> =
        remember(globalData) { globalData?.let { routesForDropdown(it.routes) } ?: emptyList() }
    val selectedRoute: Route? = routeList.find { it.id == routeId }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.last_train_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.commute_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
    Column(
        modifier =
            Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.last_train_help), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.last_train_name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (routeList.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = routeMenuExpanded,
                onExpandedChange = { routeMenuExpanded = !routeMenuExpanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = selectedRoute?.label ?: routeId,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.last_train_route)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = routeMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    singleLine = true,
                )
                ExposedDropdownMenu(
                    expanded = routeMenuExpanded,
                    onDismissRequest = { routeMenuExpanded = false },
                ) {
                    routeList.forEach { r ->
                        DropdownMenuItem(
                            text = { Text("${r.label} (${r.id})") },
                            onClick = {
                                routeId = r.id
                                routeMenuExpanded = false
                            },
                        )
                    }
                }
            }
            if (selectedRoute == null) {
                Text(
                    stringResource(R.string.last_train_route_unknown, routeId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            OutlinedTextField(
                value = routeId,
                onValueChange = { routeId = it },
                label = { Text(stringResource(R.string.last_train_route)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        Text(stringResource(R.string.last_train_direction), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 1).forEach { d ->
                androidx.compose.material3.FilterChip(
                    selected = directionId == d,
                    onClick = { directionId = d },
                    label = { Text(directionLabelForRoute(selectedRoute, d)) },
                )
            }
        }

        Text(stringResource(R.string.last_train_mode_label), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilterChip(
                selected = mode == LastTrainMode.LAST,
                onClick = { mode = LastTrainMode.LAST },
                label = { Text(stringResource(R.string.last_train_mode_last)) },
            )
            androidx.compose.material3.FilterChip(
                selected = mode == LastTrainMode.FIRST,
                onClick = { mode = LastTrainMode.FIRST },
                label = { Text(stringResource(R.string.last_train_mode_first)) },
            )
        }

        Button(onClick = { showStopDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    R.string.last_train_stop_picked,
                    stop?.name ?: "—",
                ),
            )
        }

        OutlinedTextField(
            value = notifyMin,
            onValueChange = { notifyMin = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.last_train_notify_before)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        when (mode) {
            LastTrainMode.LAST -> {
                MinutesFromMidnightPickerField(
                    minutesFromMidnight = winStart,
                    onMinutesChange = { winStart = it },
                    label = stringResource(R.string.last_train_window_start_min),
                    use24Hour = use24Hour,
                    modifier = Modifier.fillMaxWidth(),
                )
                val endsAfterMidnight = winEnd >= MINUTES_PER_DAY
                MinutesFromMidnightPickerField(
                    minutesFromMidnight = winEnd % MINUTES_PER_DAY,
                    onMinutesChange = {
                        winEnd = if (endsAfterMidnight) it + MINUTES_PER_DAY else it
                    },
                    label = stringResource(R.string.last_train_window_end_min),
                    use24Hour = use24Hour,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = endsAfterMidnight,
                                onValueChange = { after ->
                                    winEnd =
                                        if (after) {
                                            (winEnd % MINUTES_PER_DAY) + MINUTES_PER_DAY
                                        } else {
                                            winEnd % MINUTES_PER_DAY
                                        }
                                },
                                role = Role.Switch,
                            )
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(stringResource(R.string.last_train_after_midnight))
                        Text(
                            text = stringResource(R.string.last_train_after_midnight_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = endsAfterMidnight, onCheckedChange = null)
                }
            }
            LastTrainMode.FIRST -> {
                MinutesFromMidnightPickerField(
                    minutesFromMidnight = firstStart,
                    onMinutesChange = { firstStart = it },
                    label = stringResource(R.string.last_train_first_window_start),
                    use24Hour = use24Hour,
                    modifier = Modifier.fillMaxWidth(),
                )
                MinutesFromMidnightPickerField(
                    minutesFromMidnight = firstEnd,
                    onMinutesChange = { firstEnd = it },
                    label = stringResource(R.string.last_train_first_window_end),
                    use24Hour = use24Hour,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Text(stringResource(R.string.commute_days), style = MaterialTheme.typography.titleSmall)
        WeekdayChipRow(
            selectedDays = days,
            onToggleDay = { d ->
                days = if (days.contains(d)) days - d else days + d
            },
        )

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = enabled,
                        onValueChange = { enabled = it },
                        role = Role.Switch,
                    )
                    .padding(vertical = 4.dp),
        ) {
            Text(
                stringResource(R.string.commute_enabled),
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Switch(checked = enabled, onCheckedChange = null)
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val s = stop ?: return@Button
                val ws = winStart.coerceIn(0, MINUTES_PER_DAY - 1)
                // Not clamped to one day: service-day minutes above 1440 express after-midnight
                // service, which is exactly what a "last train" window needs.
                var we = winEnd.coerceIn(0, SERVICE_DAY_MAX_MINUTES)
                if (we <= ws) we = (ws + 1).coerceAtMost(24 * 60 - 1)
                val fs = firstStart.coerceIn(0, MINUTES_PER_DAY - 1)
                var fe = firstEnd.coerceIn(0, MINUTES_PER_DAY - 1)
                if (fe <= fs) fe = (fs + 1).coerceAtMost(24 * 60 - 1)
                val profile =
                    LastTrainProfile(
                        id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        routeId = routeId.trim(),
                        directionId = directionId,
                        stopId = s.id,
                        stopLabel = s.name,
                        mode = mode,
                        daysOfWeek = days.sorted(),
                        notifyMinutesBefore = notifyMin.toIntOrNull()?.coerceIn(5, 180) ?: 45,
                        windowStartMinutes = ws,
                        windowEndMinutes = we,
                        firstWindowStartMinutes = fs,
                        firstWindowEndMinutes = fe,
                        enabled = enabled,
                    )
                onSave(profile)
            },
            enabled =
                name.isNotBlank() &&
                    stop != null &&
                    routeId.isNotBlank() &&
                    (routeList.isEmpty() || selectedRoute != null),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.commute_save))
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.commute_cancel))
        }
    }
    }

    if (showStopDialog) {
        Dialog(onDismissRequest = { showStopDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.padding(8.dp)) {
                    TextButton(onClick = { showStopDialog = false }) {
                        Text(stringResource(R.string.commute_cancel))
                    }
                    StopSearchPicker(
                        restrictToStops = routeScopedStops,
                        restrictionActive = true,
                        onStopChosen = {
                            stopId = it.id
                            showStopDialog = false
                        },
                    )
                }
            }
        }
    }
}

/** Minutes in a calendar day. */
private const val MINUTES_PER_DAY = 24 * 60

/** Latest minute an MBTA service day reaches (26:59). */
private const val SERVICE_DAY_MAX_MINUTES = 27 * 60 - 1

/**
 * Persists the selected weekdays across rotation and process death.
 *
 * `rememberSaveable` cannot bundle a `Set<Int>` on its own, so it is stored as a comma-joined
 * string. Without this, rotating the device mid-edit silently reset the day chips.
 */
private val daysSaver: Saver<Set<Int>, String> =
    Saver(
        save = { it.sorted().joinToString(",") },
        restore = { raw ->
            raw.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        },
    )
