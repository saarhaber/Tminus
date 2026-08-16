package com.saarlabs.tminus.data

import android.content.Context
import android.util.Log
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.network.MbtaV3Client
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * The MBTA stop and route catalogue, cached in memory and on disk.
 *
 * The on-disk copy is around **1.7 MB of JSON**. An earlier version parsed it synchronously from
 * `Application.onCreate`, which put that parse on the main thread of every cold start — including
 * starts triggered by a widget update or a background worker. Loading is now suspending and runs on
 * [Dispatchers.IO]; nothing blocks the first frame.
 */
public class GlobalDataRepository internal constructor(
    context: Context,
    private val clientProvider: () -> MbtaV3Client,
) {

    private val app = context.applicationContext
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cached: GlobalData? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    @Volatile
    private var diskChecked: Boolean = false

    /**
     * The catalogue, from memory, disk, or the network in that order.
     *
     * On a network failure with a usable cached copy, the cached copy wins: a transient blip or a
     * 429 should not break widgets that were already working.
     */
    public suspend fun getOrLoad(forceRefresh: Boolean = false): ApiResult<GlobalData> =
        mutex.withLock {
            if (!forceRefresh) {
                cached?.let { return@withLock ApiResult.Ok(it) }
                loadFromDisk()?.let { return@withLock ApiResult.Ok(it) }
            }
            when (val fresh = clientProvider().fetchGlobalData()) {
                is ApiResult.Ok -> {
                    cached = fresh.data
                    cachedAtMs = System.currentTimeMillis()
                    persist(fresh.data)
                    fresh
                }
                is ApiResult.Error ->
                    cached?.let { ApiResult.Ok(it) }
                        ?: loadFromDisk()?.let { ApiResult.Ok(it) }
                        ?: fresh
            }
        }

    /** The catalogue if it is already in memory; never touches disk or the network. */
    public fun cachedOrNull(): GlobalData? = cached

    /**
     * Warms the cache in the background and refreshes it if the copy on disk is stale. Safe to call
     * repeatedly — [getOrLoad]'s mutex coalesces concurrent work.
     */
    public fun warmInBackground() {
        scope.launch {
            runCatching {
                getOrLoad()
                if (System.currentTimeMillis() - cachedAtMs > CACHE_TTL_MS) {
                    getOrLoad(forceRefresh = true)
                }
            }.onFailure { Log.w(TAG, "warm failed", it) }
        }
    }

    public fun invalidate() {
        cached = null
        cachedAtMs = 0L
        diskChecked = false
        runCatching { File(app.filesDir, CACHE_FILE).delete() }
    }

    private suspend fun loadFromDisk(): GlobalData? {
        if (diskChecked) return cached
        return withContext(Dispatchers.IO) {
            diskChecked = true
            val file = File(app.filesDir, CACHE_FILE)
            if (!file.exists()) return@withContext null
            runCatching {
                val parsed = json.decodeFromString(GlobalData.serializer(), file.readText())
                cached = parsed
                cachedAtMs = file.lastModified()
                parsed
            }.onFailure { Log.w(TAG, "disk cache read failed", it) }.getOrNull()
        }
    }

    private suspend fun persist(data: GlobalData) {
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(app.filesDir, "$CACHE_FILE.tmp")
                tmp.writeText(json.encodeToString(GlobalData.serializer(), data))
                val dest = File(app.filesDir, CACHE_FILE)
                if (!tmp.renameTo(dest)) {
                    dest.writeText(tmp.readText())
                    tmp.delete()
                }
            }.onFailure { Log.w(TAG, "disk cache write failed", it) }
        }
    }

    private companion object {
        const val TAG = "GlobalDataRepository"
        const val CACHE_FILE = "global_data_cache.json"
        const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
