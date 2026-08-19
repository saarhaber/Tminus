package com.saarlabs.tminus.commute.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.network.ALERT_ACTIVITIES_ACCESSIBILITY
import com.saarlabs.tminus.util.EasternTimeInstant
import com.saarlabs.tminus.AppGraph
import com.saarlabs.tminus.MainActivity
import com.saarlabs.tminus.R
import com.saarlabs.tminus.android.util.clockWithTrack
import com.saarlabs.tminus.commute.CommuteRepository
import com.saarlabs.tminus.commute.CommuteTripPlanner
import com.saarlabs.tminus.ui.formatClock
import com.saarlabs.tminus.ui.plainStopLabel
import com.saarlabs.tminus.features.AccessibilityRepository
import com.saarlabs.tminus.features.LastTrainMode
import com.saarlabs.tminus.features.LastTrainRepository
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Single periodic worker: commutes, last/first train, accessibility route alerts.
 */
public class TminusNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val graph get() = AppGraph.from(applicationContext)

    /** Notifications show times in whatever clock style the user chose in Settings. */
    private val use24Hour: Boolean get() = graph.settings.use24HourTime()

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            // AppGraph builds itself on first use, so a worker that starts the process never has
            // to wait for Application.onCreate to finish wiring networking.
            val prefs = applicationContext.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)
            pruneDeliveredKeys(prefs)

            runCommuteNotifications(prefs)
            runLastTrainNotifications(prefs)
            runAccessibilityNotifications(prefs)

            Result.success()
        }

    private suspend fun runCommuteNotifications(prefs: android.content.SharedPreferences) {
        val repo = CommuteRepository(applicationContext)
        val profiles = repo.loadProfiles().filter { it.enabled }
        if (profiles.isEmpty()) return

        val globalResult = graph.globalData.getOrLoad()
        val global =
            when (globalResult) {
                is ApiResult.Ok -> globalResult.data
                is ApiResult.Error -> return
            }

        val tz = EasternTimeInstant.timeZone
        val nowEt = EasternTimeInstant.now()
        val today = nowEt.local.date
        val todayDow = isoDayOfWeek(today)

        val serviceDateMs = NotificationActionReceiver.currentServiceDateMs(nowEt)

        for (profile in profiles) {
            if (profile.daysOfWeek.isEmpty()) continue
            if (!profile.daysOfWeek.contains(todayDow)) continue
            if (prefs.contains(muteKey(profile.id, serviceDateMs))) continue

            val fromStop = global.getStop(profile.fromStopId) ?: continue
            val toStop = global.getStop(profile.toStopId) ?: continue
            val stopIds =
                (global.stopIdsForScheduleFilter(fromStop) + global.stopIdsForScheduleFilter(toStop))
                    .distinct()

            val targetMinutes = profile.targetMinutesFromMidnight
            val windowStart = max(0, targetMinutes - profile.windowMinutesBefore)
            val windowEnd = min(24 * 60 - 1, targetMinutes + profile.windowMinutesAfter)

            val minTime = minutesToHHmm(windowStart)
            val maxTime = minutesToHHmm(windowEnd)

            val schedResult = graph.client.fetchScheduleForStopsInWindow(stopIds, minTime, maxTime)
            val schedule =
                when (schedResult) {
                    is ApiResult.Ok -> schedResult.data
                    is ApiResult.Error -> continue
                }

            val windowStartEt = atMinutesOnDate(today, windowStart, tz)
            val windowEndEt = atMinutesOnDate(today, windowEnd, tz)

            val trip =
                CommuteTripPlanner.findNextTripInWindow(
                    response = schedule,
                    globalData = global,
                    fromStopId = profile.fromStopId,
                    toStopId = profile.toStopId,
                    now = nowEt,
                    windowStart = windowStartEt,
                    windowEnd = windowEndEt,
                )
                    ?: continue

            val res = applicationContext.resources
            val fromName = plainStopLabel(profile.fromLabel, trip.fromStop, res)
            val leaveKey = "leave_${profile.id}_${trip.tripId}_${trip.departureTime.toEpochMilliseconds()}"
            val arrivalKey = "arr_${profile.id}_${trip.tripId}_${trip.arrivalTime.toEpochMilliseconds()}"

            val leadMs = profile.notifyLeadMinutes * 60_000L
            val leaveAtMs = trip.departureTime.toEpochMilliseconds() - leadMs
            val nowMs = nowEt.toEpochMilliseconds()

            // A due snooze re-posts the alert even though the original window has passed, and
            // even though the leave marker says it was already delivered — the user asked for it
            // again.
            val snoozed = dueSnoozeKey(prefs.all, profile.id, nowMs)
            if ((leaveAtMs <= nowMs && nowMs < leaveAtMs + WINDOW_MS) || snoozed != null) {
                if (snoozed != null || !prefs.contains(leaveKey)) {
                    val toName = plainStopLabel(profile.toLabel, trip.toStop, res)
                    val departureLine =
                        clockWithTrack(
                            applicationContext,
                            trip.departureTime.formatClock(use24Hour),
                            trip.fromPlatform,
                        )
                    val stopsAndTime =
                        applicationContext.getString(
                            R.string.notif_leave_text,
                            fromName,
                            toName,
                            departureLine,
                        )
                    val posted =
                        notify(
                            id = leaveKey.hashCode(),
                            channelId = TminusNotificationChannels.COMMUTE,
                            // The minute count moved out of the title and into a live chronometer
                            // in the header. A title baked at post time is wrong a minute later,
                            // and it was the reason `whenMs` had to be left unset: two countdowns
                            // on one notification drift apart. There is only one now, and it ticks.
                            title = applicationContext.getString(R.string.notif_leave_title),
                            text = stopsAndTime,
                            bigText = "$stopsAndTime\n${trip.route.label}",
                            subText = profile.name,
                            accentArgb = argbFromHexOrNull(trip.route.color),
                            countDownToMs = trip.departureTime.toEpochMilliseconds(),
                            actions =
                                commuteActions(
                                    profileId = profile.id,
                                    departureMs = trip.departureTime.toEpochMilliseconds(),
                                    nowMs = nowMs,
                                ),
                            timeoutAtMs =
                                trip.departureTime.toEpochMilliseconds() + LEAVE_GRACE_MS,
                        )
                    if (posted) markDelivered(prefs, leaveKey, nowMs)
                    snoozed?.let { prefs.edit().remove(it).apply() }
                }
            } else if (leaveAtMs > nowMs) {
                // Schedule a precise wakeup at the exact leave time so the notification arrives
                // at the user-chosen lead (e.g. 12 min before) instead of at the next 15-minute
                // periodic tick which drifts.
                PreciseNotificationScheduler.scheduleAt(
                    applicationContext,
                    tag = leaveKey,
                    triggerAtMs = leaveAtMs,
                )
            }

            if (profile.notifyOnArrival) {
                val arrMs = trip.arrivalTime.toEpochMilliseconds()
                if (arrMs <= nowMs && nowMs < arrMs + ARRIVAL_WINDOW_MS) {
                    if (!prefs.contains(arrivalKey)) {
                        val toName = plainStopLabel(profile.toLabel, trip.toStop, res)
                        val posted =
                            notify(
                                id = arrivalKey.hashCode(),
                                channelId = TminusNotificationChannels.COMMUTE,
                                title =
                                    applicationContext.getString(R.string.notif_arrival_title),
                                text =
                                    applicationContext.getString(
                                        R.string.notif_arrival_text,
                                        toName,
                                        clockWithTrack(
                                            applicationContext,
                                            trip.arrivalTime.formatClock(use24Hour),
                                            trip.toPlatform,
                                        ),
                                    ),
                                subText = profile.name,
                                accentArgb = argbFromHexOrNull(trip.route.color),
                                whenMs = trip.arrivalTime.toEpochMilliseconds(),
                                // An arrival ping is about a moment, not a standing state; retire
                                // it on the same grace period the leave notification uses rather
                                // than leaving it in the shade until it is swiped.
                                timeoutAtMs =
                                    trip.arrivalTime.toEpochMilliseconds() + LEAVE_GRACE_MS,
                            )
                        if (posted) markDelivered(prefs, arrivalKey, nowMs)
                    }
                } else if (arrMs > nowMs) {
                    PreciseNotificationScheduler.scheduleAt(
                        applicationContext,
                        tag = arrivalKey,
                        triggerAtMs = arrMs,
                    )
                }
            }
        }
    }

    private suspend fun runLastTrainNotifications(prefs: android.content.SharedPreferences) {
        val repo = LastTrainRepository(applicationContext)
        val profiles = repo.load().filter { it.enabled }
        if (profiles.isEmpty()) return

        val globalResult = graph.globalData.getOrLoad()
        val global =
            when (globalResult) {
                is ApiResult.Ok -> globalResult.data
                is ApiResult.Error -> return
            }

        val nowEt = EasternTimeInstant.now()
        val today = nowEt.local.date
        val todayDow = isoDayOfWeek(today)

        for (p in profiles) {
            if (p.daysOfWeek.isEmpty()) continue
            if (!p.daysOfWeek.contains(todayDow)) continue

            val stop = global.getStop(p.stopId) ?: continue
            val label = plainStopLabel(p.stopLabel, stop, applicationContext.resources)

            val depResult =
                when (p.mode) {
                    LastTrainMode.LAST ->
                        graph.client.fetchLastDepartureInWindow(
                            p.routeId,
                            p.directionId,
                            p.stopId,
                            minutesToHHmm(p.windowStartMinutes),
                            minutesToHHmm(p.windowEndMinutes),
                            serviceDate = nowEt.serviceDate,
                        )
                    LastTrainMode.FIRST ->
                        graph.client.fetchFirstDepartureInWindow(
                            p.routeId,
                            p.directionId,
                            p.stopId,
                            minutesToHHmm(p.firstWindowStartMinutes),
                            minutesToHHmm(p.firstWindowEndMinutes),
                            serviceDate = nowEt.serviceDate,
                        )
                }

            val dep =
                when (depResult) {
                    is ApiResult.Ok -> depResult.data.departure ?: continue
                    is ApiResult.Error -> continue
                }

            val notifyAt = dep.toEpochMilliseconds() - p.notifyMinutesBefore * 60_000L
            val nowMs = nowEt.toEpochMilliseconds()
            val key = "lt_${p.id}_${dep.toEpochMilliseconds()}"

            if (notifyAt <= nowMs && nowMs < notifyAt + WINDOW_MS) {
                if (!prefs.contains(key)) {
                    val modeLabel =
                        when (p.mode) {
                            LastTrainMode.LAST ->
                                applicationContext.getString(R.string.notif_last_train_title)
                            LastTrainMode.FIRST ->
                                applicationContext.getString(R.string.notif_first_train_title)
                        }
                    val route = global.getRoute(p.routeId)
                    val posted =
                        notify(
                            id = key.hashCode(),
                            channelId = TminusNotificationChannels.LAST_TRAIN,
                            title = modeLabel,
                            // This line used to be `dep.local.toString().take(16)`, which put a raw
                            // ISO timestamp ("2026-08-17T23:45") in front of the user and ignored
                            // the 12/24-hour preference the app asks them to choose.
                            text =
                                applicationContext.getString(
                                    R.string.notif_last_train_text,
                                    label,
                                    dep.formatClock(use24Hour),
                                ),
                            subText = p.name,
                            accentArgb = argbFromHexOrNull(route?.color),
                            whenMs = dep.toEpochMilliseconds(),
                        )
                    if (posted) markDelivered(prefs, key, nowMs)
                }
            } else if (notifyAt > nowMs) {
                PreciseNotificationScheduler.scheduleAt(
                    applicationContext,
                    tag = key,
                    triggerAtMs = notifyAt,
                )
            }
        }
    }

    private suspend fun runAccessibilityNotifications(prefs: android.content.SharedPreferences) {
        val repo = AccessibilityRepository(applicationContext)
        val watches = repo.load().filter { it.enabled }
        if (watches.isEmpty()) return

        val globalResult = graph.globalData.getOrLoad()
        val global =
            when (globalResult) {
                is ApiResult.Ok -> globalResult.data
                is ApiResult.Error -> return
            }

        val relevantEffects =
            setOf(
                "ELEVATOR_CLOSURE",
                "ESCALATOR_CLOSURE",
                "STOP_CLOSURE",
            )

        for (w in watches) {
            val stop = global.getStop(w.stopId) ?: continue
            val stopName = plainStopLabel(w.stopLabel, stop, applicationContext.resources)

            val alertsResult =
                graph.client.fetchAlertsForRoute(
                    routeId = w.routeId,
                    stopIds = global.stopIdsForScheduleFilter(stop),
                    // Without this the API applies its own BOARD/EXIT/RIDE default, which has no
                    // overlap with how lift and escalator outages are recorded. The watch list
                    // returned service alerts and never a single closure — the feature could not
                    // fire at all.
                    activities = ALERT_ACTIVITIES_ACCESSIBILITY,
                )
            val alerts =
                when (alertsResult) {
                    is ApiResult.Ok -> alertsResult.data
                    is ApiResult.Error -> continue
                }

            for (alert in alerts) {
                val effect = alert.effect ?: continue
                if (effect !in relevantEffects) continue

                val key = "acc_${w.id}_${alert.id}"
                if (prefs.contains(key)) continue

                val route = global.getRoute(w.routeId)
                val posted =
                    notify(
                        id = key.hashCode(),
                        channelId = TminusNotificationChannels.ACCESSIBILITY,
                        // The title is what a locked screen or a collapsed bundle shows, so it
                        // carries the news — which station, and what has failed — rather than the
                        // category name the icon already conveys. The MBTA's own sentence, with the
                        // unit number and the route it serves, stays in the body.
                        title = accessibilityTitle(effect, stopName),
                        // Alert headers run to a couple of sentences; BigTextStyle in [notify] is
                        // what lets the whole thing be read instead of one ellipsised line.
                        text = alert.header,
                        subText = w.name,
                        accentArgb = argbFromHexOrNull(route?.color),
                        category = NotificationCompat.CATEGORY_STATUS,
                        priority = NotificationCompat.PRIORITY_DEFAULT,
                    )
                if (posted) markDelivered(prefs, key, System.currentTimeMillis())
            }
        }
    }

    /** "Elevator out at Chinatown" rather than "Station accessibility alert". */
    private fun accessibilityTitle(effect: String, stopName: String): String =
        when (effect) {
            "ELEVATOR_CLOSURE" ->
                applicationContext.getString(R.string.notif_accessibility_title_elevator, stopName)
            "ESCALATOR_CLOSURE" ->
                applicationContext.getString(R.string.notif_accessibility_title_escalator, stopName)
            "STOP_CLOSURE" ->
                applicationContext.getString(
                    R.string.notif_accessibility_title_stop_closed,
                    stopName,
                )
            // Unreachable while the caller filters on the three above, but a new effect added to
            // that set should degrade to the old generic wording rather than to a blank title.
            else -> applicationContext.getString(R.string.notif_accessibility_title)
        }

    private fun isoDayOfWeek(date: LocalDate): Int =
        when (date.dayOfWeek) {
            kotlinx.datetime.DayOfWeek.MONDAY -> 1
            kotlinx.datetime.DayOfWeek.TUESDAY -> 2
            kotlinx.datetime.DayOfWeek.WEDNESDAY -> 3
            kotlinx.datetime.DayOfWeek.THURSDAY -> 4
            kotlinx.datetime.DayOfWeek.FRIDAY -> 5
            kotlinx.datetime.DayOfWeek.SATURDAY -> 6
            kotlinx.datetime.DayOfWeek.SUNDAY -> 7
        }

    /**
     * Minutes-from-midnight as an MBTA service-day `HH:mm`.
     *
     * Hours are **not** clamped to 23: the MBTA expresses after-midnight service as 24:00–26:59 on
     * the previous service day, and "last train home" windows routinely need it. Clamping here is
     * what made it impossible to ask about a train leaving at 00:40.
     */
    private fun minutesToHHmm(minutes: Int): String {
        val total = minutes.coerceIn(0, SERVICE_DAY_MAX_MINUTES)
        val h = total / 60
        val m = total % 60
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }

    private fun atMinutesOnDate(
        date: LocalDate,
        minutesFromMidnight: Int,
        tz: TimeZone,
    ): EasternTimeInstant {
        val h = minutesFromMidnight / 60
        val m = minutesFromMidnight % 60
        val ldt = LocalDateTime(date, LocalTime(h, m, 0, 0))
        return EasternTimeInstant(ldt.toInstant(tz))
    }

    /**
     * Posts one notification in the platform's own shape.
     *
     * The previous version supplied a `RemoteViews` that painted the whole notification in the
     * route's colour and forced the text colours to match. That fights every system it lands on:
     * the route colour sat inside the system's own background instead of replacing it, and the
     * fixed text colours ignored dark mode, Material You and the user's font settings. The line
     * colour now goes where Android expects an app accent — [NotificationCompat.Builder.setColor],
     * which tints the small icon and header — and the body is a plain big-text style so a long
     * service alert expands instead of being cut off at one line.
     *
     * Returns whether the notification actually reached the shade. Callers record a delivery
     * marker only on `true`: a run that is dropped for want of POST_NOTIFICATIONS must stay
     * retryable, and a marker written regardless would silence the event for good.
     */
    private fun notify(
        id: Int,
        channelId: String,
        title: String,
        text: String,
        bigText: String = text,
        subText: String? = null,
        accentArgb: Int? = null,
        whenMs: Long? = null,
        /**
         * Departure to count down to in the header, as a live chronometer.
         *
         * Mutually exclusive with a countdown in [title]: two countdowns on one notification drift
         * apart as the shade redraws, which is why [whenMs] alone was previously left unset.
         */
        countDownToMs: Long? = null,
        timeoutAtMs: Long? = null,
        category: String = NotificationCompat.CATEGORY_REMINDER,
        priority: Int = NotificationCompat.PRIORITY_HIGH,
        actions: List<NotificationActionSpec> = emptyList(),
    ): Boolean {
        // Inline rather than delegated to a helper so lint can see the guard, and because Android
        // 13+ silently drops notifications without this permission — recording them as "delivered"
        // would suppress the retry.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pi =
            PendingIntent.getActivity(
                applicationContext,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val builder =
            NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(R.drawable.ic_stat_tminus)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setColor(accentArgb ?: applicationContext.getColor(R.color.widget_accent))
                .setPriority(priority)
                .setCategory(category)
                // A commute alert is no use behind "contents hidden" on the lock screen, which is
                // where it is most likely to be read.
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                // Grouped per channel so a morning with several alerts bundles instead of
                // filling the shade.
                .setGroup(channelId)
                .setContentIntent(pi)
                .setAutoCancel(true)

        subText?.let(builder::setSubText)
        if (whenMs != null) {
            builder.setWhen(whenMs).setShowWhen(true)
        }
        // A chronometer ticks on its own, so the header stays true between redraws instead of
        // going stale the moment it is posted. It replaces the minute count in the title rather
        // than joining it — see [countDownToMs].
        if (countDownToMs != null) {
            builder
                .setWhen(countDownToMs)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        // "Leave in 12 min" is wrong once the train has gone; let it retire itself rather than sit
        // in the shade until the user swipes it.
        timeoutAtMs?.let { at ->
            val remaining = at - System.currentTimeMillis()
            if (remaining > 0) builder.setTimeoutAfter(remaining)
        }

        for (spec in actions) {
            builder.addAction(
                0,
                applicationContext.getString(spec.labelRes),
                NotificationActionReceiver.pendingIntent(applicationContext, id, spec),
            )
        }

        NotificationManagerCompat.from(applicationContext).notify(id, builder.build())
        return true
    }

    private fun commuteActions(
        profileId: String,
        departureMs: Long,
        nowMs: Long,
    ): List<NotificationActionSpec> =
        listOfNotNull(
            // Offered only when it can actually happen. On a short-headway route the next train is
            // routinely closer than the snooze interval, and a button that silently does nothing
            // is worse than no button — the receiver would drop the snooze and the alert would sit
            // there looking unchanged.
            NotificationActionSpec(
                action = NotificationActionReceiver.ACTION_SNOOZE,
                labelRes = R.string.notif_action_snooze,
                extras =
                    mapOf(
                        NotificationActionReceiver.EXTRA_PROFILE_ID to profileId,
                        NotificationActionReceiver.EXTRA_DEPARTURE_MS to departureMs.toString(),
                    ),
            ).takeIf { snoozeLandsBeforeDeparture(departureMs, nowMs) },
            NotificationActionSpec(
                action = NotificationActionReceiver.ACTION_MUTE_TODAY,
                labelRes = R.string.notif_action_mute_today,
                extras = mapOf(NotificationActionReceiver.EXTRA_PROFILE_ID to profileId),
            ),
        )

    /** Records that a notification went out, so the next run stays quiet about the same event. */
    private fun markDelivered(
        prefs: android.content.SharedPreferences,
        key: String,
        atMs: Long,
    ) {
        prefs.edit().putLong(key, atMs).apply()
    }

    /** Drops delivery markers past their retention — see [expiredDeliveryKeys]. */
    private fun pruneDeliveredKeys(prefs: android.content.SharedPreferences) {
        val expired = expiredDeliveryKeys(prefs.all, System.currentTimeMillis())
        if (expired.isEmpty()) return
        prefs.edit().apply { expired.forEach { remove(it) } }.apply()
    }

    public companion object {
        public const val UNIQUE_NAME: String = "TminusNotifications"
        private const val PREFS_STATE = "tminus_notif_state"

        /** Latest minute a service day can reach (26:59), so after-midnight trains are expressible. */
        private const val SERVICE_DAY_MAX_MINUTES = 27 * 60 - 1

        /**
         * How long a "leave now" notification stays in the shade after the train has actually
         * left. Past that it is only telling the user about a train they have missed.
         */
        private const val LEAVE_GRACE_MS = 60_000L * 10
        /**
         * Fire the "leave" notification only if we're this close to the exact target time
         * ([leaveAtMs] / [notifyAt]). The main firing mechanism is now a precise WorkManager
         * wakeup scheduled by [PreciseNotificationScheduler], so this window is just a safety net
         * for late ticks. Keeping it tight avoids firing a "leave in 12 min" notification 30+
         * minutes off target.
         */
        private const val WINDOW_MS = 60_000L * 5
        private const val ARRIVAL_WINDOW_MS = 60_000L * 5
    }
}
