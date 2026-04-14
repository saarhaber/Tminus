package com.saarlabs.tminus.android.util

import com.saarlabs.tminus.util.EasternTimeInstant

internal fun EasternTimeInstant.formattedTime(use24Hour: Boolean): String {
    val h = local.hour
    val min = local.minute
    return if (use24Hour) {
        "%d:%02d".format(h, min)
    } else {
        val am = h < 12
        val h12 = ((h + 11) % 12) + 1
        "%d:%02d %s".format(h12, min, if (am) "AM" else "PM")
    }
}
