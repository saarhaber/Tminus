package com.saarlabs.tminus

import android.app.Application
import android.util.Log
import com.saarlabs.tminus.model.response.ApiResult
import com.saarlabs.tminus.model.response.GlobalData
import com.saarlabs.tminus.network.MbtaV3Client
import com.saarlabs.tminus.usecases.WidgetStationBoardUseCase
import com.saarlabs.tminus.usecases.WidgetTripUseCase
import com.saarlabs.tminus.commute.CommuteRepository
import com.saarlabs.tminus.features.LastTrainRepository
import com.saarlabs.tminus.android.widget.LiveUpdateManager
import com.saarlabs.tminus.android.widget.WidgetUpdateWorker
import java.io.File
import kotlin.jvm.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

public class TminusApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        refreshNetworking()
        // Load persisted stations from disk (fast, no network) so widget configuration and
        // notifications work immediately on cold start. If the cache is missing or stale, a
        // background refresh is kicked off — but the UI never has to wait for it.
        GlobalDataStore.warmFromDisk(this)
        GlobalDataStore.preloadIfNeeded()
        CommuteRepository.ensureWorkerScheduled(this)
        LastTrainRepository.ensureWorker(this)
        WidgetUpdateWorker.ensurePeriodicRefresh(this)
        // The 15 min WorkManager floor is too coarse for a per-minute countdown, so kick the 60 s
        // exact-alarm chain whenever the user has any widgets placed.
        runCatching { LiveUpdateManager.ensureRunningIfNeeded(this) }
            .onFailure { Log.w("TminusApplication", "live update ensure failed", it) }
    }

    public companion object {
        lateinit var instance: TminusApplication
            private set

        lateinit var widgetTripUseCase: WidgetTripUseCase
            private set

        lateinit var widgetStationBoardUseCase: WidgetStationBoardUseCase
            private set

        fun refreshNetworking() {
            val prefs = instance.getSharedPreferences(SettingsKeys.PREFS, MODE_PRIVATE)
            val v3 = prefs.getString(SettingsKeys.KEY_V3_API, null)
            if (GlobalDataStore.isClientReady()) {
                runCatching { GlobalDataStore.client.close() }
            }
            val client = MbtaV3Client(v3)
            widgetTripUseCase = WidgetTripUseCase(client)
            widgetStationBoardUseCase = WidgetStationBoardUseCase(client)
            GlobalDataStore.client = client
        }
    }
}

internal object GlobalDataStore {
    lateinit var client: MbtaV3Client

    private val mutex = Mutex()
    @Volatile
    private var cached: GlobalData? = null
    @Volatile
    private var cachedAtMs: Long = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private const val CACHE_FILE = "global_data_cache.json"
    private const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
    private const val TAG = "GlobalDataStore"

    /** False until [TminusApplication] finishes [TminusApplication.refreshNetworking]. */
    internal fun isClientReady(): Boolean = ::client.isInitialized

    /**
     * Glance can run very early; wait briefly so [refreshNetworking] has assigned [client].
     */
    suspend fun awaitClientReady(timeoutMs: Long = 3000L) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (!isClientReady() && System.nanoTime() < deadline) {
            delay(50)
        }
    }

    /**
     * Populates the in-memory cache synchronously from the disk copy written by a previous run.
     * Call once on app start before any widget/config code runs so they never hit the network just
     * to show a station picker.
     */
    fun warmFromDisk(app: android.content.Context) {
        if (cached != null) return
        val file = File(app.filesDir, CACHE_FILE)
        if (!file.exists()) return
        runCatching {
            val raw = file.readText()
            val parsed = json.decodeFromString(GlobalData.serializer(), raw)
            cached = parsed
            cachedAtMs = file.lastModified()
        }.onFailure { Log.w(TAG, "warmFromDisk failed", it) }
    }

    /**
     * Fire-and-forget refresh if the in-memory cache is missing or older than [CACHE_TTL_MS].
     * Safe to call many times — coalesced by [getOrLoad]'s mutex.
     */
    fun preloadIfNeeded() {
        val stale = cached == null ||
            (System.currentTimeMillis() - cachedAtMs) > CACHE_TTL_MS
        if (!stale) return
        scope.launch {
            awaitClientReady(timeoutMs = 10_000L)
            if (!isClientReady()) return@launch
            runCatching { getOrLoad(forceRefresh = cached != null) }
                .onFailure { Log.w(TAG, "preload refresh failed", it) }
        }
    }

    suspend fun getOrLoad(forceRefresh: Boolean = false): ApiResult<GlobalData> =
        mutex.withLock {
            if (!isClientReady()) {
                return ApiResult.Error(message = "Network client not initialized")
            }
            if (!forceRefresh) {
                cached?.let { return ApiResult.Ok(it) }
            }
            when (val r = client.fetchGlobalData()) {
                is ApiResult.Ok -> {
                    cached = r.data
                    cachedAtMs = System.currentTimeMillis()
                    persistToDisk(r.data)
                    r
                }
                is ApiResult.Error -> {
                    // Fall back to the previous in-memory cache (possibly loaded from disk) so a
                    // transient network blip / 429 doesn't break widgets that were already working.
                    cached?.let { ApiResult.Ok(it) } ?: r
                }
            }
        }

    private fun persistToDisk(data: GlobalData) {
        val app = runCatching { TminusApplication.instance }.getOrNull() ?: return
        runCatching {
            val tmp = File(app.filesDir, "$CACHE_FILE.tmp")
            tmp.writeText(json.encodeToString(GlobalData.serializer(), data))
            val dest = File(app.filesDir, CACHE_FILE)
            if (!tmp.renameTo(dest)) {
                dest.writeText(tmp.readText())
                tmp.delete()
            }
        }.onFailure { Log.w(TAG, "persistToDisk failed", it) }
    }

    fun invalidate() {
        cached = null
        cachedAtMs = 0L
        runCatching {
            val app = TminusApplication.instance
            File(app.filesDir, CACHE_FILE).delete()
        }
    }
}
