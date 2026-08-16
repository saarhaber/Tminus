package com.saarlabs.tminus.android.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.saarlabs.tminus.commute.CommuteRepository
import com.saarlabs.tminus.features.LastTrainRepository

/**
 * Restores background work after a reboot or an app update.
 *
 * Alarms do not survive a restart, and WorkManager's own restore only covers work it already knows
 * about. Without this, live widget updates stayed dead until the user next opened the app — the
 * countdown fell back to the 15-minute worker with no indication anything was wrong.
 */
public class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            -> Unit
            else -> return
        }
        val appCtx = context.applicationContext
        runCatching {
            CommuteRepository.ensureWorkerScheduled(appCtx)
            LastTrainRepository.ensureWorker(appCtx)
            WidgetUpdateWorker.ensurePeriodicRefresh(appCtx)
            LiveUpdateManager.ensureRunningIfNeeded(appCtx)
        }.onFailure { Log.w(TAG, "could not restore scheduled work after boot", it) }
    }

    private companion object {
        const val TAG = "BootCompletedReceiver"
    }
}
