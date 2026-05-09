package com.btspeakerkeeper.tv.control

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.btspeakerkeeper.tv.core.TriggerSource
import com.btspeakerkeeper.tv.data.AppPrefs
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object PeriodicReconnectScheduler {
    private const val UNIQUE_PERIODIC_WORK = "bt_speaker_keeper_periodic"
    private const val UNIQUE_BOOT_WORK = "bt_speaker_keeper_boot"

    fun syncPeriodicWork(context: Context) {
        val appContext = context.applicationContext
        val settings = AppPrefs(appContext).getSettings()
        val workManager = WorkManager.getInstance(appContext)

        if (!settings.autoConnectEnabled || !settings.periodicRetryEnabled) {
            workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK)
            return
        }

        val intervalMinutes = settings.periodicRetryMinutes.coerceAtLeast(15).toLong()
        val request = PeriodicWorkRequestBuilder<ReconnectWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setInputData(workDataOf(ReconnectWorker.KEY_TRIGGER to TriggerSource.PERIODIC.name))
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun scheduleBootReconnect(context: Context) {
        val appContext = context.applicationContext
        val delaySeconds = Random.nextLong(20L, 31L)
        val request = OneTimeWorkRequestBuilder<ReconnectWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setInputData(workDataOf(ReconnectWorker.KEY_TRIGGER to TriggerSource.BOOT.name))
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_BOOT_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
