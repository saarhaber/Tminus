package com.saarlabs.tminus.commute.worker

/**
 * Bookkeeping for "this notification has already been delivered".
 *
 * Every fired notification writes a marker keyed by what it was about, so a worker run that sees
 * the same trip again stays quiet. Markers have to be pruned or the file grows for the life of the
 * install, and pruning is where this went wrong: expiry used to be read out of the key's own last
 * `_` segment, which is an epoch for trip keys (`leave_<profile>_<trip>_<epochMs>`) but an MBTA
 * alert id for accessibility keys (`acc_<watch>_<alertId>`). A six-digit alert id parses as a
 * timestamp from 1970, so every accessibility marker looked expired the moment it was written and
 * the same elevator outage notified again on every run.
 *
 * Markers now store their delivery time as the value, and expiry reads that.
 */

/** Trip alerts are about a moment that has passed; three days is plenty to stay quiet about it. */
private const val TRIP_RETENTION_MS = 3L * 24 * 60 * 60 * 1000

/**
 * Service alerts outlive a trip — an elevator can be out for weeks — so their markers are kept far
 * longer. The user still hears about a genuinely long outage again eventually, which is intended.
 */
private const val ALERT_RETENTION_MS = 30L * 24 * 60 * 60 * 1000

/** Marker keys for service alerts, which are the ones with no timestamp of their own. */
private const val ALERT_KEY_PREFIX = "acc_"

/**
 * Any epoch below this is not a delivery time — it is an id that happens to be numeric. 2001-09-09,
 * the round 10^12 ms mark, is comfortably before this app existed and far above any MBTA id.
 */
private const val PLAUSIBLE_EPOCH_FLOOR_MS = 1_000_000_000_000L

internal fun deliveryRetentionMs(key: String): Long =
    if (key.startsWith(ALERT_KEY_PREFIX)) ALERT_RETENTION_MS else TRIP_RETENTION_MS

/**
 * The markers in [entries] that are past their retention at [nowMs].
 *
 * Values written by this build are the delivery time. Values written by older builds are `true`,
 * and fall back to the epoch in the key — which trip keys carry and alert keys do not, so a legacy
 * alert marker is dropped once on upgrade. That costs at most one repeated notification per alert
 * still active at the time, and is the only way to tell it from a marker written in 1970.
 */
internal fun expiredDeliveryKeys(entries: Map<String, *>, nowMs: Long): List<String> =
    entries.mapNotNull { (key, value) ->
        val deliveredAt = (value as? Long) ?: legacyKeyTimestamp(key)
        if (deliveredAt == null || deliveredAt < nowMs - deliveryRetentionMs(key)) key else null
    }

private fun legacyKeyTimestamp(key: String): Long? =
    key.substringAfterLast('_').toLongOrNull()?.takeIf { it >= PLAUSIBLE_EPOCH_FLOOR_MS }

/**
 * `#DA291C` (or `DA291C`) as an opaque ARGB int, or null when the route has no usable colour.
 *
 * MBTA sometimes returns an empty string rather than omitting the field, and a route the catalogue
 * does not know about yields no colour at all — both have to mean "use the app's own accent"
 * rather than crashing a notification.
 */
internal fun argbFromHexOrNull(hex: String?): Int? {
    val clean = hex?.trim()?.removePrefix("#")?.takeIf { it.length == 6 || it.length == 8 } ?: return null
    val value = clean.toLongOrNull(16) ?: return null
    return if (clean.length == 8) value.toInt() else (0xFF000000L or value).toInt()
}
