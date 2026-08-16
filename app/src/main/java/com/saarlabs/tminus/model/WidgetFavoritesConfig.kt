package com.saarlabs.tminus.model

import kotlinx.serialization.Serializable

/** Per-instance config for the favorites departures widget. */
@Serializable
public data class WidgetFavoritesConfig(
    val count: Int,
    val sortBySoonest: Boolean = false,
)
