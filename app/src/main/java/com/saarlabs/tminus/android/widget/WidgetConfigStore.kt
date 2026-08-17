package com.saarlabs.tminus.android.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.saarlabs.tminus.model.WidgetAlertsConfig
import com.saarlabs.tminus.model.WidgetFavoritesConfig
import com.saarlabs.tminus.model.WidgetStationBoardConfig
import com.saarlabs.tminus.model.WidgetTripConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private const val PREFS_NAME = "widget_config"
private const val KEY_PENDING_CONFIG_WIDGET_ID = "pending_config_widget_id"
private const val KEY_PENDING_STATION_BOARD_WIDGET_ID = "pending_station_board_widget_id"
private const val KEY_REFRESH_TICK = "refresh_tick"
private const val TAG = "WidgetConfigStore"

/**
 * Per-widget configuration, stored as JSON in [SharedPreferences].
 *
 * Configs are exposed as [Flow]s so a running Glance session picks up a save immediately: the
 * widget composition collects the flow, and the configuration activity's write emits into it. This
 * is what makes "configure, then see the widget render" work — [androidx.glance.appwidget.GlanceAppWidget.update]
 * only recomposes content that is already registered, so anything read *before* `provideContent`
 * would stay stale for the lifetime of the session.
 *
 * Values written by older builds used a newline-joined format; [migrateLegacy] rewrites those in
 * place the first time they are read.
 */
internal class WidgetConfigStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences
        get() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- trip widget ---

    fun tripConfig(appWidgetId: Int): WidgetTripConfig? =
        read(tripKey(appWidgetId), WidgetTripConfig.serializer()) { legacyTripConfig(it) }

    fun tripConfigFlow(appWidgetId: Int): Flow<WidgetTripConfig?> =
        configFlow(tripKey(appWidgetId)) { tripConfig(appWidgetId) }

    fun setTripConfig(appWidgetId: Int, config: WidgetTripConfig) {
        write(tripKey(appWidgetId), json.encodeToString(WidgetTripConfig.serializer(), config))
    }

    fun removeTripConfig(appWidgetId: Int) {
        prefs.edit().remove(tripKey(appWidgetId)).apply()
    }

    // --- station board widget ---

    fun stationBoardConfig(appWidgetId: Int): WidgetStationBoardConfig? =
        read(stationKey(appWidgetId), WidgetStationBoardConfig.serializer()) {
            legacyStationBoardConfig(it)
        }

    fun stationBoardConfigFlow(appWidgetId: Int): Flow<WidgetStationBoardConfig?> =
        configFlow(stationKey(appWidgetId)) { stationBoardConfig(appWidgetId) }

    fun setStationBoardConfig(appWidgetId: Int, config: WidgetStationBoardConfig) {
        write(
            stationKey(appWidgetId),
            json.encodeToString(WidgetStationBoardConfig.serializer(), config),
        )
    }

    fun removeStationBoardConfig(appWidgetId: Int) {
        prefs.edit().remove(stationKey(appWidgetId)).apply()
    }

    // --- favorites widget ---

    fun favoritesConfig(appWidgetId: Int): WidgetFavoritesConfig? =
        read(favoritesKey(appWidgetId), WidgetFavoritesConfig.serializer()) { legacyFavoritesConfig(it) }

    fun favoritesConfigFlow(appWidgetId: Int): Flow<WidgetFavoritesConfig?> =
        configFlow(favoritesKey(appWidgetId)) { favoritesConfig(appWidgetId) }

    fun setFavoritesConfig(appWidgetId: Int, config: WidgetFavoritesConfig) {
        write(
            favoritesKey(appWidgetId),
            json.encodeToString(WidgetFavoritesConfig.serializer(), config),
        )
    }

    fun removeFavoritesConfig(appWidgetId: Int) {
        prefs.edit().remove(favoritesKey(appWidgetId)).apply()
    }

    // --- service alerts widget ---

    fun alertsConfig(appWidgetId: Int): WidgetAlertsConfig? =
        read(alertsKey(appWidgetId), WidgetAlertsConfig.serializer()) { legacyAlertsConfig(it) }

    fun alertsConfigFlow(appWidgetId: Int): Flow<WidgetAlertsConfig?> =
        configFlow(alertsKey(appWidgetId)) { alertsConfig(appWidgetId) }

    fun setAlertsConfig(appWidgetId: Int, config: WidgetAlertsConfig) {
        write(alertsKey(appWidgetId), json.encodeToString(WidgetAlertsConfig.serializer(), config))
    }

    fun removeAlertsConfig(appWidgetId: Int) {
        prefs.edit().remove(alertsKey(appWidgetId)).apply()
    }

    // --- refresh signal ---
    //
    // A counter bumped by every refresh trigger (alarm tick, manual refresh, worker). Widget
    // compositions collect it so a refresh always produces a new composition — and therefore a
    // recomputed countdown — even when nothing else about the widget's state has changed.

    fun refreshTickFlow(): Flow<Long> =
        configFlow(KEY_REFRESH_TICK) { refreshTick() }.map { it ?: 0L }

    fun refreshTick(): Long = prefs.getLong(KEY_REFRESH_TICK, 0L)

    fun bumpRefreshTick() {
        prefs.edit().putLong(KEY_REFRESH_TICK, System.currentTimeMillis()).apply()
    }

    // --- pending-id fallback ---
    //
    // Some launchers start the configuration activity without EXTRA_APPWIDGET_ID. The widget stores
    // the id it is waiting on so the activity can recover it.

    fun setPendingTripConfigWidgetId(appWidgetId: Int) {
        prefs.edit().putInt(KEY_PENDING_CONFIG_WIDGET_ID, appWidgetId).apply()
    }

    fun peekPendingTripConfigWidgetId(): Int = peekPending(KEY_PENDING_CONFIG_WIDGET_ID)

    fun clearPendingTripConfigWidgetId() {
        prefs.edit().remove(KEY_PENDING_CONFIG_WIDGET_ID).apply()
    }

    fun setPendingStationBoardConfigWidgetId(appWidgetId: Int) {
        prefs.edit().putInt(KEY_PENDING_STATION_BOARD_WIDGET_ID, appWidgetId).apply()
    }

    fun peekPendingStationBoardConfigWidgetId(): Int =
        peekPending(KEY_PENDING_STATION_BOARD_WIDGET_ID)

    fun clearPendingStationBoardConfigWidgetId() {
        prefs.edit().remove(KEY_PENDING_STATION_BOARD_WIDGET_ID).apply()
    }

    /**
     * Reads without consuming. Consuming on read meant a configuration activity that was recreated
     * — a rotation is enough — found nothing the second time and closed itself, so the widget was
     * never configured. The id is cleared once a configuration is actually saved.
     */
    private fun peekPending(key: String): Int =
        prefs.getInt(key, AppWidgetManager.INVALID_APPWIDGET_ID)

    // --- internals ---

    private fun tripKey(appWidgetId: Int) = "config_$appWidgetId"

    private fun stationKey(appWidgetId: Int) = "station_board_config_$appWidgetId"

    private fun favoritesKey(appWidgetId: Int) = "favorites_config_$appWidgetId"

    private fun alertsKey(appWidgetId: Int) = "alerts_config_$appWidgetId"

    private fun write(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    private fun <T : Any> read(
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        migrateLegacy: (String) -> T?,
    ): T? {
        val raw = prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: return null
        if (raw.trimStart().startsWith("{")) {
            return runCatching { json.decodeFromString(serializer, raw) }
                .onFailure { Log.w(TAG, "could not decode $key", it) }
                .getOrNull()
        }
        val migrated = migrateLegacy(raw) ?: return null
        write(key, json.encodeToString(serializer, migrated))
        return migrated
    }

    /**
     * Emits the current value, then again on every write to [key]. Conflated because a widget only
     * ever needs the latest configuration.
     */
    private fun <T : Any> configFlow(key: String, load: () -> T?): Flow<T?> =
        callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
                    if (changed == null || changed == key) trySend(load())
                }
            val registered = prefs
            registered.registerOnSharedPreferenceChangeListener(listener)
            trySend(load())
            awaitClose { registered.unregisterOnSharedPreferenceChangeListener(listener) }
        }
            .conflate()
            .distinctUntilChanged()

    private fun legacyTripConfig(raw: String): WidgetTripConfig? {
        val lines = raw.split("\n")
        if (lines.size < 4) return null
        val from = lines[0].trim()
        val to = lines[1].trim()
        if (from.isEmpty() || to.isEmpty()) return null
        return WidgetTripConfig(
            fromStopId = from,
            toStopId = to,
            fromLabel = lines.getOrNull(2)?.trim().orEmpty(),
            toLabel = lines.getOrNull(3)?.trim().orEmpty(),
        )
    }

    private fun legacyFavoritesConfig(raw: String): WidgetFavoritesConfig? {
        val lines = raw.split("\n")
        val count = lines.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
        return WidgetFavoritesConfig(
            count = count,
            sortBySoonest = lines.getOrNull(1)?.trim()?.toBoolean() ?: false,
        )
    }

    private fun legacyAlertsConfig(raw: String): WidgetAlertsConfig? {
        val ids = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return if (ids.isEmpty()) null else WidgetAlertsConfig(lineGroupIds = ids)
    }

    private fun legacyStationBoardConfig(raw: String): WidgetStationBoardConfig? {
        val lines = raw.split("\n")
        val stopId = lines.firstOrNull()?.trim().orEmpty()
        if (stopId.isEmpty()) return null
        return WidgetStationBoardConfig(
            stopId = stopId,
            stopLabel = lines.getOrNull(1)?.trim().orEmpty(),
            routeId = lines.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
