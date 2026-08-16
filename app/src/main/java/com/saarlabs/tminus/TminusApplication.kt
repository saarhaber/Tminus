package com.saarlabs.tminus

import android.app.Application
import android.util.Log
import com.saarlabs.tminus.android.widget.LiveUpdateManager
import com.saarlabs.tminus.android.widget.WidgetUpdateWorker
import com.saarlabs.tminus.commute.CommuteRepository
import com.saarlabs.tminus.commute.worker.TminusNotificationChannels
import com.saarlabs.tminus.features.LastTrainRepository

/**
 * Application startup.
 *
 * Deliberately does almost nothing: [AppGraph] builds itself on first use, and the ~1.7 MB stop
 * catalogue is warmed on a background dispatcher rather than parsed here. An earlier version parsed
 * it synchronously in this method, which put that work on the main thread of every cold start —
 * including starts triggered by a widget update or a background worker.
 */
public class TminusApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val graph = AppGraph.from(this)
        graph.globalData.warmInBackground()

        runCatching {
            TminusNotificationChannels.ensureChannels(this)
            CommuteRepository.ensureWorkerScheduled(this)
            LastTrainRepository.ensureWorker(this)
            WidgetUpdateWorker.ensurePeriodicRefresh(this)
            LiveUpdateManager.ensureRunningIfNeeded(this)
        }.onFailure { Log.w(TAG, "startup scheduling failed", it) }
    }

    private companion object {
        const val TAG = "TminusApplication"
    }
}
