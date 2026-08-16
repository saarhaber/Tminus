package com.saarlabs.tminus

import android.content.Context
import com.saarlabs.tminus.data.GlobalDataRepository
import com.saarlabs.tminus.data.ScheduleRepository
import com.saarlabs.tminus.network.MbtaV3Client
import com.saarlabs.tminus.usecases.WidgetStationBoardUseCase
import com.saarlabs.tminus.usecases.WidgetTripUseCase
import java.io.File

/**
 * The application's object graph.
 *
 * Built lazily on first use from any [Context], so widgets, workers and broadcast receivers never
 * race application startup. The previous design initialised `lateinit` statics in
 * `Application.onCreate` and had every caller poll `awaitClientReady()` for up to 15 seconds; a
 * process started directly into a widget update could still miss it. Here, whoever asks first
 * builds the graph.
 */
public class AppGraph private constructor(context: Context) {

    private val app: Context = context.applicationContext

    public val settings: SettingsStore = SettingsStore(app)

    @Volatile
    private var apiClient: MbtaV3Client = MbtaV3Client(settings.apiKey())

    @Volatile
    private var scheduleRepository: ScheduleRepository = newScheduleRepository(apiClient)

    /** The MBTA client for the currently configured API key. */
    public val client: MbtaV3Client
        get() = apiClient

    internal val schedules: ScheduleRepository
        get() = scheduleRepository

    public val globalData: GlobalDataRepository = GlobalDataRepository(app) { apiClient }

    internal val tripUseCase: WidgetTripUseCase
        get() = WidgetTripUseCase(scheduleRepository)

    internal val stationBoardUseCase: WidgetStationBoardUseCase
        get() = WidgetStationBoardUseCase(scheduleRepository)

    /**
     * Rebuilds the networking stack after the API key changes, and drops data fetched with the old
     * key so rate-limit behaviour and results are consistent.
     */
    public fun onApiKeyChanged() {
        val previous = apiClient
        val next = MbtaV3Client(settings.apiKey())
        apiClient = next
        scheduleRepository.invalidate()
        scheduleRepository = newScheduleRepository(next)
        globalData.invalidate()
        runCatching { previous.close() }
    }

    private fun newScheduleRepository(client: MbtaV3Client) =
        ScheduleRepository(client, File(app.cacheDir, "schedules"))

    public companion object {
        @Volatile
        private var graph: AppGraph? = null

        /** The graph if it already exists, without creating one. */
        public val instance: AppGraph?
            get() = graph

        public fun from(context: Context): AppGraph =
            graph ?: synchronized(this) {
                graph ?: AppGraph(context).also { graph = it }
            }
    }
}
