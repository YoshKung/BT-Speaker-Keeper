package com.btspeakerkeeper.tv.control

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.btspeakerkeeper.tv.core.TriggerSource

class ReconnectWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val trigger = inputData.getString(KEY_TRIGGER)
            ?.let { runCatching { TriggerSource.valueOf(it) }.getOrNull() }
            ?: TriggerSource.PERIODIC
        ReconnectCoordinator.requestReconnect(applicationContext, trigger)
        return Result.success()
    }

    companion object {
        const val KEY_TRIGGER = "trigger"
    }
}
