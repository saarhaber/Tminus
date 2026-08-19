package com.saarlabs.tminus.commute

import com.saarlabs.tminus.util.EasternTimeInstant
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/**
 * When a "time to leave" alert is due, and which departure it can be about.
 *
 * These are the two halves of one rule and they are kept in one file because they only work as a
 * pair: the alert fires in a window of [LEAVE_ALERT_WINDOW_MINUTES] around the leave time, so trip
 * selection has to hold the same trip steady for exactly that long. When the two disagreed the
 * feature was silently dead — see [commuteDepartureLowerBound].
 */

/**
 * How late a leave alert may still be posted after its exact leave time.
 *
 * The precise wakeup ([com.saarlabs.tminus.commute.worker.PreciseNotificationScheduler]) is what
 * normally posts the alert; this is the slack that absorbs a late wakeup or a periodic tick.
 */
internal const val LEAVE_ALERT_WINDOW_MINUTES: Int = 5

/** Latest minute a service day can reach (26:59), so after-midnight trains are expressible. */
internal const val SERVICE_DAY_MAX_MINUTES: Int = 27 * 60 - 1

/**
 * Earliest departure a leave alert may be about, given a [leadMinutes] lead.
 *
 * A departure sooner than `now + lead` is one the user cannot reach in the time they asked for, so
 * selecting it produces an alert that is either unactionable or — as it did until this bound
 * existed — never posted at all. The old lower bound was `max(now, windowStart)`, i.e. always the
 * soonest train; the worker then only posted when `departure - now` landed in
 * `(lead - 5, lead]`, which on a route with a 5-minute headway is unsatisfiable for every lead of
 * roughly 10 minutes or more. Nothing ever fired.
 *
 * The bound is relaxed by [graceMinutes] on purpose. Without it the selection would step to the
 * next train at the very moment the alert came due: the wakeup lands a hair *after* the leave time,
 * `departure >= now + lead` no longer holds for the trip we woke up for, and the same bug
 * reappears one train later, forever. Holding the trip for as long as the alert may still be posted
 * is what makes the two halves agree.
 */
internal fun commuteDepartureLowerBound(
    now: EasternTimeInstant,
    windowStart: EasternTimeInstant,
    leadMinutes: Int,
    graceMinutes: Int = LEAVE_ALERT_WINDOW_MINUTES,
): EasternTimeInstant {
    val leadBound = now + (leadMinutes - graceMinutes).minutes
    // `now` is in the max because a short lead (or a large grace) can push `leadBound` into the
    // past, and an alert about a train that has already gone is worse than no alert.
    return maxOf(now, windowStart, leadBound)
}

/** Whether the leave alert for a departure at [leaveAtMs] is due as of [nowMs]. */
internal fun isLeaveAlertDue(
    leaveAtMs: Long,
    nowMs: Long,
    windowMinutes: Int = LEAVE_ALERT_WINDOW_MINUTES,
): Boolean = leaveAtMs <= nowMs && nowMs < leaveAtMs + windowMinutes * 60_000L

/**
 * The schedule lookup window for [profile] on [serviceDate], in service-day minutes and as
 * instants.
 *
 * Minutes are service-day minutes, not clock minutes: an MBTA service day runs to 26:59, so a
 * commute targeted at 23:50 with a 45-minute tail ends at 24:35 — 00:35 the following calendar
 * morning. Clamping that to 23:59, as this used to, deleted the departures either side of midnight
 * from the window entirely.
 */
internal fun commuteWindow(serviceDate: LocalDate, profile: CommuteProfile): CommuteWindow {
    val target = serviceDayMinutes(profile.targetMinutesFromMidnight)
    val startMinutes = (target - profile.windowMinutesBefore).coerceAtLeast(0)
    val endMinutes = (target + profile.windowMinutesAfter).coerceAtMost(SERVICE_DAY_MAX_MINUTES)
    return CommuteWindow(
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        start = serviceDayInstant(serviceDate, startMinutes),
        end = serviceDayInstant(serviceDate, endMinutes),
    )
}

internal data class CommuteWindow(
    val startMinutes: Int,
    val endMinutes: Int,
    val start: EasternTimeInstant,
    val end: EasternTimeInstant,
)

/**
 * A stored target as a minute of the *service* day.
 *
 * The editor stores a clock time, so a 00:20 commute arrives here as minute 20. Taken literally
 * against a service date that is the number of a morning 24 hours earlier, and a service day that
 * has not started yet at minute 20, it names the wrong day and falls outside its own window. The
 * MBTA writes that departure as 24:20 of the previous service day, and so do we.
 */
internal fun serviceDayMinutes(targetMinutesFromMidnight: Int): Int =
    if (targetMinutesFromMidnight < SERVICE_DAY_START_MINUTES) {
        targetMinutesFromMidnight + 24 * 60
    } else {
        targetMinutesFromMidnight
    }

/** Service-day minute [minutes] on [serviceDate], rolling past midnight for hours >= 24. */
internal fun serviceDayInstant(serviceDate: LocalDate, minutes: Int): EasternTimeInstant {
    val clamped = minutes.coerceIn(0, SERVICE_DAY_MAX_MINUTES)
    val date = serviceDate.plus(DatePeriod(days = clamped / (24 * 60)))
    val withinDay = clamped % (24 * 60)
    return EasternTimeInstant(LocalDateTime(date, LocalTime(withinDay / 60, withinDay % 60)))
}

/** Service-day minutes as the `HH:mm` the MBTA API expects, where `HH` may be 24–26. */
internal fun serviceDayHHmm(minutes: Int): String {
    val total = minutes.coerceIn(0, SERVICE_DAY_MAX_MINUTES)
    return "${(total / 60).toString().padStart(2, '0')}:${(total % 60).toString().padStart(2, '0')}"
}

/** [EasternTimeInstant.serviceDate] flips at 03:00; the same boundary governs stored targets. */
private const val SERVICE_DAY_START_MINUTES = 3 * 60
