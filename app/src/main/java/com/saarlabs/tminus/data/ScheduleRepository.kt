package com.saarlabs.tminus.data

import android.content.Context
import android.util.Log
import com.saarlabs.tminus.model.Schedule
import com.saarlabs.tminus.model.Trip
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.model.response.ScheduleResponse
import com.saarlabs.tminus.network.MbtaV3Client
import com.saarlabs.tminus.util.EasternTimeInstant
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Caches timetable lookups so the per-minute widget countdown does not hit the network.
 *
 * A schedule request asks for every departure from "now" to the end of the service day, and MBTA
 * timetables do not change within a day. Once fetched, the same response answers every later tick —
 * the widget just re-filters it against the current time. Without this, each 60 s tick issued one
 * `/schedules` request *per placed widget*, which drained battery and burned through the anonymous
 * rate limit.
 *
 * Entries are also written to [Context.getCacheDir] so a tick that starts a fresh process (the
 * common case — the app is not running between alarms) still avoids the network.
 */
internal class ScheduleRepository(
    private val client: MbtaV3Client,
    private val cacheDir: File,
) {

    @Serializable
    private data class CachedSchedules(
        val serviceDate: String,
        val fetchedAtMs: Long,
        val coversFromMs: Long,
        val schedules: List<Schedule>,
        val trips: Map<String, Trip>,
    )

    private val memory = ConcurrentHashMap<String, CachedSchedules>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * Departures at [stopIds] from [now] to the end of the current service day.
     *
     * Returns cached data when it is younger than [TTL_MS], covers [now], and still contains at
     * least one departure in the future; otherwise refetches. On a network failure with usable
     * cached data, the cached data wins so a placed widget does not flip to an error state on a
     * transient blip.
     */
    suspend fun scheduleForStops(
        stopIds: List<String>,
        now: EasternTimeInstant,
        forceRefresh: Boolean = false,
    ): ApiResult<ScheduleResponse> {
        val key = cacheKey(stopIds)
        val mutex = locks.getOrPut(key) { Mutex() }
        return mutex.withLock {
            if (!forceRefresh) {
                usable(key, now)?.let { return@withLock ApiResult.Ok(it.toResponse()) }
            }
            when (val fresh = client.fetchScheduleForStops(stopIds, now)) {
                is ApiResult.Ok -> {
                    val entry =
                        CachedSchedules(
                            serviceDate = now.serviceDate.toString(),
                            fetchedAtMs = System.currentTimeMillis(),
                            coversFromMs = now.toEpochMilliseconds(),
                            schedules = fresh.data.schedules,
                            trips = fresh.data.trips,
                        )
                    memory[key] = entry
                    persist(key, entry)
                    fresh
                }
                is ApiResult.Error ->
                    // Prefer stale-but-real data over an error card.
                    cached(key)
                        ?.takeIf { it.serviceDate == now.serviceDate.toString() }
                        ?.let { ApiResult.Ok(it.toResponse()) }
                        ?: fresh
            }
        }
    }

    /** Drops every cached entry (e.g. after the API key changes). */
    fun invalidate() {
        memory.clear()
        runCatching { cacheDir.listFiles()?.forEach { if (it.name.startsWith(FILE_PREFIX)) it.delete() } }
    }

    private fun usable(key: String, now: EasternTimeInstant): CachedSchedules? {
        val entry = cached(key) ?: return null
        if (entry.serviceDate != now.serviceDate.toString()) return null
        if (System.currentTimeMillis() - entry.fetchedAtMs > TTL_MS) return null
        val nowMs = now.toEpochMilliseconds()
        // The window starts at the time of the fetch; a request for an earlier moment is not covered.
        if (nowMs < entry.coversFromMs) return null
        val hasFuture =
            entry.schedules.any { schedule ->
                val t = schedule.departureTime ?: schedule.arrivalTime
                t != null && t.toEpochMilliseconds() >= nowMs
            }
        return entry.takeIf { hasFuture }
    }

    private fun cached(key: String): CachedSchedules? =
        memory[key] ?: readFromDisk(key)?.also { memory[key] = it }

    private fun readFromDisk(key: String): CachedSchedules? =
        runCatching {
            val file = File(cacheDir, FILE_PREFIX + key)
            if (!file.exists()) return null
            json.decodeFromString(CachedSchedules.serializer(), file.readText())
        }.onFailure { Log.w(TAG, "schedule cache read failed", it) }.getOrNull()

    private suspend fun persist(key: String, entry: CachedSchedules) {
        withContext(Dispatchers.IO) {
            runCatching {
                cacheDir.mkdirs()
                val tmp = File(cacheDir, "$FILE_PREFIX$key.tmp")
                tmp.writeText(json.encodeToString(CachedSchedules.serializer(), entry))
                val dest = File(cacheDir, FILE_PREFIX + key)
                if (!tmp.renameTo(dest)) {
                    dest.writeText(tmp.readText())
                    tmp.delete()
                }
            }.onFailure { Log.w(TAG, "schedule cache write failed", it) }
        }
    }

    private fun CachedSchedules.toResponse() = ScheduleResponse(schedules = schedules, trips = trips)

    private fun cacheKey(stopIds: List<String>): String =
        stopIds.sorted().joinToString(",").hashCode().toUInt().toString(16)

    private companion object {
        const val TAG = "ScheduleRepository"
        const val FILE_PREFIX = "schedules_"

        /** Timetables are static for the day; an hour bounds staleness after a published change. */
        const val TTL_MS = 60L * 60L * 1000L

        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
