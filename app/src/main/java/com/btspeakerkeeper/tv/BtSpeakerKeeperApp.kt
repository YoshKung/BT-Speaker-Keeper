package com.btspeakerkeeper.tv

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.btspeakerkeeper.tv.control.PeriodicReconnectScheduler
import com.btspeakerkeeper.tv.control.ReconnectCoordinator
import com.btspeakerkeeper.tv.core.TriggerSource

class BtSpeakerKeeperApp : Application() {
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) {
                ReconnectCoordinator.requestReconnect(context, TriggerSource.SCREEN_ON)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        PeriodicReconnectScheduler.syncPeriodicWork(this)
    }
}
