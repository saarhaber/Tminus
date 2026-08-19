package com.saarlabs.tminus.commute.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.saarlabs.tminus.R

/**
 * Notification channels, created once at startup.
 *
 * Three separate channels because the alerts are unrelated: someone who wants "time to leave" pings
 * may not want an elevator-outage notification at 2 a.m., and Android only lets users make that
 * distinction per channel. Creation used to happen inside every `notify()` call, which also meant a
 * user's own channel tweaks were re-applied on every notification.
 */
internal object TminusNotificationChannels {

    const val COMMUTE = "commute_v2"
    const val LAST_TRAIN = "last_train_v1"
    const val ACCESSIBILITY = "accessibility_v1"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannels(
            listOf(
                channel(context, COMMUTE, R.string.notif_channel_commute, NotificationManager.IMPORTANCE_HIGH),
                channel(context, LAST_TRAIN, R.string.notif_channel_last_train, NotificationManager.IMPORTANCE_HIGH),
                channel(
                    context,
                    ACCESSIBILITY,
                    R.string.notif_channel_accessibility,
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            ),
        )
    }

    private fun channel(
        context: Context,
        id: String,
        nameRes: Int,
        importance: Int,
    ): NotificationChannel =
        NotificationChannel(id, context.getString(nameRes), importance).apply { setShowBadge(true) }
}
