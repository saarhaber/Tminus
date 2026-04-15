package com.saarlabs.tminus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.saarlabs.tminus.model.Stop
import com.saarlabs.tminus.model.WidgetTripData
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.util.EasternTimeInstant
import com.saarlabs.tminus.GlobalDataStore
import com.saarlabs.tminus.R
import com.saarlabs.tminus.commute.CommuteProfile
import com.saarlabs.tminus.commute.CommuteTripPlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes

/** 1 = Monday … 7 = Sunday (matches commute day chips). */
private fun isoDayOfWeek(date: LocalDate): Int =
    when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> 1
        DayOfWeek.TUESDAY -> 2
        DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4
        DayOfWeek.FRIDAY -> 5
        DayOfWeek.SATURDAY -> 6
        DayOfWeek.SUNDAY -> 7
    }

@Composable
private fun RowHorizontalButtons(onCancel: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.commute_cancel))
        }
        Button(onClick = onSave, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.commute_save))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun CommuteEditorScreen(
    initial: CommuteProfile?,
    onSave: (CommuteProfile) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var days by remember {
        mutableStateOf((initial?.daysOfWeek ?: listOf(1, 2, 3, 4, 5)).toSet())
    }
    var commuteTargetMinutes by remember {
        mutableStateOf(initial?.targetMinutesFromMidnight ?: 8 * 60 + 30)
    }
    val use24Hour = rememberUse24HourTime()
    var winBefore by remember { mutableStateOf((initial?.windowMinutesBefore ?: 45).toString()) }
    var winAfter by remember { mutableStateOf((initial?.windowMinutesAfter ?: 45).toString()) }
    var leadMin by remember { mutableStateOf((initial?.notifyLeadMinutes ?: 12).toString()) }
    var notifyArrival by remember { mutableStateOf(initial?.notifyOnArrival ?: true) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }

    var fromStop by remember { mutableStateOf<Stop?>(null) }
    var toStop by remember { mutableStateOf<Stop?>(null) }
    var pickingStops by remember { mutableStateOf(initial == null) }

    var previewText by remember { mutableStateOf<String?>(null) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(initial) {
        if (initial != null) {
            val g = GlobalDataStore.getOrLoad()
            if (g is ApiResult.Ok) {
                fromStop = g.data.getStop(initial.fromStopId)?.resolveParent(g.data.stops)
                toStop = g.data.getStop(initial.toStopId)?.resolveParent(g.data.stops)
            }
        }
    }

    if (pickingStops) {
        StopPairPicker(
            onStopsChosen = { f, t ->
                fromStop = f
                toStop = t
                pickingStops = false
            },
            onCancel = onCancel,
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.commute_editor_title)) },
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
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.commute_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        TextButton(onClick = { pickingStops = true }) {
            Text(
                stringResource(
                    R.string.commute_stops_summary,
                    fromStop?.name ?: "—",
                    toStop?.name ?: "—",
                ),
            )
        }

        Text(stringResource(R.string.commute_days), style = MaterialTheme.typography.titleSmall)
        WeekdayChipRow(
            selectedDays = days,
            onToggleDay = { d ->
                days = if (days.contains(d)) days - d else days + d
            },
        )

        Text(stringResource(R.string.commute_target_time), style = MaterialTheme.typography.titleSmall)
        MinutesFromMidnightPickerField(
            minutesFromMidnight = commuteTargetMinutes,
            onMinutesChange = { commuteTargetMinutes = it },
            label = stringResource(R.string.commute_target_time),
            use24Hour = use24Hour,
            modifier = Modifier.fillMaxWidth(),
            showLabelOnButton = false,
        )

        OutlinedTextField(
            value = winBefore,
            onValueChange = { winBefore = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.commute_window_before)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = winAfter,
            onValueChange = { winAfter = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.commute_window_after)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = leadMin,
            onValueChange = { leadMin = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(R.string.commute_lead_minutes)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = notifyArrival,
                            onValueChange = { notifyArrival = it },
                            role = Role.Switch,
                        )
                        .padding(vertical = 2.dp),
            ) {
                Text(
                    stringResource(R.string.commute_notify_arrival),
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Switch(checked = notifyArrival, onCheckedChange = null)
            }
            Text(
                text = stringResource(R.string.commute_notify_arrival_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
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
                        .padding(vertical = 2.dp),
            ) {
                Text(
                    stringResource(R.string.commute_enabled),
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Switch(checked = enabled, onCheckedChange = null)
            }
            Text(
                text = stringResource(R.string.commute_enabled_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        validationMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = {
                scope.launch {
                    val f = fromStop
                    val t = toStop
                    if (f == null || t == null) {
                        previewText = context.getString(R.string.commute_preview_need_stops)
                        return@launch
                    }
                    val globalResult = withContext(Dispatchers.IO) { GlobalDataStore.getOrLoad() }
                    val global =
                        when (globalResult) {
                            is ApiResult.Ok -> globalResult.data
                            is ApiResult.Error -> {
                                previewText = globalResult.message
                                return@launch
                            }
                        }
                    val targetMinutes = commuteTargetMinutes.coerceIn(0, 24 * 60 - 1)
                    val wb = winBefore.toIntOrNull()?.coerceIn(0, 180) ?: 45
                    val wa = winAfter.toIntOrNull()?.coerceIn(0, 180) ?: 45
                    val windowStart = max(0, targetMinutes - wb)
                    val windowEnd = min(24 * 60 - 1, targetMinutes + wa)
                    val minH = windowStart / 60
                    val minM = windowStart % 60
                    val maxH = windowEnd / 60
                    val maxM = windowEnd % 60
                    val minTime =
                        "${minH.toString().padStart(2, '0')}:${minM.toString().padStart(2, '0')}"
                    val maxTime =
                        "${maxH.toString().padStart(2, '0')}:${maxM.toString().padStart(2, '0')}"
                    val stopIds =
                        (global.stopIdsForScheduleFilter(f) + global.stopIdsForScheduleFilter(t))
                            .distinct()
                    val now = EasternTimeInstant.now()
                    val today = now.local.date
                    val allowedDays = if (days.isEmpty()) (1..7).toSet() else days
                    var trip: WidgetTripData? = null
                    for (dayOffset in 0 until 14) {
                        val d = today.plus(dayOffset, DateTimeUnit.DAY)
                        if (isoDayOfWeek(d) !in allowedDays) continue
                        val schedResult =
                            withContext(Dispatchers.IO) {
                                GlobalDataStore.client.fetchScheduleForStopsInWindow(
                                    stopIds,
                                    minTime,
                                    maxTime,
                                    serviceDate = d,
                                )
                            }
                        when (schedResult) {
                            is ApiResult.Error -> {
                                previewText = schedResult.message
                                return@launch
                            }
                            is ApiResult.Ok -> {
                                trip =
                                    CommuteTripPlanner.findNextCommutePreviewTrip(
                                        schedResult.data,
                                        global,
                                        f.id,
                                        t.id,
                                        now,
                                        windowStart,
                                        windowEnd,
                                        days,
                                    )
                                if (trip != null) break
                            }
                        }
                    }
                    val lead = leadMin.toIntOrNull()?.coerceIn(1, 120) ?: 12
                    previewText =
                        if (trip != null) {
                            val leave = trip.departureTime.minus(lead.minutes)
                            "${trip.route.label} · ${trip.headsign ?: trip.tripId}\n" +
                                "${context.getString(R.string.commute_preview_dep)} ${trip.departureTime.local}\n" +
                                "${context.getString(R.string.commute_preview_arr)} ${trip.arrivalTime.local}\n" +
                                "${context.getString(R.string.commute_preview_leave)} ${leave.local}"
                        } else {
                            context.getString(R.string.commute_preview_none)
                        }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.commute_preview_button))
        }

        RowHorizontalButtons(onCancel = onCancel, onSave = {
            validationMessage = null
            val f = fromStop
            val t = toStop
            val issues = mutableListOf<String>()
            if (name.isBlank()) {
                issues.add(context.getString(R.string.commute_validation_need_name))
            }
            if (f == null) {
                issues.add(context.getString(R.string.commute_validation_need_from_stop))
            }
            if (t == null) {
                issues.add(context.getString(R.string.commute_validation_need_to_stop))
            }
            if (days.isEmpty()) {
                issues.add(context.getString(R.string.commute_validation_need_day))
            }
            if (issues.isNotEmpty()) {
                validationMessage = issues.joinToString("\n")
                return@RowHorizontalButtons
            }
            val from = requireNotNull(f)
            val to = requireNotNull(t)
            val targetMinutes = commuteTargetMinutes.coerceIn(0, 24 * 60 - 1)
            val profile =
                CommuteProfile(
                    id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name.trim(),
                    fromStopId = from.id,
                    toStopId = to.id,
                    fromLabel = from.name,
                    toLabel = to.name,
                    daysOfWeek = days.sorted(),
                    targetMinutesFromMidnight = targetMinutes,
                    windowMinutesBefore = winBefore.toIntOrNull()?.coerceIn(5, 180) ?: 45,
                    windowMinutesAfter = winAfter.toIntOrNull()?.coerceIn(5, 180) ?: 45,
                    notifyLeadMinutes = leadMin.toIntOrNull()?.coerceIn(1, 120) ?: 12,
                    notifyOnArrival = notifyArrival,
                    enabled = enabled,
                )
            onSave(profile)
        })
        }
    }

    previewText?.let { msg ->
        AlertDialog(
            onDismissRequest = { previewText = null },
            confirmButton = {
                TextButton(onClick = { previewText = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.commute_preview_title)) },
            text = { Text(msg) },
        )
    }
}
