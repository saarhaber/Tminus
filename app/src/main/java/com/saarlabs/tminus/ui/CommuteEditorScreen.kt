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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.saarlabs.tminus.AppGraph
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
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var days by rememberSaveable(stateSaver = daysSaver) {
        mutableStateOf((initial?.daysOfWeek ?: listOf(1, 2, 3, 4, 5)).toSet())
    }
    var commuteTargetMinutes by rememberSaveable {
        mutableStateOf(initial?.targetMinutesFromMidnight ?: 8 * 60 + 30)
    }
    val use24Hour = rememberUse24HourTime()
    var winBefore by rememberSaveable { mutableStateOf((initial?.windowMinutesBefore ?: 45).toString()) }
    var winAfter by rememberSaveable { mutableStateOf((initial?.windowMinutesAfter ?: 45).toString()) }
    var leadMin by rememberSaveable { mutableStateOf((initial?.notifyLeadMinutes ?: 12).toString()) }
    var notifyArrival by rememberSaveable { mutableStateOf(initial?.notifyOnArrival ?: true) }
    var enabled by rememberSaveable { mutableStateOf(initial?.enabled ?: true) }

    // Stop ids, not Stop objects: Stop is not Parcelable, and after a rotation `initial` is briefly
    // null again — so the saved ids, not the profile, are the source of truth here.
    var fromStopId by rememberSaveable { mutableStateOf(initial?.fromStopId) }
    var toStopId by rememberSaveable { mutableStateOf(initial?.toStopId) }
    var pickingStops by rememberSaveable { mutableStateOf(initial == null) }

    var previewText by remember { mutableStateOf<String?>(null) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var nameTouchedInvalid by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val graph = remember(context) { AppGraph.from(context) }
    // Hoisted: reading resources through LocalContext inside a lambda bypasses Compose's
    // configuration handling, so locale and font-scale changes would not reach these strings.
    val previewNeedStops = stringResource(R.string.commute_preview_need_stops)
    val previewDepLabel = stringResource(R.string.commute_preview_dep)
    val previewArrLabel = stringResource(R.string.commute_preview_arr)
    val previewLeaveLabel = stringResource(R.string.commute_preview_leave)
    val previewNone = stringResource(R.string.commute_preview_none)
    val needFromStop = stringResource(R.string.commute_validation_need_from_stop)
    val needToStop = stringResource(R.string.commute_validation_need_to_stop)
    val needDay = stringResource(R.string.commute_validation_need_day)

    val globalState = rememberGlobalData()
    val globalData = (globalState as? GlobalDataState.Ready)?.data
    val fromStop =
        remember(globalData, fromStopId) {
            globalData?.getStop(fromStopId)?.resolveParent(globalData.stops)
        }
    val toStop =
        remember(globalData, toStopId) {
            globalData?.getStop(toStopId)?.resolveParent(globalData.stops)
        }

    if (pickingStops) {
        StopPairPicker(
            onStopsChosen = { f, t ->
                fromStopId = f.id
                toStopId = t.id
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
        val nameInvalid = nameTouchedInvalid && name.isBlank()
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                // Clear the error as soon as the user acts on it, rather than leaving a stale
                // "add a name" message under a field that now has a name in it.
                if (it.isNotBlank()) nameTouchedInvalid = false
                validationMessage = null
            },
            label = { Text(stringResource(R.string.commute_name)) },
            isError = nameInvalid,
            supportingText =
                if (nameInvalid) {
                    { Text(stringResource(R.string.commute_validation_need_name)) }
                } else {
                    null
                },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        TextButton(onClick = { pickingStops = true }) {
            Text(
                stringResource(
                    R.string.commute_stops_summary,
                    fromStop?.let { stopOneLineDisplay(it, context.resources) } ?: "—",
                    toStop?.let { stopOneLineDisplay(it, context.resources) } ?: "—",
                ),
            )
        }

        Text(stringResource(R.string.commute_days), style = MaterialTheme.typography.titleSmall)
        WeekdayChipRow(
            selectedDays = days,
            onToggleDay = { d ->
                days = if (days.contains(d)) days - d else days + d
                validationMessage = null
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
                        previewText = previewNeedStops
                        return@launch
                    }
                    val globalResult = withContext(Dispatchers.IO) { graph.globalData.getOrLoad() }
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
                                graph.client.fetchScheduleForStopsInWindow(
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
                            // Formatted, not `local.toString()`: that prints the raw ISO form and
                            // ignores the user's 12/24-hour preference.
                            "${trip.route.label} · ${trip.headsign ?: trip.tripId}\n" +
                                "$previewDepLabel " +
                                "${trip.departureTime.formatDayAndClock(use24Hour)}\n" +
                                "$previewArrLabel " +
                                "${trip.arrivalTime.formatClock(use24Hour)}\n" +
                                "$previewLeaveLabel " +
                                leave.formatClock(use24Hour)
                        } else {
                            previewNone
                        }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.commute_preview_button))
        }

        RowHorizontalButtons(onCancel = onCancel, onSave = {
            validationMessage = null
            nameTouchedInvalid = name.isBlank()
            val f = fromStop
            val t = toStop
            val issues = mutableListOf<String>()
            if (f == null) {
                issues.add(needFromStop)
            }
            if (t == null) {
                issues.add(needToStop)
            }
            if (days.isEmpty()) {
                issues.add(needDay)
            }
            if (issues.isNotEmpty()) {
                validationMessage = issues.joinToString("\n")
            }
            if (issues.isNotEmpty() || nameTouchedInvalid) {
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
                    fromLabel = stopOneLineDisplay(from, context.resources),
                    toLabel = stopOneLineDisplay(to, context.resources),
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
