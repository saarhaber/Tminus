package com.saarlabs.tminus.commute.worker

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.saarlabs.tminus.util.EasternTimeInstant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant

/** One action button below a notification body. */
internal data class NotificationActionSpec(
    val action: String,
    val labelRes: Int,
    val extras: Map<String, String>,
)

/**
 * Handles the two commute-notification actions.
 *
 * Both write into the same prefs file the worker uses for delivery markers, in the same shape —
 * a `Long` value that [expiredDeliveryKeys] can read — so there is one store and one prune path.
 * Writing a `Boolean` here would send these keys down the legacy fallback in [expiredDeliveryKeys]
 * and expire them wrongly.
 */
internal class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: return
        val nowMs = System.currentTimeMillis()

        when (intent.action) {
            ACTION_SNOOZE -> {
                // Extras cross the PendingIntent as strings, so parse rather than getLongExtra,
                // which would silently yield 0 and drop every snooze.
                val departureMs =
                    intent.getStringExtra(EXTRA_DEPARTURE_MS)?.toLongOrNull() ?: return
                // A train that leaves before the snooze would land is not worth waking someone
                // for. Note this does *not* return: the button was tapped, so it has to do
                // something visible, and falling through cancels the alert. The action is
                // normally not offered at all in this case — see `commuteActions` — so this only
                // catches the train becoming imminent between posting and the tap.
                if (snoozeLandsBeforeDeparture(departureMs, nowMs)) {
                    val fireAtMs = nowMs + SNOOZE_MS
                    prefs.edit().putLong(snoozeKey(profileId, fireAtMs), fireAtMs).apply()
                    PreciseNotificationScheduler.scheduleAt(
                        context,
                        tag = snoozeKey(profileId, fireAtMs),
                        triggerAtMs = fireAtMs,
                    )
                }
            }

            ACTION_MUTE_TODAY ->
                prefs.edit()
                    .putLong(muteKey(profileId, currentServiceDateMs()), nowMs)
                    .apply()

            else -> return
        }

        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    internal companion object {
        const val ACTION_SNOOZE = "com.saarlabs.tminus.action.SNOOZE"
        const val ACTION_MUTE_TODAY = "com.saarlabs.tminus.action.MUTE_TODAY"

        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_DEPARTURE_MS = "departure_ms"

        const val PREFS_STATE = "tminus_notif_state"

        internal fun pendingIntent(
            context: Context,
            notificationId: Int,
            spec: NotificationActionSpec,
        ): PendingIntent {
            val intent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = spec.action
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                spec.extras.forEach { (key, value) -> putExtra(key, value) }
            }
            return PendingIntent.getBroadcast(
                context,
                // The action is folded into the request code so snooze and mute on one
                // notification do not collapse onto the same PendingIntent.
                notificationId * 31 + spec.action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Midnight of the current *service* date as epoch millis.
         *
         * Service days run past midnight, so at 00:30 this is the previous calendar date — muting
         * then mutes the commute already in progress rather than tomorrow's.
         */
        internal fun currentServiceDateMs(now: EasternTimeInstant = EasternTimeInstant.now()): Long =
            midnightOf(now.serviceDate)

        private fun midnightOf(date: LocalDate): Long =
            LocalDateTime(date, LocalTime(0, 0))
                .toInstant(EasternTimeInstant.timeZone)
                .toEpochMilliseconds()
    }
}

/** How far ahead "Snooze 5 min" pushes the alert. */
internal const val SNOOZE_MS: Long = 5 * 60_000L

/**
 * Whether a snooze taken at [nowMs] would still land before [departureMs].
 *
 * One rule, two callers, deliberately: the worker asks it at post time so it never offers a snooze
 * that cannot happen, and the receiver asks it again on tap because the train moves closer in
 * between. It matters most on short-headway routes, where the next departure is routinely nearer
 * than the snooze interval — offering a button there that silently did nothing was the bug.
 */
internal fun snoozeLandsBeforeDeparture(departureMs: Long, nowMs: Long): Boolean =
    departureMs > nowMs + SNOOZE_MS

/** Marker for "this profile is muted for the rest of the service day". */
internal fun muteKey(profileId: String, serviceDateMs: Long): String =
    "mute_${profileId}_$serviceDateMs"

/** Marker for "re-post this profile's leave alert at [fireAtMs]". */
internal fun snoozeKey(profileId: String, fireAtMs: Long): String =
    "snooze_${profileId}_$fireAtMs"

/**
 * The due snooze marker for [profileId] at [nowMs], or null.
 *
 * A snooze fires off its own marker rather than being re-derived through the worker's ±5-minute
 * window, which a five-minute snooze lands exactly on the boundary of.
 */
internal fun dueSnoozeKey(entries: Map<String, *>, profileId: String, nowMs: Long): String? {
    val prefix = "snooze_${profileId}_"
    return entries.keys.firstOrNull { key ->
        key.startsWith(prefix) && (key.substringAfterLast('_').toLongOrNull() ?: Long.MAX_VALUE) <= nowMs
    }
}
